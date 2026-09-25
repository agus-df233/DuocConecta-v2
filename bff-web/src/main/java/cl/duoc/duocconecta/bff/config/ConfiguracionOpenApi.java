package cl.duoc.duocconecta.bff.config;

import cl.duoc.duocconecta.comun.seguridad.DocumentacionApi;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentación OpenAPI de bff-web, visible en Swagger UI. */
@Configuration
public class ConfiguracionOpenApi {

    @Bean
    public OpenAPI definicionDeLaApi() {
        return DocumentacionApi.de("bff-web", "Backend for Frontend de DuocConecta: la única puerta de entrada del frontend. Agrega en una sola respuesta los datos que cada pantalla necesita.");
    }
}
