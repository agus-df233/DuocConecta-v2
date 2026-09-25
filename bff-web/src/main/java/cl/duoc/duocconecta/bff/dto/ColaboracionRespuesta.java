package cl.duoc.duocconecta.bff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Una solicitud de colaboración tal como la ve el frontend.
 *
 * <p>El BFF la redefine en vez de importar el DTO de ms-contacto: si el microservicio cambia un
 * campo, el frontend no se entera hasta que alguien lo decida.</p>
 *
 * <p>El campo {@code contacto} trae <b>los datos de la otra parte</b>, resueltos por el BFF, que
 * es quien sabe quién está mirando. Antes venían los dos lados crudos y el frontend elegía, y se
 * equivocaba: mostraba los datos propios con el nombre del otro.</p>
 */
@Schema(description = "Solicitud de colaboración con su estado")
public record ColaboracionRespuesta(
        UUID id,
        String solicitanteId,
        @Schema(description = "Quien la envió; null si ocultó su perfil") Autor solicitante,
        String solicitadoId,
        @Schema(description = "Quien la recibió; null si ocultó su perfil") Autor solicitado,
        UUID proyectoId,
        @Schema(description = "Nombre del proyecto que originó la solicitud") String proyectoNombre,
        String mensaje,
        @Schema(description = "PENDIENTE, ACEPTADA o RECHAZADA") String estado,
        @Schema(description = "Datos de la otra parte. Solo llegan si la solicitud fue aceptada")
        DatosDeContacto contacto,
        // Los dos lados crudos, tal como los devuelve ms-contacto. No salen al frontend.
        @Schema(hidden = true) DatosDeContacto contactoSolicitante,
        @Schema(hidden = true) DatosDeContacto contactoSolicitado,
        Instant fechaSolicitud,
        Instant fechaRespuesta) {

    /**
     * Copia la solicitud sumándole quién es cada parte, el nombre del proyecto y el contacto que
     * corresponde ver a quien mira.
     *
     * @param oidDeQuienMira para decidir cuál de los dos lados es "el otro"
     */
    public ColaboracionRespuesta resuelta(Autor quienEnvia, Autor quienRecibe,
                                          String nombreDelProyecto, String oidDeQuienMira) {
        // Si miro una solicitud que envié, el otro es el solicitado; si la recibí, el solicitante.
        DatosDeContacto delOtro = solicitanteId != null && solicitanteId.equals(oidDeQuienMira)
                ? contactoSolicitado
                : contactoSolicitante;

        return new ColaboracionRespuesta(id, solicitanteId, quienEnvia, solicitadoId, quienRecibe,
                proyectoId, nombreDelProyecto, mensaje, estado,
                delOtro == null ? DatosDeContacto.NINGUNO : delOtro,
                null, null,
                fechaSolicitud, fechaRespuesta);
    }
}
