import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthAccessTokenStore } from '../services/auth-access-token.store';

/** Attaches `Authorization: Bearer <accessToken>` for Spring Boot APIs. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const tokens = inject(AuthAccessTokenStore);
  const token = tokens.getAccessToken();
  if (!token) {
    return next(req);
  }
  return next(
    req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`,
      },
    }),
  );
};
