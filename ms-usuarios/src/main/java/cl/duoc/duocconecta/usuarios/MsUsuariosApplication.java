package cl.duoc.duocconecta.usuarios;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de arranque del microservicio de identidad y perfil.
 *
 * <p>Escucha en el puerto 8081 y es dueño del schema {@code usuarios}.</p>
 */
@SpringBootApplication
public class MsUsuariosApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsUsuariosApplication.class, args);
    }
}
