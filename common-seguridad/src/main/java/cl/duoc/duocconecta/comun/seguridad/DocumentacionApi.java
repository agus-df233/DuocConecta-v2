package cl.duoc.duocconecta.comun.seguridad;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Arma la definición de OpenAPI que comparten los cuatro servicios.
 *
 * <p>Era la misma clase copiada cuatro veces: solo cambiaban el título y la descripción.</p>
 */
public final class DocumentacionApi {

    private DocumentacionApi() {
    }

    /** Nombre del esquema de seguridad. Debe coincidir con el @SecurityRequirement del controlador. */
    public static final String ESQUEMA_BEARER = "bearer-jwt";

    /** Lo que vale para todos: de dónde sale el token y cómo se deriva el rol. */
    private static final String COMUN = """

            Todos los endpoints requieren un JWT: el access token de Microsoft Entra ID (login con Microsoft) o el ID token de Amazon Cognito (login con Google).
            El rol se deriva del dominio del correo institucional: @duocuc.cl es ESTUDIANTE,
            @profesor.duoc.cl es PROFESOR y @duoc.cl es ACADEMICO. Cualquier otro dominio recibe 403.

            Para probar desde aquí: haz clic en Authorize y pega el access token, sin la palabra Bearer.""";

    /**
     * @param titulo      nombre del servicio, por ejemplo "ms-usuarios"
     * @param descripcion qué hace este servicio en particular
     */
    public static OpenAPI de(String titulo, String descripcion) {
        return new OpenAPI()
                .info(new Info()
                        .title("DuocConecta · " + titulo)
                        .version("v1")
                        .description(descripcion + COMUN)
                        .contact(new Contact().name("Equipo DuocConecta · DSY1107")))
                .components(new Components().addSecuritySchemes(ESQUEMA_BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token de Entra ID o ID token de Cognito (login con Google).")));
    }
}
