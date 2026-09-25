package cl.duoc.duocconecta.bff.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Quién publicó o quién pide, con lo mínimo para mostrarlo.
 *
 * <p>Proyectos y colaboraciones guardan solo el identificador del token; el BFF lo resuelve contra
 * ms-usuarios. La carrera es además lo que permite filtrar la vitrina por escuela.</p>
 */
@Schema(description = "Datos públicos de una persona, resueltos por el BFF")
public record Autor(String nombre, String carrera, String sede) {

    /** La persona ocultó su perfil, o todavía no lo creó. */
    public static final Autor DESCONOCIDO = new Autor(null, null, null);
}
