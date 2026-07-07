/**
 * Lightweight Gmail message — list-row payload. Excludes the body and
 * the HTML preview; the SPA fetches those lazily through
 * {@code GET /api/emails/{id}}.
 *
 * <p>{@code riskPercentage} and {@code riskLevel} carry the result of the
 * most recent security analysis (if any). When null, the email has never
 * been analysed and the list row renders no traffic-light indicator.</p>
 *
 * <p>{@code reminder} is non-null when the user has a reminder
 * configured for the email — the list row uses it to draw the
 * small bell chip.</p>
 */
export interface EmailSummary {
  id: number | null;
  gmailId: string;
  sender: string;
  senderName: string;
  subject: string;
  snippet: string;
  receivedAt: string;
  fetchedAt: string;
  isRead: boolean;
  originalDateHeader: string | null;
  isImportant: boolean;
  isHidden: boolean;
  riskPercentage?: number | null;
  riskLevel?: 'GREEN' | 'YELLOW' | 'RED' | null;
  reminder?: import('./reminder.model').ReminderSummary | null;
}

/**
 * Full Gmail message view returned by {@code GET /api/emails/{id}}.
 * Includes both the analysis-ready plain text and the HTML preview.
 *
 * <p>{@code reminder} is non-null when the current user has a
 * reminder configured for this email — the viewer uses it to render
 * the banner without an extra round-trip.</p>
 */
export interface EmailDetail {
  id: number;
  gmailId: string;
  sender: string;
  senderName: string;
  subject: string;
  snippet: string;
  contentForAnalysis: string;
  htmlContent: string | null;
  receivedAt: string;
  fetchedAt: string;
  isRead: boolean;
  originalDateHeader: string | null;
  isImportant: boolean;
  isHidden: boolean;
  reminder?: import('./reminder.model').ReminderSummary | null;
}

/**
 * Paginated response of {@code GET /api/emails/fetch}. Server page
 * size is fixed at 20 messages.
 */
export interface EmailPageResponse {
  emails: EmailSummary[];
  currentPage: number;
  pageSize: number;
  hasNextPage: boolean;
}

export interface ImportantToggleResponse {
  emailId: number;
  isImportant: boolean;
}