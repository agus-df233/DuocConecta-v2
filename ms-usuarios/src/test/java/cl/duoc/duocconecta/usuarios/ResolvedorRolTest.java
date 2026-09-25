package cl.duoc.duocconecta.usuarios;

import static org.assertj.core.api.Assertions.assertThat;

import cl.duoc.duocconecta.comun.seguridad.PropiedadesSeguridad;
import cl.duoc.duocconecta.comun.seguridad.ResolvedorRol;
import cl.duoc.duocconecta.comun.seguridad.Rol;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas del mapeo de dominio de correo a rol.
 *
 * <p>Es la regla más delicada del servicio: si se equivoca, alguien queda con el rol de otro
 * o una cuenta externa entra a la plataforma.</p>
 */
class ResolvedorRolTest {

    private ResolvedorRol resolvedor;

    @BeforeEach
    void prepararResolvedor() {
        Map<String, Rol> dominios = new LinkedHashMap<>();
        dominios.put("duocuc.cl", Rol.ESTUDIANTE);
        dominios.put("profesor.duoc.cl", Rol.PROFESOR);
        dominios.put("duoc.cl", Rol.ACADEMICO);

        PropiedadesSeguridad propiedades = new PropiedadesSeguridad();
        propiedades.setDominios(dominios);

        this.resolvedor = new ResolvedorRol(propiedades);
    }

    @Test
    @DisplayName("Un correo @duocuc.cl es ESTUDIANTE")
    void correoDeAlumnoEsEstudiante() {
        assertThat(resolvedor.resolverPorCorreo("juana.perez@duocuc.cl")).contains(Rol.ESTUDIANTE);
    }

    @Test
    @DisplayName("Un correo @duoc.cl es ACADEMICO")
    void correoInstitucionalEsAcademico() {
        assertThat(resolvedor.resolverPorCorreo("paula.contreras@duoc.cl")).contains(Rol.ACADEMICO);
    }

    /**
     * Este es el caso que justifica no usar endsWith: el dominio de profesor termina en
     * "duoc.cl", así que un match por sufijo lo clasificaría como ACADEMICO.
     */
    @Test
    @DisplayName("Un correo @profesor.duoc.cl es PROFESOR, no ACADEMICO")
    void correoDeProfesorNoSeConfundeConAcademico() {
        assertThat(resolvedor.resolverPorCorreo("ignacio.herrera@profesor.duoc.cl"))
                .contains(Rol.PROFESOR);
    }

    @Test
    @DisplayName("Un dominio externo no tiene rol")
    void dominioExternoNoTieneRol() {
        assertThat(resolvedor.resolverPorCorreo("alguien@gmail.com")).isEmpty();
    }

    @Test
    @DisplayName("Las mayúsculas y los espacios no cambian el resultado")
    void elCorreoSeNormaliza() {
        assertThat(resolvedor.resolverPorCorreo("  Juana.Perez@DuocUC.CL  ")).contains(Rol.ESTUDIANTE);
    }

    @Test
    @DisplayName("Un texto que no es un correo no tiene rol")
    void textoSinArrobaNoTieneRol() {
        assertThat(resolvedor.resolverPorCorreo("duocuc.cl")).isEmpty();
        assertThat(resolvedor.resolverPorCorreo("sin-dominio@")).isEmpty();
        assertThat(resolvedor.resolverPorCorreo("@sin-usuario.cl")).isEmpty();
        assertThat(resolvedor.resolverPorCorreo(null)).isEmpty();
    }
}
