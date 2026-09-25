package cl.duoc.duocconecta.bff.config;

import cl.duoc.duocconecta.comun.seguridad.IdDeCorrelacion;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Clientes HTTP hacia los microservicios.
 *
 * <p>Con timeout explícito: sin él, un microservicio colgado deja al BFF esperando para siempre y
 * se lleva al frontend con él. Al agotarse, el manejador de errores devuelve 503.</p>
 *
 * <p>Todos reenvían el identificador de correlación, así una misma petición del navegador queda
 * marcada igual en el registro del BFF y en el del microservicio.</p>
 */
@Configuration
public class ConfiguracionClientesHttp {

    /** Cliente hacia ms-usuarios: perfiles y redes. */
    @Bean
    public RestClient clienteMsUsuarios(PropiedadesBff propiedades) {
        return construir(propiedades.getUrlMsUsuarios(), propiedades.getTimeoutSegundos());
    }

    /** Cliente hacia ms-proyectos: la vitrina. */
    @Bean
    public RestClient clienteMsProyectos(PropiedadesBff propiedades) {
        return construir(propiedades.getUrlMsProyectos(), propiedades.getTimeoutSegundos());
    }

    /** Cliente hacia ms-contacto: solicitudes de colaboración. */
    @Bean
    public RestClient clienteMsContacto(PropiedadesBff propiedades) {
        return construir(propiedades.getUrlMsContacto(), propiedades.getTimeoutSegundos());
    }

    /** Timeout de conexión y de lectura. Usa el cliente del JDK para no sumar una librería. */
    private RestClient construir(String urlBase, int segundos) {
        Duration timeout = Duration.ofSeconds(segundos);

        // Tiempo máximo para establecer la conexión con el microservicio.
        HttpClient clienteHttp = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();

        // Tiempo máximo de espera de la respuesta una vez conectado.
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(clienteHttp);
        fabrica.setReadTimeout(timeout);

        return RestClient.builder()
                .baseUrl(urlBase)
                .requestFactory(fabrica)
                // El identificador viaja con la llamada: es lo que permite cruzar los registros
                // de los cuatro servicios cuando algo falla.
                .requestInitializer(peticion -> {
                    String id = MDC.get(IdDeCorrelacion.CLAVE);
                    if (id != null) {
                        peticion.getHeaders().set(IdDeCorrelacion.CABECERA, id);
                    }
                })
                .build();
    }
}
