package cl.duoc.duocconecta.bff.controller;

import cl.duoc.duocconecta.comun.seguridad.ManejadorErroresBase;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Errores propios del BFF: los que llegan desde los microservicios.
 * Los comunes vienen de {@link ManejadorErroresBase}.
 */
@RestControllerAdvice
public class ManejadorErrores extends ManejadorErroresBase {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);
    private static final String SIN_MOTIVO = "El servicio rechazó la petición y no explicó por qué.";

    private final ObjectMapper json;

    public ManejadorErrores(ObjectMapper json) {
        this.json = json;
    }

    /**
     * Un microservicio respondió con un error.
     *
     * <p>Los 4xx se devuelven tal cual porque son del usuario y necesita verlos. Los 5xx pasan a
     * 502: el problema es del backend, no de quien hizo la petición.</p>
     */
    @ExceptionHandler(HttpStatusCodeException.class)
    public ProblemDetail manejarErrorDeMicroservicio(HttpStatusCodeException excepcion) {
        HttpStatus estado = HttpStatus.valueOf(excepcion.getStatusCode().value());

        if (estado.is4xxClientError()) {
            String motivo = motivoDelMicroservicio(excepcion);
            log.warn("Microservicio rechazó con {}: {}", estado.value(), motivo);
            return problema(estado, "La petición fue rechazada", motivo);
        }

        log.error("Microservicio respondió {}", estado.value(), excepcion);
        return problema(HttpStatus.BAD_GATEWAY, "Error en un servicio interno",
                "No se pudo completar la operación porque un servicio interno falló. "
                        + "Intenta de nuevo en unos momentos.");
    }

    /**
     * Saca el motivo del cuerpo del microservicio.
     *
     * <p>Los cuatro servicios responden Problem Details, así que basta con leer {@code detail}.</p>
     */
    private String motivoDelMicroservicio(HttpStatusCodeException excepcion) {
        String cuerpo = excepcion.getResponseBodyAsString(StandardCharsets.UTF_8);
        if (cuerpo == null || cuerpo.isBlank()) {
            return SIN_MOTIVO;
        }
        try {
            JsonNode detalle = json.readTree(cuerpo).path("detail");
            return detalle.isTextual() && !detalle.stringValue().isBlank()
                    ? detalle.stringValue()
                    : SIN_MOTIVO;
        } catch (RuntimeException noEsJson) {
            // Un 403 de la cadena de filtros llega como texto plano, no como JSON.
            log.warn("Cuerpo de error no interpretable: {}", cuerpo);
            return SIN_MOTIVO;
        }
    }

    /** No se pudo contactar al microservicio: está caído o se agotó la espera. */
    @ExceptionHandler(ResourceAccessException.class)
    public ProblemDetail manejarMicroservicioInalcanzable(ResourceAccessException excepcion) {
        log.error("No se pudo contactar a un microservicio", excepcion);
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible",
                "Un servicio interno no está respondiendo. "
                        + "Verifica que los microservicios estén levantados e intenta de nuevo.");
    }
}
