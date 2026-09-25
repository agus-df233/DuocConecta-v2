package cl.duoc.duocconecta.bff.config;

import cl.duoc.duocconecta.comun.seguridad.CadenaDeSeguridad;
import cl.duoc.duocconecta.comun.seguridad.ConfiguracionSeguridadBase;
import cl.duoc.duocconecta.comun.seguridad.ConversorRolesJwt;
import cl.duoc.duocconecta.comun.seguridad.IdDeCorrelacion;
import cl.duoc.duocconecta.comun.seguridad.ManejadorRespuestasSeguridad;
import cl.duoc.duocconecta.comun.seguridad.RegistroDePeticiones;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Seguridad del BFF.
 *
 * <p>Valida el JWT igual que los microservicios: mismo emisor, misma audiencia, mismo mapeo de
 * roles. Es la primera de las tres capas que revisan el token.</p>
 */
@Configuration
@EnableMethodSecurity
@Import({ConfiguracionSeguridadBase.class, ManejadorRespuestasSeguridad.class,
         IdDeCorrelacion.class, RegistroDePeticiones.class})
public class ConfiguracionSeguridad {

    /** Aquí sí va CORS: el BFF es el único que recibe peticiones del navegador. */
    @Bean
    public SecurityFilterChain cadenaDeSeguridad(
            HttpSecurity http,
            ConversorRolesJwt conversorRolesJwt,
            AuthenticationEntryPoint puntoDeEntradaNoAutenticado,
            AccessDeniedHandler manejadorAccesoDenegado,
            CorsConfigurationSource fuenteDeConfiguracionCors) throws Exception {

        return CadenaDeSeguridad.armar(http, conversorRolesJwt,
                puntoDeEntradaNoAutenticado, manejadorAccesoDenegado, fuenteDeConfiguracionCors);
    }
}
