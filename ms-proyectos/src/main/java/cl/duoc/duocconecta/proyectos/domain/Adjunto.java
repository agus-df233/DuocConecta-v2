package cl.duoc.duocconecta.proyectos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * Un archivo adjunto a un proyecto.
 *
 * <p>Es un archivo subido a S3 ({@code claveS3}) o un enlace externo ({@code urlExterna}), nunca
 * las dos cosas. La base lo impone con una restricción.</p>
 */
@Entity
@Table(name = "proyecto_adjunto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Adjunto {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    /** Dónde vive el archivo en S3. Nulo si es un enlace externo. */
    @Column(name = "clave_s3", length = 400)
    private String claveS3;

    /** Enlace a algo que vive fuera. Nulo si es un archivo subido. */
    @Column(name = "url_externa", length = 600)
    private String urlExterna;

    /** Nombre que se muestra: el original del archivo, o el final del enlace. */
    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(name = "tipo_contenido", length = 120)
    private String tipoContenido;

    @Column(name = "tamano_bytes")
    private Long tamanoBytes;

    @Column(name = "subido_en", nullable = false, updatable = false)
    private Instant subidoEn;

    @PrePersist
    void alPersistir() {
        if (subidoEn == null) {
            subidoEn = Instant.now();
        }
    }

    /** True si el archivo está en S3 y hay que borrarlo al quitar el adjunto. */
    public boolean esArchivoSubido() {
        return claveS3 != null && !claveS3.isBlank();
    }
}
