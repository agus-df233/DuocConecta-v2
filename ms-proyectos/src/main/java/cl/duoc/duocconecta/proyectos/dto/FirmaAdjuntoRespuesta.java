package cl.duoc.duocconecta.proyectos.dto;

import java.time.Instant;

/**
 * La autorización para subir un archivo.
 *
 * @param clave       dónde queda el archivo en S3; es lo que se guarda en la base
 * @param urlFirmada  destino del PUT, válido por poco tiempo
 * @param urlPublica  desde dónde se descargará una vez subido
 * @param expiraEn    cuándo deja de servir la firma
 */
public record FirmaAdjuntoRespuesta(
        String clave,
        String urlFirmada,
        String urlPublica,
        Instant expiraEn) {
}
