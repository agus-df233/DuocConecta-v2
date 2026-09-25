-- Solicitudes de colaboración: el mecanismo de consentimiento de DuocConecta.
-- Nadie ve los datos de contacto de otra persona hasta que esa persona acepta.
CREATE TABLE solicitudes_contacto (
    id                UUID         PRIMARY KEY,
    -- oid de Entra ID de quien pide el contacto
    solicitante_id    VARCHAR(100) NOT NULL,
    -- oid de quien lo recibe; es la única que puede responder
    solicitado_id     VARCHAR(100) NOT NULL,
    -- proyecto de la vitrina desde el que salió la solicitud (opcional, da contexto)
    proyecto_id       UUID,
    mensaje           VARCHAR(500),
    estado            VARCHAR(20)  NOT NULL,
    -- se llena solo al aceptar: es el dato que la persona decidió compartir
    datos_compartidos VARCHAR(500),
    fecha_solicitud   TIMESTAMPTZ  NOT NULL,
    fecha_respuesta   TIMESTAMPTZ,
    CONSTRAINT ck_solicitud_estado CHECK (estado IN ('PENDIENTE', 'ACEPTADA', 'RECHAZADA')),
    CONSTRAINT ck_solicitud_distinta_persona CHECK (solicitante_id <> solicitado_id)
);

-- Las dos bandejas (recibidas y enviadas) son las consultas más frecuentes.
CREATE INDEX ix_solicitud_solicitado  ON solicitudes_contacto (solicitado_id, fecha_solicitud DESC);
CREATE INDEX ix_solicitud_solicitante ON solicitudes_contacto (solicitante_id, fecha_solicitud DESC);

-- Como máximo una solicitud pendiente entre las mismas dos personas: evita inundar la bandeja.
CREATE UNIQUE INDEX ux_solicitud_pendiente
    ON solicitudes_contacto (solicitante_id, solicitado_id)
    WHERE estado = 'PENDIENTE';
