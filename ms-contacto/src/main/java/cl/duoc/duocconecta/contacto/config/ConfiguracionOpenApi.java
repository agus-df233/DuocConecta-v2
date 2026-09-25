package cl.duoc.duocconecta.contacto.config;

import cl.duoc.duocconecta.comun.seguridad.DocumentacionApi;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentación OpenAPI de ms-contacto, visible en Swagger UI. */
@Configuration
public class ConfiguracionOpenApi {

    @Bean
    public OpenAPI definicionDeLaApi() {
        return DocumentacionApi.de("ms-contacto", "Microservicio de solicitudes de colaboración de DuocConecta.");
    }
}
