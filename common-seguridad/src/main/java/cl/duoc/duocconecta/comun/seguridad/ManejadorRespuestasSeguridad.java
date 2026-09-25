package cl.duoc.duocconecta.comun.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Respuestas de 401 y 403 con un mensaje que se entienda.
 *
 * <p>Spring las devuelve con el cuerpo vacío, y desde el frontend no hay forma de saber si falló
 * el token, la audiencia o el rol. El JSON se arma a mano para no atar este módulo compartido a
 * una versión de Jackson.</p>
 */
@Configuration
public class ManejadorRespuestasSeguridad {

    private static final Logger log = LoggerFactory.getLogger(ManejadorRespuestasSeguridad.class);

    /** Sin token o con uno inválido: 401 con la cabecera {@code WWW-Authenticate} que pide el estándar. */
    @Bean
    public AuthenticationEntryPoint puntoDeEntradaNoAutenticado() {
        return (peticion, respuesta, excepcion) -> escribirProblema(
                peticion,
                respuesta,
                HttpStatus.UNAUTHORIZED,
                "No autenticado",
                "La petición no incluye un token válido. Inicia sesión con tu cuenta institucional "
                        + "y envía el token en la cabecera Authorization: Bearer <token>.");
    }

    /** Token válido sin permiso: o el rol no alcanza, o el dominio no está autorizado. */
    @Bean
    public AccessDeniedHandler manejadorAccesoDenegado() {
        return (peticion, respuesta, excepcion) -> {
            // Este era el único camino que devolvía 403 sin dejar rastro en ningún lado, así que
            // depurarlo era adivinar. Se registran el método, la ruta y las authorities que
            // realmente tenía la petición, que es lo que decide si @PreAuthorize deja pasar.
            Authentication quien = SecurityContextHolder.getContext().getAuthentication();
            log.warn("403 en {} {} · usuario={} authorities={} · causa: {}",
                    peticion.getMethod(), peticion.getRequestURI(),
                    quien == null ? "(sin autenticación)" : quien.getName(),
                    quien == null ? "(ninguna)" : quien.getAuthorities(),
                    excepcion.getMessage());

            escribirProblema(
                peticion,
                respuesta,
                HttpStatus.FORBIDDEN,
                "Acceso denegado",
                "Tu cuenta no tiene permiso para esta operación. Puede que tu correo no pertenezca a "
                        + "un dominio institucional autorizado de Duoc UC, o que tu rol no alcance "
                        + "para este recurso.");
        };
    }

    /** Cuerpo con el formato Problem Details (RFC 9457). */
    private void escribirProblema(HttpServletRequest peticion,
                                  HttpServletResponse respuesta,
                                  HttpStatus estado,
                                  String titulo,
                                  String detalle) throws IOException {

        if (estado == HttpStatus.UNAUTHORIZED) {
            respuesta.setHeader("WWW-Authenticate", "Bearer");
        }
        respuesta.setStatus(estado.value());
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());

        String cuerpo = """
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s","instance":"%s"}"""
                .formatted(titulo, estado.value(), detalle, peticion.getRequestURI());

        respuesta.getWriter().write(cuerpo);
    }
}
