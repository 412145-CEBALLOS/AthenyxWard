/**
 * Full reminder view returned by the reminder CRUD endpoints.
 * Mirrors the backend's {@code ReminderResponse} DTO.
 */
export interface Reminder {
  id: number;
  emailId: number;
  reminderDate: string;
  message: string | null;
  done: boolean;
  createdAt: string;
  updatedAt: string;
}

/**
 * Lightweight reminder payload used to enrich email list/detail
 * endpoints. The chip UI only needs the date and the done flag.
 */
export interface ReminderSummary {
  id: number;
  reminderDate: string;
  done: boolean;
}

/** Filter selector for `GET /api/reminders`. */
export type ReminderFilter = 'all' | 'pending' | 'done';

/** Body for `POST /api/reminders`. */
export interface CreateReminderRequest {
  emailId: number;
  reminderDate: string;
  message?: string | null;
}

/** Body for `PATCH /api/reminders/{id}`. Every field is optional. */
export interface UpdateReminderRequest {
  reminderDate?: string;
  message?: string | null;
  done?: boolean;
}
