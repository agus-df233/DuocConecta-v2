package cl.duoc.duocconecta.bff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Cifras de la comunidad, tal como las entrega ms-usuarios. */
@Schema(description = "Perfiles en total, visibles y por rol, carrera y sede")
public record EstadisticasUsuarios(long total, long visibles, List<Conteo> porRol,
                                   List<Conteo> porCarrera, List<Conteo> porSede) {
}
