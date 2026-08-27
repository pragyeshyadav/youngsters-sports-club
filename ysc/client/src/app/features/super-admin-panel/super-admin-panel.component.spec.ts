import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AUTH_SESSION_STORAGE_KEY, GOOGLE_TOKEN_STORAGE_KEY } from '../../core/constants/storage.constants';
import { OrganizationContextService } from '../../core/services/organization-context.service';
import { SuperAdminPanelComponent } from './super-admin-panel.component';

function seedSuperAdminSession(email = 'superadmin@example.com'): void {
  localStorage.setItem(
    AUTH_SESSION_STORAGE_KEY,
    JSON.stringify({
      user: { id: '2', name: 'Super Admin', email, profileImageUrl: '' },
      idToken: 'token',
      accessToken: 'token',
    }),
  );
  localStorage.setItem(GOOGLE_TOKEN_STORAGE_KEY, 'token');
}

describe('SuperAdminPanelComponent', () => {
  let httpMock: HttpTestingController;
  let organizationContext: OrganizationContextService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SuperAdminPanelComponent],
      providers: [provideRouter([]), provideHttpClient(withInterceptorsFromDi()), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    organizationContext = TestBed.inject(OrganizationContextService);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.removeItem(AUTH_SESSION_STORAGE_KEY);
    localStorage.removeItem(GOOGLE_TOKEN_STORAGE_KEY);
  });

  it('renders the secured management panels with organization context branding', async () => {
    seedSuperAdminSession();
    (organizationContext as any).contextSubject.next({
      currentOrganization: {
        id: 10,
        name: 'Headquarter City Center Snooker Club',
        logoUrl: 'https://example.com/headquarter-logo.png',
      },
      currentBranch: { id: 20, name: 'Rewa' },
      currentRole: 'SUPER_ADMIN',
      hasPersistedContext: true,
      requiresSelection: false,
      availableOrganizations: [],
      accessibleBranches: [],
    });

    const fixture = TestBed.createComponent(SuperAdminPanelComponent);
    fixture.detectChanges();

    const contextReq = httpMock.expectOne('/api/super-admin/context?email=superadmin%40example.com');
    expect(contextReq.request.method).toBe('GET');
    contextReq.flush({
      organizations: [{ id: 10, name: 'Headquarter City Center Snooker Club', logoUrl: 'https://example.com/headquarter-logo.png' }],
      assignableRoles: ['ADMIN', 'MANAGER'],
    });

    const orgReq = httpMock.expectOne('/api/super-admin/organizations?email=superadmin%40example.com');
    orgReq.flush([
      { id: 10, name: 'Headquarter City Center Snooker Club', active: true, city: 'Rewa', state: 'MP', country: 'India' },
    ]);

    const branchReqs = httpMock.match('/api/super-admin/branches?email=superadmin%40example.com&organizationId=10');
    expect(branchReqs.length).toBe(3);
    branchReqs.forEach((request) => request.flush([
      { id: 20, organizationId: 10, name: 'Rewa', active: true },
      { id: 21, organizationId: 10, name: 'Satna', active: true },
    ]));

    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Super Admin Portal');
    expect(fixture.nativeElement.textContent).toContain('Organizations');
    expect(fixture.nativeElement.textContent).toContain('Branches');
    expect(fixture.nativeElement.textContent).toContain('Grant Access');
    expect(fixture.nativeElement.textContent).toContain('Onboard Staff');

    const brandFrame = fixture.nativeElement.querySelector('.authenticated-brand-frame') as HTMLElement | null;
    expect(brandFrame?.textContent).toContain('Headquarter City Center Snooker Club');
    expect(brandFrame?.textContent).not.toContain('Youngsters Sports Club & Cafe');
  });

  it('returns to the dashboard from the back link', () => {
    seedSuperAdminSession();
    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigate');
    const fixture = TestBed.createComponent(SuperAdminPanelComponent);

    fixture.componentInstance.goBack();

    expect(navigateSpy).toHaveBeenCalledWith(['/dashboard']);
  });
});
