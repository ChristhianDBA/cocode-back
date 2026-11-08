CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_ayudar') THEN
        CREATE ROLE app_ayudar LOGIN;
    END IF;
END
$$;

REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO app_ayudar;
ALTER DEFAULT PRIVILEGES REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT EXECUTE ON FUNCTIONS TO app_ayudar;

CREATE TABLE IF NOT EXISTS parametros (
    clave          text PRIMARY KEY,
    valor          text NOT NULL,
    descripcion    text,
    activo         boolean NOT NULL DEFAULT true,
    creado_en      timestamptz NOT NULL DEFAULT now(),
    actualizado_en timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS distritos (
    id                 serial PRIMARY KEY,
    nombre             text NOT NULL UNIQUE,
    telefono_serenazgo text,
    telefono_comisaria text,
    telefono_bomberos  text,
    activo             boolean NOT NULL DEFAULT true,
    creado_en          timestamptz NOT NULL DEFAULT now(),
    actualizado_en     timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS telefonos_nacionales (
    id             serial PRIMARY KEY,
    servicio       text NOT NULL UNIQUE,
    numero         text NOT NULL,
    orden          integer NOT NULL,
    activo         boolean NOT NULL DEFAULT true,
    creado_en      timestamptz NOT NULL DEFAULT now(),
    actualizado_en timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS cuadrantes (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo         text NOT NULL UNIQUE,
    nombre         text NOT NULL,
    distrito_id    integer NOT NULL REFERENCES distritos (id),
    poligono       geometry(Polygon, 4326) NOT NULL,
    habilitado     boolean NOT NULL DEFAULT true,
    activo         boolean NOT NULL DEFAULT true,
    creado_en      timestamptz NOT NULL DEFAULT now(),
    actualizado_en timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_cuadrantes_poligono ON cuadrantes USING gist (poligono);

CREATE TABLE IF NOT EXISTS usuarios (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    dni                         char(8) NOT NULL UNIQUE,
    nombres                     text NOT NULL,
    apellido_paterno            text NOT NULL,
    apellido_materno            text NOT NULL,
    fecha_nacimiento            date NOT NULL,
    sexo                        char(1) NOT NULL CHECK (sexo IN ('F', 'M')),
    celular                     char(9) NOT NULL UNIQUE,
    tipo                        text NOT NULL CHECK (tipo IN ('VECINO', 'SERENAZGO', 'ADMIN')),
    acepta_tratamiento_en       timestamptz,
    ultimo_cambio_domicilio_en  timestamptz,
    activo                      boolean NOT NULL DEFAULT true,
    creado_en                   timestamptz NOT NULL DEFAULT now(),
    actualizado_en              timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS domicilios (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id        uuid NOT NULL REFERENCES usuarios (id),
    punto             geometry(Point, 4326) NOT NULL,
    direccion         text,
    referencia_cuadra text NOT NULL,
    cuadrante_id      uuid REFERENCES cuadrantes (id),
    vigente           boolean NOT NULL DEFAULT true,
    activo            boolean NOT NULL DEFAULT true,
    registrado_en     timestamptz NOT NULL DEFAULT now(),
    actualizado_en    timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_domicilios_vigente ON domicilios (usuario_id) WHERE vigente;
CREATE INDEX IF NOT EXISTS ix_domicilios_punto ON domicilios USING gist (punto);
CREATE INDEX IF NOT EXISTS ix_domicilios_cuadrante ON domicilios (cuadrante_id) WHERE vigente;

CREATE TABLE IF NOT EXISTS altas_en_curso (
    id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash               text NOT NULL UNIQUE,
    proposito                text NOT NULL CHECK (proposito IN ('ALTA', 'RECUPERACION')),
    usuario_id               uuid REFERENCES usuarios (id),
    dni                      char(8) NOT NULL,
    nombres                  text NOT NULL,
    apellido_paterno         text NOT NULL,
    apellido_materno         text NOT NULL,
    fecha_nacimiento         date NOT NULL,
    sexo                     char(1) NOT NULL CHECK (sexo IN ('F', 'M')),
    origen                   text NOT NULL CHECK (origen IN ('ESCANEO', 'MANUAL')),
    celular                  char(9),
    celular_verificado_en    timestamptz,
    punto                    geometry(Point, 4326),
    direccion                text,
    referencia_cuadra        text,
    cuadrante_id             uuid REFERENCES cuadrantes (id),
    domicilio_verificado_en  timestamptz,
    aplica_en                timestamptz,
    cancelada_en             timestamptz,
    vence_en                 timestamptz NOT NULL,
    completada_en            timestamptz,
    activo                   boolean NOT NULL DEFAULT true,
    creado_en                timestamptz NOT NULL DEFAULT now(),
    actualizado_en           timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE altas_en_curso ADD COLUMN IF NOT EXISTS aplica_en timestamptz;
ALTER TABLE altas_en_curso ADD COLUMN IF NOT EXISTS cancelada_en timestamptz;

CREATE TABLE IF NOT EXISTS codigos_verificacion (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    celular      char(9) NOT NULL,
    proposito    text NOT NULL CHECK (proposito IN ('ALTA', 'INGRESO', 'RECUPERACION')),
    referencia   uuid NOT NULL,
    canal        text NOT NULL CHECK (canal IN ('SMS', 'WHATSAPP')),
    codigo_hash  text NOT NULL,
    intentos     integer NOT NULL DEFAULT 0,
    enviado_en   timestamptz NOT NULL DEFAULT now(),
    vence_en     timestamptz NOT NULL,
    usado_en     timestamptz,
    activo       boolean NOT NULL DEFAULT true
);
CREATE INDEX IF NOT EXISTS ix_codigos_referencia ON codigos_verificacion (referencia, proposito, enviado_en DESC);

CREATE TABLE IF NOT EXISTS sesiones (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id             uuid NOT NULL REFERENCES usuarios (id),
    token_renovacion_hash  text NOT NULL UNIQUE,
    creada_en              timestamptz NOT NULL DEFAULT now(),
    renovada_en            timestamptz,
    revocada_en            timestamptz,
    motivo_revocacion      text,
    activo                 boolean NOT NULL DEFAULT true
);
CREATE INDEX IF NOT EXISTS ix_sesiones_usuario ON sesiones (usuario_id) WHERE revocada_en IS NULL;

CREATE TABLE IF NOT EXISTS dispositivos (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    sesion_id       uuid NOT NULL REFERENCES sesiones (id),
    usuario_id      uuid NOT NULL REFERENCES usuarios (id),
    token_push      text NOT NULL,
    valido          boolean NOT NULL DEFAULT true,
    registrado_en   timestamptz NOT NULL DEFAULT now(),
    actualizado_en  timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_dispositivos_sesion ON dispositivos (sesion_id) WHERE valido;
CREATE INDEX IF NOT EXISTS ix_dispositivos_usuario ON dispositivos (usuario_id) WHERE valido;

CREATE TABLE IF NOT EXISTS alertas (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo                    text NOT NULL CHECK (tipo IN ('PANICO', 'SOSPECHA', 'ROBO', 'MEDICA', 'ALUMBRADO')),
    emisor_id               uuid NOT NULL REFERENCES usuarios (id),
    cuadrante_id            uuid REFERENCES cuadrantes (id),
    cuadrante_domicilio_id  uuid REFERENCES cuadrantes (id),
    punto                   geometry(Point, 4326) NOT NULL,
    ubicacion_aproximada    boolean NOT NULL DEFAULT false,
    referencia_cuadra       text NOT NULL,
    descripcion             text,
    estado                  text NOT NULL DEFAULT 'ACTIVA'
                            CHECK (estado IN ('ACTIVA', 'ATENDIDA', 'FALSA_ALARMA', 'RESUELTA')),
    activo                  boolean NOT NULL DEFAULT true,
    creada_en               timestamptz NOT NULL DEFAULT now(),
    actualizada_en          timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_alertas_panico_activo ON alertas (emisor_id)
    WHERE tipo = 'PANICO' AND estado IN ('ACTIVA', 'ATENDIDA');
CREATE INDEX IF NOT EXISTS ix_alertas_cuadrante_activas ON alertas (cuadrante_id, creada_en DESC)
    WHERE estado IN ('ACTIVA', 'ATENDIDA');
CREATE INDEX IF NOT EXISTS ix_alertas_emisor ON alertas (emisor_id, creada_en DESC);

CREATE TABLE IF NOT EXISTS alertas_historial (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    alerta_id        uuid NOT NULL REFERENCES alertas (id),
    estado_anterior  text,
    estado_nuevo     text NOT NULL,
    autor_id         uuid REFERENCES usuarios (id),
    autor_sistema    boolean NOT NULL DEFAULT false,
    fecha            timestamptz NOT NULL DEFAULT now(),
    CHECK (autor_sistema OR autor_id IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS ix_historial_alerta ON alertas_historial (alerta_id, fecha);

CREATE TABLE IF NOT EXISTS roles_coordinador (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id    uuid NOT NULL REFERENCES usuarios (id),
    cuadrante_id  uuid NOT NULL REFERENCES cuadrantes (id),
    otorgado_por  uuid NOT NULL REFERENCES usuarios (id),
    otorgado_en   timestamptz NOT NULL DEFAULT now(),
    retirado_en   timestamptz,
    activo        boolean NOT NULL DEFAULT true
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_coordinador_vigente ON roles_coordinador (usuario_id, cuadrante_id) WHERE activo;

CREATE TABLE IF NOT EXISTS cuentas_serenazgo (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id      uuid NOT NULL UNIQUE REFERENCES usuarios (id),
    distrito_id     integer NOT NULL REFERENCES distritos (id),
    activo          boolean NOT NULL DEFAULT true,
    creado_en       timestamptz NOT NULL DEFAULT now(),
    actualizado_en  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS suspensiones_panico (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id     uuid NOT NULL REFERENCES usuarios (id),
    motivo         text NOT NULL,
    desde          timestamptz NOT NULL DEFAULT now(),
    levantada_por  uuid REFERENCES usuarios (id),
    levantada_en   timestamptz,
    activo         boolean NOT NULL DEFAULT true
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_suspension_vigente ON suspensiones_panico (usuario_id) WHERE levantada_en IS NULL;

CREATE TABLE IF NOT EXISTS reportes_datos_civiles (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id   uuid NOT NULL REFERENCES usuarios (id),
    descripcion  text NOT NULL,
    atendido     boolean NOT NULL DEFAULT false,
    activo       boolean NOT NULL DEFAULT true,
    creado_en    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS avisos_administrador (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo           text NOT NULL CHECK (tipo IN ('SUSPENSION', 'RECUPERACION', 'DATOS_CIVILES')),
    usuario_id     uuid NOT NULL REFERENCES usuarios (id),
    referencia_id  uuid,
    detalle        text NOT NULL,
    leido          boolean NOT NULL DEFAULT false,
    activo         boolean NOT NULL DEFAULT true,
    creado_en      timestamptz NOT NULL DEFAULT now()
);
