package cl.duoc.duocconecta.bff.dto;

import java.util.List;

/**
 * Cifras de la vitrina, tal como las entrega ms-proyectos. {@code porPropietario} trae el
 * {@code oid} de cada dueña: el BFF lo usa para contar por carrera y no lo reenvía al navegador.
 * Los proyectos abiertos vienen aparte, ya por carrera, porque su dueño es el administrador.
 */
public record EstadisticasProyectos(long total, long enVitrina, long ocultosPorModeracion,
                                    long publicadosUltimos30Dias, long comentarios,
                                    List<Conteo> porVisibilidad, List<Conteo> porEstado,
                                    List<Conteo> porSede, List<Conteo> porPropietario,
                                    long abiertos, List<Conteo> porCarreraAbiertos) {
}
