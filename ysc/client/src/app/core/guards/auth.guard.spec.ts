import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { GOOGLE_TOKEN_STORAGE_KEY } from '../constants/storage.constants';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  let router: Router;

  beforeEach(() => {
    localStorage.removeItem(GOOGLE_TOKEN_STORAGE_KEY);

    TestBed.configureTestingModule({
      providers: [provideRouter([])],
    });

    router = TestBed.inject(Router);
  });

  afterEach(() => {
    localStorage.removeItem(GOOGLE_TOKEN_STORAGE_KEY);
  });

  it('redirects unauthenticated users to /login', () => {
    const expected = router.createUrlTree(['/login']);

    const result = TestBed.runInInjectionContext(() => authGuard({} as any, {} as any));

    expect(result?.toString()).toBe(expected.toString());
  });

  it('allows navigation when a Google token exists', () => {
    localStorage.setItem(GOOGLE_TOKEN_STORAGE_KEY, 'token');

    const result = TestBed.runInInjectionContext(() => authGuard({} as any, {} as any));

    expect(result).toBeTrue();
  });
});
