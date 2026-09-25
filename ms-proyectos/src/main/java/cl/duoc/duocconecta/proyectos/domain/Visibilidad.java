package cl.duoc.duocconecta.proyectos.domain;

public enum Visibilidad {
    /** Visible para todo el alumnado y profesorado autenticado. */
    PUBLICO,
    /** Visible solo para el propietario y los colaboradores agregados explícitamente. */
    COMPARTIDO,
    /** Visible solo para el propietario. */
    PRIVADO
}
