package cl.duoc.duocconecta.comun.eventos;

/** Nombres de los atributos que viajan junto al evento en SNS y llegan como atributos a SQS. */
public final class AtributosMensaje {

    /** Sobre este atributo se aplican las políticas de filtro de las suscripciones. */
    public static final String TIPO = "tipo";

    public static final String ORIGEN = "origen";

    /** Mismo nombre que la cabecera HTTP, así la traza se lee igual en ambos lados. */
    public static final String ID_PETICION = "X-Request-Id";

    /** Motivo con que un consumidor mandó el mensaje a la DLQ a propósito. */
    public static final String MOTIVO_DESCARTE = "motivo-descarte";

    /** Clave del MDC donde vive el identificador; la misma que usa {@code IdDeCorrelacion}. */
    public static final String CLAVE_MDC = "idPeticion";

    private AtributosMensaje() {
    }
}
