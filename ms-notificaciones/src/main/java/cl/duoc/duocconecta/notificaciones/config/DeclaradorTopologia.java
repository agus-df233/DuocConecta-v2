package cl.duoc.duocconecta.notificaciones.config;

import cl.duoc.duocconecta.comun.eventos.PropiedadesMensajeria;
import cl.duoc.duocconecta.comun.eventos.topologia.Cola;
import cl.duoc.duocconecta.comun.eventos.topologia.ProvisionadorAws;
import cl.duoc.duocconecta.comun.eventos.topologia.Suscripcion;
import cl.duoc.duocconecta.comun.eventos.topologia.Topico;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Al arrancar, crea en AWS lo que falte de la topología declarada en {@link ConfiguracionTopologia}.
 *
 * <p>Es lo que hace {@code RabbitAdmin} con los beans de RabbitMQ. El orden importa: las DLQ antes
 * que las colas que apuntan a ellas, y los permisos antes de suscribir. Si AWS no responde, se
 * registra y el servicio arranca igual: los listeners reintentan solos.</p>
 */
@Component
public class DeclaradorTopologia implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DeclaradorTopologia.class);

    private final PropiedadesMensajeria propiedades;
    private final ProvisionadorAws provisionador;
    private final Topico topico;
    private final List<Cola> colas;
    private final List<Suscripcion> suscripciones;

    public DeclaradorTopologia(PropiedadesMensajeria propiedades, ProvisionadorAws provisionador,
                               Topico topico, List<Cola> colas, List<Suscripcion> suscripciones) {
        this.propiedades = propiedades;
        this.provisionador = provisionador;
        this.topico = topico;
        this.colas = colas;
        this.suscripciones = suscripciones;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        if (!propiedades.isHabilitada() || !propiedades.isDeclararTopologia()) {
            log.info("Declaración de topología desactivada");
            return;
        }
        try {
            colas.stream().filter(Cola::esDescarte).forEach(provisionador::asegurarCola);
            colas.stream().filter(cola -> !cola.esDescarte()).forEach(provisionador::asegurarCola);
            String topicoArn = provisionador.asegurarTopico(topico);
            colas.stream().filter(Cola::recibeDeSns)
                    .forEach(cola -> provisionador.asegurarPermisoTopico(cola.nombre(), topicoArn));
            suscripciones.forEach(suscripcion -> provisionador.asegurarSuscripcion(suscripcion, topicoArn));
            log.info("Topología verificada: 1 tópico, {} colas, {} suscripciones", colas.size(), suscripciones.size());
        } catch (RuntimeException error) {
            log.error("No se pudo verificar la topología en AWS: {}", error.getMessage(), error);
        }
    }
}
