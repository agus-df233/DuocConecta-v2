package cl.duoc.duocconecta.comun.seguridad;

import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Errores que se repiten en los cuatro servicios, en un solo lugar.
 *
 * <p>Cada servicio extiende esta clase en su propio {@code ManejadorErrores} y agrega solo lo
 * suyo. Todos responden en formato Problem Details (RFC 9457), así el BFF lee siempre el mismo
 * campo {@code detail} y no tiene que adivinar el formato de cada microservicio.</p>
 */
public abstract class ManejadorErroresBase {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErroresBase.class);

    /** Cuenta externa: se rechaza y no se crea perfil. */
    @ExceptionHandler(DominioNoPermitidoException.class)
    public ProblemDetail manejarDominioNoPermitido(DominioNoPermitidoException excepcion) {
        log.warn("Dominio no autorizado: {}", excepcion.getDominio());
        return problema(HttpStatus.FORBIDDEN, "Dominio no autorizado",
                "Tu correo no pertenece a un dominio institucional de Duoc UC. "
                        + "Entra con tu cuenta @duocuc.cl, @profesor.duoc.cl o @duoc.cl.");
    }

    /** Token válido sin el claim del correo: es configuración del tenant, no del usuario. */
    @ExceptionHandler(CorreoNoPresenteException.class)
    public ProblemDetail manejarCorreoAusente(CorreoNoPresenteException excepcion) {
        log.error("Token sin claim de correo: {}", excepcion.getMessage());
        return problema(HttpStatus.FORBIDDEN, "No se pudo determinar tu correo institucional",
                "El token no incluye tu correo, así que no se puede asignar un rol. "
                        + "Avisa al equipo: faltan los claims opcionales del access token en Azure AD.");
    }

    /** Datos inválidos: se devuelve campo por campo para que el formulario los marque. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail manejarValidacion(MethodArgumentNotValidException excepcion) {
        Map<String, String> porCampo = new LinkedHashMap<>();
        excepcion.getBindingResult().getFieldErrors().forEach(error ->
                porCampo.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Datos inválidos",
                "Revisa los campos marcados y vuelve a intentar.");
        problema.setProperty("errores", porCampo);
        return problema;
    }

    /** Validación de un parámetro suelto, fuera del cuerpo de la petición. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail manejarRestriccion(ConstraintViolationException excepcion) {
        return problema(HttpStatus.BAD_REQUEST, "Datos inválidos", excepcion.getMessage());
    }

    /** El cuerpo no es JSON válido, o no encaja con lo que el endpoint espera. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail manejarCuerpoIlegible(HttpMessageNotReadableException excepcion) {
        return problema(HttpStatus.BAD_REQUEST, "Petición mal formada",
                "El cuerpo de la petición no se pudo interpretar. Revisa que sea JSON válido.");
    }

    /** Un tipo mal formado en la ruta, por ejemplo un UUID que no lo es. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail manejarTipoInvalido(MethodArgumentTypeMismatchException excepcion) {
        return problema(HttpStatus.BAD_REQUEST, "Dato inválido",
                "El valor de '" + excepcion.getName() + "' no tiene el formato esperado.");
    }

    /**
     * Red de seguridad: sin esto un fallo inesperado sale como 500 sin nada en los registros.
     *
     * <p>Deja pasar las excepciones que Spring ya sabe responder. Atraparlas convertiría un 403
     * por rol insuficiente en un 500, y una ruta inexistente también: este manejador corre antes
     * que la traducción de errores de Spring Security y que la de Spring MVC.</p>
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail manejarNoControlado(Exception excepcion) throws Exception {
        if (excepcion instanceof AccessDeniedException
                || excepcion instanceof AuthenticationException
                || excepcion instanceof ErrorResponse) {
            throw excepcion;
        }
        log.error("Error no controlado", excepcion);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "Algo falló de nuestro lado. Intenta de nuevo en unos momentos.");
    }

    /** Arma el cuerpo Problem Details que comparten todas las respuestas de error. */
    protected static ProblemDetail problema(HttpStatus estado, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatus(estado);
        problema.setTitle(titulo);
        problema.setDetail(detalle);
        return problema;
    }
}
