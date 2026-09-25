-- Adjuntos de un proyecto, con los datos que hacen falta para mostrarlos y borrarlos.
--
-- La tabla anterior (proyecto_archivos) solo guardaba una cadena por fila, sin clave primaria ni
-- metadatos: no se podía mostrar el nombre real del archivo, ni su tamaño, ni borrar uno concreto.

CREATE TABLE proyecto_adjunto (
    id              UUID         NOT NULL,
    proyecto_id     UUID         NOT NULL,

    -- Un adjunto es un archivo subido a S3 o un enlace externo, nunca las dos cosas.
    clave_s3        VARCHAR(400),
    url_externa     VARCHAR(600),

    nombre          VARCHAR(200) NOT NULL,
    tipo_contenido  VARCHAR(120),
    tamano_bytes    BIGINT,
    subido_en       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_proyecto_adjunto PRIMARY KEY (id),
    CONSTRAINT fk_proyecto_adjunto
        FOREIGN KEY (proyecto_id) REFERENCES proyecto (id) ON DELETE CASCADE,
    CONSTRAINT ck_proyecto_adjunto_origen
        CHECK ((clave_s3 IS NOT NULL) <> (url_externa IS NOT NULL))
);

CREATE INDEX idx_proyecto_adjunto ON proyecto_adjunto (proyecto_id);

-- Lo que ya estaba escrito a mano eran enlaces o nombres sueltos: se conservan como enlaces
-- externos para no perder los proyectos que ya los tenían.
INSERT INTO proyecto_adjunto (id, proyecto_id, url_externa, nombre, subido_en)
SELECT gen_random_uuid(),
       proyecto_id,
       url_archivo,
       -- El nombre visible es lo que va después de la última barra, o el texto completo.
       COALESCE(NULLIF(regexp_replace(url_archivo, '^.*/', ''), ''), url_archivo),
       now()
FROM proyecto_archivos;

DROP TABLE proyecto_archivos;
