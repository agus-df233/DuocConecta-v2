package cl.duoc.duocconecta.usuarios.config;

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

/**
 * Seguridad de ms-usuarios.
 *
 * <p>Es un OAuth2 Resource Server: valida el JWT de Azure AD por su cuenta, aunque el BFF y el
 * API Manager ya lo hayan validado antes. Defensa en profundidad: quien llegue salteándose
 * las capas de adelante igual necesita un token válido.</p>
 */
@Configuration
@EnableMethodSecurity
@Import({ConfiguracionSeguridadBase.class, ManejadorRespuestasSeguridad.class,
         IdDeCorrelacion.class, RegistroDePeticiones.class})
public class ConfiguracionSeguridad {

    /** Sin CORS: el microservicio no habla con el navegador, solo con el BFF. */
    @Bean
    public SecurityFilterChain cadenaDeSeguridad(
            HttpSecurity http,
            ConversorRolesJwt conversorRolesJwt,
            AuthenticationEntryPoint puntoDeEntradaNoAutenticado,
            AccessDeniedHandler manejadorAccesoDenegado) throws Exception {

        return CadenaDeSeguridad.armar(http, conversorRolesJwt,
                puntoDeEntradaNoAutenticado, manejadorAccesoDenegado, null);
    }
}
