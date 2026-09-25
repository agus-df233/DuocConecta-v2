package cl.duoc.duocconecta.bff.dto;

/**
 * Lo que se comparte al aceptar una solicitud. Lo arma el BFF con el perfil de quien acepta.
 *
 * <p>Un campo vacío es "no lo compartió" o "no lo tiene": en ambos casos no se guarda nada.</p>
 */
public record DatosDeContacto(String correo, String telefono, String redes) {

    /** Lo que se envía al rechazar: nada. */
    public static final DatosDeContacto NINGUNO = new DatosDeContacto(null, null, null);
}
