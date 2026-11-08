-- Tablas de comunidad, publicaciones, actividad, reseñas, ocupaciones, chat y tablero de emergencia

CREATE TABLE IF NOT EXISTS publicaciones_barrio (
    id text PRIMARY KEY,
    iniciales text NOT NULL,
    nombre text NOT NULL,
    rubro text NOT NULL,
    detalle text NOT NULL,
    distancia text NOT NULL DEFAULT '150 m',
    vistos integer NOT NULL DEFAULT 1,
    calificacion double precision NOT NULL DEFAULT 5.0,
    avatar_bg text NOT NULL DEFAULT '#d6eee2',
    avatar_color text NOT NULL DEFAULT '#144e40',
    categoria text NOT NULL,
    tipo text NOT NULL,
    ubicacion text NOT NULL,
    metodo_contacto text NOT NULL DEFAULT 'chat',
    condiciones jsonb NOT NULL DEFAULT '[]'::jsonb,
    tiempo text NOT NULL DEFAULT 'Recién publicado',
    latitud double precision NOT NULL DEFAULT -12.0895,
    longitud double precision NOT NULL DEFAULT -76.9900,
    tag text NOT NULL DEFAULT 'Todos',
    estado text NOT NULL DEFAULT 'Activo',
    activo boolean NOT NULL DEFAULT true,
    creado_en timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS actividad_vecinal (
    id text PRIMARY KEY,
    lista_tipo text NOT NULL, -- 'MIS_PUBLICACIONES' | 'HICE' | 'PEDI'
    tipo text NOT NULL,
    titulo text NOT NULL,
    subtitulo text NOT NULL,
    estado text NOT NULL DEFAULT 'Activo',
    fecha text NOT NULL DEFAULT 'Hoy',
    interesados_count integer NOT NULL DEFAULT 0,
    prestado_a text,
    hasta_fecha text,
    activo boolean NOT NULL DEFAULT true,
    creado_en timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS perfiles_vecinos (
    id text PRIMARY KEY,
    iniciales text NOT NULL,
    nombre text NOT NULL,
    oficio text NOT NULL,
    anios_oficio integer NOT NULL DEFAULT 5,
    distancia text NOT NULL DEFAULT 'A 200 m',
    ubicacion text NOT NULL DEFAULT 'Urb. Los Pinos',
    vistos_buenos integer NOT NULL DEFAULT 10,
    calificacion double precision NOT NULL DEFAULT 4.9,
    resenas_count integer NOT NULL DEFAULT 2,
    fecha_registro text NOT NULL DEFAULT 'vecino desde marzo 2026',
    visto_bueno_dado boolean NOT NULL DEFAULT false,
    actualizado_en timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS resenas_vecinos (
    id text PRIMARY KEY,
    vecino_id text NOT NULL,
    autor text NOT NULL,
    iniciales text NOT NULL,
    ubicacion text NOT NULL,
    calificacion integer NOT NULL DEFAULT 5,
    recomendo boolean NOT NULL DEFAULT true,
    destacados jsonb NOT NULL DEFAULT '[]'::jsonb,
    comentario text NOT NULL,
    fecha text NOT NULL DEFAULT 'Hoy',
    verificado boolean NOT NULL DEFAULT true,
    creado_en timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS ocupaciones_usuario (
    id text PRIMARY KEY,
    usuario_id uuid,
    oficio text NOT NULL,
    anios_experiencia integer NOT NULL DEFAULT 1,
    visible_directorio boolean NOT NULL DEFAULT true,
    activa boolean NOT NULL DEFAULT true,
    actualizado_en timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS chat_conversaciones (
    id text PRIMARY KEY,
    avatar_initials text NOT NULL,
    avatar_bg text NOT NULL,
    avatar_color text NOT NULL,
    name text NOT NULL,
    subtitle text NOT NULL,
    last_message text NOT NULL,
    time_label text NOT NULL,
    unread integer NOT NULL DEFAULT 0,
    is_official boolean NOT NULL DEFAULT false,
    bloqueado boolean NOT NULL DEFAULT false,
    actualizado_en timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS chat_mensajes (
    id text PRIMARY KEY,
    conversacion_id text NOT NULL,
    texto text NOT NULL,
    sender text NOT NULL,
    hora text NOT NULL,
    verificado boolean NOT NULL DEFAULT true,
    creado_en timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS chat_reportes (
    id text PRIMARY KEY,
    usuario_id text NOT NULL,
    motivo text NOT NULL,
    detalle text,
    creado_en timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS directorio_local (
    id text PRIMARY KEY,
    initials text NOT NULL,
    name text NOT NULL,
    subtitle text NOT NULL,
    distance text NOT NULL,
    phone text NOT NULL,
    bg text NOT NULL,
    color text NOT NULL,
    orden integer NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS tablero_emergencia (
    id text PRIMARY KEY,
    cuadrante text NOT NULL,
    tipo_emergencia text NOT NULL,
    descripcion text NOT NULL,
    estado text NOT NULL DEFAULT 'ACTIVO',
    confirmaciones integer NOT NULL DEFAULT 5,
    meta_confirmaciones integer NOT NULL DEFAULT 5,
    minutos_restantes integer NOT NULL DEFAULT 0,
    activo boolean NOT NULL DEFAULT true,
    personas_a_salvo integer NOT NULL DEFAULT 126,
    actualizado_en timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS tablero_necesidades (
    id text PRIMARY KEY,
    tablero_id text NOT NULL DEFAULT 'tablero-1',
    recurso text NOT NULL,
    prioridad text NOT NULL,
    actual integer NOT NULL DEFAULT 0,
    meta integer NOT NULL DEFAULT 100,
    unidad text NOT NULL DEFAULT '',
    cubierto boolean NOT NULL DEFAULT false,
    orden integer NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS tablero_aportes (
    id text PRIMARY KEY,
    tablero_id text NOT NULL DEFAULT 'tablero-1',
    descripcion text NOT NULL,
    vecino text NOT NULL,
    tiempo text NOT NULL DEFAULT 'Ahora',
    entregado boolean NOT NULL DEFAULT false,
    creado_en timestamptz NOT NULL DEFAULT clock_timestamp()
);

-- Datos semilla iniciales (solo si no existen)
INSERT INTO publicaciones_barrio (id, iniciales, nombre, rubro, detalle, distancia, vistos, calificacion, avatar_bg, avatar_color, categoria, tipo, ubicacion, metodo_contacto, condiciones, tiempo, latitud, longitud, tag)
VALUES
('1', 'RQ', 'Rosa Quispe', 'Gasfitería', 'Atiende hoy', '180 m', 42, 4.9, '#d6eee2', '#144e40', 'servicios', 'servicio', 'Mz. D, Los Pinos', 'chat', '["12 años de experiencia", "Garantía de servicio"]'::jsonb, 'Hace 2 horas', -12.0892, -76.9890, 'Gasfitería'),
('2', 'MH', 'Miguel Huamán', 'Electricista domiciliario', 'Instalaciones y emergencias', '350 m', 31, 4.8, '#faecd5', '#8c531b', 'servicios', 'servicio', 'Mz. B, Los Pinos', 'chat', '["Técnico certificado", "Disponibilidad fines de semana"]'::jsonb, 'Hace 4 horas', -12.0920, -76.9922, 'Electricidad'),
('3', 'LT', 'Lucía Torres', 'Clases de matemática', 'Secundaria y preuniversitario', '420 m', 27, 5.0, '#e0e7fb', '#2b4290', 'servicios', 'servicio', 'Mz. C, Los Pinos', 'chat', '["Modalidad presencial o virtual"]'::jsonb, 'Ayer', -12.0918, -76.9875, 'Clases'),
('4', 'JC', 'Jorge Córdova', 'Carpintería y cerrajería', 'Duplicado de llaves y chapas', '600 m', 19, 4.7, '#fee4e2', '#912018', 'servicios', 'servicio', 'Mz. A, Los Pinos', 'whatsapp', '["Atención de emergencias 24h"]'::jsonb, 'Ayer', -12.0875, -76.9935, 'Todos'),
('5', 'JR', 'Julio R. · Mz. A', 'Presto taladro percutor', 'Para el fin de semana. Solo pido que lo devuelvas limpio.', '150 m', 23, 5.0, '#e0e7fb', '#2b4290', 'favores', 'presto', 'Mz. A, Los Pinos', 'chat', '["Disponible: fin de semana", "Préstamo máximo: 3 días"]'::jsonb, 'Hace 3 días', -12.0895, -76.9915, 'Todos'),
('6', 'TL', 'Teresa L.', 'Presto escalera telescópica', 'Para trabajos en casa hasta 3 metros', '310 m', 15, 4.9, '#fee4e2', '#912018', 'favores', 'presto', 'Mz. B, Los Pinos', 'chat', '["Préstamo máximo: 2 días"]'::jsonb, 'Hace 4 días', -12.0910, -76.9880, 'Todos'),
('7', 'CR', 'Carlos Ramos', 'Dono libros escolares de primaria', '3ro y 4to de primaria en buen estado', '390 m', 22, 4.9, '#d6eee2', '#144e40', 'favores', 'dono', 'Mz. C, Los Pinos', 'chat', '["Entrega en portería"]'::jsonb, 'Hace 5 días', -12.0902, -76.9899, 'Todos')
ON CONFLICT (id) DO NOTHING;

INSERT INTO actividad_vecinal (id, lista_tipo, tipo, titulo, subtitulo, estado, fecha, interesados_count, prestado_a, hasta_fecha)
VALUES
('u1', 'MIS_PUBLICACIONES', 'Mi servicio', 'Electricidad · 8 años de experiencia', 'Visible en Servicios · 4 vecinos te contactaron esta semana', 'Activo', 'Hoy', 4, NULL, NULL),
('u2', 'MIS_PUBLICACIONES', 'Presto', 'Presto escalera de 3 metros', 'Prestada a Teresa L. hasta el domingo', 'En curso', 'Hace 2 días', 1, 'Teresa L.', 'domingo'),
('u3', 'MIS_PUBLICACIONES', 'Dono', 'Dono libros escolares de primaria', 'Publicado hace 3 días · 2 vecinos interesados', 'Activo', 'Hace 3 días', 2, NULL, NULL),
('h1', 'HICE', 'Presto', 'Presto escalera de 3 metros', 'Prestada a Teresa L. hasta el domingo', 'En curso', 'Esta semana', 1, 'Teresa L.', 'domingo'),
('h2', 'HICE', 'Ofrezco', 'Ayuda podando ramas del pasaje B', 'Completado con Jorge C.', 'Finalizado', 'Hace 2 semanas', 1, 'Jorge C.', NULL),
('p1', 'PEDI', 'Pedí', 'Taladro percutor', 'Prestado por Julio R. · Por devolver el lunes', 'En curso', 'Ayer', 1, 'Julio R.', 'lunes')
ON CONFLICT (id) DO NOTHING;

INSERT INTO perfiles_vecinos (id, iniciales, nombre, oficio, anios_oficio, distancia, ubicacion, vistos_buenos, calificacion, resenas_count, fecha_registro, visto_bueno_dado)
VALUES
('1', 'RQ', 'Rosa Quispe', 'Gasfitería', 12, 'A 180 m', 'Mz. D, Los Pinos', 42, 4.9, 18, 'vecino desde marzo 2026', false),
('2', 'MH', 'Miguel Huamán', 'Electricista', 8, 'A 350 m', 'Mz. B, Los Pinos', 31, 4.8, 12, 'vecino desde enero 2026', false),
('3', 'LT', 'Lucía Torres', 'Clases de matemática', 6, 'A 420 m', 'Mz. C, Los Pinos', 27, 5.0, 9, 'vecina desde febrero 2026', false),
('4', 'JC', 'Jorge Córdova', 'Carpintería y cerrajería', 10, 'A 600 m', 'Mz. A, Los Pinos', 19, 4.7, 7, 'vecino desde marzo 2026', false),
('5', 'JR', 'Julio R.', 'Vecino colaborador', 4, 'A 150 m', 'Mz. A, Los Pinos', 23, 5.0, 5, 'vecino desde enero 2026', false),
('6', 'TL', 'Teresa L.', 'Vecina colaboradora', 3, 'A 310 m', 'Mz. B, Los Pinos', 15, 4.9, 4, 'vecina desde febrero 2026', false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO resenas_vecinos (id, vecino_id, autor, iniciales, ubicacion, calificacion, recomendo, destacados, comentario, fecha, verificado)
VALUES
('r1', '1', 'Carmen M.', 'CM', 'Mz. B · a 2 cuadras', 5, true, '["Puntual", "Precio justo", "Trabajo limpio"]'::jsonb, 'Vino en menos de media hora por una fuga en la cocina. Cobró lo que dijo y dejó todo limpio.', 'Hace 3 días', true),
('r2', '1', 'Julio R.', 'JR', 'Mz. A · a 1 cuadra', 5, true, '["Honesta", "Explica bien"]'::jsonb, 'Cambió una llave de paso rota el domingo por la tarde. Muy atenta y honesta con los materiales.', 'Hace 2 semanas', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO ocupaciones_usuario (id, usuario_id, oficio, anios_experiencia, visible_directorio, activa)
VALUES ('default', NULL, 'Electricidad', 8, true, true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO chat_conversaciones (id, avatar_initials, avatar_bg, avatar_color, name, subtitle, last_message, time_label, unread, is_official)
VALUES
('1', 'RQ', '#d6eee2', '#144e40', 'Rosa Quispe', 'Servicio · Gasfitería', 'Puedo pasar mañana a las 9, ¿le ...', '23:05', 2, false),
('2', 'JR', '#e0e7fb', '#2b4290', 'Julio R.', 'Favor · Presto taladro', 'Te lo dejo en la portería el sábado.', '21:40', 1, false),
('3', 'MD', '#faecd5', '#8c531b', 'Vecinos Mz. D', 'Grupo de manzana', 'Gladys: ¿Alguien sabe a qué hora pasa...', '19:12', 0, false),
('4', 'TL', '#fee4e2', '#912018', 'Teresa L.', 'Favor · Necesito escalera', 'Tú: Yo tengo una, te la presto el domin...', 'Ayer', 0, false),
('5', 'JV', '#1f2937', '#ffffff', 'Junta vecinal Los Pinos', 'Canal oficial', 'Reunión del sábado en el local comunal.', 'Lun', 0, true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO chat_mensajes (id, conversacion_id, texto, sender, hora, verificado)
VALUES
('m1', '1', 'Hola Rosa, buenas noches. Quería consultar por la fuga de agua en mi lavadero.', 'yo', '22:50', true),
('m2', '1', 'Hola vecino. Sí, claro, revisé tu mensaje.', 'otro', '23:02', true),
('m3', '1', 'Puedo pasar mañana a las 9, ¿le parece bien?', 'otro', '23:05', false),
('j1', '2', 'Hola Julio, ¿aún tienes disponible el taladro percutor?', 'yo', '21:30', true),
('j2', '2', '¡Hola! Sí, está listo con todas sus brocas.', 'otro', '21:35', true),
('j3', '2', 'Te lo dejo en la portería el sábado.', 'otro', '21:40', false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO directorio_local (id, initials, name, subtitle, distance, phone, bg, color, orden)
VALUES
('1', 'SZ', 'Serenazgo del sector', 'Atención vecinal · 24 h', '0.5 km', '014115555', '#faecd5', '#8c531b', 1),
('2', 'CM', 'Comisaría del sector', '1.2 km · Atención 24 h', '1.2 km', '012711000', '#faecd5', '#8c531b', 2),
('3', 'PS', 'Posta de salud', '800 m · Abierta hasta 20:00', '800 m', '014490000', '#fee4e2', '#912018', 3),
('4', 'FT', 'Farmacia de turno', '350 m · Abierta ahora', '350 m', '013142020', '#d6eee2', '#144e40', 4),
('5', 'CJ', 'Cerrajería 24 h · Ana Paredes', 'Servicio verificado', '400 m', '987654321', '#faecd5', '#8c531b', 5)
ON CONFLICT (id) DO NOTHING;

INSERT INTO tablero_emergencia (id, cuadrante, tipo_emergencia, descripcion, estado, confirmaciones, meta_confirmaciones, minutos_restantes, activo, personas_a_salvo)
VALUES ('tablero-1', 'Urb. Los Pinos, Surco', 'Lluvias intensas en Los Pinos', 'Activado hoy 06:40 · confirmado por 5 vecinos en 12 min', 'ACTIVO', 5, 5, 0, true, 126)
ON CONFLICT (id) DO NOTHING;

INSERT INTO tablero_necesidades (id, tablero_id, recurso, prioridad, actual, meta, unidad, cubierto, orden)
VALUES
('n1', 'tablero-1', 'Agua embotellada', 'Prioridad alta', 120, 300, 'L', false, 1),
('n2', 'tablero-1', 'Frazadas', 'Prioridad alta', 45, 80, '', false, 2),
('n3', 'tablero-1', 'Botiquín básico', 'Prioridad alta', 6, 20, '', false, 3),
('n4', 'tablero-1', 'Voluntarios para limpieza', 'Prioridad alta', 8, 15, 'personas', false, 4)
ON CONFLICT (id) DO NOTHING;

INSERT INTO tablero_aportes (id, tablero_id, descripcion, vecino, tiempo, entregado)
VALUES
('a1', 'tablero-1', '10 Bidones de agua mineral', 'Julio R. · Mz. A', 'Hace 15 min', false),
('a2', 'tablero-1', '4 Frazadas y ropa abrigadora', 'Rosa Q. · Mz. C', 'Hace 25 min', false),
('a3', 'tablero-1', 'Botiquín con gasas y alcohol', 'Lucía T. · Mz. B', 'Hace 40 min', false)
ON CONFLICT (id) DO NOTHING;
