package cl.duoc.duocconecta.bff.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Una barra del dashboard: la categoría y su cantidad. */
@Schema(description = "Categoría y cantidad")
public record Conteo(@Schema(example = "Ingeniería en Informática") String clave,
                     @Schema(example = "12") long cantidad) {
}
