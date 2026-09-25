-- Esquema inicial de ms-proyectos: la vitrina de proyectos de la comunidad.
-- Las tablas se crean dentro del schema `proyectos`, que es propiedad de este servicio.

-- Un proyecto publicado por alguien de la comunidad.
CREATE TABLE proyecto (
    id              UUID          PRIMARY KEY,
    nombre          VARCHAR(150)  NOT NULL,
    descripcion     VARCHAR(2000),
    url_repositorio VARCHAR(255),
    -- Identificador de la persona en Azure AD (claim oid). Es el mismo que usa ms-usuarios,
    -- y por eso el frontend puede cruzar un proyecto con el perfil de su autor.
    propietario_id  VARCHAR(100)  NOT NULL,
    sede            VARCHAR(80),
    -- EN_DESARROLLO | TERMINADO | BUSCANDO_EQUIPO
    estado          VARCHAR(20)   NOT NULL,
    -- PUBLICO | COMPARTIDO | PRIVADO. Poner PRIVADO es la forma de ocultar
    -- un proyecto de la vitrina sin borrarlo.
    visibilidad     VARCHAR(20)   NOT NULL,
    fecha_creacion  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_proyecto_estado
        CHECK (estado IN ('EN_DESARROLLO', 'TERMINADO', 'BUSCANDO_EQUIPO')),
    CONSTRAINT ck_proyecto_visibilidad
        CHECK (visibilidad IN ('PUBLICO', 'COMPARTIDO', 'PRIVADO'))
);

COMMENT ON TABLE  proyecto              IS 'Proyecto publicado en la vitrina de DuocConecta';
COMMENT ON COLUMN proyecto.propietario_id IS 'Claim oid del token; identifica a quien lo publicó';
COMMENT ON COLUMN proyecto.visibilidad  IS 'PRIVADO lo saca de la vitrina sin borrarlo';

-- Personas que pueden ver un proyecto de visibilidad COMPARTIDO.
CREATE TABLE proyecto_colaboradores (
    proyecto_id UUID         NOT NULL,
    usuario_id  VARCHAR(100) NOT NULL,

    CONSTRAINT fk_proyecto_colaboradores
        FOREIGN KEY (proyecto_id) REFERENCES proyecto (id) ON DELETE CASCADE
);

-- Documentos o capturas adjuntas, aparte del enlace al repositorio.
CREATE TABLE proyecto_archivos (
    proyecto_id UUID         NOT NULL,
    url_archivo VARCHAR(255) NOT NULL,

    CONSTRAINT fk_proyecto_archivos
        FOREIGN KEY (proyecto_id) REFERENCES proyecto (id) ON DELETE CASCADE
);

CREATE INDEX idx_proyecto_colaboradores ON proyecto_colaboradores (proyecto_id);
CREATE INDEX idx_proyecto_archivos      ON proyecto_archivos (proyecto_id);

-- El listado de la vitrina filtra por visibilidad; el índice parcial ignora el resto.
CREATE INDEX idx_proyecto_publicos ON proyecto (sede, estado) WHERE visibilidad = 'PUBLICO';
-- "Mis proyectos" busca siempre por propietario.
CREATE INDEX idx_proyecto_propietario ON proyecto (propietario_id);
