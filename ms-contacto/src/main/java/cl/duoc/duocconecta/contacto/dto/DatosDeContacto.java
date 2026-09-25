package cl.duoc.duocconecta.contacto.dto;

/**
 * Los datos que una persona decide compartir.
 *
 * <p>Un campo vacío significa que decidió no compartirlo, que es distinto de no tenerlo.</p>
 */
public record DatosDeContacto(String correo, String telefono, String redes) {

    public static final DatosDeContacto NINGUNO = new DatosDeContacto(null, null, null);

    /** Vacío se guarda como nulo: así "no lo compartió" es un solo caso y no dos. */
    public DatosDeContacto {
        correo = normalizar(correo);
        telefono = normalizar(telefono);
        redes = normalizar(redes);
    }

    private static String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }
}
