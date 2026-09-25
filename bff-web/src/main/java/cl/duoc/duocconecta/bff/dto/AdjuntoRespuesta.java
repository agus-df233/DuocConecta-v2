package cl.duoc.duocconecta.bff.dto;

import java.util.UUID;

/** Un adjunto de un proyecto, tal como se lo pasa el BFF al frontend. */
public record AdjuntoRespuesta(
        UUID id,
        String nombre,
        String url,
        String tipoContenido,
        Long tamanoBytes) {
}
