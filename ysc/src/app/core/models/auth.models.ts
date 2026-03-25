/** Decoded Google ID token claims we care about (subset of standard OIDC claims). */
export interface GoogleIdTokenClaims {
  sub: string;
  email: string;
  name: string;
  picture: string;
  email_verified?: boolean;
}

/** Response from Spring Boot POST /api/auth/google (mocked in dev). */
export interface GoogleAuthApiResponse {
  accessToken: string;
  refreshToken?: string;
  expiresIn?: number;
  tokenType?: string;
}

/** Normalized signed-in user for UI and guards. */
export interface AuthUser {
  id: string;
  name: string;
  email: string;
  profileImageUrl: string;
}

/**
 * Full session: Google ID token (frontend-only GSI) + same token as Bearer until a backend exists.
 * Persisted to localStorage for restore on refresh.
 */
export interface AuthSession {
  user: AuthUser;
  idToken: string;
  accessToken: string;
  refreshToken?: string;
  accessTokenExpiresAt?: number;
}
