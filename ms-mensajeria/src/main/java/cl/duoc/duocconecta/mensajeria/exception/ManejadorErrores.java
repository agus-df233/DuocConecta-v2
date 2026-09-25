package cl.duoc.duocconecta.mensajeria.exception;

import cl.duoc.duocconecta.comun.seguridad.ManejadorErroresBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sns.model.SnsException;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.SqsException;

/** Errores propios de ms-mensajeria, incluidos los de AWS traducidos a Problem Details. */
@RestControllerAdvice
public class ManejadorErrores extends ManejadorErroresBase {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail manejarNoEncontrado(RecursoNoEncontradoException excepcion) {
        return problema(HttpStatus.NOT_FOUND, "No encontrado", excepcion.getMessage());
    }

    @ExceptionHandler(QueueDoesNotExistException.class)
    public ProblemDetail manejarColaInexistente(QueueDoesNotExistException excepcion) {
        return problema(HttpStatus.NOT_FOUND, "No encontrado", "La cola no existe");
    }

    /** Recurso protegido, o choca con el estado actual en AWS. */
    @ExceptionHandler(ConflictoDeEstadoException.class)
    public ProblemDetail manejarConflicto(ConflictoDeEstadoException excepcion) {
        return problema(HttpStatus.CONFLICT, "Conflicto", excepcion.getMessage());
    }

    @ExceptionHandler(SolicitudInvalidaException.class)
    public ProblemDetail manejarInvalida(SolicitudInvalidaException excepcion) {
        return problema(HttpStatus.BAD_REQUEST, "Solicitud inválida", excepcion.getMessage());
    }

    /** AWS rechazó la operación (por ejemplo, un permiso que LabRole no tiene). */
    @ExceptionHandler({SqsException.class, SnsException.class})
    public ProblemDetail manejarAws(RuntimeException excepcion) {
        log.warn("AWS rechazó la operación: {}", excepcion.getMessage());
        return problema(HttpStatus.BAD_GATEWAY, "AWS rechazó la operación", excepcion.getMessage());
    }

    /** Sin red o sin credenciales: en el Learner Lab vencen en cada sesión. */
    @ExceptionHandler(SdkClientException.class)
    public ProblemDetail manejarSinAws(SdkClientException excepcion) {
        log.warn("AWS no disponible: {}", excepcion.getMessage());
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "AWS no disponible",
                "No se pudo hablar con AWS. Revisa la red o las credenciales del laboratorio.");
    }
}
