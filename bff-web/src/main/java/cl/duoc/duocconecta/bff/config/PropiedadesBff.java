package cl.duoc.duocconecta.bff.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades propias del BFF: a quién llama y desde qué orígenes acepta peticiones.
 */
@ConfigurationProperties(prefix = "duocconecta.bff")
public class PropiedadesBff {

    /** URL base de ms-usuarios. */
    private String urlMsUsuarios = "http://localhost:8081";

    /** URL base de ms-proyectos, la vitrina. */
    private String urlMsProyectos = "http://localhost:8082";

    /** URL base de ms-contacto, que gestiona las solicitudes de colaboración. */
    private String urlMsContacto = "http://localhost:8083";

    /** Cuánto se espera una respuesta de un microservicio antes de darla por perdida, en segundos. */
    private int timeoutSegundos = 5;

    /** Configuración de CORS para el frontend. */
    private Cors cors = new Cors();

    public String getUrlMsUsuarios() {
        return urlMsUsuarios;
    }

    public void setUrlMsUsuarios(String urlMsUsuarios) {
        this.urlMsUsuarios = urlMsUsuarios;
    }

    public String getUrlMsProyectos() {
        return urlMsProyectos;
    }

    public void setUrlMsProyectos(String urlMsProyectos) {
        this.urlMsProyectos = urlMsProyectos;
    }

    public String getUrlMsContacto() {
        return urlMsContacto;
    }

    public void setUrlMsContacto(String urlMsContacto) {
        this.urlMsContacto = urlMsContacto;
    }

    public int getTimeoutSegundos() {
        return timeoutSegundos;
    }

    public void setTimeoutSegundos(int timeoutSegundos) {
        this.timeoutSegundos = timeoutSegundos;
    }

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }

    /**
     * Orígenes, métodos y cabeceras que el navegador tiene permitido usar.
     *
     * <p>Todo se declara explícitamente: sin comodines, como pide la propuesta de arquitectura.</p>
     */
    public static class Cors {

        /** Orígenes del frontend autorizados. Por defecto, el servidor de desarrollo de Vite. */
        private List<String> origenes = List.of("http://localhost:5173");

        private List<String> metodos = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

        private List<String> headers = List.of("Authorization", "Content-Type");

        /** Cuánto puede el navegador cachear la respuesta del preflight, en segundos. */
        private long maxAgeSegundos = 3600;

        public List<String> getOrigenes() {
            return origenes;
        }

        public void setOrigenes(List<String> origenes) {
            this.origenes = origenes;
        }

        public List<String> getMetodos() {
            return metodos;
        }

        public void setMetodos(List<String> metodos) {
            this.metodos = metodos;
        }

        public List<String> getHeaders() {
            return headers;
        }

        public void setHeaders(List<String> headers) {
            this.headers = headers;
        }

        public long getMaxAgeSegundos() {
            return maxAgeSegundos;
        }

        public void setMaxAgeSegundos(long maxAgeSegundos) {
            this.maxAgeSegundos = maxAgeSegundos;
        }
    }
}
