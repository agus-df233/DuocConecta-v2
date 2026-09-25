package cl.duoc.duocconecta.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cl.duoc.duocconecta.comun.seguridad.IdDeCorrelacion;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuración de CORS del BFF.
 *
 * <p>El BFF es el único componente que recibe peticiones del navegador, así que es el único que
 * necesita CORS. Los orígenes se declaran uno por uno desde la configuración: nunca se usa el
 * comodín, porque eso permitiría que cualquier sitio hiciera peticiones en nombre del usuario.</p>
 */
@Configuration
public class ConfiguracionCors {

    private final PropiedadesBff propiedades;

    public ConfiguracionCors(PropiedadesBff propiedades) {
        this.propiedades = propiedades;
    }

    /**
     * Arma la política de CORS a partir de los orígenes, métodos y cabeceras configurados.
     */
    @Bean
    public CorsConfigurationSource fuenteDeConfiguracionCors() {
        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(propiedades.getCors().getOrigenes());
        configuracion.setAllowedMethods(propiedades.getCors().getMetodos());
        configuracion.setAllowedHeaders(propiedades.getCors().getHeaders());
        configuracion.setMaxAge(propiedades.getCors().getMaxAgeSegundos());

        // Sin esto el navegador no puede leer la cabecera en peticiones de otro origen, y el
        // identificador de correlación quedaría invisible en desarrollo local.
        configuracion.setExposedHeaders(java.util.List.of(IdDeCorrelacion.CABECERA));

        // El token viaja en la cabecera Authorization, no en cookies, así que no hace falta
        // habilitar credenciales.
        configuracion.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", configuracion);
        return fuente;
    }
}
