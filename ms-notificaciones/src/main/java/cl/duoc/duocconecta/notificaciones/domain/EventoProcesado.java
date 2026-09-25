package cl.duoc.duocconecta.notificaciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Qué consumidor ya procesó qué evento. SQS entrega al menos una vez: sin esto, un duplicado
 * generaría un segundo aviso.
 */
@Entity
@Table(name = "evento_procesado")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class EventoProcesado {

    @EmbeddedId
    private Clave clave;

    @Column(name = "procesado_en", nullable = false)
    private Instant procesadoEn;

    /** El mismo evento llega a dos colas (su categoría y auditoría): el consumidor es parte de la clave. */
    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Clave implements Serializable {

        @Column(name = "id_evento", length = 100)
        private String idEvento;

        @Column(length = 40)
        private String consumidor;
    }
}
