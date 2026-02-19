package org.example.Servidor;

import org.example.Protocolo.ProtocoloHTTP;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Hilo que maneja la comunicación con un cliente individual.
 *
 * Usa el protocolo HTTP personalizado con END_HTTP:
 * - Recibe POST /conectar para registrar al jugador
 * - Recibe POST /respuesta para procesar respuestas A/B/C/D
 * - Envía respuestas HTTP con Tipo-Mensaje (BIENVENIDA, PREGUNTA, etc.)
 */
public class HilosCliente implements Runnable {
    private final Socket socketCliente;
    private BufferedReader entrada;
    private PrintWriter salida;
    private String nombre;
    private boolean conectado = true;

    public HilosCliente(Socket socketCliente) {
        this.socketCliente = socketCliente;
    }

    @Override
    public void run() {
        try {
            entrada = new BufferedReader(new InputStreamReader(socketCliente.getInputStream()));
            salida = new PrintWriter(socketCliente.getOutputStream(), true);

            // 1. Leer petición de conexión (POST /conectar)
            ProtocoloHTTP.MensajeHTTP peticion = ProtocoloHTTP.recibir(entrada);

            if (peticion != null && "/conectar".equals(peticion.getRuta())) {
                nombre = peticion.getJugador();
            }

            if (nombre == null || nombre.trim().isEmpty()) {
                nombre = "Anónimo_" + socketCliente.getPort();
            }

            System.out.println(nombre + " se ha conectado desde " + socketCliente.getInetAddress());

            // 2. Enviar bienvenida via protocolo HTTP
            ProtocoloHTTP.enviarBienvenida(salida,
                    "¡Bienvenido al Quiz, " + nombre + "!\n" +
                            "Espera a que el administrador inicie el juego.\n" +
                            "Responde con A, B, C o D cuando aparezca la pregunta.\n" +
                            "Escribe EXIT o SALIR para desconectarte.");

            // 3. Registrar jugador en el servidor
            Servidor.registrarCliente(this);

            // 4. Bucle principal: escuchar peticiones del cliente
            while (conectado) {
                ProtocoloHTTP.MensajeHTTP req = ProtocoloHTTP.recibir(entrada);

                if (req == null) {
                    break; // Cliente desconectado
                }

                // Procesar según la ruta de la petición
                String ruta = req.getRuta();

                if ("/respuesta".equals(ruta) && !req.cuerpo.isEmpty()) {
                    // El body contiene la letra de la respuesta (A/B/C/D)
                    char respuesta = Character.toUpperCase(req.cuerpo.charAt(0));
                    Servidor.procesarRespuesta(this, respuesta);
                }
                // Se pueden añadir más rutas si se necesita en el futuro
            }

        } catch (IOException e) {
            if (conectado) {
                System.out.println("Error con cliente " + getNombre() + ": " + e.getMessage());
            }
        } finally {
            desconectar();
        }
    }

    // =============================================
    // MÉTODOS DE ENVÍO (delegan en ProtocoloHTTP)
    // =============================================

    public void enviarPregunta(String preguntaFormateada) {
        if (salida != null) ProtocoloHTTP.enviarPregunta(salida, preguntaFormateada);
    }

    public void enviarResultado(String resultado) {
        if (salida != null) ProtocoloHTTP.enviarResultado(salida, resultado);
    }

    public void enviarRanking(String ranking) {
        if (salida != null) ProtocoloHTTP.enviarRanking(salida, ranking);
    }

    public void enviarNext() {
        if (salida != null) ProtocoloHTTP.enviarNext(salida);
    }

    public void enviarEsperando(String mensaje) {
        if (salida != null) ProtocoloHTTP.enviarEsperando(salida, mensaje);
    }

    public void enviarFin(String rankingFinal) {
        if (salida != null) ProtocoloHTTP.enviarFin(salida, rankingFinal);
    }

    public void enviarError(String mensaje) {
        if (salida != null) ProtocoloHTTP.enviarError(salida, mensaje);
    }

    // =============================================
    // GETTERS Y DESCONEXIÓN
    // =============================================

    public String getNombre() {
        return nombre != null ? nombre : "Desconocido";
    }

    private void desconectar() {
        conectado = false;
        Servidor.removerCliente(this);
        try {
            if (entrada != null) entrada.close();
            if (salida != null) salida.close();
            if (socketCliente != null && !socketCliente.isClosed()) {
                socketCliente.close();
            }
        } catch (IOException e) {
            System.out.println("Error al desconectar cliente " + getNombre());
        }
    }
}