/**
 * Origin of an AI explanation, written by the backend
 * {@code AiExplanationService} and stored in the
 * {@code ai_explanations} table.
 */
export type AiOrigin = 'LLM' | 'FALLBACK';

/**
 * Spanish labels for every {@link AiOrigin}. Used as badge text
 * in the AI explanation card rendered by the email viewer.
 */
export const AI_ORIGIN_LABELS: Record<AiOrigin, string> = {
  LLM: 'Generado por IA',
  FALLBACK: 'Explicación heurística',
};

/**
 * UI state of the AI explanation pane for a single email.
 */
export type AiState = 'idle' | 'loading' | 'ready' | 'unavailable-trial' | 'error';

/**
 * Full payload returned by the AI explanation endpoint.
 *mirrors the backend {@code AiExplanationResponse} DTO.
 */
export interface AiExplanation {
  id: number;
  summary: string | null;
  heuristicExplanation: string | null;
  secondOpinion: string | null;
  origin: AiOrigin;
  modelName: string;
  generatedAt: string;
}
