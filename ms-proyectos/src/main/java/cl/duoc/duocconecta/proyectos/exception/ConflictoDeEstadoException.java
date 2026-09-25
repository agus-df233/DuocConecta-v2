package cl.duoc.duocconecta.proyectos.exception;

/**
 * La acción es válida y quien la pide tiene permiso, pero choca con el estado actual.
 *
 * <p>Se responde 409 y no 403: decirle "no tienes permiso" a quien sí lo tiene despista a quien usa
 * la plataforma y a quien la depura.</p>
 */
public class ConflictoDeEstadoException extends RuntimeException {
    public ConflictoDeEstadoException(String mensaje) {
        super(mensaje);
    }
}
