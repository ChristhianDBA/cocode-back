# Constitucion del Proyecto

1. **Simplicidad del Stack**: Monolito modular en Java 21 + Spring Boot 4.x (Maven) con PostgreSQL 15 + PostGIS. Prohibido anadir dependencias sin justificacion en la spec.
2. **Spec Primero**: No se escribe ni modifica codigo sin una especificacion activa y aprobada previamente en `specs/`.
3. **Separacion Logica e Interfaz**: Los controladores solo manejan HTTP; los servicios solo orquestan; las reglas de negocio viven exclusivamente en procedimientos/funciones de PostgreSQL (`db/sp/`).
4. **Politica de Tests**: Toda regla de negocio debe tener tests; `./mvnw test` debe pasar al 100% sin errores antes de integrar.
5. **Persistencia e Integridad**: Esquema en `db/db.sql`, acceso por SP. Borrado unicamente logico (`activo = false`). Servicios externos (RENIEC, Twilio, FCM) solo detras de interfaces.
6. **Idioma**: Mensajes al usuario, errores del dominio y especificaciones en espanol. Terminos del dominio en espanol; terminos tecnicos en espanol.

## Contrato de API
- Documentacion: catalogo OpenAPI/Swagger con rutas, metodos HTTP y formato exacto de los JSON de entrada y salida.
- Autenticacion: verificacion por OTP de 4 digitos; se emite un JWT que se envia en la cabecera `Authorization: Bearer <token>`.
- Errores: JSON `ApiError {codigo, mensaje, campos[]}` con codigos HTTP 400, 401, 403, 404 y 409 segun corresponda, para mostrar el mensaje en pantalla.
