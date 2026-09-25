-- Esquema inicial de ms-usuarios: perfiles de la comunidad Duoc UC.
-- Las tablas se crean dentro del schema `usuarios`, que ya existe (lo crea docker/postgres/init.sql).

-- Perfil de una persona. Se crea solo, la primera vez que entra con su cuenta institucional.
CREATE TABLE usuario (
    id             UUID         PRIMARY KEY,
    -- Identificador de la persona en Azure AD (claim oid). A diferencia del correo, nunca cambia.
    oid_entra      VARCHAR(64)  NOT NULL UNIQUE,
    nombre         VARCHAR(150) NOT NULL,
    correo         VARCHAR(180) NOT NULL UNIQUE,
    -- ESTUDIANTE | PROFESOR | ACADEMICO. Se guarda como texto para que el dump sea legible.
    rol            VARCHAR(20)  NOT NULL,
    carrera        VARCHAR(120),
    sede           VARCHAR(120),
    bio            VARCHAR(500),
    -- Si es false, el perfil no aparece en los listados públicos. No borra nada.
    visible        BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Dato de contacto privado: nunca sale en respuestas públicas.
    telefono       VARCHAR(30),
    creado_en      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    actualizado_en TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- Solo se aceptan los roles que conoce la plataforma, aunque la aplicación falle.
    CONSTRAINT ck_usuario_rol CHECK (rol IN ('ESTUDIANTE', 'PROFESOR', 'ACADEMICO'))
);

COMMENT ON TABLE  usuario           IS 'Perfil de una persona de la comunidad Duoc UC';
COMMENT ON COLUMN usuario.oid_entra IS 'Claim oid del token de Azure AD; identificador inmutable';
COMMENT ON COLUMN usuario.visible   IS 'Si es false, el perfil no aparece en las búsquedas';
COMMENT ON COLUMN usuario.telefono  IS 'Dato de contacto privado; no se expone en respuestas públicas';

-- Redes sociales del usuario. Es una tabla aparte porque cada persona puede tener varias.
-- También son datos privados: solo salen por GET /api/v1/usuarios/me/redes.
CREATE TABLE usuario_redes (
    usuario_id UUID         NOT NULL,
    red        VARCHAR(255) NOT NULL,

    -- Si se borra el perfil, sus redes se van con él.
    CONSTRAINT fk_usuario_redes_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE
);

COMMENT ON TABLE usuario_redes IS 'Enlaces a redes sociales de cada usuario; datos privados';

-- Acelera la carga de las redes de un perfil.
CREATE INDEX idx_usuario_redes_usuario ON usuario_redes (usuario_id);

-- Índice parcial para el listado de la vitrina: solo interesan los perfiles visibles,
-- así que el índice ignora al resto y queda más chico.
CREATE INDEX idx_usuario_visible_carrera_sede
    ON usuario (carrera, sede)
    WHERE visible = TRUE;

-- Búsqueda por correo sin distinguir mayúsculas.
CREATE INDEX idx_usuario_correo_lower ON usuario (LOWER(correo));
