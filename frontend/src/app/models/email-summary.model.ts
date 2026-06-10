/**
 * Lightweight Gmail message — list-row payload. Excludes the body and
 * the HTML preview; the SPA fetches those lazily through
 * {@code GET /api/emails/{id}}.
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
}

/**
 * Full Gmail message view returned by {@code GET /api/emails/{id}}.
 * Includes both the analysis-ready plain text and the HTML preview.
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