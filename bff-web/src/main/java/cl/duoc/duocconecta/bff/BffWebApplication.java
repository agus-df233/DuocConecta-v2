package cl.duoc.duocconecta.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de arranque del BFF (Backend for Frontend).
 *
 * <p>Es el único componente con el que habla el frontend React. Escucha en el puerto 8080,
 * valida el token y agrega en una sola respuesta lo que cada pantalla necesita.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BffWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(BffWebApplication.class, args);
    }
}
