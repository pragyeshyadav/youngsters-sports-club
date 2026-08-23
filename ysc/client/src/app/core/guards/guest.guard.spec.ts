import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { GOOGLE_TOKEN_STORAGE_KEY } from '../constants/storage.constants';
import { guestGuard } from './guest.guard';

describe('guestGuard', () => {
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

  it('allows unauthenticated users to reach /login', () => {
    const result = TestBed.runInInjectionContext(() => guestGuard({} as any, {} as any));
    expect(result).toBeTrue();
  });

  it('redirects authenticated users to the existing dashboard destination', () => {
    const expected = router.createUrlTree(['/dashboard']);
    localStorage.setItem(GOOGLE_TOKEN_STORAGE_KEY, 'token');

    const result = TestBed.runInInjectionContext(() => guestGuard({} as any, {} as any));

    expect(result?.toString()).toBe(expected.toString());
  });
});
