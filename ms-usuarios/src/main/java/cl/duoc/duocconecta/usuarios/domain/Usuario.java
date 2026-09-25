package cl.duoc.duocconecta.usuarios.domain;

import cl.duoc.duocconecta.comun.seguridad.Rol;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Perfil de una persona de la comunidad Duoc UC.
 *
 * <p>El perfil se crea solo, la primera vez que la persona entra con su cuenta institucional
 * (auto-aprovisionamiento). Nunca se registra a mano.</p>
 *
 * <p>Ojo: esta entidad no se expone nunca en la API. Los controladores devuelven DTOs, porque
 * {@code telefono} y {@code redes} son datos de contacto privados que no deben salir en las
 * respuestas públicas.</p>
 */
@Entity
@Table(name = "usuario")
public class Usuario {

    /** Identificador interno de la plataforma. Se genera al crear el perfil. */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Identificador de la persona en Azure AD (claim {@code oid} del token).
     *
     * <p>Es la clave real por la que se busca al usuario: a diferencia del correo, nunca cambia.</p>
     */
    @Column(name = "oid_entra", nullable = false, unique = true, length = 64)
    private String oidEntra;

    /** Nombre para mostrar, tomado del token la primera vez y editable después. */
    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    /** Correo institucional. Determina el rol y es único en la plataforma. */
    @Column(name = "correo", nullable = false, unique = true, length = 180)
    private String correo;

    /** Rol derivado del dominio del correo o de los App Roles de Azure AD. */
    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 20)
    private Rol rol;

    /** Carrera a la que pertenece. La completa la persona; no viene en el token. */
    @Column(name = "carrera", length = 120)
    private String carrera;

    /** Sede en la que estudia o trabaja. */
    @Column(name = "sede", length = 120)
    private String sede;

    /** Presentación breve que se muestra en la vitrina. */
    @Column(name = "bio", length = 500)
    private String bio;

    /**
     * Indica si el perfil aparece en las búsquedas y listados públicos.
     *
     * <p>Ocultarse no borra el perfil: solo lo saca de los listados.</p>
     */
    @Column(name = "visible", nullable = false)
    private boolean visible = true;

    /**
     * Teléfono de contacto. <strong>Dato privado:</strong> nunca sale en respuestas públicas.
     * Compartirlo con terceros queda sujeto al consentimiento mutuo (EP2).
     */
    @Column(name = "telefono", length = 30)
    private String telefono;

    /**
     * Redes sociales de la persona. <strong>Dato privado:</strong> solo el propio usuario las ve,
     * a través de {@code GET /api/v1/usuarios/me/redes}.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "usuario_redes", joinColumns = @JoinColumn(name = "usuario_id"))
    @Column(name = "red", nullable = false, length = 255)
    private List<String> redes = new ArrayList<>();

    /** Momento en que se auto-aprovisionó el perfil. */
    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    /** Momento de la última modificación del perfil. */
    @Column(name = "actualizado_en", nullable = false)
    private OffsetDateTime actualizadoEn;

    protected Usuario() {
        // Constructor sin argumentos que exige JPA.
    }

    /**
     * Crea un perfil nuevo a partir de los datos que vienen en el token.
     *
     * @param oidEntra identificador de la persona en Azure AD
     * @param nombre   nombre para mostrar
     * @param correo   correo institucional en minúsculas
     * @param rol      rol ya resuelto
     */
    public Usuario(String oidEntra, String nombre, String correo, Rol rol) {
        this.id = UUID.randomUUID();
        this.oidEntra = oidEntra;
        this.nombre = nombre;
        this.correo = correo;
        this.rol = rol;
        this.visible = true;
    }

    /** Deja las marcas de tiempo al insertar. */
    @PrePersist
    void alCrear() {
        OffsetDateTime ahora = OffsetDateTime.now();
        this.creadoEn = ahora;
        this.actualizadoEn = ahora;
    }

    /** Actualiza la marca de tiempo en cada modificación. */
    @PreUpdate
    void alActualizar() {
        this.actualizadoEn = OffsetDateTime.now();
    }

    /**
     * Aplica los cambios del perfil que la persona puede editar.
     *
     * <p>Se dejan afuera a propósito el nombre, el correo, el rol y el {@code oidEntra}: vienen del
     * token y no se cambian desde la API. El nombre en particular se resincroniza en cada ingreso
     * con {@link #sincronizarDesdeToken}, así que si se pudiera editar aquí, el valor escrito a mano
     * se perdería la próxima vez que la persona entrara.</p>
     */
    public void actualizarPerfil(String carrera, String sede, String bio,
                                 String telefono, List<String> redes) {
        this.carrera = normalizar(carrera);
        this.sede = normalizar(sede);
        this.bio = normalizar(bio);
        this.telefono = normalizar(telefono);

        // Se reemplaza el contenido de la lista en vez de la lista misma, para que Hibernate
        // pueda seguir el rastro de la colección y sincronizar la tabla usuario_redes.
        this.redes.clear();
        if (redes != null) {
            redes.stream()
                    .filter(red -> red != null && !red.isBlank())
                    .map(String::trim)
                    .distinct()
                    .forEach(this.redes::add);
        }
    }

    /**
     * Cambia la visibilidad del perfil al estado contrario y devuelve el nuevo valor.
     */
    public boolean alternarVisibilidad() {
        this.visible = !this.visible;
        return this.visible;
    }

    /**
     * Actualiza los datos que vienen del token, por si cambiaron en Azure AD desde el último acceso.
     */
    public void sincronizarDesdeToken(String nombre, String correo, Rol rol) {
        if (nombre != null && !nombre.isBlank()) {
            this.nombre = nombre;
        }
        if (correo != null && !correo.isBlank()) {
            this.correo = correo;
        }
        if (rol != null) {
            this.rol = rol;
        }
    }

    /** Deja el texto sin espacios sobrantes y convierte el vacío en nulo. */
    private static String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    public UUID getId() {
        return id;
    }

    public String getOidEntra() {
        return oidEntra;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCorreo() {
        return correo;
    }

    public Rol getRol() {
        return rol;
    }

    public String getCarrera() {
        return carrera;
    }

    public String getSede() {
        return sede;
    }

    public String getBio() {
        return bio;
    }

    public boolean isVisible() {
        return visible;
    }

    public String getTelefono() {
        return telefono;
    }

    public List<String> getRedes() {
        return redes;
    }

    public OffsetDateTime getCreadoEn() {
        return creadoEn;
    }

    public OffsetDateTime getActualizadoEn() {
        return actualizadoEn;
    }
}
