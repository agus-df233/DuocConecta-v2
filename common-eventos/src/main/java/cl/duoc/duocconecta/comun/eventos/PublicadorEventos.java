package cl.duoc.duocconecta.comun.eventos;

/** Publica un evento de dominio. Los servicios de negocio nunca lo llaman directo: ver {@link PublicacionTrasConfirmar}. */
public interface PublicadorEventos {

    void publicar(EventoDuocConecta evento);
}
