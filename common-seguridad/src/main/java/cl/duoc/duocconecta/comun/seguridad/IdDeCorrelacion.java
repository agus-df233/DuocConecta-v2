package cl.duoc.duocconecta.comun.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Le pone un identificador a cada petición para poder seguirla entre servicios.
 *
 * <p>Los cuatro contenedores escriben al mismo grupo de CloudWatch. Sin este identificador no hay
 * forma de saber qué línea del BFF corresponde a qué línea del microservicio, y depurar un fallo
 * se vuelve adivinar. El BFF reenvía la cabecera al llamar hacia atrás, así que una misma petición
 * queda marcada igual en las cuatro corrientes.</p>
 */
@Configuration
public class IdDeCorrelacion {

    /** Cabecera que transporta el identificador entre capas. */
    public static final String CABECERA = "X-Request-Id";

    /** Clave en el MDC. El patrón de log de cada servicio la imprime en todas las líneas. */
    public static final String CLAVE = "idPeticion";

    /** Va primero que todo: si no, lo que rechace la seguridad quedaría sin identificar. */
    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> filtroIdDeCorrelacion() {
        var registro = new FilterRegistrationBean<OncePerRequestFilter>(new OncePerRequestFilter() {

            @Override
            protected void doFilterInternal(HttpServletRequest peticion,
                                            HttpServletResponse respuesta,
                                            FilterChain cadena) throws ServletException, IOException {
                MDC.put(CLAVE, identificadorDe(peticion));
                try {
                    // Se devuelve para que el navegador pueda citarlo al reportar un problema.
                    respuesta.setHeader(CABECERA, MDC.get(CLAVE));
                    cadena.doFilter(peticion, respuesta);
                } finally {
                    // Obligatorio: los hilos se reutilizan y el valor viajaría a otra petición.
                    MDC.remove(CLAVE);
                }
            }
        });
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registro;
    }

    /**
     * Reutiliza el identificador que ya venga de afuera, y si no genera uno corto.
     *
     * <p>El ALB inyecta {@code X-Amzn-Trace-Id} en toda petición, así que en la nube casi siempre
     * hay uno: aprovecharlo permite cruzar los registros de la aplicación con los del balanceador
     * y los del API Gateway.</p>
     */
    private static String identificadorDe(HttpServletRequest peticion) {
        String recibido = peticion.getHeader(CABECERA);
        if (recibido == null || recibido.isBlank()) {
            recibido = peticion.getHeader("X-Amzn-Trace-Id");
        }
        if (recibido == null || recibido.isBlank()) {
            return UUID.randomUUID().toString().substring(0, 8);
        }
        // Se acota: el de Amazon es largo y ensuciaría cada línea de log.
        return recibido.length() > 40 ? recibido.substring(0, 40) : recibido;
    }
}
