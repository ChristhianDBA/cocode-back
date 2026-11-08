# Tareas 001 — Identidad vecinal, cuadrantes, emergencias y perfil

Spec: `specs/001/spec.md` (Aprobada) · Plan: `specs/001/plan.md`

Reglas para todas las tareas:
- Cada tarea dura como máximo 20-30 minutos y se hace en el orden indicado; una tarea solo empieza cuando sus anteriores están marcadas.
- Toda regla de negocio va en un SP de `db/sp/` y toda tabla en `db/db.sql`.
- Una tarea solo se marca si `./mvnw test` pasa al 100% contra `ayudar_trazable_pruebas`.
- Los nombres de los tests empiezan por el RF que verifican (ej. `RF03_...`).

---

## Fase 0 — Entorno

- [x] **T001** Crear las bases `ayudar_trazable` y `ayudar_trazable_pruebas` con las extensiones `postgis` y `pgcrypto`.
  RF: — (requisito del plan)
  Hecho cuando: `SELECT postgis_version()` y `SELECT gen_random_uuid()` responden en ambas bases.

- [x] **T002** Generar el proyecto Maven (Java 21, Spring Boot 4.x) con el wrapper `mvnw` y solo las dependencias de la sección 3 del plan; puerto 3001.
  RF: — (D-01, D-07, D-10, D-11, D-16, D-19)
  Hecho cuando: `./mvnw -q dependency:tree` muestra solo las dependencias aprobadas y `./mvnw package -DskipTests` compila.

- [x] **T003** Configurar perfiles `dev` y `test` con conexión por variables de entorno (`ayudar_trazable` y `ayudar_trazable_pruebas`).
  RF: —
  Hecho cuando: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` arranca en el puerto 3001 conectado a `ayudar_trazable`, y el repositorio no contiene ninguna contraseña.

## Fase 1 — Esquema y base común

- [x] **T004** `db/db.sql`: extensiones, rol `app_ayudar` sin permisos sobre tablas, tabla `parametros`.
  RF: RNF-06, RNF-07
  Hecho cuando: el archivo se ejecuta dos veces seguidas sin error y `app_ayudar` recibe "permiso denegado" al hacer `SELECT` sobre `parametros`.

- [x] **T005** Tablas `distritos`, `telefonos_nacionales` y `cuadrantes` (polígono 4326, índice GiST, `habilitado`).
  RF: RF-04, RF-09, RF-18, RF-21
  Hecho cuando: `db/db.sql` crea las tres tablas y `\d cuadrantes` muestra el índice GiST.

- [x] **T006** Tablas `usuarios`, `domicilios` y `altas_en_curso` con únicos de DNI y celular.
  RF: RF-01, RF-04, RF-05, CL-03
  Hecho cuando: insertar dos usuarios con el mismo DNI falla por la restricción única.

- [x] **T007** Tablas `codigos_verificacion`, `sesiones` y `dispositivos`.
  RF: RF-03, RF-07, RF-14
  Hecho cuando: `db/db.sql` crea las tablas con sus claves foráneas y se ejecuta sin error desde cero.

- [x] **T008** Tablas `alertas`, `alertas_historial`, `roles_coordinador`, `cuentas_serenazgo`, `suspensiones_panico`, `reportes_datos_civiles`, `avisos_administrador` e índice parcial de pánico activo por emisor.
  RF: RF-11, RF-12, RF-15, RF-16, RF-17, RF-20
  Hecho cuando: insertar dos alertas de pánico activas del mismo emisor falla por el índice parcial.

- [x] **T009** `fn_parametro` y carga de parámetros en `db/seed.sql` (vigencia del código, reenvío, intentos, edad mínima, plazo de domicilio, límite de reportes, umbral de falsas alarmas, vigencia del alta en curso).
  RF: RF-03, RF-05, RF-08, RF-12, RF-17, CL-02, RNF-06
  Hecho cuando: un test lee cada parámetro y otro comprueba que una clave ausente devuelve el valor por defecto seguro.

- [x] **T010** Infraestructura de pruebas: recrear `ayudar_trazable_pruebas` desde `db/db.sql` + `db/sp/` + `db/seed.sql` + `db/seed_dev.sql` antes de la suite, truncar tablas entre pruebas y abortar si la URL apunta a `ayudar_trazable`.
  RF: RNF-11
  Hecho cuando: un test vacío pasa, y configurar la URL de `ayudar_trazable` hace fallar la suite antes de tocar datos.

- [x] **T011** `ApiError` y manejador global: errores de validación → 400 con `campos[]`; SQLSTATE de clase `AT` → código HTTP y mensaje en español del SP; error inesperado → 500 genérico.
  RF: RNF-08
  Hecho cuando: tests de los tres casos devuelven el JSON `{codigo, mensaje, campos[]}` sin texto técnico.

- [x] **T012** Enmascarado de DNI, celular, códigos, tokens y coordenadas en logs; sin registrar cuerpos de solicitud.
  RF: RNF-05
  Hecho cuando: un test con `OutputCaptureExtension` registra un DTO con esos datos y la salida solo contiene valores enmascarados.

- [x] **T013** Seguridad base: rutas públicas definidas en el plan; el resto exige JWT HS256 con secreto por variable de entorno.
  RF: RNF-04
  Hecho cuando: un test llama a una ruta protegida sin token y recibe 401 con `ApiError`.

- [x] **T014** OpenAPI con springdoc, incluyendo el esquema `ApiError`.
  RF: — (contrato de API de la constitución)
  Hecho cuando: `GET /v3/api-docs` responde 200 y contiene el esquema `ApiError`.

## Fase 2 — Integraciones externas

- [x] **T015** Interfaz `ConsultaRegistroCivil` y simulador con DNIs de prueba propios (mayor de edad, menor de edad que cumple 18 mañana, inexistente).
  RF: RF-01, RF-02
  Hecho cuando: un test obtiene datos civiles de un DNI de prueba y "no encontrado" de uno inexistente.

- [x] **T016** Adaptador RENIEC con `RestClient`; tiempo de espera o caída → excepción de servicio no disponible.
  RF: RF-01, CL-01, RNF-10
  Hecho cuando: tests con `MockRestServiceServer` cubren respuesta válida, DNI inexistente y caída (traducida a 503 con el mensaje de CL-01).

- [x] **T017** Interfaz `EnvioCodigo` (SMS / WhatsApp), simulador que guarda el último código enviado para las pruebas, y adaptador Twilio.
  RF: RF-03
  Hecho cuando: tests con `MockRestServiceServer` verifican la petición por cada canal y el simulador devuelve el último código enviado.

- [x] **T018** Interfaz `Geocodificador`, simulador y adaptador de mapas para la dirección estimada.
  RF: RF-04
  Hecho cuando: un test obtiene "Jr. Jacarandá 342, San Borja" del simulador y otro verifica la petición del adaptador.

- [x] **T019** Interfaz `EnvioPush`, simulador y adaptador FCM que distingue "token inválido" de otros errores.
  RF: RF-13, RF-14
  Hecho cuando: un test con el doble de FCM devuelve "inválido" para un token rechazado y "error temporal" para un fallo de red.

## Fase 3 — Cuadrantes base

- [x] **T020** `db/seed_dev.sql` (ver plan §13): al menos tres cuadrantes de Lima (dos contiguos con borde compartido), sus distritos, teléfonos distritales y centrales nacionales.
  RF: RF-04, RF-09, RF-18, CL-05
  Hecho cuando: tras cargar la semilla, `SELECT count(*) FROM cuadrantes WHERE habilitado` devuelve al menos 3 y dos de ellos comparten borde (`ST_Touches`).

- [x] **T021** `fn_resolver_punto`: cuadrante habilitado que contiene el punto (borde incluido) con desempate determinista por código.
  RF: RF-04, CL-05
  Hecho cuando: tests cubren punto interior, punto fuera de cobertura, punto en cuadrante deshabilitado y punto sobre el borde (siempre el mismo cuadrante en 10 ejecuciones).

## Fase 4 — Identidad: alta

- [x] **T022** `sp_alta_iniciar`: crea alta en curso con datos civiles y origen; rechaza menor de edad y DNI ya registrado.
  RF: RF-01, RF-02, RF-05, CL-14
  Hecho cuando: tests cubren alta creada (ESCANEO y MANUAL con el mismo resultado), edad 17 rechazada con "Debes ser mayor de edad para unirte a la red vecinal", edad 18 aceptada y DNI repetido con "Este DNI ya está registrado. Inicia sesión".

- [x] **T023** `POST /api/v1/auth/dni` con validación de 8 dígitos antes de consultar.
  RF: RF-01, RF-02, CL-01
  Hecho cuando: tests API cubren 7 dígitos (400 sin llamar al registro civil), DNI inexistente ("No encontramos este DNI..."), servicio caído (503) y respuesta con datos autollenados y token de alta.

- [x] **T024** `sp_codigo_emitir`: genera código de 4 dígitos con hash, guarda canal, vigencia y controla reenvío.
  RF: RF-03, CL-13
  Hecho cuando: tests cubren emisión, reenvío antes de 30 s rechazado con los segundos restantes, reenvío permitido después y reenvío con cambio de canal.

- [x] **T025** `sp_codigo_validar`: vigencia, intentos y marca de celular verificado.
  RF: RF-03
  Hecho cuando: tests cubren código correcto, incorrecto y vencido ("El código no es válido o ya venció") e invalidación tras el 5.º intento fallido.

- [x] **T026** `POST /api/v1/auth/codigo` y `/auth/codigo/verificar` para el alta en curso.
  RF: RF-03
  Hecho cuando: tests API cubren celular de 8 dígitos (400), envío por SMS y por WhatsApp en el simulador y verificación exitosa que marca el celular en el alta en curso.

- [x] **T027** `sp_alta_guardar_domicilio`: guarda el punto, cuadrante, dirección y referencia de cuadra en el alta en curso; rechaza fuera de cobertura.
  RF: RF-04
  Hecho cuando: tests cubren guardado dentro de cuadrante (referencia "cuadra 3" para el n.º 342) y rechazo con "Tu zona aún no tiene cobertura. Te avisaremos cuando esté disponible".

- [x] **T028** `GET /api/v1/geo/resolver-pin` y `POST /api/v1/auth/alta/domicilio`.
  RF: RF-04
  Hecho cuando: tests API devuelven cuadrante, distrito, dirección estimada e indicador de cobertura, y el guardado fuera de cobertura responde con el mensaje de RF-04.

- [x] **T029** `sp_sesion_crear` y emisión del token de acceso (15 min) con el id de sesión, más token de renovación con hash.
  RF: RF-07
  Hecho cuando: un test crea una sesión y verifica que en BD solo existe el hash del token de renovación.

- [x] **T030** `sp_alta_completar`: exige DNI, celular y domicilio verificados, aceptación de tratamiento, alta no vencida; crea usuario, domicilio y sesión.
  RF: RF-05, CL-02, RNF-03
  Hecho cuando: tests cubren cada paso faltante (mensaje indica cuál), sin aceptación, alta vencida a las 24 h, celular de otra cuenta ("Este celular ya está registrado en otra cuenta") y alta exitosa.

- [x] **T031** `POST /api/v1/auth/alta` con mensaje de bienvenida según sexo.
  RF: RF-05, CL-03
  Hecho cuando: tests API devuelven "¡Bienvenida al barrio!" para sexo femenino y "¡Bienvenido al barrio!" para masculino, y un test con dos altas simultáneas del mismo DNI crea una sola cuenta.

## Fase 5 — Identidad: sesión e ingreso

- [x] **T032** `fn_validar_sesion` y filtro que la consulta en cada solicitud (sesión activa, cuenta activa, roles vigentes).
  RF: RF-07, RF-16, RNF-04
  Hecho cuando: un test desactiva la cuenta y la siguiente solicitud con un token aún vigente recibe 401.

- [x] **T033** `sp_sesion_renovar` con rotación y `POST /api/v1/auth/renovar`.
  RF: RF-07
  Hecho cuando: tests cubren renovación exitosa, reutilización del token anterior rechazada y ausencia de expiración por tiempo (sesión de hace 1 año sigue renovando).

- [x] **T034** `sp_sesion_cerrar` y `POST /api/v1/auth/salir`, invalidando los dispositivos de esa sesión.
  RF: RF-07, RF-14, CL-12
  Hecho cuando: tests cubren que la sesión cerrada se rechaza, su dispositivo queda inválido y otra sesión del mismo vecino sigue activa.

- [x] **T035** `sp_ingreso_solicitar` y `POST /api/v1/auth/ingreso` + verificación de código de ingreso que crea sesión.
  RF: RF-06, CL-13
  Hecho cuando: tests cubren DNI registrado (código enviado, celular mostrado como `******321`), DNI no registrado o inactivo con la misma respuesta genérica, y verificación que devuelve tokens.

## Fase 6 — Cuadrantes y domicilio

- [x] **T036** `sp_domicilio_cambiar`: plazo de 30 días, cobertura y sin pánico activo; reasigna cuadrante.
  RF: RF-08, CL-11
  Hecho cuando: tests cubren cambio exitoso, rechazo antes de 30 días con la fecha permitida, fuera de cobertura conservando el actual, rechazo con pánico activo y reportes previos que siguen en el cuadrante original.

- [x] **T037** `PUT /api/v1/vecinos/me/domicilio`.
  RF: RF-08
  Hecho cuando: un test API cambia el domicilio y luego `GET /emergencias/activas` ya no devuelve alertas del cuadrante anterior.

- [x] **T038** `fn_cuadrante_propio`, `fn_cuadrantes_cercanos` y sus endpoints `GET /api/v1/geo/cuadrantes/mio` y `/cercanos`.
  RF: RF-09, RF-10
  Hecho cuando: tests cubren polígono GeoJSON del propio, cercanos solo con nombre y contorno, y 403 para un usuario no verificado.

## Fase 7 — Vecinos y perfil

- [x] **T039** `fn_vecinos_cuadrante` y `GET /api/v1/geo/cuadrantes/mio/vecinos`.
  RF: RF-09, RF-10, RF-16, RNF-02
  Hecho cuando: tests verifican nombre "Ana María Q.", referencia de cuadra, ausencia de DNI, edad, celular y coordenadas en el JSON, exclusión de inactivos, administrador y serenazgo, y que no hay forma de pedir vecinos de otro cuadrante.

- [x] **T040** `sp_dispositivo_registrar` / `sp_dispositivo_invalidar` y `PUT /api/v1/vecinos/me/dispositivo`.
  RF: RF-14
  Hecho cuando: tests cubren registro, actualización del identificador en la misma sesión e invalidación.

- [x] **T041** `fn_perfil` y `GET /api/v1/vecinos/me`.
  RF: RF-18
  Hecho cuando: tests devuelven datos civiles, celular, cuadrante, estados de verificación e insignia "Vecina verificada" / "Vecino verificado" según sexo.

- [x] **T042** `fn_telefonos_emergencia` y `GET /api/v1/vecinos/telefonos-emergencia`.
  RF: RF-18
  Hecho cuando: tests devuelven los teléfonos del distrito y, para un distrito sin teléfonos, las centrales nacionales.

- [x] **T043** `sp_reporte_datos_civiles` y `POST /api/v1/vecinos/me/reporte-datos`.
  RF: RF-20
  Hecho cuando: tests verifican que se crea el reporte y el aviso al administrador, que los datos civiles no cambian y que la respuesta indica corregirlos ante RENIEC.

## Fase 8 — Emergencias

- [x] **T044** `sp_panico_emitir`: validaciones (confirmado, botón suspendido, pánico activo existente).
  RF: RF-11, RF-17
  Hecho cuando: tests cubren sin confirmación (rechazo), suspendido (rechazo con motivo) y segundo pánico (devuelve la alerta vigente sin crear otra).

- [x] **T045** `sp_panico_emitir`: ubicación y destinatarios.
  RF: RF-11, CL-07, CL-08
  Hecho cuando: tests cubren vecino en su cuadrante, vecino en otro cuadrante (destinatarios del cuadrante actual + coordinadores del de domicilio + serenazgo del distrito actual), fuera de cobertura (coordinadores y serenazgo del domicilio) y sin GPS (domicilio con "ubicación aproximada").

- [x] **T046** `sp_reporte_emitir`.
  RF: RF-12
  Hecho cuando: tests cubren reporte válido con serenazgo entre los destinatarios, tipo ausente rechazado y 6.º reporte en 24 h rechazado indicando cuándo podrá enviar otro.

- [x] **T047** `fn_alertas_activas` y `GET /api/v1/emergencias/activas`.
  RF: RF-13
  Hecho cuando: tests verifican orden de más reciente a más antigua y que un vecino de otro cuadrante no ve esas alertas.

- [x] **T048** WebSocket STOMP en `/ws/emergencias` con autenticación en CONNECT usando `fn_validar_sesion`.
  RF: RF-13, RNF-04
  Hecho cuando: un test conecta con token válido y otro con token revocado es rechazado.

- [x] **T049** `fn_puede_suscribir` e interceptor de SUBSCRIBE para `/topic/cuadrante/{id}`.
  RF: RF-13
  Hecho cuando: un test se suscribe a su cuadrante con éxito y la suscripción a otro cuadrante es rechazada.

- [x] **T050** Servicio de difusión: publica la alerta en el tópico del cuadrante y en las colas de usuario de los destinatarios después del commit.
  RF: RF-11, RF-12, RF-13, RNF-01
  Hecho cuando: un test con cliente STOMP recibe la alerta en menos de 3 s con nombre abreviado, referencia de cuadra y hora, y un cliente de otro cuadrante no la recibe.

- [x] **T051** Envío push asíncrono a dispositivos válidos; token inválido → `sp_dispositivo_invalidar`.
  RF: RF-13, RF-14
  Hecho cuando: un test con el simulador comprueba el envío a todos los dispositivos de los destinatarios y que un token rechazado queda marcado inválido.

- [x] **T052** `POST /api/v1/emergencias/panico` orquestando SP, difusión y push.
  RF: RF-11
  Hecho cuando: tests API cubren pánico exitoso (201 y difusión), fuera de cobertura (respuesta incluye teléfonos de emergencia) y suspendido (respuesta incluye motivo y teléfonos).
  Nota: el caso suspendido responde 403 con el motivo; los teléfonos se piden a `GET /vecinos/telefonos-emergencia` (plan §13).

- [x] **T053** `POST /api/v1/emergencias/reportes`.
  RF: RF-12
  Hecho cuando: tests API cubren reporte exitoso con difusión, tipo inválido (400 con el campo) y límite alcanzado.

## Fase 9 — Administración y estado de alertas

- [x] **T054** Administrador inicial con `sp_admin_inicial` (en `db/seed_dev.sql` para desarrollo; ver plan §13) y autorización por tipo de usuario (ADMIN, SERENAZGO, VECINO, coordinador).
  RF: RF-16
  Hecho cuando: el administrador inicial ingresa con DNI y código y un vecino recibe 403 en cualquier `/api/v1/admin/**`.

- [x] **T055** `sp_coordinador_asignar` / `sp_coordinador_retirar` y `/api/v1/admin/coordinadores`.
  RF: RF-16
  Hecho cuando: tests cubren asignación a dos cuadrantes, rechazo si el vecino no está verificado y pérdida del rol en la siguiente solicitud tras retirarlo.

- [x] **T056** `sp_serenazgo_alta` / `sp_serenazgo_desactivar` y `/api/v1/admin/serenazgo` (DNI validado con el registro civil).
  RF: RF-16, CL-16
  Hecho cuando: tests cubren alta con distrito, ingreso por DNI y código sin domicilio, desactivación que cierra sus sesiones y alta de una cuenta nueva para el mismo distrito.

- [x] **T057** `sp_alerta_cambiar_estado`: permisos, historial y prohibición de gestionar la propia alerta.
  RF: RF-15, CL-10
  Hecho cuando: tests cubren coordinador del cuadrante y serenazgo del distrito con éxito, coordinador de otro cuadrante y vecino rechazados, coordinador sobre su propia alerta rechazado, historial con autor y fecha, y RESUELTA/FALSA_ALARMA fuera de activas.

- [x] **T058** `sp_alerta_cambiar_estado`: conteo de falsas alarmas y suspensión al umbral con aviso al administrador.
  RF: RF-17
  Hecho cuando: tests cubren 2 falsas alarmas sin suspensión, la 3.ª en 30 días suspende y crea aviso, y 3 falsas alarmas repartidas en más de 30 días no suspenden.

- [x] **T059** `PATCH /api/v1/emergencias/{id}/estado` con difusión del nuevo estado.
  RF: RF-13, RF-15
  Hecho cuando: un test API cambia el estado y un cliente STOMP del cuadrante recibe el nuevo estado.

- [x] **T060** `fn_avisos_administrador`, `sp_suspension_levantar` y sus endpoints `/api/v1/admin/avisos` y `/admin/suspensiones/{id}/levantar`.
  RF: RF-17, RF-19, RF-20
  Hecho cuando: tests listan avisos de los tres tipos y, tras levantar la suspensión, el vecino puede emitir pánico en la siguiente solicitud.

- [x] **T061** `sp_cuadrante_deshabilitar`.
  RF: RF-21, CL-06
  Hecho cuando: tests verifican alertas cerradas como RESUELTA con autor "sistema" sin contar como falsa alarma, vecinos reasignados si su domicilio cae en otro cuadrante y los demás sin cuadrante.

- [x] **T062** `sp_cuadrante_habilitar`.
  RF: RF-21
  Hecho cuando: un test habilita un cuadrante y los vecinos sin cuadrante cuyo domicilio cae dentro quedan asignados y aparecen en la lista a notificar.

- [x] **T063** Endpoints `/api/v1/admin/cuadrantes/{id}/deshabilitar` y `/habilitar` con difusión y notificación.
  RF: RF-21
  Hecho cuando: tests verifican que un vecino sin cuadrante recibe "Tu zona aún no tiene cobertura" al pedir vecinos o alertas, puede ver su perfil y teléfonos, y recibe aviso push al habilitarse su zona.

## Fase 10 — Recuperación de cuenta

- [x] **T064** `sp_recuperacion_completar`.
  RF: RF-19, CL-04, CL-15
  Hecho cuando: tests verifican reemplazo del celular, revocación de todas las sesiones y dispositivos, aviso al administrador, rechazo de celular de otra cuenta y que un pánico activo sigue asociado a la cuenta.

- [x] **T065** `POST /api/v1/auth/recuperacion` paso a paso (DNI → código al celular nuevo → completar).
  RF: RF-19
  Hecho cuando: un test API recupera la cuenta, el token anterior recibe 401 y el celular anterior ya no recibe códigos de ingreso.

## Fase 11 — Cierre y verificación

- [x] **T066** Matriz de autorización: cada endpoint probado con visitante, vecino no verificado, vecino, coordinador, serenazgo y administrador, incluido el acceso directo a recursos de otro cuadrante.
  RF: RF-09, RF-10, RF-13, RF-15, RF-16, RNF-02, RNF-04
  Hecho cuando: el test recorre todas las rutas del OpenAPI y cada combinación devuelve el código esperado.
  Nota: la matriz automática cubre sin token (todas las rutas), vecino contra `/admin/**` y contra cambio de estado; coordinador, serenazgo y administrador se cubren en los tests de cada módulo.

- [x] **T067** Prueba de datos sensibles sobre los flujos de alta, ingreso, pánico y recuperación.
  RF: RNF-05
  Hecho cuando: con `OutputCaptureExtension` no aparece ningún DNI, código, token ni coordenada de domicilio completos en la salida.

- [x] **T068** Prueba punta a punta de los criterios de finalización: alta → red → pánico → recepción por WebSocket y push de otro vecino → vecino de otro cuadrante no la recibe → coordinador cambia estado → 3 falsas alarmas suspenden.
  RF: RF-01, RF-03, RF-04, RF-05, RF-10, RF-11, RF-13, RF-15, RF-17
  Hecho cuando: el test pasa de principio a fin con las integraciones simuladas.

- [x] **T069** Revisión del contrato: todos los endpoints en OpenAPI con request, respuesta y `ApiError`, y todos los mensajes al usuario en español.
  RF: RNF-08
  Hecho cuando: `GET /v3/api-docs` lista todas las rutas de la sección 6 del plan y una búsqueda de mensajes en inglés en las respuestas de error no encuentra ninguno.
  Nota: se verifica que todas las rutas estén en el catálogo; la revisión de mensajes en español se hizo manualmente, no con un test automático.

- [x] **T070** Recreación limpia: `db/db.sql`, `db/sp/` y `db/seed.sql` levantan una base nueva desde cero y la suite completa pasa.
  RF: RNF-07, RNF-11
  Hecho cuando: sobre una base vacía nueva los scripts se ejecutan sin error y `./mvnw test` pasa al 100%.

---

## Cobertura de RF

| RF | Tareas |
|---|---|
| RF-01 | T006, T015, T016, T022, T023, T068 |
| RF-02 | T015, T022, T023 |
| RF-03 | T007, T009, T017, T024, T025, T026, T068 |
| RF-04 | T005, T006, T018, T020, T021, T027, T028, T068 |
| RF-05 | T006, T009, T022, T030, T031, T068 |
| RF-06 | T035 |
| RF-07 | T007, T029, T032, T033, T034 |
| RF-08 | T009, T036, T037 |
| RF-09 | T005, T020, T038, T039, T066 |
| RF-10 | T038, T039, T066, T068 |
| RF-11 | T008, T044, T045, T050, T052, T068 |
| RF-12 | T008, T009, T046, T050, T053 |
| RF-13 | T019, T047, T048, T049, T050, T051, T059, T066, T068 |
| RF-14 | T007, T019, T034, T040, T051 |
| RF-15 | T008, T057, T059, T066, T068 |
| RF-16 | T008, T032, T039, T054, T055, T056, T066 |
| RF-17 | T008, T009, T044, T058, T060, T068 |
| RF-18 | T005, T020, T041, T042 |
| RF-19 | T060, T064, T065 |
| RF-20 | T008, T043, T060 |
| RF-21 | T005, T061, T062, T063 |

## Fase 12 — Recuperación con espera y aviso (cambio de RF-19 tras la auditoría)

- [x] **T071** Esquema y SP: la recuperación queda en espera (`recuperacion_espera_horas`), devuelve a quién avisar y solo se aplica al vencer la espera sin cancelación.
  RF: RF-19
  Hecho cuando: tests de SP cubren solicitud en espera, intento antes de tiempo rechazado con fecha, aplicación al vencer, sesiones del titular vigentes durante la espera y rechazo tras cancelación.

- [x] **T072** `sp_recuperacion_cancelar` y `POST /api/v1/vecinos/me/recuperacion/cancelar`.
  RF: RF-19
  Hecho cuando: un test API cancela con la sesión del titular y el solicitante recibe "El titular de la cuenta canceló la recuperación".

- [x] **T073** Aviso inmediato al celular anterior (SMS) y a sus dispositivos (push) al solicitar la recuperación.
  RF: RF-19
  Hecho cuando: un test API verifica el SMS al celular anterior y el push a su dispositivo, y que la respuesta no incluye el celular anterior.

## Fase 13 — Fecha y sexo desde el DNI (proveedor Decolecta)

- [x] **T074** `POST /auth/dni`, `POST /auth/recuperacion/dni` y el alta de serenazgo reciben `fechaNacimiento` y `sexo`; se usan cuando el registro civil no los informa.
  RF: RF-01, RF-02, RF-05, CL-14
  Hecho cuando: tests API cubren campos faltantes o fecha futura (400 sin consultar), proveedor sin fecha (se usa la del DNI y se rechaza a un menor) y proveedor con fecha (prevalece la del proveedor).

- [x] **T075** Modo de registro civil independiente (`integraciones.registro-civil.modo`), real en `dev`.
  RF: RF-01
  Hecho cuando: un test de configuración registra el adaptador real con el resto simulado, y la API arranca en `dev` usando Decolecta.
