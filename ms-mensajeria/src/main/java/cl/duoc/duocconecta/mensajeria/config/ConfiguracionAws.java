package cl.duoc.duocconecta.mensajeria.config;

import cl.duoc.duocconecta.comun.eventos.PropiedadesMensajeria;
import cl.duoc.duocconecta.comun.eventos.topologia.ProvisionadorAws;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.json.JsonMapper;

/** Clientes de SQS y SNS. Credenciales del proveedor por defecto: {@code ~/.aws} o {@code LabRole}. */
@Configuration
@EnableConfigurationProperties(PropiedadesMensajeria.class)
public class ConfiguracionAws {

    @Bean
    public SqsClient clienteSqs(PropiedadesMensajeria propiedades) {
        return SqsClient.builder().region(Region.of(propiedades.getRegion())).build();
    }

    @Bean
    public SnsClient clienteSns(PropiedadesMensajeria propiedades) {
        return SnsClient.builder().region(Region.of(propiedades.getRegion())).build();
    }

    @Bean
    public ProvisionadorAws provisionadorAws(SqsClient clienteSqs, SnsClient clienteSns, JsonMapper jsonMapper) {
        return new ProvisionadorAws(clienteSqs, clienteSns, jsonMapper);
    }
}
