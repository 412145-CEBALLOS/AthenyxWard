import { HttpErrorResponse } from '@angular/common/http';

/**
 * Spanish user-facing message keyed by HTTP status code. Used as a
 * fallback when the backend doesn't include a meaningful error body.
 */
export const HTTP_ERROR_MESSAGES: Readonly<Record<number, string>> = {
  0: 'Sin conexión con el servidor. Verifica tu conexión a internet.',
  400: 'Solicitud no válida. Revisa los datos enviados.',
  403: 'No tienes permiso para realizar esta acción.',
  404: 'No se encontró el recurso solicitado.',
  408: 'La solicitud tardó demasiado. Inténtalo de nuevo.',
  409: 'Conflicto al procesar la solicitud.',
  413: 'La solicitud es demasiado grande.',
  422: 'Los datos enviados no son válidos.',
  429: 'Demasiadas solicitudes. Espera un momento e inténtalo de nuevo.',
  500: 'Error interno del servidor. Inténtalo más tarde.',
  502: 'El servidor no está disponible. Inténtalo más tarde.',
  503: 'Servicio no disponible temporalmente.',
  504: 'El servidor tardó demasiado en responder.',
};

/**
 * Picks the most useful user-facing string for a failed HTTP call.
 *
 * <p>Precedence:
 * <ol>
 *     <li>{@code error} / {@code message} field in the response body
 *         (matches what the backend's {@code GlobalExceptionHandler}
 *         produces).</li>
 *     <li>{@link HTTP_ERROR_MESSAGES} entry for the status code.</li>
 *     <li>A generic fallback mentioning the status code.</li>
 * </ol>
 */
export function resolveErrorMessage(err: HttpErrorResponse): string {
  const backendMessage = extractBackendMessage(err);
  if (backendMessage) {
    return backendMessage;
  }

  const mapped = HTTP_ERROR_MESSAGES[err.status];
  if (mapped) {
    return mapped;
  }

  return `Ocurrió un error inesperado (código ${err.status}).`;
}

function extractBackendMessage(err: HttpErrorResponse): string | null {
  const body = err.error;
  if (!body) {
    return null;
  }

  if (typeof body === 'string' && body.trim().length > 0) {
    return body.trim();
  }

  if (typeof body === 'object') {
    const candidate = (body as Record<string, unknown>)['error'];
    if (typeof candidate === 'string' && candidate.trim().length > 0) {
      return candidate.trim();
    }
    const message = (body as Record<string, unknown>)['message'];
    if (typeof message === 'string' && message.trim().length > 0) {
      return message.trim();
    }
  }

  return null;
}
