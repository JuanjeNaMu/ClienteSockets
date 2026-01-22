package org.example.Cliente;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 * Cliente para el sistema de Quiz. Se conecta al servidor, responde preguntas y ve rankings.
 */
public class Cliente {

    public static void main(String[] args) {
        System.out.println("Cliente Quiz - Conectando...");

        //Conexión TCP con el servidor en puerto 8080
        //try(Socket socket = new Socket("192.168.40.112", 8080)){
            try(Socket socket = new Socket("localhost", 8080)){
                System.out.println("Conectado al servidor");

                // Streams para comunicación con el servidor
                BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter salida = new PrintWriter(socket.getOutputStream(), true);
                Scanner scanner = new Scanner(System.in);

                // Hilo para recibir mensajes del servidor sin bloquear la entrada del usuario
                Thread lectura = new Thread(() -> {
                    try {
                        String mensajeServidor;
                        while ((mensajeServidor = entrada.readLine()) != null) {
                            System.out.println(mensajeServidor);
                        }
                    } catch (IOException e) {
                        System.out.println("Desconectado del servidor");
                    }
                });
                lectura.start();

                // Bucle principal: captura y envía respuestas del usuario
                while (true) {
                    String mensaje = scanner.nextLine().trim();

                    // Comando para salir del programa
                    if (mensaje.equalsIgnoreCase("EXIT")) {
                        salida.println("EXIT");
                        break;
                    }

                    // Valida que sea una respuesta válida (A, B, C, D) de un solo carácter
                    if (mensaje.length() == 1 && Pattern.matches("[a-dA-D]", mensaje)) {
                        salida.println(mensaje.toUpperCase()); // Convierte a mayúscula
                    }
                    // Envía otros mensajes (como el nombre al conectarse)
                    else if (!mensaje.isEmpty()) {
                        salida.println(mensaje);
                    }
                }

                scanner.close();
                System.out.println("Sesión terminada");

            } catch (IOException e) {
                System.out.println("No se pudo conectar al servidor: " + e.getMessage());
                System.out.println("Asegúrate de que el servidor esté ejecutándose en localhost:8080");
            }
        }
    }