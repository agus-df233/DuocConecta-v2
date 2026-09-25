package cl.duoc.duocconecta.contacto.exception;

/** Los datos de la solicitud no tienen sentido: no es un problema de permisos sino de la petición. */
public class SolicitudInvalidaException extends RuntimeException {
    public SolicitudInvalidaException(String mensaje) {
        super(mensaje);
    }
}
