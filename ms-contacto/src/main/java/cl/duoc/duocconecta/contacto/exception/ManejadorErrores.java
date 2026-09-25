package cl.duoc.duocconecta.contacto.exception;

import cl.duoc.duocconecta.comun.seguridad.ManejadorErroresBase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Errores propios de ms-contacto. Los comunes vienen de {@link ManejadorErroresBase}. */
@RestControllerAdvice
public class ManejadorErrores extends ManejadorErroresBase {

    /** La solicitud no existe. */
    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail manejarNoEncontrado(RecursoNoEncontradoException excepcion) {
        return problema(HttpStatus.NOT_FOUND, "No encontrado", excepcion.getMessage());
    }

    /** Falta de permiso de verdad: la acción es de otra persona. */
    @ExceptionHandler(OperacionNoPermitidaException.class)
    public ProblemDetail manejarNoPermitido(OperacionNoPermitidaException excepcion) {
        return problema(HttpStatus.FORBIDDEN, "Sin permiso", excepcion.getMessage());
    }

    /** Choca con el estado actual, no con los permisos: 409 y no 403. */
    @ExceptionHandler(ConflictoDeEstadoException.class)
    public ProblemDetail manejarConflicto(ConflictoDeEstadoException excepcion) {
        return problema(HttpStatus.CONFLICT, "La solicitud ya no está disponible",
                excepcion.getMessage());
    }

    /** La petición en sí no tiene sentido. */
    @ExceptionHandler(SolicitudInvalidaException.class)
    public ProblemDetail manejarSolicitudInvalida(SolicitudInvalidaException excepcion) {
        return problema(HttpStatus.BAD_REQUEST, "Solicitud inválida", excepcion.getMessage());
    }
}
