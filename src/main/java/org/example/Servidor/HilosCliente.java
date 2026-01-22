package org.example.Servidor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Hilo que maneja la comunicación con un cliente individual.
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

            salida.println("¡Bienvenido al Quiz en Tiempo Real!");
            salida.println("Escribe tu nombre:");
            salida.println("Teclea EXIT para salir");

            nombre = entrada.readLine();
            if (nombre == null || nombre.trim().isEmpty()) {
                nombre = "Anónimo" + socketCliente.getPort();
            }

            System.out.println(nombre + " se ha conectado desde " + socketCliente.getInetAddress());
            salida.println("Conectado como: " + nombre);
            salida.println("Instrucciones:");
            salida.println("   • Responde con A, B, C o D");
            salida.println("   • Más rápido = mejor ranking");
            salida.println("   • Espera a que el servidor envía 'NEXT'");
            salida.println("   • Recuerda, para salir en cualquier momento envía 'EXIT'\n");

            Servidor.broadcastTodos(nombre + " se ha unido al juego!");

            String mensaje;
            while (conectado && (mensaje = entrada.readLine()) != null) {
                if (mensaje.equalsIgnoreCase("EXIT")) {
                    break;
                }
                // Envía la respuesta al servidor para procesarla
                Servidor.procesarRespuesta(this, mensaje);
            }

        } catch (IOException e) {
            System.out.println("Error con cliente " + nombre + ": " + e.getMessage());
        } finally {
            desconectar();
        }
    }

    // Envía un mensaje al cliente
    public void enviarMensaje(String mensaje) {
        if (salida != null) {
            salida.println(mensaje);
        }
    }

    // Obtiene el nombre del cliente
    public String getNombre() {
        return nombre != null ? nombre : "Desconocido";
    }

    // Cierra la conexión con el cliente
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
            System.out.println("Error al desconectar cliente " + nombre);
        }
    }
}