/**
 * Single entry of the bell-panel + toast notification stream.
 * Mirrors the backend's {@code UpcomingReminderNotification} DTO.
 */
export interface UpcomingNotification {
  reminderId: number;
  emailId: number;
  emailSubject: string;
  emailSender: string;
  message: string | null;
  reminderDate: string;
  isOverdue: boolean;
}
