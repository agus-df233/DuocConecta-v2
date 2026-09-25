package cl.duoc.duocconecta.contacto.exception;

/**
 * La acción es válida, pero choca con el estado actual de la solicitud.
 *
 * <p>Existe aparte de {@link OperacionNoPermitidaException} porque son cosas distintas: aquí la
 * persona sí tiene permiso, solo que la solicitud ya está pedida o ya fue respondida. Devolver
 * 403 en estos casos hacía que la aplicación dijera "no tienes permiso", que despista a quien
 * está usando la plataforma y a quien la depura.</p>
 */
public class ConflictoDeEstadoException extends RuntimeException {
    public ConflictoDeEstadoException(String mensaje) {
        super(mensaje);
    }
}
