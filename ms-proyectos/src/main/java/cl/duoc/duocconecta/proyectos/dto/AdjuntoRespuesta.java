package cl.duoc.duocconecta.proyectos.dto;

import cl.duoc.duocconecta.proyectos.domain.Adjunto;
import java.util.UUID;

/**
 * Un adjunto tal como lo ve el frontend: con la URL lista para descargar.
 *
 * @param url dónde descargarlo, sea de S3 o el enlace externo original
 */
public record AdjuntoRespuesta(
        UUID id,
        String nombre,
        String url,
        String tipoContenido,
        Long tamanoBytes) {

    /** @param urlDeS3 cómo construir la URL pública a partir de la clave */
    public static AdjuntoRespuesta desdeEntidad(Adjunto a, java.util.function.Function<String, String> urlDeS3) {
        String url = a.esArchivoSubido() ? urlDeS3.apply(a.getClaveS3()) : a.getUrlExterna();
        return new AdjuntoRespuesta(a.getId(), a.getNombre(), url, a.getTipoContenido(), a.getTamanoBytes());
    }
}
