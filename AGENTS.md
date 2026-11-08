# AGENTS.md - AyudarTrazable.Api

## Proyecto
API REST en Java 21 + Spring Boot 4.x (Maven) para Ayudar Trazable: red vecinal verificada con DNI (RENIEC),
domicilio geolocalizado por cuadrantes (PostGIS) y alertas de emergencia en tiempo real (WebSocket / FCM).
Monolito modular por feature (package-by-feature). Persistencia en PostgreSQL 15 + PostGIS; toda la logica de
negocio vive en funciones/procedimientos PL/pgSQL; Java solo expone HTTP, valida entrada y llama a la BD.

Estructura (`src/main/java/com/ayudartrazable/api/`):
```
comun/            errores (ApiError, manejador global), seguridad, parametros, enmascarado de logs, OpenAPI
identidad/        alta en curso, validacion de DNI, codigos, alta, ingreso, sesiones, recuperacion
cuadrantes/       resolver pin, domicilio, datos y contornos de cuadrantes
vecinos/          perfil, red de vecinos, dispositivos, telefonos de emergencia, reporte de datos
emergencias/      panico, reportes preventivos, alertas activas, cambio de estado
administracion/   coordinadores, serenazgo, suspensiones, avisos, habilitar/deshabilitar cuadrantes
notificaciones/   difusion WebSocket y envio push
integraciones/    registrocivil/, mensajeria/, geocodificacion/, push/ (interfaz + real + simulada)
AyudarTrazableApplication.java
```
Cada modulo: `XxxControlador`, `XxxServicio`, `XxxRepositorio` y `dto/`.
Base de datos: `db/db.sql` (esquema), `db/sp/*.sql` (funciones/procedimientos), `db/seed.sql` (parametros, cuadrantes,
telefonos, administrador inicial), `db/seed_dev.sql` (solo desarrollo y pruebas).

## Comandos
- Ejecutar API: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` (http://localhost:3001)
- Tests: `./mvnw test`
- Crear esquema: `psql -h localhost -U postgres -d ayudar_trazable -f db/db.sql`
- Cargar SP: `psql -h localhost -U postgres -d ayudar_trazable -f db/sp/<archivo>.sql` (un SP por archivo, `CREATE OR REPLACE`)
- Cargar semilla: `psql -h localhost -U postgres -d ayudar_trazable -f db/seed.sql`

## Estilo
- Java 21, sin Lombok salvo justificacion en la spec; DTOs como `record`.
- Clases/metodos en PascalCase/camelCase; identificadores en espanol, tanto del dominio (Cuadrante, Vecino, Domicilio)
  como tecnicos (Controlador, Servicio, Repositorio). Se exceptuan los nombres impuestos por Java o Spring.
- Inyeccion por constructor; configuracion por modulo en clases `@Configuration`.
- Controladores solo manejan HTTP; Service solo orquesta; Repository solo llama SP via `JdbcTemplate`/`SimpleJdbcCall`.
- Respuestas de error estandar `ApiError {codigo, mensaje, campos[]}` con mensajes al usuario solo en espanol.
- Endpoints bajo `/api/v1/...` y WebSocket STOMP en `/ws/emergencias`; el catalogo vigente esta en el `plan.md` de la spec activa.

## Reglas
- Lee `docs/` y la spec activa en `specs/` antes de escribir o modificar codigo.
- Toda la logica de negocio va en SP/funciones PostgreSQL (`db/sp/`); prohibido SQL de negocio embebido en Java.
- Todas las tablas viven en `db/db.sql`; si se crea o relaciona una tabla se actualiza ahi para poder migrar a otro servidor.
- Sin eliminacion fisica: nunca `DELETE`; solo desactivar (`activo = false`).
- Privacidad de cuadrantes: un vecino solo ve zona y alertas de su cuadrante y solo si esta verificado; se aplica en
  las funciones SQL (el backend se conecta con rol de servicio, no se puede asumir RLS).
- Datos sensibles: nunca registrar en logs ni devolver DNI completo, OTP, tokens ni coordenadas exactas del domicilio.
- OTP de 4 digitos con expiracion y reenvio limitado; los parametros se leen de la tabla `parametros`, no se hardcodean.
  El codigo `1234` solo existe en el perfil `dev`.
- Integraciones externas (RENIEC, Twilio, FCM, mapas) solo detras de interfaces, con implementacion simulada en `dev` y tests.
- Autorizacion en servidor: cada endpoint protegido con Spring Security + JWT y `@PreAuthorize`; la UI no cuenta como control.
- No agregues dependencias al `pom.xml` ni modifiques `specs/` salvo peticion explicita.
- Secretos (JWT, Twilio, FCM, credenciales de BD) por variables de entorno, nunca en el repositorio.
- **Base de datos de desarrollo**: siempre es `ayudar_trazable` (localhost, `application-dev.yml`). Los SP y la verificacion manual
  se hacen sobre esa base; no se crean bases adicionales para verificar. Las pruebas automaticas usan unicamente
  `ayudar_trazable_pruebas` porque borran datos y nunca deben apuntar a `ayudar_trazable`.
- NO PONER DE NINGUNA MANERA COMENTARIOS CON LOS REQUISITOS FUNCIONALES O NO FUNCIONALES DENTRO DEL CODIGO, ni emojis de ningun tipo, salvo comentarios cortos y precisos.

## Al terminar cualquier tarea
- Ejecuta `./mvnw test` y confirma en tu respuesta que todos los tests pasan correctamente.
