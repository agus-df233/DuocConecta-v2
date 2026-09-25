package cl.duoc.duocconecta.bff.dto;

/** Perfil ajeno, tal como lo devuelve ms-usuarios. Se declaran todos los campos por el mapeo. */
public record PerfilPublicoRespuesta(
        String id,
        String nombre,
        String rol,
        String carrera,
        String sede,
        String bio) {
}
