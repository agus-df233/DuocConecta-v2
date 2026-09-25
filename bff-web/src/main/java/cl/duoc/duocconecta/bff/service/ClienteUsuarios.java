package cl.duoc.duocconecta.bff.service;

import cl.duoc.duocconecta.bff.dto.Autor;
import cl.duoc.duocconecta.bff.dto.PerfilPublicoRespuesta;
import cl.duoc.duocconecta.bff.dto.PerfilUsuario;
import cl.duoc.duocconecta.bff.dto.Redes;
import cl.duoc.duocconecta.comun.seguridad.UsuarioActual;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Cliente hacia ms-usuarios.
 *
 * <p>Propaga el token original en vez de una credencial propia: el microservicio resuelve los
 * permisos con la identidad real, y el BFF nunca puede pedir más que el usuario.</p>
 */
@Service
public class ClienteUsuarios {

    private static final String RUTA_PERFIL_PROPIO = "/api/v1/usuarios/me";
    private static final String RUTA_REDES_PROPIAS = "/api/v1/usuarios/me/redes";
    private static final String RUTA_PERFIL_POR_OID = "/api/v1/usuarios/por-oid/{oid}";

    private final RestClient cliente;
    private final UsuarioActual usuarioActual;

    public ClienteUsuarios(RestClient clienteMsUsuarios, UsuarioActual usuarioActual) {
        this.cliente = clienteMsUsuarios;
        this.usuarioActual = usuarioActual;
    }

    /** Si es el primer ingreso, el microservicio crea el perfil en el momento. */
    public PerfilUsuario obtenerPerfilPropio() {
        return cliente.get()
                .uri(RUTA_PERFIL_PROPIO)
                .header(HttpHeaders.AUTHORIZATION, usuarioActual.cabeceraAuthorization())
                .retrieve()
                .body(PerfilUsuario.class);
    }

    public List<String> obtenerRedesPropias() {
        Redes respuesta = cliente.get()
                .uri(RUTA_REDES_PROPIAS)
                .header(HttpHeaders.AUTHORIZATION, usuarioActual.cabeceraAuthorization())
                .retrieve()
                .body(Redes.class);

        return (respuesta == null || respuesta.redes() == null) ? List.of() : respuesta.redes();
    }

    /**
     * Devuelve {@link Autor#DESCONOCIDO} si el perfil no existe o está oculto: que alguien se
     * haya ocultado no es motivo para que falle la pantalla entera.
     */
    public Autor buscarAutorPorOid(String oid) {
        try {
            PerfilPublicoRespuesta perfil = cliente.get()
                    .uri(RUTA_PERFIL_POR_OID, oid)
                    .header(HttpHeaders.AUTHORIZATION, usuarioActual.cabeceraAuthorization())
                    .retrieve()
                    .body(PerfilPublicoRespuesta.class);
            return perfil == null
                    ? Autor.DESCONOCIDO
                    : new Autor(perfil.nombre(), perfil.carrera(), perfil.sede());
        } catch (HttpClientErrorException.NotFound ignorada) {
            return Autor.DESCONOCIDO;
        }
    }
}
