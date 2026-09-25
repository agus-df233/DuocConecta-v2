package cl.duoc.duocconecta.proyectos.service;

import cl.duoc.duocconecta.proyectos.dto.FirmaAdjuntoDatos;
import cl.duoc.duocconecta.proyectos.dto.FirmaAdjuntoRespuesta;
import cl.duoc.duocconecta.proyectos.exception.SolicitudInvalidaException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Adjuntos de un proyecto en S3. Es el único punto que habla con S3.
 *
 * <p>El navegador sube directo: el backend solo firma una autorización temporal. Así el archivo
 * nunca pasa por el contenedor, que tiene 1 GB de memoria compartido con otras tres JVM.</p>
 */
@Service
public class ServicioAdjuntos {

    private static final Logger log = LoggerFactory.getLogger(ServicioAdjuntos.class);

    /** Todo lo subido cuelga de este prefijo, que es el único con lectura pública en el bucket. */
    public static final String PREFIJO = "adjuntos/";

    /** Tope por archivo. El navegador podría mentir, así que la firma lo vuelve a imponer. */
    public static final long TAMANO_MAXIMO = 10L * 1024 * 1024;

    /** Cuántos adjuntos admite un proyecto. */
    public static final int MAXIMO_POR_PROYECTO = 5;

    private static final Set<String> EXTENSIONES = Set.of(
            "pdf", "png", "jpg", "jpeg", "gif", "webp",
            "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "md", "zip", "fig");

    private static final Duration VIGENCIA = Duration.ofMinutes(10);

    private final S3Client clienteS3;
    private final S3Presigner firmadorS3;
    private final String bucket;
    private final String region;

    public ServicioAdjuntos(S3Client clienteS3, S3Presigner firmadorS3,
                            @Value("${duocconecta.s3.bucket:}") String bucket,
                            @Value("${duocconecta.s3.region:us-east-1}") String region) {
        this.clienteS3 = clienteS3;
        this.firmadorS3 = firmadorS3;
        this.bucket = bucket;
        this.region = region;
    }

    /**
     * Autoriza la subida de un archivo.
     *
     * <p>No se pide el identificador del proyecto a propósito: al crear uno todavía no existe. La
     * clave lleva un UUID propio del archivo, así que tampoco se puede adivinar desde el proyecto.</p>
     */
    public FirmaAdjuntoRespuesta firmarSubida(FirmaAdjuntoDatos datos) {
        if (bucket.isBlank()) {
            throw new IllegalStateException("Falta configurar duocconecta.s3.bucket");
        }
        validar(datos);

        String clave = PREFIJO + UUID.randomUUID() + "/" + sanear(datos.nombre());

        PutObjectRequest objeto = PutObjectRequest.builder()
                .bucket(bucket)
                .key(clave)
                .contentType(datos.tipoContenido())
                .contentLength(datos.tamanoBytes())
                .build();

        var firmada = firmadorS3.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(VIGENCIA)
                .putObjectRequest(objeto)
                .build());

        log.debug("Firmada la subida de {} ({} bytes)", clave, datos.tamanoBytes());

        return new FirmaAdjuntoRespuesta(
                clave,
                firmada.url().toString(),
                urlPublica(clave),
                Instant.now().plus(VIGENCIA));
    }

    /** Dónde queda el archivo una vez subido. El prefijo tiene lectura pública en el bucket. */
    public String urlPublica(String clave) {
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + clave;
    }

    /** Borra el objeto. Si ya no está, no es un error: el resultado buscado es que no exista. */
    public void borrar(String clave) {
        if (clave == null || !clave.startsWith(PREFIJO)) {
            return;
        }
        try {
            clienteS3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(clave).build());
        } catch (RuntimeException fallo) {
            // Que no se pueda borrar el archivo no debe impedir borrar el proyecto.
            log.warn("No se pudo borrar {} de S3: {}", clave, fallo.getMessage());
        }
    }

    /** Una clave que no venga de una firma nuestra no se acepta. */
    public void validarClave(String clave) {
        if (clave == null || !clave.startsWith(PREFIJO) || clave.contains("..")) {
            throw new SolicitudInvalidaException("La referencia del archivo no es válida.");
        }
    }

    private void validar(FirmaAdjuntoDatos datos) {
        if (datos.tamanoBytes() > TAMANO_MAXIMO) {
            throw new SolicitudInvalidaException(
                    "El archivo supera el máximo de " + (TAMANO_MAXIMO / 1024 / 1024) + " MB.");
        }
        String extension = extensionDe(datos.nombre());
        if (!EXTENSIONES.contains(extension)) {
            throw new SolicitudInvalidaException(
                    "No se admiten archivos ." + extension + ". Se aceptan documentos, imágenes y comprimidos.");
        }
    }

    private static String extensionDe(String nombre) {
        int punto = nombre.lastIndexOf('.');
        return punto < 0 ? "" : nombre.substring(punto + 1).toLowerCase(Locale.ROOT);
    }

    /** Quita rutas y caracteres raros: el nombre viaja del dispositivo de otra persona. */
    private static String sanear(String nombre) {
        String base = nombre.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        return base.length() > 120 ? base.substring(base.length() - 120) : base;
    }
}
