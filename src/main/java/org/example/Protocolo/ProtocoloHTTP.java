package org.example.Protocolo;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Protocolo HTTP
 * === PETICIONES (Cliente -> Servidor) ===
 *
 * POST /conectar HTTP/1.1
 * Jugador: NombreJugador
 * Content-Length: 0
 * Body: (vacío)
 * END_HTTP
 *
 * POST /respuesta HTTP/1.1
 * Jugador: NombreJugador
 * Content-Length: 1
 * Body: A
 * END_HTTP
 *
 * === RESPUESTAS (Servidor -> Cliente) ===
 *
 * HTTP/1.1 200 OK
 * Content-Type: text/plain; charset=UTF-8
 * Tipo-Mensaje: PREGUNTA | RANKING | BIENVENIDA | RESULTADO | FIN | NEXT | ESPERANDO | ERROR
 * Body: [cuerpo codificado]
 * END_HTTP
 */
public class ProtocoloHTTP {

    public static final String DELIMITADOR = "END_HTTP";

    // =============================================
    // CODIFICAR / DECODIFICAR cuerpo multilínea
    // =============================================

    /**
     * Codifica un cuerpo multilínea para enviarlo en una sola línea.
     * Reemplaza \n por la secuencia literal \\n
     */
    public static String codificarCuerpo(String cuerpo) {
        if (cuerpo == null) return "";
        return cuerpo.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "");
    }

    /**
     * Decodifica un cuerpo recibido, restaurando los saltos de línea.
     */
    public static String decodificarCuerpo(String cuerpoCodificado) {
        if (cuerpoCodificado == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cuerpoCodificado.length(); i++) {
            if (cuerpoCodificado.charAt(i) == '\\' && i + 1 < cuerpoCodificado.length()) {
                char next = cuerpoCodificado.charAt(i + 1);
                if (next == 'n') {
                    sb.append('\n');
                    i++;
                } else if (next == '\\') {
                    sb.append('\\');
                    i++;
                } else {
                    sb.append(cuerpoCodificado.charAt(i));
                }
            } else {
                sb.append(cuerpoCodificado.charAt(i));
            }
        }
        return sb.toString();
    }

    // =============================================
    // ENVIAR Y RECIBIR (PrintWriter / BufferedReader)
    // =============================================

    /**
     * Envía un mensaje HTTP (petición o respuesta) línea por línea.
     * Cada cabecera es una línea, el cuerpo es UNA línea codificada, y END_HTTP cierra.
     */
    public static void enviar(PrintWriter salida, String[] cabeceras, String cuerpo) {
        for (String cabecera : cabeceras) {
            salida.println(cabecera);
        }
        salida.println("Body: " + codificarCuerpo(cuerpo));
        salida.println(DELIMITADOR);
        salida.flush();
    }

    /**
     * Lee un mensaje HTTP completo del socket.
     * Retorna null si la conexión se cerró.
     */
    public static MensajeHTTP recibir(BufferedReader entrada) throws IOException {
        MensajeHTTP mensaje = new MensajeHTTP();
        String linea;

        while ((linea = entrada.readLine()) != null) {
            // Fin del mensaje
            if (linea.equals(DELIMITADOR)) {
                return mensaje;
            }

            // Primera línea (request line o status line)
            if (mensaje.primeraLinea == null) {
                mensaje.primeraLinea = linea;
                continue;
            }

            // Body
            if (linea.startsWith("Body: ")) {
                mensaje.cuerpoCodificado = linea.substring(6);
                mensaje.cuerpo = decodificarCuerpo(mensaje.cuerpoCodificado);
                continue;
            }

            // Cabecera: "Clave: Valor"
            int separador = linea.indexOf(": ");
            if (separador > 0) {
                String clave = linea.substring(0, separador);
                String valor = linea.substring(separador + 2);
                mensaje.cabeceras.put(clave, valor);
            }
        }

        // Si llega aquí, la conexión se cerró
        return null;
    }

    // =============================================
    // MENSAJE PARSEADO
    // =============================================

    public static class MensajeHTTP {
        public String primeraLinea;
        public Map<String, String> cabeceras = new HashMap<>();
        public String cuerpoCodificado = "";
        public String cuerpo = "";

        // Para peticiones
        public String getMetodo() {
            if (primeraLinea == null) return "";
            String[] partes = primeraLinea.split(" ");
            return partes.length > 0 ? partes[0] : "";
        }

        public String getRuta() {
            if (primeraLinea == null) return "";
            String[] partes = primeraLinea.split(" ");
            return partes.length > 1 ? partes[1] : "";
        }

        // Para respuestas
        public int getCodigo() {
            if (primeraLinea == null) return 0;
            String[] partes = primeraLinea.split(" ");
            if (partes.length > 1) {
                try { return Integer.parseInt(partes[1]); }
                catch (NumberFormatException e) { return 0; }
            }
            return 0;
        }

        public String getTipoMensaje() {
            return cabeceras.getOrDefault("Tipo-Mensaje", "DESCONOCIDO");
        }

        public String getJugador() {
            return cabeceras.getOrDefault("Jugador", "");
        }
    }

    // =============================================
    // CONSTRUIR RESPUESTAS (Servidor -> Cliente)
    // =============================================

    public static void enviarRespuesta(PrintWriter salida, int codigo, String estado,
                                       String tipoMensaje, String cuerpo) {
        String[] cabeceras = {
                "HTTP/1.1 " + codigo + " " + estado,
                "Content-Type: text/plain; charset=UTF-8",
                "Tipo-Mensaje: " + tipoMensaje
        };
        enviar(salida, cabeceras, cuerpo);
    }

    public static void enviarBienvenida(PrintWriter salida, String mensaje) {
        enviarRespuesta(salida, 200, "OK", "BIENVENIDA", mensaje);
    }

    public static void enviarPregunta(PrintWriter salida, String preguntaFormateada) {
        enviarRespuesta(salida, 200, "OK", "PREGUNTA", preguntaFormateada);
    }

    public static void enviarResultado(PrintWriter salida, String resultado) {
        enviarRespuesta(salida, 200, "OK", "RESULTADO", resultado);
    }

    public static void enviarRanking(PrintWriter salida, String ranking) {
        enviarRespuesta(salida, 200, "OK", "RANKING", ranking);
    }

    public static void enviarNext(PrintWriter salida) {
        enviarRespuesta(salida, 200, "OK", "NEXT", "Siguiente pregunta...");
    }

    public static void enviarEsperando(PrintWriter salida, String mensaje) {
        enviarRespuesta(salida, 200, "OK", "ESPERANDO", mensaje);
    }

    public static void enviarFin(PrintWriter salida, String rankingFinal) {
        enviarRespuesta(salida, 200, "OK", "FIN", rankingFinal);
    }

    public static void enviarError(PrintWriter salida, String mensaje) {
        enviarRespuesta(salida, 400, "Bad Request", "ERROR", mensaje);
    }

    // =============================================
    // CONSTRUIR PETICIONES (Cliente -> Servidor)
    // =============================================

    public static void enviarPeticionConectar(PrintWriter salida, String nombreJugador) {
        String[] cabeceras = {
                "POST /conectar HTTP/1.1",
                "Jugador: " + nombreJugador,
                "Content-Length: 0"
        };
        enviar(salida, cabeceras, "");
    }

    public static void enviarPeticionRespuesta(PrintWriter salida, String nombreJugador, char respuesta) {
        String cuerpo = String.valueOf(Character.toUpperCase(respuesta));
        String[] cabeceras = {
                "POST /respuesta HTTP/1.1",
                "Jugador: " + nombreJugador,
                "Content-Length: " + cuerpo.length()
        };
        enviar(salida, cabeceras, cuerpo);
    }
}