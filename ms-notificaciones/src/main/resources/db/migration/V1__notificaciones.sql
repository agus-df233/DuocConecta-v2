-- Esquema inicial de ms-notificaciones (EP2). Todo vive en el schema `notificaciones`.

-- La bandeja de cada persona: lo que los consumidores le avisan.
CREATE TABLE aviso (
    id              UUID          PRIMARY KEY,
    -- Claim oid de quien recibe el aviso (el mismo de ms-usuarios).
    destinatario_id VARCHAR(100)  NOT NULL,
    tipo            VARCHAR(60)   NOT NULL,
    titulo          VARCHAR(200)  NOT NULL,
    detalle         VARCHAR(500),
    recurso_id      VARCHAR(100),
    proyecto_id     VARCHAR(100),
    id_evento       VARCHAR(100)  NOT NULL,
    leido           BOOLEAN       NOT NULL DEFAULT FALSE,
    creado_en       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX ix_aviso_destinatario ON aviso (destinatario_id, creado_en DESC);

-- Todo evento que pasó por la plataforma, tal como llegó (la cola sin filtro, el "#").
CREATE TABLE evento_auditado (
    id_evento       VARCHAR(100)  PRIMARY KEY,
    tipo            VARCHAR(60)   NOT NULL,
    origen          VARCHAR(60),
    actor_id        VARCHAR(100),
    destinatario_id VARCHAR(100),
    recurso_id      VARCHAR(100),
    proyecto_id     VARCHAR(100),
    detalle         VARCHAR(500),
    ocurrido_en     TIMESTAMPTZ,
    recibido_en     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    id_peticion     VARCHAR(100)
);

-- Qué consumidor ya procesó qué evento. La PK combinada rechaza el duplicado sola; el mismo
-- evento llega a dos colas (su categoría y auditoría), por eso el consumidor es parte de la clave.
CREATE TABLE evento_procesado (
    id_evento       VARCHAR(100)  NOT NULL,
    consumidor      VARCHAR(40)   NOT NULL,
    procesado_en    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id_evento, consumidor)
);
