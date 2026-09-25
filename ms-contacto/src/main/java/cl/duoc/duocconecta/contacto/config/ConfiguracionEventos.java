package cl.duoc.duocconecta.contacto.config;

import cl.duoc.duocconecta.comun.eventos.ConfiguracionMensajeria;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Publicación de eventos en SNS. Vive aparte de la seguridad y del negocio: los servicios solo
 * hacen {@code publishEvent(...)} y no saben que existe SNS.
 */
@Configuration
@Import(ConfiguracionMensajeria.class)
public class ConfiguracionEventos {
}
