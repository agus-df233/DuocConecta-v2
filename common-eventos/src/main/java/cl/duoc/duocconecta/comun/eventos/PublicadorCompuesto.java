package cl.duoc.duocconecta.comun.eventos;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publica el mismo evento en cada broker configurado (SNS y RabbitMQ). Si uno falla, los demás
 * igual publican: un broker caído no debe dejar sin aviso a los consumidores del otro.
 */
public class PublicadorCompuesto implements PublicadorEventos {

    private static final Logger log = LoggerFactory.getLogger(PublicadorCompuesto.class);

    private final List<PublicadorEventos> publicadores;

    public PublicadorCompuesto(List<PublicadorEventos> publicadores) {
        this.publicadores = List.copyOf(publicadores);
    }

    @Override
    public void publicar(EventoDuocConecta evento) {
        RuntimeException primerError = null;
        int fallidos = 0;
        for (PublicadorEventos publicador : publicadores) {
            try {
                publicador.publicar(evento);
            } catch (RuntimeException error) {
                log.error("{} no pudo publicar el evento {} '{}': {}", publicador.getClass().getSimpleName(),
                        evento.idEvento(), evento.tipo(), error.getMessage());
                fallidos++;
                if (primerError == null) {
                    primerError = error;
                }
            }
        }
        // Solo se propaga si fallaron TODOS: entonces el evento de verdad no llegó a ningún broker.
        if (primerError != null && fallidos == publicadores.size()) {
            throw primerError;
        }
    }

    public List<PublicadorEventos> publicadores() {
        return publicadores;
    }
}
