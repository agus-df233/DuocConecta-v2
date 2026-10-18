package cl.duoc.duocconecta.usuarios.service;

import cl.duoc.duocconecta.usuarios.dto.EstadisticasUsuariosRespuesta;
import cl.duoc.duocconecta.usuarios.dto.EstadisticasUsuariosRespuesta.Conteo;
import cl.duoc.duocconecta.usuarios.repository.RepositorioUsuarios;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cifras de la comunidad para la administración. Agrupa en la base, no en memoria. */
@Service
public class ServicioEstadisticas {

    /** Etiqueta para quien todavía no completa la carrera o la sede. */
    static final String SIN_DATO = "Sin completar";

    private final RepositorioUsuarios repositorio;

    public ServicioEstadisticas(RepositorioUsuarios repositorio) {
        this.repositorio = repositorio;
    }

    /** Totales y conteos por rol, carrera y sede. */
    @Transactional(readOnly = true)
    public EstadisticasUsuariosRespuesta calcular() {
        return new EstadisticasUsuariosRespuesta(
                repositorio.count(),
                repositorio.countByVisibleIsTrue(),
                conteos(repositorio.contarPorRol()),
                conteos(repositorio.contarPorCarrera()),
                conteos(repositorio.contarPorSede()));
    }

    /** Cada fila llega como {@code [clave, cantidad]}; una clave nula o vacía es "Sin completar". */
    private static List<Conteo> conteos(List<Object[]> filas) {
        return filas.stream()
                .map(fila -> new Conteo(
                        fila[0] == null || fila[0].toString().isBlank() ? SIN_DATO : fila[0].toString(),
                        ((Number) fila[1]).longValue()))
                .toList();
    }
}
