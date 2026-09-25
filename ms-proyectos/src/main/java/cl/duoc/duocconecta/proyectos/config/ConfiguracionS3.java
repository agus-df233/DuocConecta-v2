package cl.duoc.duocconecta.proyectos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Clientes de S3 para los adjuntos de los proyectos.
 *
 * <p>Las credenciales salen del proveedor por defecto, que en Fargate resuelve el rol de la tarea.
 * No hay claves en el código ni en la configuración.</p>
 */
@Configuration
public class ConfiguracionS3 {

    @Value("${duocconecta.s3.region:us-east-1}")
    private String region;

    /** Se usa para borrar objetos cuando se quita un adjunto o se borra el proyecto. */
    @Bean
    public S3Client clienteS3() {
        return S3Client.builder().region(Region.of(region)).build();
    }

    /** Firma las subidas. Firmar no llama a S3: la firma se calcula con las credenciales locales. */
    @Bean
    public S3Presigner firmadorS3() {
        return S3Presigner.builder().region(Region.of(region)).build();
    }
}
