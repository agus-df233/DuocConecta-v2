-- El dato compartido dejaba de ser legible: era un solo texto con el correo, el teléfono y las
-- redes pegados. Ahora va en tres columnas, para que la interfaz pueda mostrarlos por separado
-- y para poder distinguir "no lo compartió" de "no lo tiene".
ALTER TABLE solicitudes_contacto ADD COLUMN correo_compartido   VARCHAR(255);
ALTER TABLE solicitudes_contacto ADD COLUMN telefono_compartido VARCHAR(50);
ALTER TABLE solicitudes_contacto ADD COLUMN redes_compartidas   VARCHAR(500);

-- Nada que migrar: la columna vieja se estrena en esta misma entrega y no hay datos en producción.
ALTER TABLE solicitudes_contacto DROP COLUMN datos_compartidos;

COMMENT ON COLUMN solicitudes_contacto.correo_compartido
    IS 'Solo se llena al aceptar: es el consentimiento hecho dato';
