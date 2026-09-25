package cl.duoc.duocconecta.bff.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Traduce identificadores de proyecto a nombres que se puedan leer.
 *
 * <p>Mismo patrón que {@link ResolvedorAutores}: una consulta por proyecto distinto y no una por
 * fila, y sin memoria entre peticiones, porque un nombre guardado de más sobreviviría a que el
 * proyecto se borrara o se hiciera privado.</p>
 */
@Service
public class ResolvedorProyectos {

    private final ClienteProyectos clienteProyectos;

    public ResolvedorProyectos(ClienteProyectos clienteProyectos) {
        this.clienteProyectos = clienteProyectos;
    }

    /** Una consulta con memoria. Se usa y se descarta en cada petición. */
    public Consulta abrir() {
        return new Consulta();
    }

    public class Consulta {

        private final Map<UUID, String> conocidos = new HashMap<>();

        /** Null si el proyecto ya no existe o no es visible para quien pregunta. */
        public String nombreDe(UUID id) {
            if (id == null) {
                return null;
            }
            return conocidos.computeIfAbsent(id, clienteProyectos::nombreDe);
        }
    }
}
