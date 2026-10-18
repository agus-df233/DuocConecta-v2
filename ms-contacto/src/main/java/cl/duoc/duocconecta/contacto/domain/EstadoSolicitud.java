package cl.duoc.duocconecta.contacto.domain;

/** Estados de una solicitud. Solo PENDIENTE se puede responder o retirar. */
public enum EstadoSolicitud {
    PENDIENTE,
    ACEPTADA,
    RECHAZADA,
    /** Quien la pidió se arrepintió antes de que le respondieran. */
    RETIRADA
}
