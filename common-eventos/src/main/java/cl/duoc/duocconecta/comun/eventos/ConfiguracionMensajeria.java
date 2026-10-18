package cl.duoc.duocconecta.comun.eventos;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publicación de eventos. Cada productor la incorpora con {@code @Import(ConfiguracionMensajeria.class)}.
 *
 * <p>Con {@code duocconecta.mensajeria.rabbit.habilitada} cada evento va a los dos brokers: SNS y
 * RabbitMQ. Los servicios de negocio no se enteran: siguen haciendo {@code publishEvent(...)}.</p>
 *
 * <p>Las credenciales salen del proveedor por defecto: {@code ~/.aws} en local y {@code LabRole}
 * en Fargate. No hay claves en el código.</p>
 */
@Configuration
@EnableConfigurationProperties(PropiedadesMensajeria.class)
public class ConfiguracionMensajeria {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionMensajeria.class);

    @Bean
    public SnsClient clienteSns(PropiedadesMensajeria propiedades) {
        return SnsClient.builder().region(Region.of(propiedades.getRegion())).build();
    }

    @Bean
    public PublicadorEventos publicadorEventos(SnsClient clienteSns, JsonMapper jsonMapper,
                                               PropiedadesMensajeria propiedades,
                                               ObjectProvider<RabbitTemplate> plantillaRabbit) {
        List<PublicadorEventos> publicadores = new ArrayList<>();
        publicadores.add(new PublicadorSns(clienteSns, jsonMapper, propiedades));
        RabbitTemplate plantilla = plantillaRabbit.getIfAvailable();
        if (propiedades.getRabbit().isHabilitada() && plantilla != null) {
            // Publisher confirm: el broker avisa si guardó el mensaje. Un rechazo queda en el log.
            plantilla.setConfirmCallback((correlacion, confirmado, causa) -> {
                if (!confirmado) {
                    log.error("RabbitMQ no confirmó el evento {}: {}",
                            correlacion == null ? "?" : correlacion.getId(), causa);
                }
            });
            publicadores.add(new PublicadorRabbit(plantilla, jsonMapper, propiedades));
        }
        return publicadores.size() == 1 ? publicadores.get(0) : new PublicadorCompuesto(publicadores);
    }

    /**
     * El exchange topic donde publican los productores. Se declara también aquí y no solo en el
     * consumidor: así publicar no falla aunque ms-notificaciones todavía no haya arrancado.
     */
    @Bean
    @ConditionalOnProperty(prefix = "duocconecta.mensajeria.rabbit", name = "habilitada", havingValue = "true")
    public TopicExchange exchangeEventosRabbit(PropiedadesMensajeria propiedades) {
        return new TopicExchange(propiedades.getRabbit().getExchange(), true, false);
    }

    @Bean
    public PublicacionTrasConfirmar publicacionTrasConfirmar(PublicadorEventos publicadorEventos) {
        return new PublicacionTrasConfirmar(publicadorEventos);
    }
}
