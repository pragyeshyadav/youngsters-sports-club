import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthAccessTokenStore } from '../services/auth-access-token.store';
import { AuthService } from '../services/auth.service';

/** Attaches `Authorization: Bearer <accessToken>` for Spring Boot APIs. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.url.includes('/auth/google')) {
    return next(req);
  }
  const tokens = inject(AuthAccessTokenStore);
  const authService = inject(AuthService);
  const token = tokens.getAccessToken();
  const actorEmail = authService.getSnapshot()?.user.email?.trim();

  if (!token && !actorEmail) {
    return next(req);
  }

  const headers: Record<string, string> = {};
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  if (actorEmail) {
    headers['X-User-Email'] = actorEmail;
  }

  return next(
    req.clone({
      setHeaders: headers,
    }),
  );
};
