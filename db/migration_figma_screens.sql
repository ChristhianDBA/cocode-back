-- ============================================================================
-- MIGRACIÓN: TABLAS PARA LAS PANTALLAS DE FIGMA (AYUDAR TRAZABLE)
-- Publicaciones, Favores, Reseñas, Chat y Tablero de Emergencia
-- ============================================================================

-- 1. Ampliación de la tabla usuarios (Ocupación y Perfil)
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS oficio_profesion VARCHAR(100);
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS anios_experiencia INTEGER DEFAULT 0;
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS visible_directorio BOOLEAN DEFAULT TRUE;

-- 2. Tabla de Publicaciones (Servicios y Favores)
CREATE TABLE IF NOT EXISTS publicaciones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID REFERENCES usuarios(id) ON DELETE SET NULL,
    cuadrante_id UUID REFERENCES cuadrantes(id) ON DELETE SET NULL,
    categoria VARCHAR(20) NOT NULL CHECK (categoria IN ('servicios', 'favores')),
    tipo VARCHAR(30) NOT NULL CHECK (tipo IN ('servicio', 'dono', 'presto', 'ofrezco', 'necesito')),
    titulo VARCHAR(140) NOT NULL,
    descripcion TEXT,
    manzana_referencia VARCHAR(50) DEFAULT 'Mz. D, Los Pinos',
    metodo_contacto VARCHAR(20) DEFAULT 'chat' CHECK (metodo_contacto IN ('chat', 'whatsapp')),
    estado VARCHAR(20) DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO', 'PAUSADO', 'FINALIZADO')),
    calificacion_promedio NUMERIC(3, 1) DEFAULT 5.0,
    vistos_buenos_count INTEGER DEFAULT 0,
    creado_el TIMESTAMPTZ DEFAULT NOW(),
    actualizado_el TIMESTAMPTZ DEFAULT NOW()
);

-- 3. Tabla de Solicitudes de Favores ("Pedir prestado")
CREATE TABLE IF NOT EXISTS solicitudes_favor (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    publicacion_id UUID NOT NULL REFERENCES publicaciones(id) ON DELETE CASCADE,
    solicitante_id UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    dueno_id UUID REFERENCES usuarios(id) ON DELETE SET NULL,
    estado VARCHAR(20) DEFAULT 'EN_CURSO' CHECK (estado IN ('SOLICITADO', 'EN_CURSO', 'DEVUELTO', 'CANCELADO')),
    fecha_devolucion_acordada TIMESTAMPTZ,
    devuelto_el TIMESTAMPTZ,
    creado_el TIMESTAMPTZ DEFAULT NOW()
);

-- 4. Tabla de Visto Bueno Vecinal
CREATE TABLE IF NOT EXISTS vistos_buenos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vecino_origen_id UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    vecino_destino_id UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    creado_el TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (vecino_origen_id, vecino_destino_id)
);

-- 5. Tabla de Reseñas y Calificaciones Vecinales
CREATE TABLE IF NOT EXISTS resenas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vecino_evaluado_id UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    vecino_autor_id UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    publicacion_id UUID REFERENCES publicaciones(id) ON DELETE SET NULL,
    calificacion INTEGER NOT NULL CHECK (calificacion BETWEEN 1 AND 5),
    recomendo BOOLEAN DEFAULT TRUE,
    destacados TEXT[], -- ['Puntual', 'Precio justo', 'Trabajo limpio', etc.]
    comentario VARCHAR(280) NOT NULL,
    creado_el TIMESTAMPTZ DEFAULT NOW()
);

-- 6. Tablas de Mensajería y Chat Interno
CREATE TABLE IF NOT EXISTS conversaciones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo VARCHAR(20) DEFAULT 'DIRECTO' CHECK (tipo IN ('DIRECTO', 'GRUPO_MANZANA', 'CANAL_OFICIAL')),
    titulo VARCHAR(100),
    referencia_publicacion_id UUID REFERENCES publicaciones(id) ON DELETE SET NULL,
    creado_el TIMESTAMPTZ DEFAULT NOW(),
    actualizado_el TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS conversacion_participantes (
    conversacion_id UUID NOT NULL REFERENCES conversaciones(id) ON DELETE CASCADE,
    usuario_id UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    ultimo_leido_el TIMESTAMPTZ DEFAULT NOW(),
    activo BOOLEAN DEFAULT TRUE,
    PRIMARY KEY (conversacion_id, usuario_id)
);

CREATE TABLE IF NOT EXISTS mensajes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversacion_id UUID NOT NULL REFERENCES conversaciones(id) ON DELETE CASCADE,
    remitente_id UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    contenido TEXT NOT NULL,
    leido BOOLEAN DEFAULT FALSE,
    creado_el TIMESTAMPTZ DEFAULT NOW()
);

-- 7. Tablas del Tablero Comunitario de Ayuda (Emergencias)
CREATE TABLE IF NOT EXISTS tableros_emergencia (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cuadrante_id UUID REFERENCES cuadrantes(id) ON DELETE CASCADE,
    solicitante_id UUID REFERENCES usuarios(id) ON DELETE SET NULL,
    tipo_emergencia VARCHAR(50) NOT NULL CHECK (tipo_emergencia IN ('Lluvias o huaico', 'Sismo', 'Incendio', 'Sin agua o luz', 'Otro')),
    descripcion TEXT NOT NULL,
    estado VARCHAR(20) DEFAULT 'SOLICITADO' CHECK (estado IN ('SOLICITADO', 'ACTIVO', 'EXPIRADO', 'CERRADO')),
    confirmaciones_count INTEGER DEFAULT 1,
    expira_el TIMESTAMPTZ DEFAULT (NOW() + INTERVAL '30 minutes'),
    activado_el TIMESTAMPTZ,
    creado_el TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tablero_confirmaciones (
    tablero_id UUID NOT NULL REFERENCES tableros_emergencia(id) ON DELETE CASCADE,
    vecino_id UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    creado_el TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (tablero_id, vecino_id)
);

CREATE TABLE IF NOT EXISTS tablero_personas_a_salvo (
    tablero_id UUID NOT NULL REFERENCES tableros_emergencia(id) ON DELETE CASCADE,
    vecino_id UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    creado_el TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (tablero_id, vecino_id)
);

CREATE TABLE IF NOT EXISTS tablero_necesidades (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tablero_id UUID NOT NULL REFERENCES tableros_emergencia(id) ON DELETE CASCADE,
    nombre VARCHAR(100) NOT NULL,
    prioridad VARCHAR(30) DEFAULT 'Prioridad alta',
    meta INTEGER DEFAULT 0,
    reunido INTEGER DEFAULT 0,
    unidad VARCHAR(20) DEFAULT '',
    creado_el TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tablero_aportes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    necesidad_id UUID REFERENCES tablero_necesidades(id) ON DELETE CASCADE,
    vecino_id UUID REFERENCES usuarios(id) ON DELETE SET NULL,
    descripcion VARCHAR(200) NOT NULL,
    cantidad INTEGER DEFAULT 1,
    contactado BOOLEAN DEFAULT FALSE,
    creado_el TIMESTAMPTZ DEFAULT NOW()
);

-- Índices de consulta rápida
CREATE INDEX IF NOT EXISTS idx_publicaciones_cuadrante ON publicaciones(cuadrante_id, categoria, estado);
CREATE INDEX IF NOT EXISTS idx_mensajes_conversacion ON mensajes(conversacion_id, creado_el);
CREATE INDEX IF NOT EXISTS idx_resenas_vecino ON resenas(vecino_evaluado_id);
