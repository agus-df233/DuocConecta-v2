package cl.duoc.duocconecta.mensajeria.config;

import cl.duoc.duocconecta.comun.seguridad.DocumentacionApi;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentación OpenAPI de ms-mensajeria, visible en Swagger UI. */
@Configuration
public class ConfiguracionOpenApi {

    @Bean
    public OpenAPI definicionDeLaApi() {
        return DocumentacionApi.de("ms-mensajeria",
                "Administración de la mensajería: colas SQS, tópicos SNS (exchanges) y suscripciones (bindings).");
    }
}
