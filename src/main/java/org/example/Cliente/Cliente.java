package org.example.Cliente;

import org.example.Protocolo.ProtocoloHTTP;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

/**
 * Cliente para el sistema de Quiz.
 *
 * Flujo:
 * 1. Se conecta al servidor con POST /conectar
 * 2. Recibe bienvenida (Tipo-Mensaje: BIENVENIDA)
 * 3. Escucha mensajes del servidor (PREGUNTA, RANKING, RESULTADO, etc.)
 * 4. Cuando llega PREGUNTA, el jugador responde A/B/C/D
 * 5. Solo se permite 1 respuesta por pregunta
 */
public class Cliente {

    // Cambiar a la IP del servidor si no es local
    private static final String HOST = "172.20.10.2";
    //private static final String HOST = "localhost";
    private static final int PUERTO = 8080;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("===========================================");
        System.out.println("       CLIENTE QUIZ - org.example");
        System.out.println("===========================================");
        System.out.print("Ingresa tu nombre de jugador: ");
        String nombre = scanner.nextLine().trim();

        if (nombre.isEmpty()) {
            nombre = "Jugador_" + System.currentTimeMillis() % 1000;
            System.out.println("Usando nombre: " + nombre);
        }

        final String nombreJugador = nombre;

        try (Socket socket = new Socket(HOST, PUERTO)) {
            System.out.println("Conectado al servidor en " + HOST + ":" + PUERTO);

            BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter salida = new PrintWriter(socket.getOutputStream(), true);

            // 1. Enviar petición HTTP de conexión (POST /conectar)
            ProtocoloHTTP.enviarPeticionConectar(salida, nombreJugador);

            // Estado: controla si podemos responder a una pregunta
            final boolean[] esperandoRespuesta = {false};
            final boolean[] conectado = {true};

            // 2. Hilo para recibir mensajes del servidor
            Thread lectura = new Thread(() -> {
                try {
                    while (conectado[0]) {
                        // Leer un mensaje HTTP completo (hasta END_HTTP)
                        ProtocoloHTTP.MensajeHTTP respuesta = ProtocoloHTTP.recibir(entrada);

                        if (respuesta == null) {
                            System.out.println("Servidor desconectado.");
                            conectado[0] = false;
                            break;
                        }

                        // Procesar según Tipo-Mensaje
                        String tipo = respuesta.getTipoMensaje();

                        switch (tipo) {
                            case "BIENVENIDA":
                                System.out.println("\n" + respuesta.cuerpo);
                                break;

                            case "PREGUNTA":
                                esperandoRespuesta[0] = true;
                                System.out.println(respuesta.cuerpo);
                                break;

                            case "RESULTADO":
                                System.out.println("\n>> " + respuesta.cuerpo);
                                break;

                            case "RANKING":
                                System.out.println("\n" + respuesta.cuerpo);
                                break;

                            case "NEXT":
                                System.out.println("\n... Siguiente pregunta en breve ...\n");
                                break;

                            case "ESPERANDO":
                                System.out.println(respuesta.cuerpo);
                                break;

                            case "FIN":
                                System.out.println("\n" + respuesta.cuerpo);
                                System.out.println("\nEl quiz ha terminado. Escribe EXIT para cerrar.");
                                esperandoRespuesta[0] = false;
                                break;

                            case "ERROR":
                                System.out.println("[ERROR] " + respuesta.cuerpo);
                                break;

                            default:
                                // Mensaje desconocido, mostrar cuerpo igualmente
                                if (!respuesta.cuerpo.isEmpty()) {
                                    System.out.println(respuesta.cuerpo);
                                }
                        }
                    }
                } catch (IOException e) {
                    if (conectado[0]) {
                        System.out.println("Conexión perdida con el servidor.");
                    }
                }
            });
            lectura.setDaemon(true);
            lectura.start();

            // 3. Bucle principal: capturar input del jugador
            while (conectado[0]) {
                String mensaje = scanner.nextLine().trim().toUpperCase();

                if (mensaje.isEmpty()) continue;

                // Comando para salir
                if (mensaje.equals("EXIT") || mensaje.equals("SALIR")) {
                    conectado[0] = false;
                    break;
                }

                if (!esperandoRespuesta[0]) {
                    System.out.println("Espera a que llegue una pregunta...");
                    continue;
                }

                // Validar respuesta A/B/C/D
                if (mensaje.length() == 1 && "ABCD".contains(mensaje)) {
                    char respuesta = mensaje.charAt(0);
                    ProtocoloHTTP.enviarPeticionRespuesta(salida, nombreJugador, respuesta);
                    esperandoRespuesta[0] = false; // Solo 1 respuesta por pregunta
                } else {
                    System.out.println("Respuesta inválida. Escribe solo A, B, C o D.");
                }
            }

            scanner.close();
            System.out.println("Sesión terminada.");

        } catch (IOException e) {
            System.out.println("No se pudo conectar al servidor: " + e.getMessage());
            System.out.println("Asegúrate de que el servidor esté ejecutándose en " + HOST + ":" + PUERTO);
        }
    }
}