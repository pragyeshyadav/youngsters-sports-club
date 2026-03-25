import { isPlatformBrowser } from '@angular/common';
import { inject, PLATFORM_ID } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { GOOGLE_TOKEN_STORAGE_KEY } from '../constants/storage.constants';

export const guestGuard: CanActivateFn = () => {
  const router = inject(Router);
  const platformId = inject(PLATFORM_ID);

  if (!isPlatformBrowser(platformId)) {
    return true;
  }

  const token = localStorage.getItem(GOOGLE_TOKEN_STORAGE_KEY);
  if (!token) {
    return true;
  }

  return router.createUrlTree(['/dashboard']);
};
