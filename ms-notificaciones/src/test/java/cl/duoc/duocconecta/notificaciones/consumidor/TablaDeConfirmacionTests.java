package cl.duoc.duocconecta.notificaciones.consumidor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import cl.duoc.duocconecta.notificaciones.consumidor.auditoria.ConsumidorAuditoria;
import cl.duoc.duocconecta.notificaciones.consumidor.colaboraciones.ConsumidorColaboraciones;
import cl.duoc.duocconecta.notificaciones.consumidor.proyectos.ConsumidorProyectos;
import cl.duoc.duocconecta.notificaciones.repository.RepositorioAvisos;
import cl.duoc.duocconecta.notificaciones.repository.RepositorioEventosAuditados;
import io.awspring.cloud.sqs.listener.Visibility;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Cada fila de la tabla de confirmación de {@link ProcesadorDeMensajes}, con el
 * {@code Acknowledgement} y la {@code Visibility} simulados: no hace falta SQS.
 */
@SpringBootTest(properties = "duocconecta.mensajeria.permitir-fallos-simulados=true")
@ActiveProfiles("test")
class TablaDeConfirmacionTests {

    @Autowired
    private ConsumidorColaboraciones colaboraciones;

    @Autowired
    private ConsumidorProyectos proyectos;

    @Autowired
    private ConsumidorAuditoria auditoria;

    @Autowired
    private RepositorioAvisos avisos;

    @Autowired
    private RepositorioEventosAuditados auditados;

    @MockitoBean
    private EnvioADescarte envioADescarte;

    private static String evento(String id, String tipo, String actor, String destinatario, String detalle) {
        return """
                {"idEvento":"%s","tipo":"%s","version":1,"origen":"ms-contacto","ocurridoEn":"2026-10-05T14:03:11Z",
                 "actorId":"%s","destinatarioId":%s,"recursoId":"r-1","proyectoId":"p-1","detalle":%s}"""
                .formatted(id, tipo, actor, destinatario == null ? "null" : "\"" + destinatario + "\"",
                        detalle == null ? "null" : "\"" + detalle + "\"");
    }

    @Test
    @DisplayName("Procesado: crea el aviso y confirma")
    void procesadoConfirma() {
        String id = UUID.randomUUID().toString();
        Acknowledgement ack = mock(Acknowledgement.class);

        colaboraciones.recibir(evento(id, "colaboracion.aceptada", "oid-a", "oid-b", null), "demo-1", "1",
                ack, mock(Visibility.class));

        verify(ack).acknowledge();
        assertThat(avisos.countByIdEvento(id)).isEqualTo(1);
    }

    @Test
    @DisplayName("Retirada: avisa a quien recibió la solicitud y confirma")
    void retiradaAvisaAQuienLaRecibio() {
        String id = UUID.randomUUID().toString();
        Acknowledgement ack = mock(Acknowledgement.class);

        colaboraciones.recibir(evento(id, "colaboracion.retirada", "oid-a", "oid-b", null), null, "1",
                ack, mock(Visibility.class));

        verify(ack).acknowledge();
        assertThat(avisos.countByIdEvento(id)).isEqualTo(1);
    }

    @Test
    @DisplayName("Duplicado: el mismo idEvento dos veces da un solo aviso, y ambos se confirman")
    void duplicadoSeConfirmaSinRepetir() {
        String id = UUID.randomUUID().toString();
        String cuerpo = evento(id, "colaboracion.solicitada", "oid-a", "oid-b", null);
        Acknowledgement primero = mock(Acknowledgement.class);
        Acknowledgement segundo = mock(Acknowledgement.class);

        colaboraciones.recibir(cuerpo, null, "1", primero, mock(Visibility.class));
        colaboraciones.recibir(cuerpo, null, "2", segundo, mock(Visibility.class));

        verify(primero).acknowledge();
        verify(segundo).acknowledge();
        assertThat(avisos.countByIdEvento(id)).isEqualTo(1);
    }

    @Test
    @DisplayName("No recuperable (JSON inválido): a la DLQ de inmediato y se confirma")
    void jsonInvalidoVaALaDlq() throws Exception {
        Acknowledgement ack = mock(Acknowledgement.class);

        colaboraciones.recibir("esto no es JSON", "demo-2", "1", ack, mock(Visibility.class));

        verify(envioADescarte).enviar(eq("colaboraciones"), eq("esto no es JSON"), contains("JSON inválido"), eq("demo-2"));
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("No recuperable (versión no soportada): a la DLQ")
    void versionNoSoportadaVaALaDlq() throws Exception {
        String cuerpo = evento(UUID.randomUUID().toString(), "colaboracion.aceptada", "oid-a", "oid-b", null)
                .replace("\"version\":1", "\"version\":99");
        Acknowledgement ack = mock(Acknowledgement.class);

        colaboraciones.recibir(cuerpo, null, "1", ack, mock(Visibility.class));

        verify(envioADescarte).enviar(eq("colaboraciones"), anyString(), contains("Versión"), eq(null));
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("No recuperable (colaboración sin destinatario): a la DLQ, sin aviso")
    void sinDestinatarioVaALaDlq() throws Exception {
        String id = UUID.randomUUID().toString();
        Acknowledgement ack = mock(Acknowledgement.class);

        colaboraciones.recibir(evento(id, "colaboracion.aceptada", "oid-a", null, null), null, "1",
                ack, mock(Visibility.class));

        verify(envioADescarte).enviar(eq("colaboraciones"), anyString(), contains("sin destinatario"), eq(null));
        verify(ack).acknowledge();
        assertThat(avisos.countByIdEvento(id)).isZero();
    }

    @Test
    @DisplayName("Recuperable: sin ack y con más visibilidad; al reintentar se procesa (el registro se revirtió)")
    void recuperableReintentaConBackoff() {
        String id = UUID.randomUUID().toString();
        Acknowledgement ack = mock(Acknowledgement.class);
        Visibility visibilidad = mock(Visibility.class);

        proyectos.recibir(evento(id, "proyecto.comentado", "oid-a", "oid-b", ProcesadorDeMensajes.DETALLE_FALLO_SIMULADO),
                null, "1", ack, visibilidad);

        verify(ack, never()).acknowledge();
        verify(visibilidad).changeTo(10);
        verifyNoInteractions(envioADescarte);

        // Otro mensaje sano con el mismo idEvento se procesa: el registro de la vez fallida no quedó.
        Acknowledgement segundo = mock(Acknowledgement.class);
        proyectos.recibir(evento(id, "proyecto.comentado", "oid-a", "oid-b", null), null, "2", segundo,
                mock(Visibility.class));
        verify(segundo).acknowledge();
        assertThat(avisos.countByIdEvento(id)).isEqualTo(1);
    }

    @Test
    @DisplayName("Recuperable en el último intento: sin ack ni cambio de visibilidad; la redrive lo lleva a la DLQ")
    void ultimoIntentoQuedaParaLaRedrive() {
        Acknowledgement ack = mock(Acknowledgement.class);
        Visibility visibilidad = mock(Visibility.class);

        proyectos.recibir(evento(UUID.randomUUID().toString(), "proyecto.comentado", "oid-a", "oid-b",
                ProcesadorDeMensajes.DETALLE_FALLO_SIMULADO), null, "3", ack, visibilidad);

        verify(ack, never()).acknowledge();
        verify(visibilidad, never()).changeTo(anyInt());
        verifyNoInteractions(envioADescarte);
    }

    @Test
    @DisplayName("Un tipo nuevo de la categoría se confirma sin acción: no es un error")
    void tipoNuevoSeConfirma() {
        String id = UUID.randomUUID().toString();
        Acknowledgement ack = mock(Acknowledgement.class);

        proyectos.recibir(evento(id, "proyecto.destacado", "oid-a", "oid-b", null), null, "1", ack, mock(Visibility.class));

        verify(ack).acknowledge();
        verifyNoInteractions(envioADescarte);
        assertThat(avisos.countByIdEvento(id)).isZero();
    }

    @Test
    @DisplayName("Nadie se avisa a sí mismo: la dueña comentando su proyecto no genera aviso")
    void sinAutoaviso() {
        String id = UUID.randomUUID().toString();
        Acknowledgement ack = mock(Acknowledgement.class);

        proyectos.recibir(evento(id, "proyecto.comentado", "oid-a", "oid-a", null), null, "1", ack, mock(Visibility.class));

        verify(ack).acknowledge();
        assertThat(avisos.countByIdEvento(id)).isZero();
    }

    @Test
    @DisplayName("Auditoría registra todo evento, con el X-Request-Id que viajó en el mensaje")
    void auditoriaRegistraConCorrelacion() {
        String id = UUID.randomUUID().toString();
        Acknowledgement ack = mock(Acknowledgement.class);

        auditoria.recibir(evento(id, "proyecto.destacado", "oid-a", null, null), "demo-ep2", "1", ack, mock(Visibility.class));

        verify(ack).acknowledge();
        assertThat(auditados.findById(id)).get()
                .satisfies(e -> {
                    assertThat(e.getTipo()).isEqualTo("proyecto.destacado");
                    assertThat(e.getIdPeticion()).isEqualTo("demo-ep2");
                    assertThat(e.getOrigen()).isEqualTo("ms-contacto");
                });
    }
}
