-- Tres cosas que la vitrina necesita y el esquema inicial no tenía:
-- un resumen corto para la tarjeta, las herramientas del proyecto, y los comentarios.

-- La tarjeta muestra una línea; el detalle completo sigue en `descripcion`.
ALTER TABLE proyecto ADD COLUMN resumen VARCHAR(200);

-- Herramientas y tecnologías. Es texto libre a propósito: un proyecto de Diseño lista Figma
-- y uno de Administración lista Excel, así que una lista cerrada dejaría carreras fuera.
CREATE TABLE proyecto_herramientas (
    proyecto_id UUID         NOT NULL,
    herramienta VARCHAR(60)  NOT NULL,

    CONSTRAINT fk_proyecto_herramientas
        FOREIGN KEY (proyecto_id) REFERENCES proyecto (id) ON DELETE CASCADE
);

CREATE INDEX idx_proyecto_herramientas ON proyecto_herramientas (proyecto_id);

-- Comentarios: retroalimentación pública, sin necesidad de pedir contacto.
CREATE TABLE comentario (
    id          UUID          PRIMARY KEY,
    proyecto_id UUID          NOT NULL,
    -- Claim oid del token de quien comentó, igual que en el resto de la plataforma.
    autor_id    VARCHAR(100)  NOT NULL,
    texto       VARCHAR(1000) NOT NULL,
    fecha       TIMESTAMPTZ   NOT NULL,

    CONSTRAINT fk_comentario_proyecto
        FOREIGN KEY (proyecto_id) REFERENCES proyecto (id) ON DELETE CASCADE
);

-- El hilo se lee siempre completo y en orden, por proyecto.
CREATE INDEX idx_comentario_proyecto ON comentario (proyecto_id, fecha);

COMMENT ON TABLE  comentario         IS 'Comentarios públicos sobre un proyecto de la vitrina';
COMMENT ON COLUMN proyecto.resumen   IS 'Una línea; es lo que se lee en la tarjeta';
