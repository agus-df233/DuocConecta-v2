package cl.duoc.duocconecta.bff.service;

/** La solicitud de colaboración no tiene sentido: el proyecto no las recibe. Se responde 400. */
public class ColaboracionNoPermitidaException extends RuntimeException {

    public ColaboracionNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}
