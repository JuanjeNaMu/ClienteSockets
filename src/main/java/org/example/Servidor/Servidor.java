package org.example.Servidor;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class Servidor {
    private static Set<HilosCliente> listaClientes = ConcurrentHashMap.newKeySet();
    private static AtomicInteger preguntaActual = new AtomicInteger(0);
    private static boolean preguntaActiva = false;
    private static long tiempoInicioPregunta;
    private static Map<HilosCliente, RespuestaCliente> respuestas = new ConcurrentHashMap<>();

    // Lista de preguntas
    private static final List<Pregunta> preguntas = Arrays.asList(
            new Pregunta("¿Cuántas veces se ha dormido Hugo en clase?",
                    "A) Una", "B) Dos", "C) Todas", "D) No se", "C"),
            new Pregunta("En una escala del 1 al 10 cuando quiere Pozo ser funcionario",
                    "A) 1", "B) Con todo su corazón", "C) No quiere, es liberal", "D) Se queja de vicio", "D"),
            new Pregunta("¿Cómo es la relación de Kristian con Claude?",
                    "A) Profesional", "B) Muy íntima", "C) Nocturna", "D) Le es infiel con Gemini", "C")
    );

    // Clase para almacenar preguntas y sus opciones
    static class Pregunta {
        String texto;
        String opcionA, opcionB, opcionC, opcionD;
        String respuestaCorrecta;

        Pregunta(String texto, String a, String b, String c, String d, String correcta) {
            this.texto = texto;
            this.opcionA = a;
            this.opcionB = b;
            this.opcionC = c;
            this.opcionD = d;
            this.respuestaCorrecta = correcta;
        }

        String obtenerTextoCompleto() {
            return texto + "\n" + opcionA + "\n" + opcionB + "\n" + opcionC + "\n" + opcionD;
        }
    }

    // Clase para almacenar respuestas de clientes con tiempo
    static class RespuestaCliente {
        String respuesta;
        long tiempoRespuesta;
        boolean correcta;

        RespuestaCliente(String respuesta, long tiempo, boolean correcta) {
            this.respuesta = respuesta;
            this.tiempoRespuesta = tiempo;
            this.correcta = correcta;
        }
    }

    public static void main(String[] args) {
        ExecutorService pool = Executors.newFixedThreadPool(10);

        try(ServerSocket serverSocket = new ServerSocket(8080)){
            System.out.println("Servidor creado en el puerto 8080");
            System.out.println("Comandos del servidor: 'NEXT' para siguiente pregunta");

            // Hilo para leer comandos del servidor (NEXT)
            new Thread(() -> {
                Scanner scanner = new Scanner(System.in);
                while(true) {
                    String comando = scanner.nextLine();
                    if (comando.equalsIgnoreCase("NEXT")) {
                        siguientePregunta();
                    }
                }
            }).start();

            // Aceptar nuevas conexiones de clientes
            while(true){
                Socket socket = serverSocket.accept();
                System.out.println("Cliente conectado desde " + socket.getInetAddress());
                HilosCliente cliente = new HilosCliente(socket);
                listaClientes.add(cliente);
                pool.execute(cliente);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            pool.shutdown();
        }
    }

    // Avanza a la siguiente pregunta y se la envía a todos
    public static synchronized void siguientePregunta() {
        int numPregunta = preguntaActual.getAndIncrement();

        if (numPregunta >= preguntas.size()) {
            broadcastTodos("JUEGO TERMINADO - No hay más preguntas");
            System.out.println("Juego terminado - No hay más preguntas");
            return;
        }

        //Reseteo
        respuestas.clear();
        preguntaActiva = true;
        tiempoInicioPregunta = System.currentTimeMillis();

        Pregunta p = preguntas.get(numPregunta);
        String mensajePregunta = "\n═══════════════════════════════════\n" +
                "PREGUNTA " + (numPregunta + 1) + ":\n" +
                p.obtenerTextoCompleto() + "\n" +
                "¡Responde rápido! (A/B/C/D)\n" +
                "═══════════════════════════════════\n";

        System.out.println("\nEnviando pregunta " + (numPregunta + 1) + ": " + p.texto);
        broadcastTodos(mensajePregunta);

        // Temporizador: 30 segundos para responder
        new Thread(() -> {
            try {
                Thread.sleep(30000);
                if (preguntaActiva) {
                    mostrarResultados();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    // Procesa una respuesta recibida de un cliente
    public static synchronized void procesarRespuesta(HilosCliente cliente, String respuesta) {
        if (!preguntaActiva) {
            cliente.enviarMensaje("No hay pregunta activa. Espera a la siguiente.");
            return;
        }

        if (respuestas.containsKey(cliente)) {
            cliente.enviarMensaje("Ya has respondido esta pregunta.");
            return;
        }

        respuesta = respuesta.toUpperCase();
        if (!respuesta.matches("[ABCD]")) {
            cliente.enviarMensaje("Respuesta inválida. Usa A, B, C o D.");
            return;
        }

        long tiempo = System.currentTimeMillis() - tiempoInicioPregunta;
        Pregunta p = preguntas.get(preguntaActual.get() - 1);
        boolean correcta = respuesta.equals(p.respuestaCorrecta);

        respuestas.put(cliente, new RespuestaCliente(respuesta, tiempo, correcta));

        cliente.enviarMensaje("Respuesta '" + respuesta + "' registrada en " + tiempo + "ms");
        System.out.println(cliente.getNombre() + " respondió: " + respuesta +
                " (" + tiempo + "ms)" + (correcta ? " ✓" : " ✗"));

        // Si todos respondieron, mostrar resultados
        if (respuestas.size() == listaClientes.size() && listaClientes.size() > 0) {
            mostrarResultados();
        }
    }

    // Muestra el ranking de respuestas para la pregunta actual
    private static synchronized void mostrarResultados() {
        if (!preguntaActiva) return;

        preguntaActiva = false;

        // Ordenar respuestas por tiempo (más rápido primero)
        List<Map.Entry<HilosCliente, RespuestaCliente>> rankingList =
                new ArrayList<>(respuestas.entrySet());

        rankingList.sort((a, b) -> Long.compare(a.getValue().tiempoRespuesta,
                b.getValue().tiempoRespuesta));

        StringBuilder resultados = new StringBuilder();
        resultados.append("\n═══════════════════════════════════\n");
        resultados.append("RANKING - Pregunta ").append(preguntaActual.get()).append("\n");
        resultados.append("═══════════════════════════════════\n");

        if (rankingList.isEmpty()) {
            resultados.append("Nadie respondió a tiempo\n");
        } else {
            int posicion = 1;
            for (Map.Entry<HilosCliente, RespuestaCliente> entry : rankingList) {
                String correctoSimbolo = entry.getValue().correcta ? "✓" : "✗";
                resultados.append(posicion++).append("º ")
                        .append(entry.getKey().getNombre()).append(": ")
                        .append(entry.getValue().respuesta).append(" ")
                        .append(correctoSimbolo).append(" (")
                        .append(entry.getValue().tiempoRespuesta).append("ms)\n");
            }
        }

        Pregunta p = preguntas.get(preguntaActual.get() - 1);
        resultados.append("\nRespuesta correcta: ").append(p.respuestaCorrecta).append("\n");
        resultados.append("═══════════════════════════════════\n");
        resultados.append("Esperando siguiente pregunta (NEXT)...\n");

        String resultadoStr = resultados.toString();
        System.out.println(resultadoStr);
        broadcastTodos(resultadoStr);
    }

    // Envía un mensaje a todos los clientes conectados
    public static void broadcastTodos(String mensaje) {
        for (HilosCliente cliente : listaClientes){
            cliente.enviarMensaje(mensaje);
        }
    }

    // Elimina un cliente de la lista de conectados
    public static void removerCliente(HilosCliente cliente) {
        listaClientes.remove(cliente);
        respuestas.remove(cliente);
        System.out.println("Cliente " + cliente.getNombre() + " desconectado");
    }
}