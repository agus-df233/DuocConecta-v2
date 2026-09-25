package cl.duoc.duocconecta.comun.seguridad;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * La cadena de filtros que comparten los cuatro servicios.
 *
 * <p>Era idéntica en los cuatro salvo el CORS, así que vive aquí y cada servicio la invoca desde
 * su propia {@code ConfiguracionSeguridad}. Se mantiene el bean por servicio a propósito: cada uno
 * declara su seguridad y se ve que la valida por su cuenta.</p>
 */
public final class CadenaDeSeguridad {

    private CadenaDeSeguridad() {
    }

    /** Rutas abiertas: monitoreo y documentación. No exponen datos de nadie. */
    public static final String[] RUTAS_PUBLICAS = {
            "/actuator/health",
            "/actuator/health/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };

    /**
     * Arma la cadena: sin sesión, sin CSRF, todo autenticado salvo las rutas públicas, y el JWT
     * validado en firma, vigencia, emisor y audiencia.
     *
     * @param cors fuente de configuración de CORS, o {@code null} para deshabilitarlo. Solo el BFF
     *             habla con el navegador; los microservicios no lo necesitan.
     */
    public static SecurityFilterChain armar(
            HttpSecurity http,
            ConversorRolesJwt conversorRolesJwt,
            AuthenticationEntryPoint puntoDeEntradaNoAutenticado,
            AccessDeniedHandler manejadorAccesoDenegado,
            CorsConfigurationSource cors) throws Exception {

        http
            // Sin cookies ni sesión: la identidad viaja en el token de cada petición, así que no
            // hay nada que proteger con CSRF.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(rutas -> rutas
                    .requestMatchers(RUTAS_PUBLICAS).permitAll()
                    .anyRequest().authenticated())

            .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(conversorRolesJwt))
                    .authenticationEntryPoint(puntoDeEntradaNoAutenticado)
                    .accessDeniedHandler(manejadorAccesoDenegado))

            // Los mismos manejadores para el resto de la cadena: así los 401 y 403 salen siempre
            // con el mismo formato, los produzca quien los produzca.
            .exceptionHandling(errores -> errores
                    .authenticationEntryPoint(puntoDeEntradaNoAutenticado)
                    .accessDeniedHandler(manejadorAccesoDenegado));

        if (cors == null) {
            http.cors(c -> c.disable());
        } else {
            http.cors(c -> c.configurationSource(cors));
        }

        return http.build();
    }
}
