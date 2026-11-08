# Spec 001 — Identidad vecinal, cuadrantes, emergencias y perfil

Estado: Aprobado
Fecha: 07/11/2026

## 1. Contexto y objetivo

Ayudar Trazable es una red vecinal cerrada: solo participan personas cuya identidad civil y domicilio han sido verificados. Hoy los grupos vecinales de mensajería aceptan a cualquiera, no garantizan que el miembro viva en la zona y difunden alertas sin control, lo que genera desconfianza, exposición de datos personales y falsas alarmas.

Objetivo: que un vecino pueda registrarse verificando su DNI, su celular y su domicilio; quedar asignado al cuadrante donde vive; ver solo a los vecinos verificados de su cuadrante sin exponer su ubicación exacta; y disparar o recibir alertas de emergencia en tiempo real, aun con la aplicación cerrada.

Por qué: la confianza de la red depende de que cada miembro sea una persona real que vive en el cuadrante, y la utilidad de las alertas depende de que lleguen rápido, solo a quien corresponde y con mecanismos que desalienten el abuso.

## 2. Usuarios

| Usuario | Descripción |
|---|---|
| Visitante | Persona sin cuenta o sin sesión. Solo puede ver la bienvenida, registrarse o iniciar sesión. |
| Vecino en registro | Persona que inició el alta y aún no completa DNI, celular y domicilio verificados. |
| Vecino verificado | Persona con identidad, celular y domicilio verificados, asignada a un cuadrante. |
| Coordinador de cuadrante | Vecino verificado designado por el administrador para gestionar las alertas de uno o más cuadrantes. |
| Serenazgo | Cuenta institucional del distrito designada por el administrador para atender alertas de los cuadrantes de su distrito. |
| Administrador de plataforma | Designa coordinadores, da de alta cuentas de serenazgo, revisa suspensiones del botón de pánico y recibe reportes de datos civiles incorrectos. No tiene domicilio ni cuadrante. |

## 3. Historias de usuario

- HU-01: Como visitante, quiero escanear mi DNI para que mis datos civiles se completen solos y no tenga que escribirlos.
- HU-02: Como visitante cuyo DNI no se pudo leer, quiero escribir mi número de DNI a mano y obtener igual mis datos verificados.
- HU-03: Como visitante, quiero verificar mi celular con un código para que solo yo pueda usar la cuenta.
- HU-04: Como visitante, quiero ubicar mi casa en el mapa para unirme al cuadrante donde vivo.
- HU-05: Como visitante, quiero confirmar mis datos y completar el registro para ser parte de mi comunidad.
- HU-06: Como vecino, quiero iniciar sesión con mi DNI y un código enviado a mi celular, sin recordar contraseñas.
- HU-07: Como vecino, quiero mantener mi sesión abierta en mi celular para no tener que ingresar cada vez.
- HU-08: Como vecino que se mudó, quiero cambiar mi domicilio para pertenecer a mi nuevo cuadrante.
- HU-09: Como vecino, quiero ver los límites y el nombre de mi cuadrante para saber qué zona cubre mi red.
- HU-10: Como vecino, quiero ver qué vecinos verificados hay en mi cuadrante sin que se conozca la ubicación exacta de nadie.
- HU-11: Como vecino en peligro, quiero pulsar un botón de pánico para que todos los vecinos de mi cuadrante y la autoridad sepan de inmediato dónde ocurre.
- HU-12: Como vecino, quiero reportar una situación preventiva (sospecha, robo, emergencia médica, alumbrado) para alertar a mi comunidad.
- HU-13: Como vecino, quiero ver las alertas activas de mi cuadrante y recibir las nuevas al instante, incluso con la aplicación cerrada.
- HU-14: Como coordinador o serenazgo, quiero marcar una alerta como atendida, falsa alarma o resuelta para que la comunidad conozca su estado.
- HU-15: Como administrador, quiero designar coordinadores y cuentas de serenazgo para organizar la atención de alertas.
- HU-16: Como vecino, quiero ver mi perfil y el estado de mis verificaciones.
- HU-17: Como vecino, quiero tener a mano los teléfonos de emergencia de mi distrito.
- HU-18: Como vecino que perdió su celular, quiero recuperar mi cuenta validando de nuevo mi DNI para no perder mi lugar en el barrio.
- HU-19: Como vecino con datos civiles incorrectos, quiero reportarlo para que el administrador lo sepa.

## 4. Requisitos funcionales

Notación EARS: Cuando / Mientras / Si / Donde ... el sistema deberá ...

### RF-01 Lectura del DNI por escaneo
- Cuando un visitante escanee su DNI, el sistema deberá extraer el número de DNI, la fecha de nacimiento y el sexo impresos en el documento, y validar el número contra el registro civil oficial.
- Cuando el registro civil confirme el DNI, el sistema deberá autollenar nombres y apellidos desde el registro civil, marcarlos como verificados y no permitir editarlos.
- Donde el registro civil no informe fecha de nacimiento o sexo, el sistema deberá usar los leídos del DNI (o escritos a mano según RF-02) para calcular la edad y elegir el trato; si el registro civil sí los informa, prevalecerán los del registro civil.
- Si el escaneo no permite leer el número de DNI, entonces el sistema deberá mostrar la pantalla "No pudimos leer tu DNI" con consejos de luz, posición y limpieza del lente, y ofrecer "Intentar de nuevo" e "Ingresar mis datos a mano".
- Si el número leído no existe en el registro civil, entonces el sistema deberá rechazarlo con el mensaje "No encontramos este DNI. Revisa el número e inténtalo de nuevo".

### RF-02 Ingreso manual del DNI
- Cuando un visitante elija "Ingresar mis datos a mano" y escriba su número de DNI, fecha de nacimiento y sexo, el sistema deberá validar el número contra el registro civil y autollenar nombres y apellidos como en RF-01.
- Si el número de DNI no tiene exactamente 8 dígitos, entonces el sistema deberá indicarlo sin realizar la consulta.
- Si falta la fecha de nacimiento, es futura, o falta el sexo, entonces el sistema deberá indicarlo sin realizar la consulta.
- El sistema deberá tratar a un vecino validado por ingreso manual exactamente igual que a uno validado por escaneo.

### RF-03 Verificación del celular
- Cuando un visitante o vecino ingrese un celular peruano de 9 dígitos y elija el canal (mensaje de texto o WhatsApp), el sistema deberá enviarle un código de 4 dígitos por ese canal.
- Cuando el vecino solicite reenviar el código, el sistema deberá permitirle cambiar de canal.
- El sistema deberá aceptar el código solo dentro de su tiempo de vigencia (5 minutos, parametrizable).
- Si el vecino solicita reenviar el código antes de 30 segundos (parametrizable) desde el último envío, entonces el sistema deberá rechazarlo indicando cuántos segundos faltan.
- Si el código es incorrecto o venció, entonces el sistema deberá mostrar "El código no es válido o ya venció" y permitir solicitar uno nuevo.
- Si se superan 5 intentos fallidos (parametrizable) sobre un mismo código, entonces el sistema deberá invalidarlo y exigir un código nuevo.
- Cuando el código sea correcto, el sistema deberá marcar el celular como verificado.

### RF-04 Ubicación del domicilio y asignación de cuadrante
- Cuando el vecino coloque o arrastre el pin en el mapa, el sistema deberá mostrar el cuadrante al que pertenece el punto y una dirección estimada.
- Cuando el vecino pulse "Guardar domicilio" con el pin dentro de un cuadrante habilitado, el sistema deberá guardar la ubicación exacta, asignarle ese cuadrante y marcar el domicilio como verificado.
- Si el pin cae fuera de todo cuadrante habilitado, entonces el sistema deberá impedir guardar el domicilio y mostrar "Tu zona aún no tiene cobertura. Te avisaremos cuando esté disponible".
- Donde se muestre el mapa del domicilio, el sistema deberá informar "Solo vecinos verificados de tu cuadrante verán tu zona".

### RF-05 Alta comunal
- Mientras el DNI, el celular o el domicilio no estén verificados, el sistema deberá mantener deshabilitado el botón "Registrar" e indicar qué falta.
- Cuando los tres estén verificados y el visitante pulse "Registrar", el sistema deberá crear la cuenta del vecino verificado, iniciar su sesión y mostrar "¡Bienvenida/Bienvenido al barrio!" según el sexo registrado.
- Si el DNI ya pertenece a una cuenta existente, entonces el sistema deberá rechazar el alta con "Este DNI ya está registrado. Inicia sesión".
- Si el celular ya está asociado a otra cuenta, entonces el sistema deberá rechazar el alta con "Este celular ya está registrado en otra cuenta".
- Si la edad del registro civil es menor a 18 años (parametrizable), entonces el sistema deberá rechazar el alta con "Debes ser mayor de edad para unirte a la red vecinal".
- Cuando el visitante vaya a registrarse, el sistema deberá pedirle aceptar el tratamiento de sus datos personales; sin esa aceptación no deberá completar el alta.

### RF-06 Inicio de sesión
- Cuando un vecino registrado ingrese su DNI, el sistema deberá enviar un código de 4 dígitos al celular verificado de su cuenta, aplicando las reglas de RF-03.
- Cuando el código sea correcto, el sistema deberá iniciar la sesión y llevarlo a "El barrio en vivo".
- Si el DNI no corresponde a una cuenta activa, entonces el sistema deberá responder con un mensaje genérico que no revele si el DNI está registrado.
- Donde se muestre el envío del código, el sistema deberá mostrar solo los últimos 3 dígitos del celular de destino.

### RF-07 Continuidad de la sesión
- Mientras la sesión del vecino esté vigente, el sistema deberá permitirle usar la aplicación sin volver a ingresar.
- Cuando el acceso de corta duración venza, el sistema deberá renovarlo de forma transparente sin pedir un nuevo código, mientras la sesión no haya sido cerrada ni revocada.
- Cuando el vecino cierre sesión, el sistema deberá invalidarla de inmediato en ese dispositivo y dejar de enviarle notificaciones a ese dispositivo.
- Si la cuenta es desactivada, entonces el sistema deberá rechazar la siguiente solicitud y cerrar todas sus sesiones.
- El sistema no deberá cerrar la sesión por inactividad ni por tiempo; solo al cerrar sesión, al desactivar la cuenta o al recuperar la cuenta con un celular nuevo (RF-19).

### RF-08 Cambio de domicilio
- Cuando un vecino verificado registre un nuevo domicilio válido, el sistema deberá reasignarlo al cuadrante correspondiente y dejar de mostrarle el cuadrante anterior y sus alertas.
- Si no han pasado 30 días (parametrizable) desde el último cambio de domicilio, entonces el sistema deberá rechazar el cambio indicando la fecha a partir de la cual podrá hacerlo.
- Si el nuevo domicilio cae fuera de todo cuadrante habilitado, entonces el sistema deberá rechazarlo según RF-04 y conservar el domicilio actual.
- Mientras un vecino tenga una alerta de pánico activa, el sistema no deberá permitirle cambiar de domicilio.

### RF-09 Información del cuadrante
- Cuando un vecino verificado consulte su cuadrante, el sistema deberá mostrar su nombre, código, distrito y límites en el mapa.
- Donde se muestre el mapa, el sistema deberá mostrar como referencia el contorno y el nombre de los cuadrantes cercanos.
- Si un vecino intenta consultar los vecinos o las alertas de un cuadrante distinto al suyo, entonces el sistema deberá denegarlo.

### RF-10 Vecinos del cuadrante y privacidad
- Cuando un vecino verificado consulte su red, el sistema deberá listar solo vecinos verificados y activos de su mismo cuadrante.
- Donde se muestre a otro vecino, el sistema deberá mostrar su nombre de pila y la inicial del primer apellido, y su ubicación solo a nivel de cuadra, nunca la dirección exacta ni las coordenadas.
- El sistema no deberá mostrar a ningún vecino el DNI, la edad ni el celular de otro vecino.
- Mientras un vecino no esté verificado, el sistema no deberá mostrarle vecinos, alertas ni límites de ningún cuadrante.

### RF-11 Botón de pánico
- Cuando un vecino verificado pulse el botón de pánico, el sistema deberá pedir una confirmación explícita antes de enviar la alerta.
- Cuando el vecino confirme, el sistema deberá registrar una alerta de tipo PÁNICO con su ubicación actual y hora, y avisar al instante a los vecinos verificados y coordinadores del cuadrante donde se encuentra, al serenazgo de ese distrito y a los coordinadores de su cuadrante de domicilio.
- Si la ubicación actual del vecino está fuera de todo cuadrante habilitado, entonces el sistema deberá avisar a los coordinadores de su cuadrante de domicilio y al serenazgo de su distrito de domicilio, y mostrarle los teléfonos de emergencia de RF-18.
- Donde se muestre una alerta de pánico a otros vecinos, el sistema deberá mostrar el nombre abreviado del emisor (ej. "Ana María Q."), la referencia de cuadra y la hora.
- Si el vecino ya tiene una alerta de pánico activa, entonces el sistema no deberá crear otra y deberá mostrarle la alerta vigente.
- Si la alerta no puede enviarse por falta de conexión, entonces el sistema deberá informarlo de inmediato y mostrar los teléfonos de emergencia de RF-18.

### RF-12 Reporte preventivo
- Cuando un vecino verificado envíe un reporte de tipo SOSPECHA, ROBO, MÉDICA o ALUMBRADO con una descripción, el sistema deberá registrarlo como alerta activa y avisar a los vecinos y coordinadores de su cuadrante y al serenazgo del distrito.
- Si el reporte no indica tipo, entonces el sistema deberá rechazarlo indicando el campo obligatorio.
- Si el vecino ya envió 5 reportes (parametrizable) en las últimas 24 horas, entonces el sistema deberá rechazar el nuevo reporte indicando cuándo podrá enviar otro.

### RF-13 Alertas activas y aviso en tiempo real
- Cuando un vecino verificado consulte las alertas, el sistema deberá listar las alertas activas de su cuadrante, de la más reciente a la más antigua.
- Mientras la aplicación esté abierta, el sistema deberá mostrar cada nueva alerta del cuadrante sin que el vecino tenga que recargar.
- Mientras la aplicación esté cerrada o en segundo plano, el sistema deberá entregar la alerta como notificación en el celular.
- Cuando el estado de una alerta cambie, el sistema deberá reflejar el nuevo estado a los vecinos del cuadrante.
- El sistema no deberá entregar alertas de un cuadrante a vecinos de otro cuadrante.

### RF-14 Registro del dispositivo para notificaciones
- Cuando un vecino inicie sesión o el identificador de notificaciones de su celular cambie, el sistema deberá registrar ese dispositivo para recibir alertas.
- Si la entrega de una notificación falla porque el dispositivo ya no es válido, entonces el sistema deberá dejar de usar ese dispositivo.

### RF-15 Gestión del estado de una alerta
- Cuando un coordinador del cuadrante o el serenazgo del distrito cambie el estado de una alerta a ATENDIDA, FALSA_ALARMA o RESUELTA, el sistema deberá registrar el cambio con su autor y fecha.
- Si un usuario sin ese rol para el cuadrante intenta cambiar el estado, entonces el sistema deberá denegarlo.
- Cuando una alerta pase a RESUELTA o FALSA_ALARMA, el sistema deberá retirarla de las alertas activas y conservarla en el historial.
- El sistema deberá conservar todos los cambios de estado de una alerta; nunca se eliminan.
- El sistema no deberá cerrar alertas automáticamente por tiempo; una alerta permanece activa hasta que un coordinador o el serenazgo la cierre, salvo lo indicado en RF-21.

### RF-16 Designación de roles
- Cuando el administrador designe a un vecino verificado como coordinador de uno o más cuadrantes, el sistema deberá otorgarle la gestión de alertas solo de esos cuadrantes.
- Cuando el administrador cree una cuenta de serenazgo para un distrito, el sistema deberá otorgarle la gestión de alertas de todos los cuadrantes de ese distrito.
- Cuando el administrador retire un rol, el sistema deberá aplicarlo en la siguiente solicitud del afectado.
- Cuando el sistema se instale por primera vez, deberá existir un administrador inicial identificado por DNI y celular.
- Cuando el administrador dé de alta una cuenta de serenazgo, el sistema deberá exigir el DNI validado y el celular del responsable, y el distrito que atiende.
- El administrador y las cuentas de serenazgo deberán iniciar sesión con DNI y código al celular (RF-06), sin domicilio ni cuadrante propio, y no deberán aparecer en la lista de vecinos de ningún cuadrante.

### RF-17 Control de abuso del botón de pánico
- Cuando una alerta de pánico de un vecino se marque como FALSA_ALARMA, el sistema deberá contabilizarla en su historial.
- Si un vecino acumula 3 falsas alarmas (parametrizable) en 30 días (parametrizable), entonces el sistema deberá suspender su botón de pánico y avisar al administrador para revisión.
- Mientras el botón esté suspendido, el sistema deberá mostrar al vecino el motivo y los teléfonos de emergencia de RF-18, y le permitirá seguir enviando reportes preventivos.
- Cuando el administrador levante la suspensión, el sistema deberá rehabilitar el botón de inmediato.

### RF-18 Perfil y teléfonos de emergencia
- Cuando un vecino consulte su perfil, el sistema deberá mostrar sus datos civiles, celular, cuadrante y el estado de verificación de DNI, celular y domicilio, con la insignia "Vecina verificada" o "Vecino verificado".
- Cuando un vecino consulte los teléfonos de emergencia, el sistema deberá mostrar los de serenazgo, comisaría y bomberos de su distrito.
- Si un distrito no tiene teléfonos configurados, entonces el sistema deberá mostrar las centrales nacionales de emergencia.

### RF-19 Recuperación de cuenta por cambio de celular
- Cuando un vecino registrado indique que perdió o cambió su celular, el sistema deberá exigirle volver a validar su DNI (escaneo o ingreso manual, RF-01/RF-02) y verificar el celular nuevo (RF-03).
- Cuando ambas verificaciones sean correctas y se solicite completar la recuperación, el sistema deberá dejarla en espera durante 24 horas (parametrizable) y avisar de inmediato al celular anterior por mensaje de texto y a los dispositivos registrados de la cuenta.
- Mientras la recuperación esté en espera, el sistema deberá mantener vigentes las sesiones actuales del titular y permitirle cancelarla desde su cuenta.
- Si el titular cancela la recuperación, entonces el sistema deberá rechazar su finalización con "El titular de la cuenta canceló la recuperación".
- Si se intenta completar la recuperación antes de que termine la espera, entonces el sistema deberá rechazarlo indicando la fecha y hora a partir de la cual podrá completarse.
- Cuando termine la espera sin cancelación y se solicite completar la recuperación, el sistema deberá reemplazar el celular de la cuenta, cerrar todas las sesiones anteriores, dejar de notificar a los dispositivos anteriores y avisar al administrador.
- Si el celular nuevo ya pertenece a otra cuenta, entonces el sistema deberá rechazarlo con "Este celular ya está registrado en otra cuenta".
- El sistema solo deberá permitir la recuperación de cuentas de vecinos; las cuentas de administrador y serenazgo se gestionan por el administrador.

### RF-20 Reporte de datos civiles incorrectos
- Cuando un vecino reporte desde su perfil que sus datos civiles son incorrectos, el sistema deberá registrar el reporte para el administrador y confirmarle la recepción.
- El sistema no deberá modificar los datos civiles por ese reporte; deberá indicar al vecino que la corrección se realiza ante RENIEC y que sus datos se actualizarán al volver a validar su DNI.

### RF-21 Deshabilitación de un cuadrante
- Cuando un cuadrante se deshabilite, el sistema deberá cerrar sus alertas activas como RESUELTA con autor "sistema", sin contarlas como falsa alarma.
- Cuando un cuadrante se deshabilite, el sistema deberá reasignar a cada vecino cuyo domicilio caiga en otro cuadrante habilitado, y dejar sin cuadrante a los demás.
- Mientras un vecino esté sin cuadrante, el sistema deberá mostrarle "Tu zona aún no tiene cobertura", no mostrarle vecinos ni alertas, y permitirle ver su perfil y los teléfonos de emergencia.
- Cuando su zona vuelva a tener un cuadrante habilitado, el sistema deberá asignárselo y notificarle.

## 5. Requisitos no funcionales

- RNF-01 Latencia de alertas: una alerta de pánico llega a los vecinos con la aplicación abierta en menos de 3 segundos en condiciones normales.
- RNF-02 Privacidad: ningún vecino puede obtener la ubicación exacta, el DNI ni el celular de otro, aunque llame directamente al servidor.
- RNF-03 Protección de datos: el tratamiento de datos personales cumple la Ley 29733 de Protección de Datos Personales del Perú; el vecino acepta el tratamiento antes de registrarse.
- RNF-04 Autorización en servidor: toda restricción por rol, cuadrante o estado de verificación se aplica en el servidor, independientemente de la interfaz.
- RNF-05 Datos sensibles: DNI, códigos de verificación, sesiones y coordenadas del domicilio nunca aparecen en registros técnicos ni en mensajes de error.
- RNF-06 Parametrización: vigencia y reenvío del código, intentos, plazo de cambio de domicilio y umbral de falsas alarmas se configuran sin cambiar el sistema.
- RNF-07 Integridad: no existe eliminación física de vecinos, alertas ni historial; solo desactivación.
- RNF-08 Mensajes: todos los textos al usuario están en español, en lenguaje simple y sin información técnica.
- RNF-09 Accesibilidad: contraste mínimo 4.5:1, botones táctiles grandes y botón de pánico alcanzable con una mano.
- RNF-10 Disponibilidad: si un servicio externo de identidad o mensajería no responde, el sistema informa al vecino que intente más tarde, sin perder los datos ya verificados del alta en curso.
- RNF-11 Calidad: cada regla de negocio de esta spec tiene pruebas automáticas.

## 6. Casos límite

- CL-01: El registro civil no responde durante el alta: se muestra "No pudimos validar tu DNI en este momento. Intenta más tarde" y no se permite continuar sin validación.
- CL-02: El vecino cierra la aplicación a mitad del alta: al volver conserva los pasos ya verificados durante 24 horas (parametrizable).
- CL-03: Dos personas intentan registrar el mismo DNI al mismo tiempo: solo una cuenta se crea; la otra recibe "Este DNI ya está registrado".
- CL-04: El vecino cambia de número de celular: recupera la cuenta según RF-19; el celular anterior deja de recibir códigos y alertas.
- CL-05: El pin cae exactamente sobre el límite entre dos cuadrantes: se asigna a uno solo, de forma determinista y siempre al mismo.
- CL-06: Un cuadrante se deshabilita con vecinos asignados: se aplica RF-21.
- CL-07: El vecino pulsa pánico sin señal GPS precisa: la alerta se envía con la ubicación de su domicilio registrado y se indica "ubicación aproximada".
- CL-08: El emisor de una alerta de pánico está fuera de su cuadrante: la alerta llega al cuadrante donde se encuentra y a los coordinadores de su cuadrante de domicilio (RF-11).
- CL-09: Una alerta activa nadie la atiende: permanece activa hasta que un coordinador o el serenazgo la cierre (RF-15).
- CL-10: Un coordinador recibe su propia alerta de pánico: no puede marcarla como falsa alarma ni resuelta; lo hace otro coordinador o el serenazgo.
- CL-11: Un vecino cambia de domicilio mientras tiene reportes preventivos activos: los reportes permanecen en el cuadrante original.
- CL-12: El vecino tiene la sesión abierta en dos celulares: ambos reciben alertas; cerrar sesión en uno no afecta al otro.
- CL-13: El vecino pide el código de inicio de sesión muchas veces seguidas: se aplica el límite de reenvío de RF-03.
- CL-14: El registro civil devuelve una edad de 17 años y el vecino cumple 18 días después: el alta se rechaza hoy y se acepta cuando la edad devuelta sea 18.
- CL-15: Un vecino recupera su cuenta (RF-19) con una alerta de pánico activa: la alerta sigue activa y se reasocia a la cuenta, no al celular anterior.
- CL-16: El responsable de una cuenta de serenazgo deja el cargo: el administrador desactiva esa cuenta y da de alta una nueva para el distrito.

## 7. Fuera de alcance

- Chat o mensajería entre vecinos.
- Cierre automático de alertas por tiempo.
- Corrección de datos civiles dentro de la aplicación.
- Inicio de sesión con contraseña, biometría o cuentas externas.
- Panel web de administración y reportes estadísticos.
- Despacho operativo del serenazgo (asignación de patrulleros, seguimiento en ruta).
- Adjuntar fotos, audio o video a las alertas.
- Más de un domicilio por vecino.
- Alta de nuevos cuadrantes desde la aplicación (se cargan por configuración).

## 8. Criterios de finalización

- Todos los criterios EARS de RF-01 a RF-21 están cubiertos por pruebas automáticas que pasan.
- Flujo de punta a punta: un visitante escanea o escribe su DNI, verifica su celular, ubica su domicilio dentro de un cuadrante, se registra, ve a sus vecinos sin coordenadas exactas, dispara una alerta de pánico confirmada y otro vecino del mismo cuadrante la recibe con la aplicación abierta y cerrada.
- Un vecino de otro cuadrante no recibe ni puede consultar esa alerta, aunque llame directamente al servidor.
- Un coordinador designado cambia el estado de la alerta y el cambio queda en el historial; tras 3 falsas alarmas el botón del vecino queda suspendido.
- Un domicilio fuera de cobertura no puede guardarse.
- Ningún DNI, código, sesión ni coordenada de domicilio aparece en registros técnicos.
- La spec es aprobada por el responsable del proyecto antes de iniciar el plan técnico.

## 9. Dudas abiertas

No quedan dudas abiertas. Decisiones tomadas el 07/11/2026:

| Duda | Decisión | Referencia |
|---|---|---|
| Edad mínima | 18 años, parametrizable | RF-05 |
| Duración de la sesión | No expira por inactividad ni por tiempo | RF-07 |
| Cuadrantes vecinos en el mapa | Solo contorno y nombre | RF-09 |
| Reportes preventivos | Llegan también al serenazgo; máximo 5 por vecino cada 24 horas | RF-12 |
| Administrador y serenazgo | DNI + código, sin domicilio; el administrador da de alta al serenazgo | RF-16 |
| Pérdida del celular | Revalidar DNI y verificar celular nuevo; espera de 24 h con aviso al celular anterior, cancelable por el titular; luego se cierran sesiones anteriores (decisión del 07/11/2026 tras auditoría) | RF-19 |
| Cuadrante deshabilitado | Alertas cerradas por sistema; vecinos reasignados o sin cuadrante | RF-21 |
| Pánico fuera del cuadrante | Cuadrante donde está + coordinadores del cuadrante de domicilio | RF-11 |
| Alertas sin atender | No se cierran solas | RF-15 |
| Canal del código | El vecino elige SMS o WhatsApp | RF-03 |
| Datos civiles incorrectos | Se reportan al administrador; se corrigen ante RENIEC | RF-20 |
| Fecha de nacimiento y sexo | El proveedor de registro civil solo confirma nombres; fecha y sexo se leen del DNI por OCR o se escriben a mano (decisión del 07/11/2026) | RF-01, RF-02 |
