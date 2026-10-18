package cl.duoc.duocconecta.bff.service;

import cl.duoc.duocconecta.bff.dto.Conteo;
import cl.duoc.duocconecta.bff.dto.EstadisticasProyectos;
import cl.duoc.duocconecta.bff.dto.EstadisticasUsuarios;
import cl.duoc.duocconecta.bff.dto.ResumenAdministracionRespuesta;
import cl.duoc.duocconecta.bff.dto.ResumenAdministracionRespuesta.Vitrina;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Arma el dashboard de administración juntando ms-usuarios y ms-proyectos.
 *
 * <p>Lo que solo el BFF puede hacer: contar proyectos por carrera, que ninguno de los dos sabe por
 * separado (ms-proyectos guarda el {@code oid} de la dueña y ms-usuarios su carrera).</p>
 */
@Service
public class ServicioAdministracion {

    /** Dueña que ocultó su perfil o que todavía no completa la carrera. */
    static final String SIN_CARRERA = "Perfil oculto o sin carrera";

    private final ClienteUsuarios clienteUsuarios;
    private final ClienteProyectos clienteProyectos;
    private final ResolvedorAutores resolvedorAutores;

    public ServicioAdministracion(ClienteUsuarios clienteUsuarios, ClienteProyectos clienteProyectos,
                                  ResolvedorAutores resolvedorAutores) {
        this.clienteUsuarios = clienteUsuarios;
        this.clienteProyectos = clienteProyectos;
        this.resolvedorAutores = resolvedorAutores;
    }

    /** Las cifras de la comunidad y de la vitrina, con los proyectos ya contados por carrera. */
    public ResumenAdministracionRespuesta resumen() {
        EstadisticasUsuarios comunidad = clienteUsuarios.estadisticas();
        EstadisticasProyectos proyectos = clienteProyectos.estadisticas();
        Vitrina vitrina = new Vitrina(proyectos.total(), proyectos.enVitrina(), proyectos.ocultosPorModeracion(),
                proyectos.publicadosUltimos30Dias(), proyectos.comentarios(),
                lista(proyectos.porVisibilidad()), lista(proyectos.porEstado()), lista(proyectos.porSede()),
                porCarrera(lista(proyectos.porPropietario())));
        return new ResumenAdministracionRespuesta(comunidad, vitrina);
    }

    /** Suma los proyectos de cada dueña en su carrera. Una consulta a ms-usuarios por persona. */
    private List<Conteo> porCarrera(List<Conteo> porPropietario) {
        ResolvedorAutores.Consulta consulta = resolvedorAutores.abrir();
        Map<String, Long> suma = new LinkedHashMap<>();
        for (Conteo propietario : porPropietario) {
            String carrera = consulta.autorDe(propietario.clave()).carrera();
            String clave = carrera == null || carrera.isBlank() ? SIN_CARRERA : carrera;
            suma.merge(clave, propietario.cantidad(), Long::sum);
        }
        return suma.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(e -> new Conteo(e.getKey(), e.getValue()))
                .toList();
    }

    private static List<Conteo> lista(List<Conteo> conteos) {
        return conteos == null ? List.of() : conteos;
    }
}
