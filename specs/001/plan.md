# Plan 001 — Identidad vecinal, cuadrantes, emergencias y perfil

Estado: Borrador
Fecha: 07/11/2026
Spec: `specs/001/spec.md` (Aprobada)
Constitución: `docs/constitution.md`

## 1. Resumen técnico

Backend monolítico modular en Java 21 + Spring Boot 4.x (Maven) sobre PostgreSQL 15 + PostGIS. Los controladores solo traducen HTTP/WebSocket; los servicios solo orquestan (llamar a la BD, llamar a integraciones externas, publicar avisos); **todas las reglas de negocio de la spec viven en funciones/procedimientos PL/pgSQL** en `db/sp/`. El esquema completo vive en `db/db.sql`. RENIEC, Twilio, FCM y geocodificación están detrás de interfaces con una implementación real y una simulada (perfil `dev` y pruebas).

Responsabilidades que la spec asigna a la aplicación móvil y no a este backend se marcan como **[CLIENTE]**; el backend entrega los datos y validaciones que esas pantallas necesitan.

## 2. Estructura de módulos

Paquete raíz `com.ayudartrazable.api`. Un paquete por módulo; dentro de cada uno: `Controlador`, `Servicio`, `Repositorio` (solo llama SP) y `dto/` con `record`. Nombres en español según la constitución (regla 6).

```
src/main/java/com/ayudartrazable/api/
├── comun/            Errores (ApiError, manejador global), seguridad (filtro de sesión, roles),
│                     parámetros, enmascarado de logs, configuración OpenAPI
├── identidad/        Alta en curso, validación de DNI, códigos de verificación, alta comunal,
│                     inicio de sesión, renovación y cierre de sesión, recuperación de cuenta
├── cuadrantes/       Resolver pin, guardar/cambiar domicilio, datos y contornos de cuadrantes
├── vecinos/          Perfil, red de vecinos, dispositivos, reporte de datos civiles, teléfonos de emergencia
├── emergencias/      Pánico, reportes preventivos, alertas activas, cambio de estado
├── administracion/   Coordinadores, cuentas de serenazgo, suspensiones, avisos al administrador,
│                     habilitar/deshabilitar cuadrantes
├── notificaciones/   Difusión en tiempo real (WebSocket) y envío push; decide canal según destinatarios
├── integraciones/
│   ├── registrocivil/   Interfaz ConsultaRegistroCivil + adaptador RENIEC + simulador
│   ├── mensajeria/      Interfaz EnvioCodigo (SMS/WhatsApp) + adaptador Twilio + simulador
│   ├── geocodificacion/ Interfaz Geocodificador + adaptador de mapas + simulador
│   └── push/            Interfaz EnvioPush + adaptador FCM + simulador
└── AyudarTrazableApplication.java

db/
├── db.sql            Extensiones, esquema, tablas, índices, roles de BD y permisos
├── sp/               Un archivo por función/procedimiento (CREATE OR REPLACE)
├── seed.sql          Parámetros, cuadrantes y teléfonos de Lima, administrador inicial
└── seed_dev.sql      Solo desarrollo y pruebas: DNIs de prueba, código fijo 1234

src/test/java/...     Mismo árbol de paquetes (ver sección 9)
```

| Módulo | RF que cubre |
|---|---|
| identidad | RF-01, RF-02, RF-03, RF-05, RF-06, RF-07, RF-19 |
| cuadrantes | RF-04, RF-08, RF-09 |
| vecinos | RF-10, RF-14, RF-18, RF-20 |
| emergencias | RF-11, RF-12, RF-13 (consulta), RF-15, RF-17 (conteo) |
| administracion | RF-16, RF-17 (levantar suspensión), RF-21 |
| notificaciones | RF-11, RF-12, RF-13 (difusión), RF-14 (invalidar dispositivo) |
| comun | RNF-02, RNF-04, RNF-05, RNF-06, RNF-08 transversales |

## 3. Decisiones técnicas

| # | Decisión | Justificación | Alternativa descartada y por qué |
|---|---|---|---|
| D-01 | Acceso a datos con `JdbcTemplate`/`SimpleJdbcCall` llamando funciones PL/pgSQL | La constitución exige reglas en SP; JDBC no añade capa de mapeo que tiente a poner lógica en Java | Spring Data JPA + Hibernate Spatial: duplica el modelo en Java y empuja la lógica a entidades, contra la regla 3 |
| D-02 | PostGIS solo dentro de la BD; Java recibe/entrega GeoJSON como texto y lat/lng como números | Evita dependencias geoespaciales en Java (constitución regla 1) y concentra el cálculo de cuadrantes en SP | `hibernate-spatial` / JTS en Java: dependencia extra sin necesidad |
| D-03 | Esquema versionado en `db/db.sql` + `db/sp/` aplicado con `psql`; pruebas lo recrean desde esos archivos | Regla 5: el esquema vive en `db/db.sql` para migrar a otro servidor | Flyway/Liquibase: dependencia nueva y segunda fuente de verdad del esquema |
| D-04 | La app se conecta con un rol de BD `app_ayudar` que solo tiene `EXECUTE` sobre funciones, sin `SELECT/INSERT/UPDATE/DELETE` directo sobre tablas; nadie tiene `DELETE` | Hace cumplir en la propia BD la privacidad (RNF-02), el acceso solo por SP (regla 5) y el borrado lógico (RNF-07) | Row Level Security de Supabase: requiere propagar la identidad del vecino a cada conexión; con rol de servicio se salta |
| D-05 | Sesión = token de acceso JWT corto (15 min) + token de renovación opaco, rotativo, guardado con hash en tabla `sesiones` sin fecha de expiración | RF-07: sin expiración por tiempo, renovación transparente y revocación por dispositivo | JWT largo sin estado: no se puede revocar al cerrar sesión, desactivar o recuperar cuenta |
| D-06 | En cada solicitud el filtro de seguridad valida la sesión con `fn_validar_sesion` (sesión activa, cuenta activa, roles vigentes) | RF-07, RF-16 y RNF-04 piden efecto "en la siguiente solicitud"; el JWT solo identifica la sesión | Confiar en los roles del JWT: un rol retirado seguiría vigente hasta 15 min |
| D-07 | JWT con `spring-boot-starter-oauth2-resource-server` (firma HS256 con secreto en variable de entorno) | Viene en el ecosistema Spring, valida firma y expiración sin código propio | `jjwt`: librería adicional para lo mismo |
| D-08 | Códigos de verificación generados, guardados con hash y validados dentro de la BD (`pgcrypto`) | Vigencia, reenvío, intentos y canal son reglas de negocio (RF-03) → SP. `pgcrypto` es extensión de PostgreSQL, no dependencia Java | Generar y validar en Java con caché en memoria: lógica fuera de SP y se pierde al reiniciar |
| D-09 | Lectura OCR del DNI en el celular (ML Kit) **[CLIENTE]**; el backend recibe solo el número y el origen (`ESCANEO`/`MANUAL`) | No se reciben ni almacenan imágenes del DNI (RNF-03, RNF-05); RF-02 exige el mismo trato para ambos orígenes | OCR en el servidor con la imagen: almacenar/transferir imágenes de documentos sin necesidad y añadir un motor OCR |
| D-10 | Tiempo real con `spring-boot-starter-websocket` (STOMP, broker simple en memoria). Tópico `/topic/cuadrante/{id}` para vecinos y cola `/user/queue/alertas` para coordinadores, serenazgo y administrador | RNF-01 (< 3 s); el interceptor de suscripción valida con SP que el usuario pertenezca al cuadrante (RF-13) | SSE: no permite autorización por suscripción tan simple; consulta periódica: no cumple RNF-01; Supabase Realtime: acopla a un proveedor |
| D-11 | Push con la API HTTP v1 de FCM vía `RestClient`, autenticando con `google-auth-library-oauth2-http`; envío asíncrono tras confirmar la transacción | RF-13 (app cerrada) y RF-14; la librería de autenticación es pequeña y el adaptador se prueba con `MockRestServiceServer` | `firebase-admin`: arrastra muchas dependencias (Firestore, Storage) y sus excepciones no se pueden simular en pruebas |
| D-12 | Twilio, RENIEC y geocodificación vía `RestClient` de Spring (sin SDK) | Evita tres SDK; los adaptadores son pocas llamadas HTTP | SDK oficial de Twilio: dependencia pesada para dos llamadas |
| D-13 | El SP decide los destinatarios de cada alerta y los devuelve; Java solo difunde | Quién recibe qué (RF-11, RF-12, CL-08) es regla de negocio | Calcular destinatarios en el servicio Java: lógica fuera de SP |
| D-14 | Errores de negocio: los SP lanzan excepciones con SQLSTATE propio (clase `AT`) y mensaje en español; el manejador global los traduce a `ApiError` y al código HTTP | Contrato de errores de la constitución; un único lugar de traducción | Devolver códigos de resultado en cada SP: cada servicio tendría que interpretarlos |
| D-15 | Parámetros (vigencias, límites, umbrales, edad mínima) en tabla `parametros`, leídos por los SP | RNF-06: se cambian sin desplegar | Propiedades en `application.yml`: requieren reinicio y quedan fuera de los SP |
| D-16 | Documentación con `springdoc-openapi` | La constitución exige catálogo OpenAPI/Swagger | Documentar a mano: se desactualiza |
| D-17 | Pruebas contra PostgreSQL real `ayudar_trazable_pruebas` (local, con PostGIS) | La lógica está en SP: solo se prueba ejecutándolos; AGENTS.md fija esa base | Testcontainers: requiere Docker (no instalado) y dependencia extra; H2: no ejecuta PL/pgSQL ni PostGIS |
| D-18 | Enmascarado de datos sensibles en logs (DNI, celular, códigos, tokens, coordenadas) mediante un convertidor de Logback y DTOs con `toString` seguro; sin registro de cuerpos de solicitud | RNF-05 | Confiar en la disciplina de cada desarrollador |
| D-19 | Puerto 3001 y rutas bajo `/api/v1/` | Contrato ya usado por el prototipo del front | Puerto 8080: rompe el cliente existente |

### Dependencias del `pom.xml` (todas justificadas arriba)
`spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server` (D-07), `spring-boot-starter-validation`, `spring-boot-starter-jdbc` (D-01), `spring-boot-starter-websocket` (D-10), `postgresql` (driver, ámbito compile para leer el SQLSTATE y DETAIL de los SP en D-14), `google-auth-library-oauth2-http` (D-11), `springdoc-openapi-starter-webmvc-ui` (D-16). Pruebas: `spring-boot-starter-webmvc-test`, `spring-boot-starter-security-test`, `spring-boot-starter-jdbc-test`, `spring-boot-starter-websocket-test`.
Con Spring Boot 4 los starters se llaman `spring-boot-starter-webmvc` y `spring-boot-starter-security-oauth2-resource-server`.
Descartadas de la propuesta inicial: `spring-boot-starter-data-jpa`, `hibernate-spatial` (D-01, D-02) y `firebase-admin` (D-11).

## 4. Modelo de datos (`db/db.sql`)

Todas las tablas con `activo boolean`, `creado_en`, `actualizado_en`; ninguna admite borrado físico.

| Tabla | Contenido | RF |
|---|---|---|
| `parametros` | clave, valor, descripción | RNF-06 |
| `distritos` | nombre, teléfonos de serenazgo/comisaría/bomberos | RF-18 |
| `telefonos_nacionales` | centrales nacionales de emergencia | RF-18 |
| `cuadrantes` | código, nombre, distrito, polígono (4326), habilitado | RF-04, RF-09, RF-21 |
| `altas_en_curso` | token, datos civiles validados, origen, celular verificado, domicilio provisional, vence_en (24 h) | RF-01..RF-05, CL-02 |
| `usuarios` | DNI (único), nombres, apellidos, fecha de nacimiento, sexo, celular (único), tipo (VECINO/SERENAZGO/ADMIN), acepta_tratamiento_en | RF-05, RF-16, RNF-03 |
| `domicilios` | usuario, punto (4326), dirección, referencia de cuadra, cuadrante, vigente, registrado_en | RF-04, RF-08, RF-21 |
| `codigos_verificacion` | celular, propósito (ALTA/INGRESO/RECUPERACION), canal, hash, intentos, enviado_en, vence_en, usado | RF-03, RF-06, RF-19 |
| `sesiones` | usuario, hash del token de renovación, dispositivo, creada_en, revocada_en, motivo | RF-07, RF-19 |
| `dispositivos` | sesión, identificador push, válido | RF-14 |
| `roles_coordinador` | usuario, cuadrante, otorgado_por, retirado_en | RF-16 |
| `cuentas_serenazgo` | usuario, distrito | RF-16 |
| `alertas` | tipo, emisor, cuadrante, punto, ubicación aproximada, referencia de cuadra, descripción, estado | RF-11, RF-12, RF-13 |
| `alertas_historial` | alerta, estado anterior/nuevo, autor (usuario o "sistema"), fecha | RF-15, RF-21 |
| `suspensiones_panico` | usuario, motivo, desde, levantada_por, levantada_en | RF-17 |
| `reportes_datos_civiles` | usuario, descripción, atendido | RF-20 |
| `avisos_administrador` | tipo (SUSPENSION/RECUPERACION/DATOS_CIVILES), referencia, leído | RF-17, RF-19, RF-20 |

Índices: GiST en `cuadrantes.poligono` y `domicilios.punto`; únicos sobre `usuarios.dni` y `usuarios.celular` (CL-03); índice parcial de alerta de pánico activa por emisor (RF-11, evita duplicados por concurrencia).

Roles de BD (D-04): `app_ayudar` con solo `EXECUTE` sobre funciones `SECURITY DEFINER`.

## 5. Funciones y procedimientos (`db/sp/`)

| Función / procedimiento | Regla que implementa | RF |
|---|---|---|
| `fn_parametro` | Lectura de parámetros con valor por defecto seguro | RNF-06 |
| `sp_alta_iniciar` | Crea alta en curso con los datos civiles devueltos por el registro civil; valida 8 dígitos, edad mínima, DNI no registrado | RF-01, RF-02, RF-05, CL-14 |
| `sp_codigo_emitir` | Genera código de 4 dígitos, controla reenvío (30 s), canal, vigencia (5 min); devuelve el código en claro solo para enviarlo | RF-03, RF-06, CL-13 |
| `sp_codigo_validar` | Valida vigencia, intentos (5), marca celular verificado | RF-03 |
| `fn_resolver_punto` | Cuadrante habilitado que contiene el punto; desempate determinista por código | RF-04, CL-05 |
| `sp_alta_guardar_domicilio` | Guarda domicilio provisional en el alta en curso; rechaza fuera de cobertura | RF-04 |
| `sp_alta_completar` | Exige DNI + celular + domicilio verificados y aceptación de tratamiento; crea usuario, domicilio y sesión | RF-05, CL-03 |
| `sp_ingreso_solicitar` | Busca usuario activo por DNI; si no existe responde igual (genérico); devuelve celular enmascarado | RF-06 |
| `sp_sesion_crear` / `sp_sesion_renovar` / `sp_sesion_cerrar` / `fn_validar_sesion` | Crear, rotar, revocar y validar sesiones; cerrar invalida dispositivos de esa sesión | RF-07, RF-14, CL-12 |
| `sp_recuperacion_completar` | Revalida DNI, reemplaza celular, revoca todas las sesiones y dispositivos, crea aviso al administrador | RF-19, CL-04, CL-15 |
| `sp_domicilio_cambiar` | Plazo de 30 días, sin pánico activo, dentro de cobertura; reasigna cuadrante | RF-08, CL-11 |
| `fn_cuadrante_propio` / `fn_cuadrantes_cercanos` | Datos y polígono GeoJSON del propio; contorno y nombre de cercanos | RF-09 |
| `fn_vecinos_cuadrante` | Solo verificados y activos, nombre abreviado, referencia de cuadra, sin DNI/edad/celular/coordenadas | RF-10 |
| `sp_panico_emitir` | Exige confirmación, verifica suspensión y pánico activo, ubica cuadrante actual o usa domicilio (aproximada), devuelve alerta + destinatarios | RF-11, RF-17, CL-07, CL-08 |
| `sp_reporte_emitir` | Tipo obligatorio, límite 5/24 h, devuelve alerta + destinatarios | RF-12 |
| `fn_alertas_activas` | Activas del cuadrante del solicitante, más recientes primero | RF-13 |
| `fn_puede_suscribir` | Autoriza suscripción al tópico del cuadrante | RF-13 |
| `sp_alerta_cambiar_estado` | Verifica rol para el cuadrante, impide gestionar la propia alerta, registra historial, cuenta falsas alarmas y suspende al llegar al umbral | RF-15, RF-17, CL-10 |
| `sp_dispositivo_registrar` / `sp_dispositivo_invalidar` | Alta/actualización e invalidación de identificador push | RF-14 |
| `sp_coordinador_asignar` / `sp_coordinador_retirar` / `sp_serenazgo_alta` / `sp_serenazgo_desactivar` | Gestión de roles | RF-16, CL-16 |
| `sp_suspension_levantar` | Rehabilita el botón de pánico | RF-17 |
| `fn_perfil` / `fn_telefonos_emergencia` | Perfil con insignia según sexo; teléfonos del distrito o nacionales | RF-18 |
| `sp_reporte_datos_civiles` | Registra reporte y aviso al administrador | RF-20 |
| `sp_cuadrante_deshabilitar` / `sp_cuadrante_habilitar` | Cierra alertas como RESUELTA por "sistema", reasigna o deja sin cuadrante; al habilitar reasigna y devuelve vecinos a notificar | RF-21 |
| `fn_avisos_administrador` | Lista de avisos pendientes | RF-17, RF-19, RF-20 |

## 6. API REST (`/api/v1`)

| Método | Ruta | Acceso | RF |
|---|---|---|---|
| POST | `/auth/dni` | Público | RF-01, RF-02 |
| GET | `/auth/tramite` (cabecera `X-Tramite`) | Trámite en curso | RF-05, CL-02 |
| POST | `/auth/codigo` (cabecera `X-Tramite`) | Trámite de alta o recuperación | RF-03, RF-19 |
| POST | `/auth/codigo/verificar` (cabecera `X-Tramite`) | Trámite de alta o recuperación | RF-03, RF-19 |
| GET | `/geo/resolver-pin?lat&lng` | Trámite en curso o sesión | RF-04 |
| POST | `/auth/alta/domicilio` (cabecera `X-Tramite`) | Trámite de alta | RF-04 |
| POST | `/auth/alta` (cabecera `X-Tramite`) | Trámite de alta | RF-05 |
| POST | `/auth/ingreso` | Público | RF-06 |
| POST | `/auth/ingreso/verificar` | Público | RF-06 |
| POST | `/auth/renovar` | Token de renovación | RF-07 |
| POST | `/auth/salir` | Autenticado | RF-07 |
| POST | `/auth/recuperacion/dni` | Público | RF-19 |
| POST | `/auth/recuperacion/completar` (cabecera `X-Tramite`) | Trámite de recuperación | RF-19 |
| PUT | `/vecinos/me/domicilio` | Vecino | RF-08 |
| GET | `/geo/cuadrantes/mio` | Vecino | RF-09 |
| GET | `/geo/cuadrantes/cercanos` | Vecino | RF-09 |
| GET | `/geo/cuadrantes/mio/vecinos` | Vecino | RF-10 |
| POST | `/emergencias/panico` | Vecino | RF-11, RF-17 |
| POST | `/emergencias/reportes` | Vecino | RF-12 |
| GET | `/emergencias/activas` | Vecino / coordinador / serenazgo | RF-13 |
| PATCH | `/emergencias/{alertaId}/estado` | Coordinador / serenazgo (regla en filtro y en SP) | RF-15, RF-17 |
| PUT | `/vecinos/me/dispositivo` | Autenticado | RF-14 |
| GET | `/vecinos/me` | Autenticado | RF-18 |
| GET | `/vecinos/telefonos-emergencia` | Autenticado | RF-18 |
| POST | `/vecinos/me/reporte-datos` | Vecino | RF-20 |
| GET | `/admin/usuarios?dni=` | Administrador | RF-16 |
| POST | `/admin/coordinadores` · `/admin/coordinadores/{usuarioId}/retirar` | Administrador | RF-16 |
| POST | `/admin/serenazgo` · `/admin/serenazgo/{usuarioId}/desactivar` | Administrador | RF-16, CL-16 |
| GET | `/admin/avisos` | Administrador | RF-17, RF-19, RF-20 |
| POST | `/admin/suspensiones/{suspensionId}/levantar` | Administrador | RF-17 |
| POST | `/admin/cuadrantes/{cuadranteId}/deshabilitar` · `/habilitar` | Administrador | RF-21 |
| WS | `/ws/emergencias` (STOMP; `/topic/cuadrante/{id}`, `/user/queue/alertas`) | Autenticado en CONNECT | RF-11, RF-12, RF-13, RF-15 |

Eventos WebSocket: `{evento, alerta}` con `evento` = `ALERTA_CREADA`, `ALERTA_ACTUALIZADA` o `CUADRANTE_DESHABILITADO`. Solo la cola de serenazgo recibe `latitud`/`longitud`.

Formato de error: `ApiError {codigo, mensaje, campos[]}` (constitución). Validaciones de forma (8 dígitos DNI, 9 dígitos celular, tipo de reporte obligatorio) con Bean Validation antes de llamar a la BD (RF-02, RF-03, RF-12).

## 7. Flujos clave

1. **Alta (RF-01..RF-05):** `/auth/dni` → servicio consulta `ConsultaRegistroCivil` → `sp_alta_iniciar` devuelve token de alta en curso (24 h, CL-02) y datos autollenados no editables → `/auth/codigo` (canal elegido) → `/auth/codigo/verificar` → `/geo/resolver-pin` (SP + `Geocodificador` para la dirección) → `/auth/alta/domicilio` → `/auth/alta` crea usuario y sesión. Si el registro civil no responde: error 503 con mensaje de CL-01 y el alta en curso conserva lo ya verificado (RNF-10).
2. **Ingreso (RF-06, RF-07):** `/auth/ingreso` con DNI → respuesta genérica + celular enmascarado → código → sesión (acceso 15 min + renovación sin vencimiento).
3. **Pánico (RF-11, RF-13, RF-17):** `/emergencias/panico` con `confirmado=true` y ubicación → `sp_panico_emitir` (transacción) → tras confirmar: difusión STOMP al tópico del cuadrante y a colas de coordinadores/serenazgo (síncrona, RNF-01) → push FCM asíncrono a dispositivos válidos; dispositivo inválido → `sp_dispositivo_invalidar` (RF-14).
4. **Cambio de estado (RF-15, RF-17):** `sp_alerta_cambiar_estado` → difusión del nuevo estado → si suspende, aviso al administrador.
5. **Deshabilitar cuadrante (RF-21):** endpoint de administrador → SP → difusión del cierre de alertas y notificación a vecinos reasignados o sin cobertura.

## 8. Responsabilidades del cliente móvil **[CLIENTE]**

No las implementa este backend; se listan para trazabilidad:
- RF-01: OCR con ML Kit y pantalla "No pudimos leer tu DNI" con consejos.
- RF-04: texto "Solo vecinos verificados de tu cuadrante verán tu zona".
- RF-05: botón "Registrar" deshabilitado mientras falte algo (el backend además lo rechaza).
- RF-11: diálogo de confirmación (el backend exige `confirmado=true`) y aviso sin conexión con teléfonos guardados localmente.
- RNF-09: accesibilidad visual.

## 9. Estrategia de tests

Ejecución: `./mvnw test`, debe pasar al 100% (constitución regla 4). Base exclusiva `ayudar_trazable_pruebas`; antes de la suite se recrea desde `db/db.sql` + `db/sp/` + `db/seed.sql` + `db/seed_dev.sql`, y entre pruebas se truncan tablas. Nunca apunta a `ayudar_trazable`.

| Nivel | Qué prueba | Herramienta | Cubre |
|---|---|---|---|
| 1. Reglas en BD | Cada criterio EARS implementado en un SP: casos válidos, límites y errores (código SQLSTATE y mensaje en español) | JUnit 5 + `JdbcTemplate` contra la BD de pruebas | RF-01..RF-21 (reglas), CL-03, CL-05, CL-07, CL-08, CL-10, CL-11, CL-14, CL-15 |
| 2. API | Contrato HTTP, validación de forma, `ApiError`, autorización por rol/cuadrante/verificación, llamada directa a recursos ajenos | `@SpringBootTest` + MockMvc + `spring-security-test`, integraciones simuladas | RNF-02, RNF-04, RNF-08, RF-06 (mensaje genérico), RF-07, RF-09, RF-10, RF-16 |
| 3. Tiempo real | Suscripción permitida/denegada por cuadrante, recepción < 3 s, cambio de estado difundido | Cliente STOMP de Spring sobre servidor en puerto aleatorio | RF-11, RF-12, RF-13, RF-15, RNF-01 |
| 4. Adaptadores | Peticiones y respuestas de RENIEC, Twilio, mapas y FCM; servicio caído → 503 | `MockRestServiceServer` (spring-test) y doble de FCM | RF-01, RF-03, RF-04, RF-14, RNF-10, CL-01 |
| 5. Datos sensibles | Ningún DNI, código, token ni coordenada en logs ni errores | `OutputCaptureExtension` sobre flujos completos | RNF-05 |
| 6. Punta a punta | Escenario de criterios de finalización: alta → red → pánico → recepción por otro vecino (WS + push simulado) → vecino de otro cuadrante no la recibe → coordinador cambia estado → 3 falsas alarmas suspenden | `@SpringBootTest` puerto aleatorio | Criterios de finalización de la spec |
| 7. Concurrencia | Dos altas simultáneas con el mismo DNI; dos pánicos simultáneos del mismo vecino | Hilos paralelos contra la BD de pruebas | CL-03, RF-11 |

Cada prueba se nombra con el RF y el criterio que verifica (p. ej. `RF03_reenvioAntesDe30Segundos_rechazaConSegundosRestantes`) para mantener la matriz de trazabilidad.

## 10. Orden de implementación

1. Proyecto Maven, `comun/` (errores, seguridad base, enmascarado de logs, OpenAPI), `db/db.sql` base, `parametros`, BD de pruebas automatizada.
2. Integraciones con simuladores (registro civil, mensajería, geocodificación, push).
3. Identidad: alta en curso, códigos, alta, ingreso, sesiones (RF-01..RF-07).
4. Cuadrantes: resolver pin, domicilio, cambio, consultas (RF-04, RF-08, RF-09).
5. Vecinos: red, perfil, dispositivos, teléfonos, reporte de datos (RF-10, RF-14, RF-18, RF-20).
6. Emergencias + notificaciones: pánico, reportes, activas, WebSocket, push (RF-11..RF-13).
7. Administración y estados: roles, cambio de estado, suspensiones, cuadrantes (RF-15..RF-17, RF-21).
8. Recuperación de cuenta (RF-19).
9. Prueba punta a punta y revisión de RNF.

## 11. Matriz de cobertura

| RF | Módulo | SP principal | Endpoint / canal | Tests |
|---|---|---|---|---|
| RF-01 | identidad + registrocivil | `sp_alta_iniciar` | POST `/auth/dni` | 1, 2, 4 |
| RF-02 | identidad | `sp_alta_iniciar` | POST `/auth/dni` (origen MANUAL) | 1, 2 |
| RF-03 | identidad + mensajeria | `sp_codigo_emitir`, `sp_codigo_validar` | POST `/auth/codigo`, `/auth/codigo/verificar` | 1, 2, 4 |
| RF-04 | cuadrantes + geocodificacion | `fn_resolver_punto`, `sp_alta_guardar_domicilio` | GET `/geo/resolver-pin`, POST `/auth/alta/domicilio` | 1, 2, 4 |
| RF-05 | identidad | `sp_alta_completar` | POST `/auth/alta` | 1, 2, 7 |
| RF-06 | identidad | `sp_ingreso_solicitar` | POST `/auth/ingreso` | 1, 2 |
| RF-07 | identidad + comun | `sp_sesion_*`, `fn_validar_sesion` | POST `/auth/renovar`, `/auth/salir`, filtro | 1, 2 |
| RF-08 | cuadrantes | `sp_domicilio_cambiar` | PUT `/vecinos/me/domicilio` | 1, 2 |
| RF-09 | cuadrantes | `fn_cuadrante_propio`, `fn_cuadrantes_cercanos` | GET `/geo/cuadrantes/*` | 1, 2 |
| RF-10 | vecinos | `fn_vecinos_cuadrante` | GET `/geo/cuadrantes/mio/vecinos` | 1, 2 |
| RF-11 | emergencias + notificaciones | `sp_panico_emitir` | POST `/emergencias/panico`, WS, push | 1, 3, 6, 7 |
| RF-12 | emergencias + notificaciones | `sp_reporte_emitir` | POST `/emergencias/reportes`, WS, push | 1, 2, 3 |
| RF-13 | emergencias + notificaciones | `fn_alertas_activas`, `fn_puede_suscribir` | GET `/emergencias/activas`, WS | 1, 2, 3 |
| RF-14 | vecinos + push | `sp_dispositivo_*` | PUT `/vecinos/me/dispositivo` | 1, 4 |
| RF-15 | emergencias | `sp_alerta_cambiar_estado` | PATCH `/emergencias/{id}/estado`, WS | 1, 2, 3 |
| RF-16 | administracion | `sp_coordinador_*`, `sp_serenazgo_*` | `/admin/coordinadores`, `/admin/serenazgo` | 1, 2 |
| RF-17 | emergencias + administracion | `sp_alerta_cambiar_estado`, `sp_suspension_levantar` | PATCH estado, POST `/admin/suspensiones/{id}/levantar` | 1, 2, 6 |
| RF-18 | vecinos | `fn_perfil`, `fn_telefonos_emergencia` | GET `/vecinos/me`, `/vecinos/telefonos-emergencia` | 1, 2 |
| RF-19 | identidad | `sp_recuperacion_completar` | POST `/auth/recuperacion` | 1, 2 |
| RF-20 | vecinos | `sp_reporte_datos_civiles` | POST `/vecinos/me/reporte-datos` | 1, 2 |
| RF-21 | administracion + notificaciones | `sp_cuadrante_deshabilitar`, `sp_cuadrante_habilitar` | POST `/admin/cuadrantes/{id}/*` | 1, 2, 3 |

## 12. Riesgos y pendientes técnicos

- **Acceso a RENIEC:** no hay API pública directa; se requiere convenio o proveedor autorizado y su contrato (campos, fecha de nacimiento, costos). Mientras tanto se usa el simulador. Sin esto RF-01/RF-02 no pueden validarse en producción.
- **Credenciales externas:** Twilio (SMS y WhatsApp aprobado), proyecto Firebase y proveedor de geocodificación; todas por variables de entorno.
- **PostGIS local:** debe estar instalado en PostgreSQL 15 antes de crear `ayudar_trazable` y `ayudar_trazable_pruebas`.
- **Broker en memoria (D-10):** válido para una sola instancia; escalar a varias requerirá un broker externo en una spec futura.
- **Justificación de dependencias:** la constitución pide justificarlas "en la spec"; aquí se justifican en el plan (sección 3). Confirmar que es suficiente.

## 13. Desviaciones registradas durante la implementación

- Spring Boot 4.1.2 en lugar de 3.x (la línea 3.5 quedó sin soporte gratuito); constitución actualizada.
- FCM por API HTTP v1 + `google-auth-library-oauth2-http` en lugar de `firebase-admin` (D-11).
- Cuadrantes de ejemplo, teléfonos distritales y administrador inicial están en `db/seed_dev.sql`, no en `db/seed.sql`: los polígonos y números reales de producción no se conocen. En producción el administrador inicial se crea ejecutando `sp_admin_inicial` como `postgres` (la app no puede ejecutarlo).
- Alta y recuperación comparten el mismo trámite (`altas_en_curso.proposito`) y la cabecera `X-Tramite`.
- Los SP que verifican códigos (`sp_tramite_verificar_codigo`, `sp_ingreso_verificar`) devuelven `{error:{...}}` en vez de lanzar excepción, para no perder el contador al hacer rollback.
- El pánico de un vecino con el botón suspendido responde 403 con el motivo; los teléfonos se obtienen con `GET /vecinos/telefonos-emergencia`.
- Referencia de cuadra del pánico: si el vecino está a menos de 150 m (parametrizable) de su casa se usa la de su domicilio; si no, el nombre del cuadrante, para no depender de geocodificación en la ruta crítica (RNF-01).

## 14. Correcciones tras la auditoría independiente (07/11/2026)

- La recuperación de cuenta solo aplica a usuarios `VECINO`; antes permitía tomar la cuenta del administrador o del serenazgo conociendo su DNI.
- Las suscripciones WebSocket se revalidan en cada mensaje saliente (`InterceptorSalidaStomp`): sesión vigente y permiso sobre el cuadrante. Cerrar sesión, recuperar la cuenta, mudarse o perder un rol corta la recepción sin reconectar.
- `fn_referencia_cuadra` nunca devuelve números de puerta: si la dirección no es "vía + número" (rangos, pisos, manzana-lote) usa el nombre del cuadrante.
- `sp_codigo_emitir` usa un bloqueo consultivo por celular y propósito, y limita a 5 códigos por hora (`codigos_maximos_hora`).
- La app no arranca fuera de los perfiles `dev`/`test` si la base tiene activo `codigo_fijo`.
- Reportes, pánico y cambio de domicilio bloquean la fila del usuario para que los límites no se superen con solicitudes simultáneas.
- La alerta con ubicación (serenazgo) omite coordenadas cuando la ubicación es aproximada, es decir, el domicilio.
- Las respuestas de administración ya no incluyen tokens push; los vecinos reasignados al deshabilitar un cuadrante reciben un aviso.
- Iniciar un trámite ya no anula los trámites en curso de la misma persona.

## 15. Registro civil: Decolecta (07/11/2026)

- Proveedor: `GET https://api.decolecta.com/v1/reniec/dni?numero={dni}` con `Authorization: Bearer <token>`. URL y token por variables de entorno `RENIEC_URL` y `RENIEC_TOKEN`; nunca en el repositorio.
- Respuesta documentada: `first_name`, `first_last_name`, `second_last_name`, `full_name`, `document_number`. DNI inexistente: 404 `{"message":"not found"}`; token inválido o cuota agotada: 401 (se trata como servicio no disponible).
- **Limitación:** no devuelve fecha de nacimiento ni sexo. Decisión: los aporta el cliente (OCR del DNI o ingreso manual) en `POST /auth/dni` y `POST /auth/recuperacion/dni`; si el proveedor los trae, prevalecen. Riesgo aceptado: un cliente modificado podría declarar otra edad.
- `integraciones.registro-civil.modo` permite usar RENIEC real aunque el resto de integraciones esté simulado; en `dev` es `real` salvo `RENIEC_MODO=simulado`.
- Plan gratuito: 1 000 consultas al mes.
