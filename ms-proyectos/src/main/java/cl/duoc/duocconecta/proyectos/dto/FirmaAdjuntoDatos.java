package cl.duoc.duocconecta.proyectos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Lo que el navegador declara antes de subir un archivo.
 *
 * @param nombre         nombre original, tal como está en el dispositivo
 * @param tipoContenido  tipo MIME que el navegador enviará en el PUT; tiene que coincidir con el
 *                       que se firma, o S3 rechaza la firma
 * @param tamanoBytes    tamaño declarado, para rechazar temprano lo que excede el tope
 */
public record FirmaAdjuntoDatos(
        @NotBlank(message = "El nombre del archivo es obligatorio")
        @Size(max = 200, message = "El nombre del archivo es demasiado largo")
        String nombre,

        @NotBlank(message = "El tipo de contenido es obligatorio")
        @Size(max = 120)
        String tipoContenido,

        @NotNull(message = "El tamaño del archivo es obligatorio")
        @Positive(message = "El tamaño tiene que ser mayor que cero")
        Long tamanoBytes) {
}
