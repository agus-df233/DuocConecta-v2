package cl.duoc.duocconecta.proyectos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Un adjunto tal como lo manda el navegador al crear o editar un proyecto.
 *
 * <p>Trae {@code claveS3} si el archivo ya se subió con una URL firmada, o {@code urlExterna} si
 * es un enlace. El servicio comprueba que venga uno y solo uno.</p>
 */
public record AdjuntoDatos(
        @Size(max = 400) String claveS3,
        @Size(max = 600) String urlExterna,
        @NotBlank(message = "El adjunto necesita un nombre")
        @Size(max = 200) String nombre,
        @Size(max = 120) String tipoContenido,
        Long tamanoBytes) {
}
