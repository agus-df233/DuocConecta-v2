package cl.duoc.duocconecta.comun.eventos;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de mensajería, bajo {@code duocconecta.mensajeria} en el YAML.
 *
 * <p>Es el único lugar donde viven los nombres de tópicos, colas y DLQ: ningún servicio ni
 * consumidor escribe un nombre a mano. Cambiar uno es editar el YAML.</p>
 */
@ConfigurationProperties(prefix = "duocconecta.mensajeria")
public class PropiedadesMensajeria {

    /** En falso, los eventos solo se registran en el log. Los tests lo usan así. */
    private boolean habilitada = true;

    private String region = "us-east-1";

    /** Servicio que publica; va como atributo {@code origen} en cada evento. */
    private String origen;

    /** Si es verdadero, el servicio crea al arrancar lo que falte de la topología. */
    private boolean declararTopologia = false;

    private Topico topico = new Topico();

    /** Colas de trabajo: clave lógica (proyectos, colaboraciones, auditoria) → nombre real. */
    private Map<String, String> colas = new LinkedHashMap<>();

    /** DLQ de cada cola de trabajo, con la misma clave lógica. */
    private Map<String, String> descartes = new LinkedHashMap<>();

    /**
     * Prefijo de {@code tipo} que recibe cada cola: el binding. Una cola sin entrada aquí recibe
     * todo, como el {@code #} de RabbitMQ.
     */
    private Map<String, String> filtros = new LinkedHashMap<>();

    /** DLQ de la suscripción SNS: lo que SNS no logró entregar a ninguna cola. */
    private String noEntregados;

    private Parametros parametros = new Parametros();

    /** El nombre real de una cola de trabajo; falla con un mensaje claro si falta en el YAML. */
    public String cola(String clave) {
        return obligatorio(colas, clave, "colas");
    }

    /** El nombre real de la DLQ de una cola de trabajo. */
    public String descarte(String clave) {
        return obligatorio(descartes, clave, "descartes");
    }

    private static String obligatorio(Map<String, String> mapa, String clave, String seccion) {
        String nombre = mapa.get(clave);
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalStateException(
                    "Falta duocconecta.mensajeria." + seccion + "." + clave + " en application.yml");
        }
        return nombre;
    }

    /** El tópico SNS que hace de exchange topic. */
    public static class Topico {

        private String nombre;

        /** ARN del tópico. Vacío = no se publica (entorno local sin AWS). */
        private String arn;

        public String getNombre() {
            return nombre;
        }

        public void setNombre(String nombre) {
            this.nombre = nombre;
        }

        public String getArn() {
            return arn;
        }

        public void setArn(String arn) {
            this.arn = arn;
        }
    }

    /** Valores de las colas. Los mismos que se crearon a mano en la consola. */
    public static class Parametros {

        /** 4 días: la retención por defecto de SQS. */
        private int retencionTrabajoSegundos = 345_600;

        /** 14 días, el máximo: en SQS lo vencido se borra, así que la DLQ retiene lo más posible. */
        private int retencionDescartesSegundos = 1_209_600;

        private int visibilidadSegundos = 30;

        /** Long polling: el consumidor espera mensajes hasta 20 s por llamada. */
        private int esperaSegundos = 20;

        /** A la tercera entrega sin confirmar, SQS mueve el mensaje a la DLQ. */
        private int maxRecepciones = 3;

        public int getRetencionTrabajoSegundos() {
            return retencionTrabajoSegundos;
        }

        public void setRetencionTrabajoSegundos(int retencionTrabajoSegundos) {
            this.retencionTrabajoSegundos = retencionTrabajoSegundos;
        }

        public int getRetencionDescartesSegundos() {
            return retencionDescartesSegundos;
        }

        public void setRetencionDescartesSegundos(int retencionDescartesSegundos) {
            this.retencionDescartesSegundos = retencionDescartesSegundos;
        }

        public int getVisibilidadSegundos() {
            return visibilidadSegundos;
        }

        public void setVisibilidadSegundos(int visibilidadSegundos) {
            this.visibilidadSegundos = visibilidadSegundos;
        }

        public int getEsperaSegundos() {
            return esperaSegundos;
        }

        public void setEsperaSegundos(int esperaSegundos) {
            this.esperaSegundos = esperaSegundos;
        }

        public int getMaxRecepciones() {
            return maxRecepciones;
        }

        public void setMaxRecepciones(int maxRecepciones) {
            this.maxRecepciones = maxRecepciones;
        }
    }

    public boolean isHabilitada() {
        return habilitada;
    }

    public void setHabilitada(boolean habilitada) {
        this.habilitada = habilitada;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getOrigen() {
        return origen;
    }

    public void setOrigen(String origen) {
        this.origen = origen;
    }

    public boolean isDeclararTopologia() {
        return declararTopologia;
    }

    public void setDeclararTopologia(boolean declararTopologia) {
        this.declararTopologia = declararTopologia;
    }

    public Topico getTopico() {
        return topico;
    }

    public void setTopico(Topico topico) {
        this.topico = topico;
    }

    public Map<String, String> getColas() {
        return colas;
    }

    public void setColas(Map<String, String> colas) {
        this.colas = colas;
    }

    public Map<String, String> getDescartes() {
        return descartes;
    }

    public void setDescartes(Map<String, String> descartes) {
        this.descartes = descartes;
    }

    /** El prefijo que filtra una cola, o nulo si recibe todo. */
    public String filtro(String clave) {
        String prefijo = filtros.get(clave);
        return prefijo == null || prefijo.isBlank() ? null : prefijo;
    }

    public Map<String, String> getFiltros() {
        return filtros;
    }

    public void setFiltros(Map<String, String> filtros) {
        this.filtros = filtros;
    }

    public String getNoEntregados() {
        return noEntregados;
    }

    public void setNoEntregados(String noEntregados) {
        this.noEntregados = noEntregados;
    }

    public Parametros getParametros() {
        return parametros;
    }

    public void setParametros(Parametros parametros) {
        this.parametros = parametros;
    }
}
