package cl.duoc.duocconecta.usuarios.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Redes sociales del usuario autenticado.
 *
 * <p>Solo devuelve las del propio usuario. Las de terceros quedan sujetas al consentimiento
 * mutuo que se implementa en EP2.</p>
 */
@Schema(description = "Redes sociales del usuario autenticado")
public record RedesRespuesta(

        @Schema(description = "Listado de redes sociales",
                example = "[\"https://github.com/juanaperez\", \"https://linkedin.com/in/juanaperez\"]")
        List<String> redes) {
}
