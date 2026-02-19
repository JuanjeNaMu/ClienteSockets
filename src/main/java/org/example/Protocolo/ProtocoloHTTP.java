package org.example.Protocolo;

/**
 * Clase que define la estructura mínima de peticiones/respuestas HTTP
 * para el sistema de Quiz. Se usa para enviar y parsear mensajes entre
 * cliente y servidor con formato HTTP.
 */
public class ProtocoloHTTP {

    // ── CONSTRUCCIÓN DE MENSAJES ──────────────────────────────────────────────

    /**
     * Crea una petición HTTP POST con el body indicado.
     * Usada por el cliente para enviar su respuesta (A/B/C/D).
     *
     * Ejemplo:
     *   POST /respuesta HTTP/1.1
     *   Content-Type: text/plain
     *   Content-Length: 1
     *
     *   C
     */
    public static String crearPeticionPOST(String ruta, String body) {
        return "POST " + ruta + " HTTP/1.1\r\n"
                + "Content-Type: text/plain\r\n"
                + "Content-Length: " + body.length() + "\r\n"
                + "\r\n"
                + body;
    }

    /**
     * Crea una respuesta HTTP 200 OK con el body indicado.
     * Usada por el servidor para enviar preguntas, rankings, etc.
     */
    public static String crearRespuesta200(String body) {
        return "HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Length: " + body.getBytes().length + "\r\n"
                + "\r\n"
                + body;
    }

    /**
     * Crea una respuesta HTTP 400 Bad Request.
     * Usada cuando el cliente envía una respuesta inválida.
     */
    public static String crearRespuesta400(String motivo) {
        return "HTTP/1.1 400 Bad Request\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Length: " + motivo.getBytes().length + "\r\n"
                + "\r\n"
                + motivo;
    }

    // ── PARSEO DE MENSAJES ────────────────────────────────────────────────────

    /**
     * Extrae el body de un mensaje HTTP (lo que hay después de la línea vacía).
     * Funciona tanto para peticiones como para respuestas.
     */
    public static String extraerBody(String mensajeHTTP) {
        int separador = mensajeHTTP.indexOf("\r\n\r\n");
        if (separador == -1) separador = mensajeHTTP.indexOf("\n\n");
        if (separador == -1) return mensajeHTTP; // fallback: devuelve el mensaje entero
        return mensajeHTTP.substring(separador).trim();
    }

    /**
     * Extrae el método HTTP de una petición (GET, POST...).
     */
    public static String extraerMetodo(String mensajeHTTP) {
        return mensajeHTTP.split(" ")[0];
    }

    /**
     * Extrae la ruta de una petición HTTP (/respuesta, /nombre...).
     */
    public static String extraerRuta(String mensajeHTTP) {
        String[] partes = mensajeHTTP.split(" ");
        if (partes.length >= 2) return partes[1];
        return "/";
    }
}