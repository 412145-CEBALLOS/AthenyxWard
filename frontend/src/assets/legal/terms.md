---
version: v1.0
date: 2026-07-14
---

Fecha de entrada en vigor: 14 de julio de 2026

## 1. Objeto

Athenyx Ward ("el Servicio") es una herramienta de análisis de seguridad del correo electrónico que utiliza técnicas heurísticas y, opcionalmente, inteligencia artificial local para evaluar el riesgo de amenazas en los mensajes de Gmail del usuario. Al utilizar el Servicio, el Usuario acepta quedar vinculado por los presentes Términos y Condiciones.

## 2. Datos de Gmail tratados

Athenyx Ward accede a la cuenta de Gmail del Usuario exclusivamente a través de la API de Google y únicamente con el alcance `gmail.readonly`. Los datos que el Servicio lee de Gmail son:

- **Remitente (dirección de correo electrónico)** del mensaje.
- **Fecha y hora de envío y recepción**.
- **Asunto y cuerpo del mensaje** (contenido textual).
- **URLs** incluidas en el cuerpo del mensaje.
- **Metadatos de cabecera** del mensaje (incluyendo indicadores de autenticación DKIM, SPF y DMARC).

El Servicio **no modifica, elimina ni envía ningún mensaje** de Gmail. Todas las operaciones son de solo lectura.

## 3. Inteligencia artificial local

El análisis de IA es realizado **exclusivamente en el dispositivo del usuario** mediante Ollama con el modelo Llama 3 (o Qwen 2.5, según la configuración del Servicio). **No existe comunicación con servidores externos ni servicios de IA en la nube** para el procesamiento de los correos. Los datos del usuario nunca abandonan su entorno local durante el análisis de IA.

## 4. Almacenamiento permanente

Athenyx Ward almacena de forma permanente los siguientes datos del usuario:

- **Dirección de correo electrónico, nombre y foto de perfil** obtenidos de la cuenta de Google.
- **Registros de análisis de correo**, incluyendo: fecha del análisis, nivel de riesgo calculado (en porcentaje), resumen del análisis heurístico y, si el usuario lo solicita, la explicación generada por IA.
- ** recordatorios creados por el usuario** vinculados a mensajes concretos.
- **Configuración de accesibilidad** (modo accesibilidad activado o desactivado).

Los datos de análisis se conservan mientras la cuenta del usuario esté activa. Athenyx Ward se reserva el derecho de eliminar datos de análisis inactivos después de un período de 12 meses sin actividad del usuario.

## 5. Contraseñas

Athenyx Ward **no almacena contraseñas**. La autenticación se realiza exclusivamente mediante OAuth 2.0 con Google. El Servicio nunca tiene acceso a la contraseña de la cuenta de Google del Usuario.

Los tokens de acceso y refresh de Google están cifrados mediante AES-GCM y se almacenan en la base de datos de Athenyx Ward para permitir la sincronización con Gmail. Estos tokens pueden ser revocados por el usuario en cualquier momento desde la configuración de su cuenta de Google.

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

El Servicio está destinado exclusivamente a personas mayores de edad. El uso del Servicio es responsabilidad del Usuario. Athenyx Ward no garantiza que el análisis heurístico o la explicación de IA detecten el 100% de las amenazas; se trata de una herramienta de ayuda, no de un sustituto del juicio profesional en materia de ciberseguridad.

## 8. Cambios a los Términos

Athenyx Ward se reserva el derecho de modificar los presentes Términos y Condiciones. En caso de modificación sustancial, se notificará al Usuario mediante un aviso en el Servicio la próxima vez que acceda tras la publicación de la nueva versión. El uso continuado del Servicio tras la notificación constituye aceptación de los nuevos términos.

Si el Usuario no acepta los nuevos términos, su único recurso es solicitar la eliminación de su cuenta conforme al apartado 6.

## 9. Ley aplicable y jurisdicción

Los presentes Términos y Condiciones se rigen por la legislación española. Cualquier controversia derivada de la interpretación o ejecución de los mismos será sometida a los tribunales competentes del domicilio del Usuario.

## 10. Contacto

Para cualquier cuestión relativa a estos Términos y Condiciones o a la Política de Privacidad, el Usuario puede contactar con Athenyx Ward en: soporte@athenyxward.com
