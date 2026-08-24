import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AUTH_SESSION_STORAGE_KEY, GOOGLE_TOKEN_STORAGE_KEY } from '../../core/constants/storage.constants';
import { ClubSetupPortalComponent } from './club-setup-portal.component';

function seedAdminSession(email = 'admin@example.com'): void {
  localStorage.setItem(
    AUTH_SESSION_STORAGE_KEY,
    JSON.stringify({
      user: { id: 'user-1', name: 'Test Admin', email },
      idToken: 'token',
      accessToken: 'token',
    }),
  );
  localStorage.setItem(GOOGLE_TOKEN_STORAGE_KEY, 'token');
}

describe('ClubSetupPortalComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ClubSetupPortalComponent],
      providers: [provideRouter([]), provideHttpClient(withInterceptorsFromDi()), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.removeItem(AUTH_SESSION_STORAGE_KEY);
    localStorage.removeItem(GOOGLE_TOKEN_STORAGE_KEY);
  });

  function resolveRole(role: string): void {
    const req = httpMock.expectOne((r) => r.url.startsWith('/api/user?email='));
    expect(req.request.method).toBe('GET');
    req.flush({ role });
  }

  it('renders the three club setup panels and toggles them for admins', async () => {
    seedAdminSession();
    const fixture = TestBed.createComponent(ClubSetupPortalComponent);
    fixture.detectChanges();
    resolveRole('ADMIN');
    fixture.detectChanges();

    const buttons = (): NodeListOf<HTMLButtonElement> =>
      fixture.nativeElement.querySelectorAll('.panel-header');
    expect(buttons().length).toBe(3);
    expect(fixture.nativeElement.textContent).toContain('Update Snooker Table');
    expect(fixture.nativeElement.textContent).toContain('Update Consumable Items');
    expect(fixture.nativeElement.textContent).toContain('Update Manager');

    const tablesHeader = buttons()[0] as HTMLButtonElement;
    tablesHeader.click();
    await fixture.whenStable();
    fixture.detectChanges();

    const tablesReq = httpMock.expectOne('/api/snooker/tables/manage');
    expect(tablesReq.request.method).toBe('GET');
    expect(tablesReq.request.headers.get('X-User-Email')).toBe('admin@example.com');
    tablesReq.flush([]);

    expect(fixture.nativeElement.querySelector('.tables-submit-btn')).toBeTruthy();

    (buttons()[0] as HTMLButtonElement).click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.tables-submit-btn')).toBeFalsy();
  });

  it('hides management panels and shows the access message for non-admins', async () => {
    seedAdminSession('customer@example.com');
    const fixture = TestBed.createComponent(ClubSetupPortalComponent);
    fixture.detectChanges();
    resolveRole('CUSTOMER');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.panel-header').length).toBe(0);
    expect(fixture.nativeElement.textContent).toContain('This portal is available only for admin users.');
  });

  it('returns to the admin page via the back link', async () => {
    seedAdminSession();
    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigate');
    const fixture = TestBed.createComponent(ClubSetupPortalComponent);
    fixture.detectChanges();
    resolveRole('ADMIN');

    (fixture.nativeElement.querySelector('.back-link') as HTMLButtonElement).click();

    expect(navigateSpy).toHaveBeenCalledWith(['/admin-page']);
  });
});
