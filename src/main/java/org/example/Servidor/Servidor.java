package org.example.Servidor;

import org.example.Protocolo.ProtocoloHTTP;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Flujo:
 * 1. Espera conexiones de jugadores (POST /conectar)
 * 2. Admin escribe NEXT para avanzar de pregunta
 * 3. Envía pregunta a todos (Tipo-Mensaje: PREGUNTA)
 * 4. Recibe respuestas (POST /respuesta)
 * 5. Calcula ranking por velocidad y envía resultados
 * 6. Al acabar las preguntas, envía ranking final (Tipo-Mensaje: FIN)
 */
public class Servidor {

    private static final int PUERTO = 8080;
    private static final int TIMEOUT_RESPUESTA_SEGUNDOS = 30;

    // Clientes conectados
    private static Set<HilosCliente> listaClientes = ConcurrentHashMap.newKeySet();

    // Control de preguntas
    private static int preguntaActual = 0;
    private static volatile boolean preguntaActiva = false;
    private static volatile long tiempoInicioPregunta;

    // Respuestas de la ronda actual
    private static Map<HilosCliente, RespuestaCliente> respuestas = new ConcurrentHashMap<>();

    // Ranking acumulado: nombre -> puntos totales
    private static Map<String, int[]> rankingAcumulado = new ConcurrentHashMap<>();
    // int[0] = puntos, int[1] = correctas, int[2] = totales

    // Lista de preguntas
    private static final List<Pregunta> preguntas = Arrays.asList(
            new Pregunta("¿Cuántas veces se ha dormido Hugo en clase?",
                    "Una", "Dos", "Todas", "No se", 'C'),
            new Pregunta("En una escala del 1 al 10 cuando quiere Pozo ser funcionario",
                    "1", "Con todo su corazón", "No quiere, es liberal", "Se queja de vicio", 'D'),
            new Pregunta("¿Cómo es la relación de Kristian con Claude?",
                    "Profesional", "Muy íntima", "Nocturna", "Le es infiel con Gemini", 'C')
    );

    // Clase para almacenar preguntas
    static class Pregunta {
        String texto;
        String opcionA, opcionB, opcionC, opcionD;
        char respuestaCorrecta; // 'A', 'B', 'C' o 'D'

        Pregunta(String texto, String a, String b, String c, String d, char correcta) {
            this.texto = texto;
            this.opcionA = a;
            this.opcionB = b;
            this.opcionC = c;
            this.opcionD = d;
            this.respuestaCorrecta = Character.toUpperCase(correcta);
        }

        String formatear(int numero) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n========================================\n");
            sb.append("PREGUNTA ").append(numero).append(": ").append(texto).append("\n");
            sb.append("----------------------------------------\n");
            sb.append("  A) ").append(opcionA).append("\n");
            sb.append("  B) ").append(opcionB).append("\n");
            sb.append("  C) ").append(opcionC).append("\n");
            sb.append("  D) ").append(opcionD).append("\n");
            sb.append("========================================\n");
            sb.append("Responde con A, B, C o D:");
            return sb.toString();
        }

        boolean esCorrecta(char respuesta) {
            return Character.toUpperCase(respuesta) == respuestaCorrecta;
        }
    }

    // Clase para almacenar respuestas con tiempo
    static class RespuestaCliente {
        char respuesta;
        long tiempoMs;
        boolean correcta;

        RespuestaCliente(char respuesta, long tiempoMs, boolean correcta) {
            this.respuesta = respuesta;
            this.tiempoMs = tiempoMs;
            this.correcta = correcta;
        }
    }

    // =============================================
    // MAIN
    // =============================================

    public static void main(String[] args) {
        ExecutorService pool = Executors.newFixedThreadPool(20);

        System.out.println("===========================================");
        System.out.println("    SERVIDOR QUIZ - org.example");
        System.out.println("===========================================");
        System.out.println("Puerto: " + PUERTO);
        System.out.println("Preguntas: " + preguntas.size());
        System.out.println("Timeout: " + TIMEOUT_RESPUESTA_SEGUNDOS + "s");
        System.out.println("Esperando jugadores...\n");

        // Hilo para aceptar conexiones
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
                while (true) {
                    Socket socket = serverSocket.accept();
                    System.out.println("[INFO] Nueva conexión desde " + socket.getInetAddress());
                    HilosCliente cliente = new HilosCliente(socket);
                    pool.execute(cliente);
                }
            } catch (IOException e) {
                System.err.println("[ERROR] Error en el servidor: " + e.getMessage());
            }
        }).start();

        // Consola del admin
        consolaAdmin();
    }

    // =============================================
    // CONSOLA DEL ADMIN
    // =============================================

    private static void consolaAdmin() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Comandos del admin:");
        System.out.println("  NEXT o ENTER  -> Siguiente pregunta");
        System.out.println("  ESTADO        -> Ver jugadores conectados");
        System.out.println("  SALIR         -> Cerrar servidor\n");

        while (true) {
            String comando = scanner.nextLine().trim().toLowerCase();

            if (comando.equals("salir")) {
                System.out.println("Cerrando servidor...");
                System.exit(0);
            } else if (comando.equals("estado")) {
                System.out.println("Jugadores conectados: " + listaClientes.size());
                for (HilosCliente c : listaClientes) {
                    System.out.println("  - " + c.getNombre());
                }
            } else if (comando.equals("next") || comando.isEmpty()) {
                if (listaClientes.isEmpty()) {
                    System.out.println("[WARN] No hay jugadores conectados!");
                    continue;
                }
                siguientePregunta();
            }
        }
    }

    // =============================================
    // LÓGICA DEL QUIZ
    // =============================================

    public static synchronized void siguientePregunta() {
        if (preguntaActual >= preguntas.size()) {
            // Quiz terminado: enviar ranking final
            String rankingFinal = generarRankingFinal();
            System.out.println("\n[QUIZ] === QUIZ FINALIZADO ===");
            System.out.println(rankingFinal);
            broadcastFin(rankingFinal);
            return;
        }

        // Resetear ronda
        respuestas.clear();
        preguntaActiva = true;
        preguntaActual++;
        tiempoInicioPregunta = System.currentTimeMillis();

        Pregunta p = preguntas.get(preguntaActual - 1);
        String preguntaTexto = p.formatear(preguntaActual);

        System.out.println("\n[QUIZ] Pregunta " + preguntaActual + "/" + preguntas.size() +
                ": " + p.texto);
        System.out.println("[QUIZ] Respuesta correcta: " + p.respuestaCorrecta);

        // Enviar pregunta a todos via protocolo HTTP
        for (HilosCliente cliente : listaClientes) {
            cliente.enviarPregunta(preguntaTexto);
        }

        // Temporizador de timeout
        new Thread(() -> {
            try {
                Thread.sleep(TIMEOUT_RESPUESTA_SEGUNDOS * 1000L);
                if (preguntaActiva) {
                    mostrarResultados();
                }
            } catch (InterruptedException e) {
                // ignorar
            }
        }).start();
    }

    /**
     * Procesa una respuesta recibida de un cliente.
     * Llamado desde HilosCliente cuando recibe POST /respuesta.
     */
    public static synchronized void procesarRespuesta(HilosCliente cliente, char respuesta) {
        if (!preguntaActiva) {
            cliente.enviarError("No hay pregunta activa. Espera a la siguiente.");
            return;
        }

        if (respuestas.containsKey(cliente)) {
            cliente.enviarError("Ya has respondido a esta pregunta.");
            return;
        }

        respuesta = Character.toUpperCase(respuesta);
        if (respuesta != 'A' && respuesta != 'B' && respuesta != 'C' && respuesta != 'D') {
            cliente.enviarError("Respuesta inválida. Usa A, B, C o D.");
            return;
        }

        long tiempoMs = System.currentTimeMillis() - tiempoInicioPregunta;
        Pregunta p = preguntas.get(preguntaActual - 1);
        boolean correcta = p.esCorrecta(respuesta);

        respuestas.put(cliente, new RespuestaCliente(respuesta, tiempoMs, correcta));

        // Confirmar al jugador que su respuesta fue registrada
        cliente.enviarEsperando("Respuesta '" + respuesta + "' registrada en " +
                tiempoMs + "ms. Esperando al resto...");

        System.out.println("[QUIZ] Respuesta de " + cliente.getNombre() + ": " + respuesta +
                " (" + tiempoMs + "ms)" + (correcta ? " CORRECTO" : " INCORRECTO") +
                " (" + respuestas.size() + "/" + listaClientes.size() + ")");

        // Si todos respondieron, mostrar resultados
        if (respuestas.size() >= listaClientes.size() && !listaClientes.isEmpty()) {
            System.out.println("[QUIZ] Todos han respondido.");
            mostrarResultados();
        }
    }

    /**
     * Calcula puntuaciones, envía resultados y ranking parcial.
     */
    private static synchronized void mostrarResultados() {
        if (!preguntaActiva) return;
        preguntaActiva = false;

        Pregunta p = preguntas.get(preguntaActual - 1);
        long timeoutMs = TIMEOUT_RESPUESTA_SEGUNDOS * 1000L;

        // Calcular puntos y enviar resultado individual a cada jugador
        for (Map.Entry<HilosCliente, RespuestaCliente> entry : respuestas.entrySet()) {
            HilosCliente cliente = entry.getKey();
            RespuestaCliente resp = entry.getValue();
            String nombre = cliente.getNombre();

            // Inicializar ranking si no existe
            rankingAcumulado.putIfAbsent(nombre, new int[]{0, 0, 0});
            int[] stats = rankingAcumulado.get(nombre);
            stats[2]++; // totales

            if (resp.correcta) {
                // Puntos por velocidad: más rápido = más puntos (100-1000)
                int puntos = (int) (1000 - (900.0 * resp.tiempoMs / timeoutMs));
                puntos = Math.max(100, Math.min(1000, puntos));
                stats[0] += puntos; // puntos
                stats[1]++;         // correctas

                cliente.enviarResultado("¡CORRECTO! +" + puntos + " puntos (" +
                        String.format("%.1f", resp.tiempoMs / 1000.0) + "s)");
            } else {
                cliente.enviarResultado("INCORRECTO. La respuesta era: " + p.respuestaCorrecta);
            }
        }

        // Jugadores que no respondieron
        for (HilosCliente cliente : listaClientes) {
            if (!respuestas.containsKey(cliente)) {
                String nombre = cliente.getNombre();
                rankingAcumulado.putIfAbsent(nombre, new int[]{0, 0, 0});
                rankingAcumulado.get(nombre)[2]++;
                cliente.enviarResultado("TIEMPO AGOTADO. La respuesta era: " + p.respuestaCorrecta);
            }
        }

        // Generar y enviar ranking parcial
        String rankingStr = generarRanking(preguntaActual, preguntas.size());

        System.out.println("[QUIZ] --- Resultados pregunta " + preguntaActual + " ---");
        for (Map.Entry<HilosCliente, RespuestaCliente> entry : respuestas.entrySet()) {
            System.out.println("  " + entry.getKey().getNombre() + ": " +
                    entry.getValue().respuesta + " -> " +
                    (entry.getValue().correcta ? "CORRECTO" : "INCORRECTO"));
        }
        for (HilosCliente c : listaClientes) {
            if (!respuestas.containsKey(c)) {
                System.out.println("  " + c.getNombre() + ": SIN RESPUESTA");
            }
        }

        for (HilosCliente cliente : listaClientes) {
            cliente.enviarRanking(rankingStr);
        }

        // Si quedan preguntas, enviar NEXT tras una pausa
        if (preguntaActual < preguntas.size()) {
            new Thread(() -> {
                try { Thread.sleep(3000); } catch (InterruptedException e) { }
                for (HilosCliente cliente : listaClientes) {
                    cliente.enviarNext();
                }
            }).start();
        } else {
            // Última pregunta: enviar FIN
            new Thread(() -> {
                try { Thread.sleep(2000); } catch (InterruptedException e) { }
                String rankingFinal = generarRankingFinal();
                System.out.println("\n[QUIZ] === QUIZ FINALIZADO ===");
                System.out.println(rankingFinal);
                broadcastFin(rankingFinal);
            }).start();
        }
    }

    // =============================================
    // RANKING
    // =============================================

    private static String generarRanking(int preguntaNum, int totalPreguntas) {
        List<Map.Entry<String, int[]>> lista = new ArrayList<>(rankingAcumulado.entrySet());
        lista.sort((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));

        StringBuilder sb = new StringBuilder();
        sb.append("--- RANKING (Pregunta ").append(preguntaNum)
                .append("/").append(totalPreguntas).append(") ---\n");

        int pos = 1;
        for (Map.Entry<String, int[]> entry : lista) {
            String medalla = "";
            if (pos == 1) medalla = " [1ro]";
            else if (pos == 2) medalla = " [2do]";
            else if (pos == 3) medalla = " [3ro]";

            int[] stats = entry.getValue();
            sb.append("  ").append(pos).append(". ")
                    .append(entry.getKey()).append(" - ")
                    .append(stats[0]).append(" pts (")
                    .append(stats[1]).append("/").append(stats[2]).append(" correctas)")
                    .append(medalla).append("\n");
            pos++;
        }
        sb.append("-----------------------------------");
        return sb.toString();
    }

    private static String generarRankingFinal() {
        List<Map.Entry<String, int[]>> lista = new ArrayList<>(rankingAcumulado.entrySet());
        lista.sort((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));

        StringBuilder sb = new StringBuilder();
        sb.append("============================================\n");
        sb.append("        RANKING FINAL DEL QUIZ\n");
        sb.append("============================================\n");

        int pos = 1;
        for (Map.Entry<String, int[]> entry : lista) {
            String medalla = "";
            if (pos == 1) medalla = " *** GANADOR ***";
            else if (pos == 2) medalla = " ** Segundo **";
            else if (pos == 3) medalla = " * Tercero *";

            int[] stats = entry.getValue();
            sb.append("  ").append(pos).append(". ")
                    .append(entry.getKey()).append(" - ")
                    .append(stats[0]).append(" pts (")
                    .append(stats[1]).append("/").append(stats[2]).append(" correctas)")
                    .append(medalla).append("\n");
            pos++;
        }
        sb.append("============================================\n");
        sb.append("¡Gracias por jugar!");
        return sb.toString();
    }

    // =============================================
    // BROADCAST Y GESTIÓN DE CLIENTES
    // =============================================

    private static void broadcastFin(String rankingFinal) {
        for (HilosCliente cliente : listaClientes) {
            cliente.enviarFin(rankingFinal);
        }
    }

    public static void registrarCliente(HilosCliente cliente) {
        listaClientes.add(cliente);
        rankingAcumulado.putIfAbsent(cliente.getNombre(), new int[]{0, 0, 0});
        System.out.println("[INFO] Jugador registrado: " + cliente.getNombre() +
                " (Total: " + listaClientes.size() + ")");
    }

    public static void removerCliente(HilosCliente cliente) {
        listaClientes.remove(cliente);
        respuestas.remove(cliente);
        System.out.println("[INFO] Desconectado: " + cliente.getNombre() +
                " (Total: " + listaClientes.size() + ")");
    }
}