package cl.duoc.duocconecta.notificaciones.config;

import cl.duoc.duocconecta.comun.eventos.PropiedadesMensajeria;
import cl.duoc.duocconecta.comun.eventos.PropiedadesMensajeria.Parametros;
import cl.duoc.duocconecta.comun.eventos.topologia.Cola;
import cl.duoc.duocconecta.comun.eventos.topologia.Suscripcion;
import cl.duoc.duocconecta.comun.eventos.topologia.Topico;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * La topología de mensajería, una ruta por bean. Es el equivalente de declarar Exchange, Queue y
 * Binding en RabbitMQ: {@link Topico} es el exchange, {@link Cola} la queue y
 * {@link Suscripcion} el binding. Los nombres salen de {@code duocconecta.mensajeria}.
 *
 * <pre>
 * SNS duocconecta-eventos ─┬─ prefix "proyecto."     ─► notificaciones-proyectos      ─► …-descartes
 *                          ├─ prefix "colaboracion." ─► notificaciones-colaboraciones ─► …-descartes
 *                          └─ (sin filtro, el "#")   ─► auditoria-eventos             ─► …-descartes
 * Lo que SNS no logra entregar ─► eventos-no-entregados
 * </pre>
 *
 * <p>{@link DeclaradorTopologia} crea al arrancar lo que falte, sin borrar ni pisar nada.</p>
 */
@Configuration
public class ConfiguracionTopologia {

    private final PropiedadesMensajeria propiedades;
    private final Parametros parametros;

    public ConfiguracionTopologia(PropiedadesMensajeria propiedades) {
        this.propiedades = propiedades;
        this.parametros = propiedades.getParametros();
    }

    // ---------------------------------------------------------------- exchange

    /** Recibe cada evento una vez y entrega una copia a cada cola suscrita. */
    @Bean
    public Topico topicoEventos() {
        return new Topico(propiedades.getTopico().getNombre());
    }

    // ---------------------------------------------------------------- DLQ (van primero)

    @Bean
    public Cola descartesProyectos() {
        return descarte(propiedades.descarte("proyectos"), false);
    }

    @Bean
    public Cola descartesColaboraciones() {
        return descarte(propiedades.descarte("colaboraciones"), false);
    }

    @Bean
    public Cola descartesAuditoria() {
        return descarte(propiedades.descarte("auditoria"), false);
    }

    /** Lo que SNS no pudo entregar. A esta sí le escribe SNS, así que necesita su permiso. */
    @Bean
    public Cola noEntregados() {
        return descarte(propiedades.getNoEntregados(), true);
    }

    // ---------------------------------------------------------------- colas de trabajo

    /** Avisos por comentarios, colaboradores y moderación. */
    @Bean
    public Cola colaProyectos() {
        return trabajo("proyectos");
    }

    /** Avisos de solicitudes de colaboración y sus respuestas. */
    @Bean
    public Cola colaColaboraciones() {
        return trabajo("colaboraciones");
    }

    /** Registro de todo evento. */
    @Bean
    public Cola colaAuditoria() {
        return trabajo("auditoria");
    }

    // ---------------------------------------------------------------- bindings

    /** {@code proyecto.*}: publicado, comentado, ocultado… y cualquier evento de proyecto futuro. */
    @Bean
    public Suscripcion proyectosEscuchaProyecto() {
        return suscripcion("proyectos");
    }

    /** {@code colaboracion.*}: solicitada, aceptada y rechazada. */
    @Bean
    public Suscripcion colaboracionesEscuchaColaboracion() {
        return suscripcion("colaboraciones");
    }

    /** Sin filtro: todo, incluidos los eventos que aún no existen. */
    @Bean
    public Suscripcion auditoriaEscuchaTodo() {
        return suscripcion("auditoria");
    }

    private Cola trabajo(String clave) {
        return Cola.deTrabajo(propiedades.cola(clave), parametros.getRetencionTrabajoSegundos(),
                parametros.getVisibilidadSegundos(), parametros.getEsperaSegundos(),
                propiedades.descarte(clave), parametros.getMaxRecepciones());
    }

    private Cola descarte(String nombre, boolean recibeDeSns) {
        return Cola.deDescarte(nombre, parametros.getRetencionDescartesSegundos(),
                parametros.getVisibilidadSegundos(), recibeDeSns);
    }

    private Suscripcion suscripcion(String clave) {
        return new Suscripcion(propiedades.getTopico().getNombre(), propiedades.cola(clave),
                propiedades.filtro(clave), propiedades.getNoEntregados());
    }
}
