package cl.duoc.duocconecta.comun.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * El mensaje que viaja por SNS y SQS.
 *
 * <p>Solo lleva identificadores: nunca correo, teléfono ni redes. Un evento queda copiado en
 * varias colas y en la auditoría, y ahí no se puede hacer valer el consentimiento mutuo.</p>
 *
 * @param idEvento       identificador único; permite al consumidor detectar duplicados
 * @param tipo           uno de {@link TiposDeEvento}; también va como atributo para los filtros
 * @param version        versión del contrato, para poder cambiarlo sin romper consumidores
 * @param origen         servicio que lo publicó
 * @param actorId        {@code oid} de quien provocó el hecho
 * @param destinatarioId {@code oid} de quien debe enterarse; puede ser nulo
 * @param recursoId      proyecto, comentario o solicitud afectada
 * @param proyectoId     proyecto relacionado, si aplica
 * @param detalle        texto corto sin datos personales (por ejemplo, el motivo de moderación)
 */
public record EventoDuocConecta(
        String idEvento,
        String tipo,
        int version,
        String origen,
        Instant ocurridoEn,
        String actorId,
        String destinatarioId,
        String recursoId,
        String proyectoId,
        String detalle) {

    public static final int VERSION_ACTUAL = 1;

    /**
     * Un evento recién ocurrido. El identificador, la versión y la fecha no los elige quien llama,
     * y el origen lo completa el publicador desde {@code duocconecta.mensajeria.origen}.
     */
    public static EventoDuocConecta nuevo(String tipo, String actorId, String destinatarioId,
                                          String recursoId, String proyectoId, String detalle) {
        return new EventoDuocConecta(UUID.randomUUID().toString(), tipo, VERSION_ACTUAL, null,
                Instant.now(), actorId, destinatarioId, recursoId, proyectoId, detalle);
    }

    /** Copia con el servicio de origen puesto. */
    public EventoDuocConecta conOrigen(String nuevoOrigen) {
        return new EventoDuocConecta(idEvento, tipo, version, nuevoOrigen, ocurridoEn, actorId,
                destinatarioId, recursoId, proyectoId, detalle);
    }
}
