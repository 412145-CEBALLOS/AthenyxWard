---
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

- **Dirección IP**: recopilada en cada solicitud HTTP para medidas de seguridad y prevención de fraude.
- **Agente de usuario**: información del navegador utilizada para diagnóstico de incidencias.
- **Identificador de correlación**: identificador único generado para cada sesión que permite relacionar eventos de distintos servicios.

## 3. Finalidades del tratamiento

Los datos personales del Usuario son tratados para las siguientes finalidades:

1. **Autenticación**: verificar la identidad del Usuario mediante OAuth 2.0 con Google.
2. **Prestación del Servicio**: realizar el análisis heurístico y, en su caso, la explicación por IA de los mensajes de Gmail solicitados por el Usuario.
3. **Recordatorios**: gestionar los recordatorios creados por el Usuario y notificarle cuando se aproximen.
4. **Mejora del Servicio**: los datos de uso agregados y anonimizados pueden ser utilizados para identificar patrones de uso y mejorar la precisión de los análisis heurísticos.
5. **Comunicación de incidencias**: contactar con el Usuario en caso de detectarse una incidencia de seguridad relevante en su cuenta.
6. **Cumplimiento legal**:保留 datos durante el período que la legislación aplicable exija para el cumplimiento de obligaciones legales.

## 4. Base jurídica del tratamiento

El tratamiento de datos personales realizado por Athenyx Ward se fundamenta en las siguientes bases jurídicas:

- **Ejecución de un contrato** (artículo 6.1.b del RGPD): los tratamientos necesarios para prestar el Servicio al Usuario, incluyendo el análisis de correos y la gestión de recordatorios, se realizan porque son necesarios para la ejecución del contrato de uso del Servicio.
- **Consentimiento explícito** (artículo 6.1.a del RGPD): el tratamiento de los datos para la explicación por IA y la aceptación de los Términos y Condiciones se realizan con el consentimiento explícito del Usuario, que puede ser revocado en cualquier momento.
- **Interés legítimo** (artículo 6.1.f del RGPD): la recopilación de datos de navegación (IP, agente de usuario) para medidas de seguridad se realiza en virtud del interés legítimo de Athenyx Ward de proteger la integridad del Servicio.

## 5. Inteligencia artificial local

Los datos de los mensajes de Gmail del Usuario **no son transmitidos a ningún servicio externo de IA**. El análisis mediante inteligencia artificial se realiza en el dispositivo del Usuario utilizando Ollama con el modelo Llama 3 (o Qwen 2.5, según la configuración del Servicio). Los datos del Usuario no abandonan su entorno local durante ningún momento del proceso de análisis de IA.

## 6. Destinatarios de los datos

Los datos personales del Usuario **no son vendidos, alquilados ni cedidos a terceros** fuera de los siguientes supuestos:

- **Proveedores de infraestructura**: Athenyx Ward utiliza proveedores de hosting y bases de datos (actualmente, MySQL en servidor propio) para el almacenamiento de datos. Estos proveedores tratan los datos únicamente siguiendo las instrucciones de Athenyx Ward y no para fines propios.
- **Obligaciones legales**: los datos podrán ser comunicados a las autoridades competentes cuando así lo exija la legislación aplicable, notamment en el ámbito de la prevención del fraude o la investigación de delitos.

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

Para ejercer cualquiera de estos derechos, el Usuario debe enviar un correo electrónico a soporte@athenyxward.com con el assunto "Ejercicio de derechos de protección de datos" indicando el derecho que desea ejercer y los datos necesarios para localizar su cuenta.

El Usuario también tiene derecho a presentar una reclamación ante la autoridad de control competente (en España, la Agencia Española de Protección de Datos — aepd.es) si considera que el tratamiento de sus datos infringe la legislación aplicable.

## 9. Medidas de seguridad

Athenyx Ward implementa medidas técnicas y organizativas apropiadas para proteger los datos personales del Usuario contra tratamiento no autorizado o ilícito y contra su pérdida, destrucción o daño accidental. Entre otras:

- Cifrado de los tokens de Google almacenados mediante AES-GCM.
- Uso de HTTPS para todas las comunicaciones entre el navegador del Usuario y el servidor de Athenyx Ward.
- Almacenamiento de contraseñas de la base de datos en un sistema de gestión de secretos.
- Control de acceso a los sistemas de producción restringido al personal autorizado.

## 10. Uso de cookies

Athenyx Ward utiliza cookies solo con fines de autenticación y seguridad:

- `athenyx_token`: cookie de sesión que contiene el JWT de acceso. HttpOnly, sin embargo no se utiliza el flag Secure en desarrollo local.
- `athenyx_refresh`: cookie de refresh que permite la renovación automática de la sesión. HttpOnly.

No se utilizan cookies de seguimiento, publicidad ni cookies de terceros.

## 11. Menores de edad

El Servicio no está dirigido a menores de edad. Athenyx Ward no recopila deliberadamente datos personales de menores. Si se detecta que se han recopilado datos de un menor, Athenyx Ward procederá a su eliminación inmediata.

## 12. Cambios a esta Política de Privacidad

Esta Política de Privacidad puede ser actualizada periódicamente. En caso de cambios sustanciales, se aplicará el mismo procedimiento de notificación descrito en el apartado 8 de los Términos y Condiciones.

## 13. Contacto

Para cualquier cuestión relativa a esta Política de Privacidad, el Usuario puede contactar con Athenyx Ward en: soporte@athenyxward.com
