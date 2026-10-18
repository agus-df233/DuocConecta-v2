package cl.duoc.duocconecta.comun.eventos.topologia;

import cl.duoc.duocconecta.comun.eventos.AtributosMensaje;

/**
 * Une una cola a un tópico: el equivalente del binding de RabbitMQ.
 *
 * @param filtroPrefijo    por ejemplo {@code proyecto.}; nulo = recibe todo (el {@code #} de RabbitMQ)
 * @param colaNoEntregados DLQ de la suscripción, para lo que SNS no logra entregar
 */
public record Suscripcion(String topico, String cola, String filtroPrefijo, String colaNoEntregados) {

    /** Política de filtro sobre el atributo {@code tipo}, o nula si no filtra. */
    public String politicaDeFiltro() {
        return filtroPrefijo == null ? null
                : "{\"" + AtributosMensaje.TIPO + "\":[{\"prefix\":\"" + filtroPrefijo + "\"}]}";
    }
}
