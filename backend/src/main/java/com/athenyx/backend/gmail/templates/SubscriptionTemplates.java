package com.athenyx.backend.gmail.templates;

public final class SubscriptionTemplates {

    private SubscriptionTemplates() {}

    public static String welcomeSubject() {
        return "¡Bienvenido a Premium — Athenyx Ward";
    }

    public static String welcomePlain(String name, String plan, String price, String renewsAt) {
        return String.format("""
            Hola %s,

            ¡Felicidades! Tu suscripción Premium de Athenyx Ward está activa.

            Resumen:
              Plan: %s
              Importe: %s
              Próxima renovación: %s

            Con Premium tienes acceso a:
              - Análisis ilimitados de correos
              - Explicaciones con IA para cada correo
              - Recordatorios y marcas importantes
              - Historial completo sin límite

            Para ver tu actividad y gestionar tu suscripción, visitá Mi Plan en Athenyx Ward.

            ¡Gracias por confiar en nosotros!

            El equipo de Athenyx Ward
            """, name, plan, price, renewsAt);
    }

    public static String welcomeHtml(String name, String plan, String price, String renewsAt) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; color: #333;">
              <h2 style="color: #1d4ed8;">¡Bienvenido a Premium, %s!</h2>
              <p>Tu suscripción <strong>Premium</strong> está activa. Gracias por confiar en <strong>Athenyx Ward</strong>.</p>
              <table style="width: 100%%; border-collapse: collapse; margin: 20px 0;">
                <tr><td style="padding: 8px; border-bottom: 1px solid #eee;"><strong>Plan</strong></td><td style="padding: 8px; border-bottom: 1px solid #eee;">%s</td></tr>
                <tr><td style="padding: 8px; border-bottom: 1px solid #eee;"><strong>Importe</strong></td><td style="padding: 8px; border-bottom: 1px solid #eee;">%s</td></tr>
                <tr><td style="padding: 8px;"><strong>Próxima renovación</strong></td><td style="padding: 8px;">%s</td></tr>
              </table>
              <p>¿Preguntas? Escribinos a <a href="mailto:soporte@athenyxward.com">soporte@athenyxward.com</a>.</p>
              <p style="color: #888; font-size: 12px;">El equipo de Athenyx Ward</p>
            </body>
            </html>
            """, name, plan, price, renewsAt);
    }

    public static String cancelSubject() {
        return "Tu suscripción Premium fue cancelada — Athenyx Ward";
    }

    public static String cancelPlain(String name, String canceledAt, String effectiveUntil) {
        return String.format("""
            Hola %s,

            Tu suscripción Premium de Athenyx Ward fue cancelada el %s.

            Seguís teniendo acceso a todas las funciones Premium hasta el %s. A partir de esa fecha volverás al plan gratuito.

            ¿Qué cambia con el plan gratuito?
              - Volvés a tener %d análisis de correos por mes
              - No podés usar recordatorios ni marcas importantes
              - El historial queda limitado a 7 días
              - Las explicaciones con IA no están disponibles

            ¿Fue un problema con el servicio? Escribinos a soporte@athenyxward.com antes de irte.

            El equipo de Athenyx Ward
            """, name, canceledAt, effectiveUntil);
    }

    public static String cancelHtml(String name, String canceledAt, String effectiveUntil) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; color: #333;">
              <h2 style="color: #dc2626;">Tu suscripción fue cancelada</h2>
              <p>Hola <strong>%s</strong>,</p>
              <p>Tu plan Premium fue cancelado el %s. Seguís teniendo acceso completo hasta el %s; a partir de esa fecha volverás al plan gratuito.</p>
              <p style="color: #888; font-size: 12px;">El equipo de Athenyx Ward · <a href="mailto:soporte@athenyxward.com">soporte@athenyxward.com</a></p>
            </body>
            </html>
            """, name, canceledAt, effectiveUntil);
    }
}
