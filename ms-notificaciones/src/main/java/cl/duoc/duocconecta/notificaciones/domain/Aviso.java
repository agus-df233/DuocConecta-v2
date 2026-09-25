package cl.duoc.duocconecta.notificaciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un aviso en la bandeja de una persona, generado por un consumidor a partir de un evento. */
@Entity
@Table(name = "aviso")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Aviso {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** {@code oid} de quien lo recibe. */
    @Column(name = "destinatario_id", nullable = false, length = 100)
    private String destinatarioId;

    @Column(nullable = false, length = 60)
    private String tipo;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(length = 500)
    private String detalle;

    @Column(name = "recurso_id", length = 100)
    private String recursoId;

    @Column(name = "proyecto_id", length = 100)
    private String proyectoId;

    @Column(name = "id_evento", nullable = false, length = 100)
    private String idEvento;

    @Column(nullable = false)
    private boolean leido;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @PrePersist
    void alPersistir() {
        this.creadoEn = Instant.now();
    }
}
