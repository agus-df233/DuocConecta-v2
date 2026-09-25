package cl.duoc.duocconecta.comun.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Deja constancia de cómo terminó cada petición, sin importar qué componente la resolvió.
 *
 * <p>Los manejadores de excepciones solo ven los errores que pasan por ellos. Un 403 puede salir
 * de la cadena de filtros o del procesador de CORS y no dejar rastro en ninguno: la aplicación
 * dice que no y no hay forma de saber quién lo decidió. Este filtro va antes que todo y mira el
 * estado final, así que las ve todas.</p>
 */
@Configuration
public class RegistroDePeticiones {

    private static final Logger log = LoggerFactory.getLogger(RegistroDePeticiones.class);

    /** Sobre este tiempo la petición se registra aunque haya salido bien. */
    private static final long LENTA_MS = 1000;

    /** Justo después del identificador de correlación, para que sus líneas ya lo lleven. */
    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> filtroRegistroDePeticiones() {
        var registro = new FilterRegistrationBean<OncePerRequestFilter>(new OncePerRequestFilter() {

            @Override
            protected void doFilterInternal(HttpServletRequest peticion,
                                            HttpServletResponse respuesta,
                                            FilterChain cadena) throws ServletException, IOException {
                long inicio = System.currentTimeMillis();
                try {
                    cadena.doFilter(peticion, respuesta);
                } finally {
                    anotar(peticion, respuesta, System.currentTimeMillis() - inicio);
                }
            }

            @Override
            protected boolean shouldNotFilter(HttpServletRequest peticion) {
                // El monitoreo consulta la salud cada 30 segundos: no tiene sentido registrarlo.
                return peticion.getRequestURI().startsWith("/actuator");
            }
        });
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registro;
    }

    /**
     * Los errores y las peticiones lentas se registran siempre; el resto solo en modo depuración.
     *
     * <p>Una petición lenta importa aunque haya salido bien: con el recolector serie y CPU
     * compartida, es lo que anticipa que el balanceador dé el contenedor por muerto.</p>
     */
    private static void anotar(HttpServletRequest peticion, HttpServletResponse respuesta, long ms) {
        int estado = respuesta.getStatus();
        if (estado >= 400) {
            // Sin usuario ni permisos a propósito: este filtro es más externo que la cadena de
            // seguridad, que ya limpió el contexto cuando se llega aquí. Ese dato lo registra
            // ManejadorRespuestasSeguridad, que sí corre dentro de la cadena.
            log.warn("{} {} -> {} en {} ms",
                    peticion.getMethod(), peticion.getRequestURI(), estado, ms);
        } else if (ms >= LENTA_MS) {
            log.warn("Lenta: {} {} -> {} en {} ms",
                    peticion.getMethod(), peticion.getRequestURI(), estado, ms);
        } else if (log.isDebugEnabled()) {
            log.debug("{} {} -> {} en {} ms",
                    peticion.getMethod(), peticion.getRequestURI(), estado, ms);
        }
    }
}
