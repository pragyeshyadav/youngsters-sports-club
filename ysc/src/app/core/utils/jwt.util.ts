/**
 * Decodes JWT payload (middle segment) without verifying signature.
 * Suitable only for reading trusted issuer tokens (e.g. Google id_token) for display.
 */
export function decodeJwtPayload<T extends object>(token: string): T {
  const parts = token.split('.');
  if (parts.length < 2) {
    throw new Error('Invalid JWT: expected 3 segments');
  }
  const base64Url = parts[1];
  const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
  const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
  const json = atob(padded);
  return JSON.parse(json) as T;
}
