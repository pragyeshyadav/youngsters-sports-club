import { inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from '../services/auth.service';

interface SuperAdminContextResponse {
  organizations?: unknown[];
  assignableRoles?: string[];
}

export const superAdminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const http = inject(HttpClient);
  const router = inject(Router);

  const actorEmail = auth.getSnapshot()?.user.email?.trim();
  if (!actorEmail) {
    return router.createUrlTree(['/login']);
  }

  return http
    .get<SuperAdminContextResponse>(`/api/super-admin/context?email=${encodeURIComponent(actorEmail)}`)
    .pipe(
      map(() => true),
      catchError(() => of(router.createUrlTree(['/dashboard']))),
    );
};
