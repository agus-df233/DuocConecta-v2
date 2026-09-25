package cl.duoc.duocconecta.notificaciones.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deja en el log cuándo llega algo a una DLQ. Así lo descartado queda registrado aunque nadie
 * mire la consola de AWS. Solo avisa cuando el número cambia, para no llenar el log.
 */
@Component
@ConditionalOnProperty(name = "duocconecta.mensajeria.vigilar-descartes", havingValue = "true")
public class VigilanciaDescartes {

    private static final Logger log = LoggerFactory.getLogger(VigilanciaDescartes.class);

    private final MonitorDescartes monitor;
    private final Map<String, Long> ultimos = new ConcurrentHashMap<>();

    public VigilanciaDescartes(MonitorDescartes monitor) {
        this.monitor = monitor;
    }

    @Scheduled(fixedDelayString = "${duocconecta.mensajeria.intervalo-vigilancia:PT1M}")
    public void revisar() {
        for (String nombre : monitor.colasDeDescarte()) {
            try {
                long actual = monitor.mensajes(nombre);
                Long anterior = ultimos.put(nombre, actual);
                if (actual > 0 && (anterior == null || actual != anterior)) {
                    log.warn("DLQ {}: {} mensaje(s) esperando revisión", nombre, actual);
                }
            } catch (RuntimeException error) {
                log.warn("No se pudo revisar la DLQ {}: {}", nombre, error.getMessage());
            }
        }
    }
}
