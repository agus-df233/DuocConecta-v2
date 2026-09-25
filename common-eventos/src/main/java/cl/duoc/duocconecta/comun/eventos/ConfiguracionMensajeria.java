package cl.duoc.duocconecta.comun.eventos;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publicación de eventos. Cada productor la incorpora con {@code @Import(ConfiguracionMensajeria.class)}.
 *
 * <p>Las credenciales salen del proveedor por defecto: {@code ~/.aws} en local y {@code LabRole}
 * en Fargate. No hay claves en el código.</p>
 */
@Configuration
@EnableConfigurationProperties(PropiedadesMensajeria.class)
public class ConfiguracionMensajeria {

    @Bean
    public SnsClient clienteSns(PropiedadesMensajeria propiedades) {
        return SnsClient.builder().region(Region.of(propiedades.getRegion())).build();
    }

    @Bean
    public PublicadorEventos publicadorEventos(SnsClient clienteSns, JsonMapper jsonMapper,
                                               PropiedadesMensajeria propiedades) {
        return new PublicadorSns(clienteSns, jsonMapper, propiedades);
    }

    @Bean
    public PublicacionTrasConfirmar publicacionTrasConfirmar(PublicadorEventos publicadorEventos) {
        return new PublicacionTrasConfirmar(publicadorEventos);
    }
}
