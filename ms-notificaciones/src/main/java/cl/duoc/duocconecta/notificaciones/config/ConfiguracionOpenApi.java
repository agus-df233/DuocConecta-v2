package cl.duoc.duocconecta.notificaciones.config;

import cl.duoc.duocconecta.comun.seguridad.DocumentacionApi;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentación OpenAPI de ms-notificaciones, visible en Swagger UI. */
@Configuration
public class ConfiguracionOpenApi {

    @Bean
    public OpenAPI definicionDeLaApi() {
        return DocumentacionApi.de("ms-notificaciones",
                "Consumidores de eventos (SQS), bandeja de avisos y estado de las colas de descarte.");
    }
}
