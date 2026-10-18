-- Quien pidió contacto puede retirar su solicitud mientras siga pendiente.
--
-- RETIRADA no entra en el índice único de V3 (solo PENDIENTE y ACEPTADA), así que retirar libera
-- el cupo y se puede volver a pedir por el mismo proyecto.
ALTER TABLE solicitudes_contacto DROP CONSTRAINT ck_solicitud_estado;
ALTER TABLE solicitudes_contacto ADD CONSTRAINT ck_solicitud_estado
    CHECK (estado IN ('PENDIENTE', 'ACEPTADA', 'RECHAZADA', 'RETIRADA'));
