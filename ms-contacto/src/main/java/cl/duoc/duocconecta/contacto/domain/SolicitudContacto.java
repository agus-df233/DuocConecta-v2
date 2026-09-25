package cl.duoc.duocconecta.contacto.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "solicitudes_contacto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolicitudContacto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** oid de quien solicita el contacto. */
    @Column(name = "solicitante_id", nullable = false, length = 100)
    private String solicitanteId;

    /** oid del alumno cuyo contacto se solicita; es quien debe aceptar o rechazar. */
    @Column(name = "solicitado_id", nullable = false, length = 100)
    private String solicitadoId;

    /** Proyecto de la vitrina que originó la solicitud; da contexto a quien la recibe. */
    @Column(name = "proyecto_id")
    private UUID proyectoId;

    @Column(length = 500)
    private String mensaje;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSolicitud estado;

    /**
     * Lo que ofrece quien pide, al momento de pedir. Pedir contacto es ofrecer el propio.
     *
     * <p>Se guarda desde el principio pero <b>no se muestra a nadie</b> hasta que hay aceptación:
     * el consentimiento sigue siendo la condición, solo que ahora corre en los dos sentidos.</p>
     */
    @Column(name = "correo_solicitante", length = 255)
    private String correoSolicitante;

    @Column(name = "telefono_solicitante", length = 50)
    private String telefonoSolicitante;

    @Column(name = "redes_solicitante", length = 500)
    private String redesSolicitante;

    /**
     * Lo que comparte quien acepta. Se completa solo al aceptar.
     *
     * <p>Van en tres campos y no en un texto único para que se vea qué se compartió y qué no: un
     * teléfono vacío significa que decidió no darlo, no que se perdió en el camino.</p>
     */
    @Column(name = "correo_solicitado", length = 255)
    private String correoSolicitado;

    @Column(name = "telefono_solicitado", length = 50)
    private String telefonoSolicitado;

    @Column(name = "redes_solicitado", length = 500)
    private String redesSolicitado;

    @Column(name = "fecha_solicitud", nullable = false, updatable = false)
    private Instant fechaSolicitud;

    @Column(name = "fecha_respuesta")
    private Instant fechaRespuesta;

    @PrePersist
    void alPersistir() {
        this.fechaSolicitud = Instant.now();
        if (this.estado == null) {
            this.estado = EstadoSolicitud.PENDIENTE;
        }
    }
}
