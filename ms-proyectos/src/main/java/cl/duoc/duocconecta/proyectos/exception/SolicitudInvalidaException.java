package cl.duoc.duocconecta.proyectos.exception;

/** Los datos de la petición no tienen sentido: no es un problema de permisos sino de la petición. */
public class SolicitudInvalidaException extends RuntimeException {
    public SolicitudInvalidaException(String mensaje) {
        super(mensaje);
    }
}
