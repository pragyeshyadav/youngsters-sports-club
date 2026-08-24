import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AUTH_SESSION_STORAGE_KEY, GOOGLE_TOKEN_STORAGE_KEY } from '../../core/constants/storage.constants';
import { AdminPageComponent } from './admin-page.component';

function seedSession(email: string): void {
  localStorage.setItem(
    AUTH_SESSION_STORAGE_KEY,
    JSON.stringify({
      user: { id: 'user-1', name: 'Test User', email },
      idToken: 'token',
      accessToken: 'token',
    }),
  );
  localStorage.setItem(GOOGLE_TOKEN_STORAGE_KEY, 'token');
}

describe('AdminPageComponent – Club Setup Portal entry', () => {
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminPageComponent],
      providers: [provideRouter([]), provideHttpClient(withInterceptorsFromDi()), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.removeItem(AUTH_SESSION_STORAGE_KEY);
    localStorage.removeItem(GOOGLE_TOKEN_STORAGE_KEY);
  });

  function flushUserRole(role: string): void {
    const req = httpMock.expectOne((r) => r.url.startsWith('/api/user?email='));
    expect(req.request.method).toBe('GET');
    req.flush({ role });
    httpMock.match((r) => r.method === 'GET').forEach((match) => match.flush([]));
  }

  function rerender(fixture: ReturnType<typeof TestBed.createComponent<AdminPageComponent>>): void {
    fixture.detectChanges();
  }

  it('shows the Club Setup Portal button for admins', async () => {
    seedSession('admin@example.com');
    const navigateSpy = spyOn(router, 'navigate');
    const fixture = TestBed.createComponent(AdminPageComponent);
    fixture.detectChanges();

    flushUserRole('ADMIN');
    rerender(fixture);

    const button = fixture.nativeElement.querySelector(
      '.club-setup-nav-btn',
    ) as HTMLButtonElement | null;
    expect(button).toBeTruthy();
    expect(button?.textContent).toContain('Club Setup Portal');

    button?.click();
    await fixture.whenStable();
    expect(navigateSpy).toHaveBeenCalledWith(['/club-setup-portal']);
  });

  it('hides the Club Setup Portal button for non-admins', async () => {
    seedSession('customer@example.com');
    const fixture = TestBed.createComponent(AdminPageComponent);
    fixture.detectChanges();

    flushUserRole('CUSTOMER');
    rerender(fixture);

    expect(fixture.nativeElement.querySelector('.club-setup-nav-btn')).toBeNull();
  });
});
