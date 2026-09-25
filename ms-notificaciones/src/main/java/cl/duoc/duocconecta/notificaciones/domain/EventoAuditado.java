package cl.duoc.duocconecta.notificaciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Un evento tal como llegó a la cola de auditoría, que recibe todo. */
@Entity
@Table(name = "evento_auditado")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoAuditado {

    @Id
    @Column(name = "id_evento", length = 100)
    private String idEvento;

    @Column(nullable = false, length = 60)
    private String tipo;

    @Column(length = 60)
    private String origen;

    @Column(name = "actor_id", length = 100)
    private String actorId;

    @Column(name = "destinatario_id", length = 100)
    private String destinatarioId;

    @Column(name = "recurso_id", length = 100)
    private String recursoId;

    @Column(name = "proyecto_id", length = 100)
    private String proyectoId;

    @Column(length = 500)
    private String detalle;

    @Column(name = "ocurrido_en")
    private Instant ocurridoEn;

    @Column(name = "recibido_en", nullable = false)
    private Instant recibidoEn;

    /** El {@code X-Request-Id} que viajó con el mensaje: cruza este registro con los logs. */
    @Column(name = "id_peticion", length = 100)
    private String idPeticion;
}
