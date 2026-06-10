import { Pipe, PipeTransform } from '@angular/core';
import { EmailDetail, EmailSummary } from '../models/email-summary.model';

const MONTHS: Record<string, number> = {
  Jan: 0, Feb: 1, Mar: 2, Apr: 3, May: 4, Jun: 5,
  Jul: 6, Aug: 7, Sep: 8, Oct: 9, Nov: 10, Dec: 11,
};

/**
 * Renders a localised Spanish timestamp for an email row.
 *
 * <p>Prefers the raw {@code Date} header from Gmail (which keeps the
 * sender's timezone); falls back to the normalised
 * {@code receivedAt}. When the date cannot be parsed, returns the
 * original string unchanged so nothing breaks the layout.</p>
 *
 * <p>Output format: {@code "lun 14:32 - 02/06/2026"} (or just
 * {@code "14:32 - 02/06/2026"} when {@code showDayName} is
 * {@code false}).</p>
 */
@Pipe({
  name: 'emailDate',
  standalone: true,
  pure: true,
})
export class EmailDatePipe implements PipeTransform {
  transform(email: EmailSummary | EmailDetail | null | undefined, showDayName = true): string {
    if (!email) return '';
    const dateStr = email.originalDateHeader || email.receivedAt;
    if (!dateStr) return '';
    try {
      const date = this.parseDate(dateStr);
      if (Number.isNaN(date.getTime())) return dateStr;
      const dayName = date.toLocaleDateString('es-ES', { weekday: 'short' }).replace('.', '');
      const time = date.toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' });
      const day = date.getDate().toString().padStart(2, '0');
      const month = (date.getMonth() + 1).toString().padStart(2, '0');
      const year = date.getFullYear();
      return showDayName
        ? `${dayName} ${time} - ${day}/${month}/${year}`
        : `${time} - ${day}/${month}/${year}`;
    } catch {
      return dateStr;
    }
  }

  private parseDate(dateStr: string): Date {
    const isoMatch = dateStr.match(/(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2}:\d{2})/);
    if (isoMatch) return new Date(dateStr);
    return this.parseRfc2822(dateStr);
  }

  private parseRfc2822(dateStr: string): Date {
    const match = dateStr.match(/,?\s*(\d{1,2})\s+(\w{3})\s+(\d{4})\s+(\d{2}):(\d{2}):(\d{2})\s*([+-]\d{4})?/);
    if (!match) return new Date(dateStr);
    const day = parseInt(match[1], 10);
    const month = MONTHS[match[2]];
    const year = parseInt(match[3], 10);
    const hours = parseInt(match[4], 10);
    const minutes = parseInt(match[5], 10);
    const seconds = parseInt(match[6], 10);
    const tz = match[7];
    const date = new Date(Date.UTC(year, month, day, hours, minutes, seconds));
    if (tz) {
      const tzOffsetMinutes = parseInt(tz.slice(1), 10) / 100 * 60;
      const sign = tz[0] === '+' ? -1 : 1;
      date.setMinutes(date.getMinutes() + sign * tzOffsetMinutes);
    }
    return date;
  }
}
