package cl.duoc.duocconecta.proyectos.domain;

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

/**
 * Un comentario dejado en un proyecto de la vitrina.
 *
 * <p>Es la forma de dar retroalimentación sin tener que pedir contacto: cualquiera que pueda ver
 * el proyecto puede comentarlo, y eso no comparte ningún dato privado.</p>
 */
@Entity
@Table(name = "comentario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comentario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Proyecto comentado. */
    @Column(name = "proyecto_id", nullable = false)
    private UUID proyectoId;

    /** Identificador en Azure AD (claim oid) de quien comentó. */
    @Column(name = "autor_id", nullable = false, length = 100)
    private String autorId;

    @Column(nullable = false, length = 1000)
    private String texto;

    @Column(nullable = false, updatable = false)
    private Instant fecha;

    @PrePersist
    void alPersistir() {
        this.fecha = Instant.now();
    }
}
