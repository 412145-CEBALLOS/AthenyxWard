/**
 * Public user profile returned by {@code GET /api/auth/me} and friends.
 * Mirrors the backend's {@code UserInfo} DTO field-for-field.
 */
export interface UserInfo {
  id: number;
  name: string;
  email: string;
  pictureUrl: string;
  role: 'ADMIN' | 'PREMIUM' | 'TRIAL';
  trialEndDate: string | null;
  trialExpired: boolean;
  accessibilityMode: boolean;
  termsAcceptedAt: string | null;
  termsVersion: string | null;
}

/**
 * Response body of {@code POST /api/auth/refresh}. The fresh access
 * token is mirrored in the {@code athenyx_token} cookie; this DTO is
 * mostly for debugging.
 */
export interface RefreshResponse {
  accessToken: string;
  expiresIn: number;
}
