package cl.duoc.duocconecta.mensajeria.dto;

/**
 * Una cola con sus contadores y configuración.
 *
 * @param mensajesVisibles esperando consumidor
 * @param mensajesEnVuelo  tomados por un consumidor, todavía sin confirmar
 * @param colaDescarte     su DLQ, o nula
 * @param protegida        parte de la topología base: la API no la elimina
 */
public record ColaRespuesta(String nombre, String url, String arn, long mensajesVisibles,
                            long mensajesEnVuelo, Integer retencionSegundos, Integer visibilidadSegundos,
                            String colaDescarte, Integer maxRecepciones, boolean protegida) {
}
