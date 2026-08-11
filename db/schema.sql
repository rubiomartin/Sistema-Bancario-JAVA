CREATE EXTENSION IF NOT EXISTS pgcrypto;

DROP TABLE IF EXISTS operacion_rechazo CASCADE;
DROP TABLE IF EXISTS movimiento CASCADE;
DROP TABLE IF EXISTS operacion CASCADE;
DROP TABLE IF EXISTS sys_batch_err CASCADE;
DROP TABLE IF EXISTS sys_batch_log CASCADE;
DROP TABLE IF EXISTS sys_tasas CASCADE;
DROP TABLE IF EXISTS sys_config CASCADE;
DROP TABLE IF EXISTS usuarios CASCADE;

CREATE TABLE usuarios (
    id_usuario      VARCHAR(8) PRIMARY KEY,
    password_hash   TEXT NOT NULL,
    saldo_actual    NUMERIC(13, 2) NOT NULL DEFAULT 0.00,
    cvu             CHAR(22) NOT NULL DEFAULT '',
    estado          CHAR(3) NOT NULL DEFAULT 'ACT',
    fecha_ult_rend  DATE NOT NULL DEFAULT '0001-01-01',
    CONSTRAINT chk_usuarios_estado CHECK (estado IN ('ACT', 'INA'))
);

CREATE TABLE operacion (
    id_operacion     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_operacion   VARCHAR(4) NOT NULL,
    id_idempotencia  UUID NOT NULL,
    id_coelsa        VARCHAR(22) NOT NULL DEFAULT '',
    cae_afip         VARCHAR(14) NOT NULL DEFAULT '',
    estado           VARCHAR(10) NOT NULL DEFAULT 'PENDIENTE',
    fecha_creacion   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_operacion_idempotencia UNIQUE (id_idempotencia)
);

CREATE TABLE movimiento (
    id_movimiento    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_operacion     UUID NOT NULL REFERENCES operacion (id_operacion) ON DELETE CASCADE,
    id_usuario       VARCHAR(8) NOT NULL REFERENCES usuarios (id_usuario),
    tipo             VARCHAR(2) NOT NULL,
    monto            NUMERIC(13, 2) NOT NULL,
    contraparte      VARCHAR(22) NOT NULL DEFAULT '',
    creado_en        TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_contable   DATE NOT NULL DEFAULT CURRENT_DATE
);

CREATE INDEX idx_movimiento_usuario ON movimiento (id_usuario, creado_en DESC);

CREATE TABLE operacion_rechazo (
    id_rechazo      BIGSERIAL PRIMARY KEY,
    id_operacion    UUID REFERENCES operacion (id_operacion) ON DELETE CASCADE,
    codigo_error    CHAR(2) NOT NULL,
    mensaje_error   VARCHAR(100) NOT NULL,
    origen          VARCHAR(10) NOT NULL DEFAULT 'GO',
    fecha_rechazo   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sys_batch_log (
    id_log           BIGSERIAL PRIMARY KEY,
    proceso          VARCHAR(20) NOT NULL,
    fecha_ejecucion  DATE NOT NULL DEFAULT CURRENT_DATE,
    hora_inicio      TIME NOT NULL DEFAULT CURRENT_TIME,
    hora_fin         TIME,
    reg_procesados   INT NOT NULL DEFAULT 0,
    reg_errores      INT NOT NULL DEFAULT 0,
    estado           VARCHAR(10) NOT NULL,
    detalle_error    VARCHAR(100) NOT NULL DEFAULT ''
);

CREATE TABLE sys_batch_err (
    id_error         BIGSERIAL PRIMARY KEY,
    id_log           BIGINT NOT NULL REFERENCES sys_batch_log (id_log),
    fecha_ejecucion  DATE NOT NULL DEFAULT CURRENT_DATE,
    proceso          VARCHAR(20) NOT NULL,
    id_usuario       VARCHAR(8) NOT NULL,
    saldo_cache      NUMERIC(13, 2) NOT NULL,
    saldo_real       NUMERIC(13, 2) NOT NULL,
    diferencia       NUMERIC(13, 2) NOT NULL,
    desc_err         VARCHAR(50) NOT NULL DEFAULT '',
    hora_deteccion   TIME NOT NULL DEFAULT CURRENT_TIME
);

CREATE TABLE sys_tasas (
    cod_tasa        CHAR(4) PRIMARY KEY,
    descripcion     VARCHAR(50) NOT NULL,
    porcentaje      NUMERIC(7, 4) NOT NULL,
    fecha_vigencia  DATE NOT NULL DEFAULT CURRENT_DATE,
    activo          CHAR(1) NOT NULL DEFAULT 'S'
);

INSERT INTO sys_tasas (cod_tasa, descripcion, porcentaje)
VALUES ('REND', 'Tasa de rendimiento diario', 0.1000);

CREATE TABLE sys_config (
    id_config            SMALLINT PRIMARY KEY DEFAULT 1,
    estado_sistema       VARCHAR(10) NOT NULL DEFAULT 'ONLINE',
    fecha_operativa      DATE NOT NULL DEFAULT CURRENT_DATE,
    fecha_proxima        DATE NOT NULL,
    hora_corte_eod       TIME NOT NULL DEFAULT '23:59:59',
    mantenimiento        CHAR(1) NOT NULL DEFAULT 'N',
    fecha_actualizacion  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_sys_config_id_unico CHECK (id_config = 1)
);

INSERT INTO sys_config (estado_sistema, fecha_operativa, fecha_proxima, mantenimiento)
VALUES ('ONLINE', CURRENT_DATE, CURRENT_DATE + INTERVAL '1 day', 'N');

INSERT INTO usuarios (id_usuario, password_hash, saldo_actual, cvu, estado) VALUES
    ('JGONZALE', '$2a$10$QyOV1Cu58oN1e7ktA.Dljea6m3L5of6psNQRAMySasdKFAlTAHbOO', 15000.00, '0000003100012345678901', 'ACT'),
    ('MRODRIGU', '$2a$10$QyOV1Cu58oN1e7ktA.Dljea6m3L5of6psNQRAMySasdKFAlTAHbOO',  3200.50, '0000003100019876543210', 'ACT'),
    ('LFERNAND', '$2a$10$QyOV1Cu58oN1e7ktA.Dljea6m3L5of6psNQRAMySasdKFAlTAHbOO',  8750.25, '0000003100011122334455', 'ACT'),
    ('SPEREYRA', '$2a$10$QyOV1Cu58oN1e7ktA.Dljea6m3L5of6psNQRAMySasdKFAlTAHbOO',   500.00, '0000003100015566778899', 'ACT'),
    ('CSUAREZ',  '$2a$10$QyOV1Cu58oN1e7ktA.Dljea6m3L5of6psNQRAMySasdKFAlTAHbOO', 42300.75, '0000003100019988776655', 'ACT');

DO $$
DECLARE
    op_apertura UUID;
BEGIN
    INSERT INTO operacion (tipo_operacion, id_idempotencia, estado)
    VALUES ('APER', gen_random_uuid(), 'COMPLETADA') RETURNING id_operacion INTO op_apertura;
    INSERT INTO movimiento (id_operacion, id_usuario, tipo, monto)
    VALUES (op_apertura, 'JGONZALE', 'D', 15000.00);

    INSERT INTO operacion (tipo_operacion, id_idempotencia, estado)
    VALUES ('APER', gen_random_uuid(), 'COMPLETADA') RETURNING id_operacion INTO op_apertura;
    INSERT INTO movimiento (id_operacion, id_usuario, tipo, monto)
    VALUES (op_apertura, 'MRODRIGU', 'D', 3200.50);

    INSERT INTO operacion (tipo_operacion, id_idempotencia, estado)
    VALUES ('APER', gen_random_uuid(), 'COMPLETADA') RETURNING id_operacion INTO op_apertura;
    INSERT INTO movimiento (id_operacion, id_usuario, tipo, monto)
    VALUES (op_apertura, 'LFERNAND', 'D', 8750.25);

    INSERT INTO operacion (tipo_operacion, id_idempotencia, estado)
    VALUES ('APER', gen_random_uuid(), 'COMPLETADA') RETURNING id_operacion INTO op_apertura;
    INSERT INTO movimiento (id_operacion, id_usuario, tipo, monto)
    VALUES (op_apertura, 'SPEREYRA', 'D', 500.00);

    INSERT INTO operacion (tipo_operacion, id_idempotencia, estado)
    VALUES ('APER', gen_random_uuid(), 'COMPLETADA') RETURNING id_operacion INTO op_apertura;
    INSERT INTO movimiento (id_operacion, id_usuario, tipo, monto)
    VALUES (op_apertura, 'CSUAREZ', 'D', 42300.75);
END $$;
