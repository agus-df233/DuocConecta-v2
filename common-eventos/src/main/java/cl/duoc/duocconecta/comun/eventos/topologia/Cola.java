package cl.duoc.duocconecta.comun.eventos.topologia;

/**
 * Una cola SQS, de trabajo o de descarte.
 *
 * @param colaDescarte   DLQ a la que va lo que falla; nula si esta cola es una DLQ
 * @param maxRecepciones entregas sin confirmar antes de mover el mensaje a la DLQ
 * @param recibeDeSns    si SNS escribe en ella, necesita una política que se lo permita
 */
public record Cola(String nombre, int retencionSegundos, int visibilidadSegundos,
                   int esperaSegundos, String colaDescarte, int maxRecepciones,
                   boolean recibeDeSns) {

    /** Cola de trabajo: recibe de SNS y manda lo que falla a su DLQ. */
    public static Cola deTrabajo(String nombre, int retencionSegundos, int visibilidadSegundos,
                                 int esperaSegundos, String colaDescarte, int maxRecepciones) {
        return new Cola(nombre, retencionSegundos, visibilidadSegundos, esperaSegundos,
                colaDescarte, maxRecepciones, true);
    }

    /** DLQ: sin DLQ propia, o un mensaje que falla daría vueltas para siempre. */
    public static Cola deDescarte(String nombre, int retencionSegundos, int visibilidadSegundos,
                                  boolean recibeDeSns) {
        return new Cola(nombre, retencionSegundos, visibilidadSegundos, 0, null, 0, recibeDeSns);
    }

    public boolean esDescarte() {
        return colaDescarte == null;
    }
}
