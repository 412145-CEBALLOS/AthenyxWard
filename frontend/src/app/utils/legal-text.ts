export const TERMS_CONTENT = `---
version: v1.0
date: 2026-07-14
---

Fecha de entrada en vigor: 14 de julio de 2026

## 1. Objeto

Athenyx Ward ("el Servicio") es una herramienta de análisis de seguridad del correo electrónico que utiliza técnicas heurísticas y, opcionalmente, inteligencia artificial local para evaluar el riesgo de amenazas en los mensajes de Gmail del usuario. Al utilizar el Servicio, el Usuario acepta quedar vinculado por los presentes Términos y Condiciones.

## 2. Datos de Gmail tratados

Athenyx Ward accede a la cuenta de Gmail del Usuario exclusivamente a través de la API de Google ("interfaz técnica controlada que permite acceder a Gmail de forma segura") y únicamente con el alcance \`gmail.readonly\` ("permiso de solo lectura — sin posibilidad de modificar, eliminar ni enviar mensajes"). Los datos que el Servicio lee de Gmail son:

- **Remitente (dirección de correo electrónico)** del mensaje.
- **Fecha y hora de envío y recepción**.
- **Asunto y cuerpo del mensaje** (contenido textual).
- **URLs** incluidas en el cuerpo del mensaje.
- **Metadatos de cabecera** del mensaje (incluyendo indicadores de autenticación DKIM, SPF y DMARC — "protocolos estándar que verifican la autenticidad del remitente del correo").

El Servicio **no modifica, elimina ni envía ningún mensaje** de Gmail. Todas las operaciones son de solo lectura.

## 3. Inteligencia artificial local

El análisis de IA se realiza **en la infraestructura propia del Servicio** (servidores gestionados por Athenyx Ward) mediante Ollama ("plataforma de código abierto para ejecutar modelos de inteligencia artificial en infraestructura propia") con modelos de inteligencia artificial de código abierto como Llama 3 o Qwen 2.5 (según la configuración del Servicio). **No se utilizan servicios externos de IA en la nube** (como ChatGPT, Google AI, Claude o equivalentes) para el procesamiento de los correos. Los datos del Usuario no abandonan la infraestructura del Servicio en ningún momento del análisis.

## 4. Almacenamiento permanente

Athenyx Ward almacena de forma permanente los siguientes datos del usuario:

- **Dirección de correo electrónico, nombre y foto de perfil** obtenidos de la cuenta de Google.
- **Registros de análisis de correo**, incluyendo: fecha del análisis, nivel de riesgo calculado (en porcentaje), resumen del análisis heurístico y, si el usuario lo solicita, la explicación generada por IA.
- **Recordatorios creados por el usuario** vinculados a mensajes concretos.
- **Configuración de accesibilidad** (modo accesibilidad activado o desactivado).

Los datos de análisis se conservan mientras la cuenta del usuario esté activa. Athenyx Ward se reserva el derecho de eliminar datos de análisis inactivos después de un período de 12 meses sin actividad del usuario.

## 5. Contraseñas

Athenyx Ward **no almacena contraseñas**. La autenticación se realiza exclusivamente mediante OAuth 2.0 ("un protocolo estándar de autenticación que permite iniciar sesión sin compartir contraseña") con Google. El Servicio nunca tiene acceso a la contraseña de la cuenta de Google del Usuario.

Los tokens de acceso y refresh de Google están cifrados mediante AES-GCM ("un algoritmo de cifrado de alta seguridad") y se almacenan en la base de datos de Athenyx Ward para permitir la sincronización con Gmail. Estos tokens pueden ser revocados por el usuario en cualquier momento desde la configuración de su cuenta de Google.

## 6. Eliminación de la cuenta y revocación

El Usuario puede solicitar la eliminación total de su cuenta y todos los datos asociados en cualquier momento. Para ello, debe enviar un correo electrónico a soporte@athenyxward.com con el asunto "Solicitud de eliminación de cuenta" incluyendo la dirección de correo electrónico vinculada a su cuenta Athenyx Ward.

La eliminación incluye la supresión de:
- Todos los datos personales (correo electrónico, nombre, foto).
- Todos los registros de análisis.
- Todos los recordatorios.
- Los tokens de Google almacenados.

Tras la recepción de la solicitud, Athenyx Ward procesará la eliminación en un plazo máximo de 30 días hábiles. Una vez eliminada la cuenta, **no es posible recuperar los datos**.

La revocación del acceso de Athenyx Ward a la cuenta de Google puede realizarse en cualquier momento desde la página de aplicaciones de terceros de la cuenta de Google (myaccount.google.com/permissions). Esta acción no constituye por sí sola la eliminación de los datos almacenados por Athenyx Ward; para ello es necesario realizar la solicitud de eliminación descrita arriba.

## 7. Acceso y uso del Servicio

El Servicio está dirigido a cualquier persona que desee mejorar la **gestión, la productividad y la seguridad** de su correo electrónico, incluyendo un modo de **accesibilidad pensado especialmente para personas adultas mayores** o usuarios con necesidades especiales de visualización. Para aceptar estos Términos y utilizar el Servicio, el Usuario debe ser **mayor de 18 años** (o la edad legal aplicable en su país de residencia). El uso del Servicio es responsabilidad del Usuario. Athenyx Ward no garantiza que el análisis heurístico o la explicación de IA detecten el 100% de las amenazas; se trata de una herramienta de ayuda, no de un sustituto del juicio profesional en materia de ciberseguridad.

## 8. Cambios a los Términos

Athenyx Ward se reserva el derecho de modificar los presentes Términos y Condiciones. En caso de modificación sustancial, se notificará al Usuario mediante un aviso en el Servicio la próxima vez que acceda tras la publicación de la nueva versión. El uso continuado del Servicio tras la notificación constituye aceptación de los nuevos términos.

Si el Usuario no acepta los nuevos términos, su único recurso es solicitar la eliminación de su cuenta conforme al apartado 6.

## 9. Ley aplicable y jurisdicción

Los presentes Términos y Condiciones se rigen por la **legislación argentina**. Cualquier controversia derivada de la interpretación o ejecución de los mismos será sometida a los **tribunales competentes del domicilio del Usuario**.

## 10. Contacto

Para cualquier cuestión relativa a estos Términos y Condiciones o a la Política de Privacidad, el Usuario puede contactar con Athenyx Ward en: soporte@athenyxward.com`;

export const PRIVACY_CONTENT = `---
version: v1.0
date: 2026-07-14
---

Fecha de entrada en vigor: 14 de julio de 2026

## 1. Responsable del tratamiento

El responsable del tratamiento de los datos personales descritos en esta Política de Privacidad es Athenyx Ward, con domicilio en soporte@athenyxward.com.

## 2. Datos personales tratados

Athenyx Ward trata los siguientes datos personales del Usuario:

### 2.1 Datos obtenidos de Google OAuth

- **Correo electrónico**: dirección de Gmail principal de la cuenta de Google utilizada para autenticarse.
- **Nombre**: nombre completo asociado a la cuenta de Google, si está disponible.
- **Fotografía de perfil**: imagen asociada a la cuenta de Google, si está disponible.

### 2.2 Datos de uso del Servicio

- **Registros de análisis**: cada vez que el Usuario solicita el análisis de un mensaje de Gmail, Athenyx Ward registra la fecha del análisis, la dirección del remitente, el nivel de riesgo calculado (en porcentaje), el resumen del análisis heurístico y, cuando el Usuario lo solicita, la explicación generada por inteligencia artificial.
- **Recordatorios**: cuando el Usuario crea un recordatorio vinculado a un mensaje, se almacena el texto del recordatorio, la fecha de recordatorio y el mensaje al que está vinculado.
- **Configuración de accesibilidad**: preferencia del Usuario respecto al modo accesibilidad (activado/desactivado).
- **Fecha de aceptación de los Términos y Condiciones y versión de los términos aceptados**.

### 2.3 Datos de navegación

- **Dirección IP** ("un identificador numérico de tu conexión a internet"): recopilada en cada solicitud HTTP para medidas de seguridad y prevención de fraude.
- **Agente de usuario** ("información del navegador y dispositivo que usas para acceder al Servicio"): información del navegador utilizada para diagnóstico de incidencias.
- **Identificador de correlación** ("un código único generado para cada sesión que permite relacionar eventos con fines de diagnóstico y seguridad").

## 3. Finalidades del tratamiento

Los datos personales del Usuario son tratados para las siguientes finalidades:

1. **Autenticación**: verificar la identidad del Usuario mediante OAuth 2.0 ("un protocolo estándar de autenticación que permite iniciar sesión sin compartir contraseña") con Google.
2. **Prestación del Servicio**: realizar el análisis heurístico y, en su caso, la explicación por IA de los mensajes de Gmail solicitados por el Usuario.
3. **Recordatorios**: gestionar los recordatorios creados por el Usuario y notificarle cuando se aproximen.
4. **Mejora del Servicio**: los datos de uso agregados y anonimizados pueden ser utilizados para identificar patrones de uso y mejorar la precisión de los análisis heurísticos.
5. **Comunicación de incidencias**: contactar con el Usuario en caso de detectarse una incidencia de seguridad relevante en su cuenta.
6. **Cumplimiento legal**: conservar datos durante el período que la legislación aplicable exija para el cumplimiento de obligaciones legales.

## 4. Base jurídica del tratamiento

El tratamiento de datos personales realizado por Athenyx Ward se fundamenta en las siguientes bases jurídicas:

- **Ejecución de un contrato** (artículo 6.1.b del RGPD — "Reglamento General de Protección de Datos de la Unión Europea, o la normativa equivalente aplicable en el país del Usuario"): los tratamientos necesarios para prestar el Servicio al Usuario, incluyendo el análisis de correos y la gestión de recordatorios, se realizan porque son necesarios para la ejecución del contrato de uso del Servicio.
- **Consentimiento explícito** (artículo 6.1.a del RGPD): el tratamiento de los datos para la explicación por IA y la aceptación de los Términos y Condiciones se realizan con el consentimiento explícito del Usuario, que puede ser revocado en cualquier momento.
- **Interés legítimo** (artículo 6.1.f del RGPD): la recopilación de datos de navegación (IP, agente de usuario) para medidas de seguridad se realiza en virtud del interés legítimo de Athenyx Ward de proteger la integridad del Servicio.

## 5. Inteligencia artificial local

Los datos de los mensajes de Gmail del Usuario **no son transmitidos a servicios externos de IA en la nube** (como ChatGPT, Google AI, Claude u otros). El análisis mediante inteligencia artificial se realiza **en la infraestructura propia del Servicio** (servidores gestionados por Athenyx Ward) mediante Ollama ("plataforma de código abierto para ejecutar modelos de inteligencia artificial en infraestructura propia") con modelos de inteligencia artificial de código abierto como Llama 3 o Qwen 2.5. Los datos del Usuario no abandonan la infraestructura del Servicio durante ningún momento del proceso de análisis de IA.

## 6. Destinatarios de los datos

Los datos personales del Usuario **no son vendidos, alquilados ni cedidos a terceros** fuera de los siguientes supuestos:

- **Proveedores de infraestructura**: Athenyx Ward utiliza proveedores de hosting y bases de datos (actualmente, MySQL en servidor propio) para el almacenamiento de datos. Estos proveedores tratan los datos únicamente siguiendo las instrucciones de Athenyx Ward y no para fines propios.
- **Obligaciones legales**: los datos podrán ser comunicados a las autoridades competentes cuando así lo exija la legislación aplicable, en el ámbito de la prevención del fraude o la investigación de delitos.

Athenyx Ward no realiza transferencias internacionales de datos fuera del Espacio Económico Europeo.

## 7. Plazos de conservación

Athenyx Ward conserva los datos personales del Usuario durante los siguientes plazos:

- **Datos de autenticación (correo, nombre, foto)**: mientras la cuenta esté activa. Tras la solicitud de eliminación de cuenta, se eliminan en un plazo máximo de 30 días hábiles.
- **Registros de análisis**: mientras la cuenta esté activa, y hasta 12 meses después de la última actividad del Usuario. Los registros inactivos son eliminados transcurrido ese período sin necesidad de notificación previa.
- **Recordatorios**: eliminados junto con la cuenta del Usuario.
- **Tokens de Google**: eliminados junto con la cuenta del Usuario o inmediatamente al revocar el acceso desde la cuenta de Google.
- **Datos de aceptación de Términos**: conservados de forma indefinida como prueba del consentimiento prestado.

## 8. Derechos del Usuario

El Usuario tiene los siguientes derechos sobre sus datos personales:

- **Acceso**: obtener confirmación de si Athenyx Ward trata sus datos y, en caso afirmativo, acceder a los mismos.
- **Rectificación**: solicitar la corrección de datos inexactos o la completación de datos incompletos.
- **Supresión**: solicitar la eliminación de sus datos personales en los términos descritos en el apartado 6 de los Términos y Condiciones.
- **Limitación del tratamiento**: solicitar que se limite el uso de sus datos en determinadas circunstancias.
- **Portabilidad**: recibir sus datos en un formato estructurado, de uso común y lectura mecánica, y transmitirlos a otro responsable cuando el tratamiento se base en el consentimiento o en un contrato.
- **Oposición**: oponerse al tratamiento de sus datos cuando el tratamiento se base en el interés legítimo.

Para ejercer cualquiera de estos derechos, el Usuario debe enviar un correo electrónico a soporte@athenyxward.com con el asunto "Ejercicio de derechos de protección de datos" indicando el derecho que desea ejercer y los datos necesarios para localizar su cuenta.

El Usuario también tiene derecho a presentar una reclamación ante la autoridad de control competente (**en Argentina, la Agencia de Acceso a la Información Pública — AAIP — argentina.gob.ar/aaip**) si considera que el tratamiento de sus datos infringe la legislación aplicable.

## 9. Medidas de seguridad

Athenyx Ward implementa medidas técnicas y organizativas apropiadas para proteger los datos personales del Usuario contra tratamiento no autorizado o ilícito y contra su pérdida, destrucción o daño accidental. Entre otras:

- Cifrado de los tokens de Google almacenados mediante AES-GCM ("un algoritmo de cifrado de alta seguridad").
- Uso de HTTPS ("protocolo seguro de comunicación en internet") para todas las comunicaciones entre el navegador del Usuario y el servidor de Athenyx Ward.
- Almacenamiento de contraseñas de la base de datos en un sistema de gestión de secretos.
- Control de acceso a los sistemas de producción restringido al personal autorizado.

## 10. Uso de cookies

Athenyx Ward utiliza cookies ("pequeños archivos de texto que el Servicio almacena en tu navegador para mantener tu sesión iniciada") solo con fines de autenticación y seguridad:

- \`athenyx_token\`: cookie de sesión que contiene el JWT ("token de autenticación cifrado") de acceso. HttpOnly ("configuración de seguridad que impide que la cookie sea accesible desde scripts"), sin embargo no se utiliza el flag Secure en desarrollo local.
- \`athenyx_refresh\`: cookie de refresh que permite la renovación automática de la sesión. HttpOnly.

No se utilizan cookies de seguimiento, publicidad ni cookies de terceros.

## 11. Menores de edad

El Servicio no está dirigido a menores de edad. Athenyx Ward no recopila deliberadamente datos personales de menores. Si se detecta que se han recopilado datos de un menor, Athenyx Ward procederá a su eliminación inmediata.

## 12. Cambios a esta Política de Privacidad

Esta Política de Privacidad puede ser actualizada periódicamente. En caso de cambios sustanciales, se aplicará el mismo procedimiento de notificación descrito en el apartado 8 de los Términos y Condiciones.

## 13. Contacto

Para cualquier cuestión relativa a esta Política de Privacidad, el Usuario puede contactar con Athenyx Ward en: soporte@athenyxward.com`;
