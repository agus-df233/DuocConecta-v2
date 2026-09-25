package cl.duoc.duocconecta.proyectos.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "proyecto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Proyecto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String nombre;

    /** Una línea que resume el proyecto. Es lo que se lee en la tarjeta de la vitrina. */
    @Column(length = 200)
    private String resumen;

    /** El detalle completo: qué resuelve, en qué etapa está, qué ayuda necesita. */
    @Column(length = 2000)
    private String descripcion;

    @Column(name = "url_repositorio")
    private String urlRepositorio;

    @Column(name = "propietario_id", nullable = false, length = 100)
    private String propietarioId;

    @Column(length = 80)
    private String sede;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoProyecto estado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibilidad visibilidad;

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "proyecto_colaboradores", joinColumns = @JoinColumn(name = "proyecto_id"))
    @Column(name = "usuario_id")
    private List<String> colaboradoresIds = new ArrayList<>();

    /**
     * Herramientas y tecnologías con las que está hecho.
     *
     * <p>No se llama "stack" a propósito: DuocConecta es de toda la comunidad, y un proyecto de
     * Diseño usa Figma y uno de Administración usa Excel igual que uno de Informática usa React.
     * Es texto libre para que ninguna carrera quede fuera de una lista cerrada.</p>
     */
    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "proyecto_herramientas", joinColumns = @JoinColumn(name = "proyecto_id"))
    @Column(name = "herramienta")
    private List<String> herramientas = new ArrayList<>();

    /** Documentos o capturas, aparte del enlace al repositorio. Archivos subidos o enlaces. */
    @Builder.Default
    @OneToMany(mappedBy = "proyecto", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Adjunto> adjuntos = new ArrayList<>();

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @PrePersist
    void alPersistir() {
        this.fechaCreacion = Instant.now();
    }

    /** Añade un adjunto y deja las dos puntas de la relación apuntándose. */
    public void agregarAdjunto(Adjunto adjunto) {
        adjunto.setProyecto(this);
        this.adjuntos.add(adjunto);
    }

    public boolean esVisiblePara(String usuarioId) {
        if (visibilidad == Visibilidad.PUBLICO) {
            return true;
        }
        if (propietarioId.equals(usuarioId)) {
            return true;
        }
        return visibilidad == Visibilidad.COMPARTIDO && colaboradoresIds.contains(usuarioId);
    }
}