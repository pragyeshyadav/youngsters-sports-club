import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AUTH_SESSION_STORAGE_KEY, GOOGLE_TOKEN_STORAGE_KEY } from '../../core/constants/storage.constants';
import { OrganizationContextService } from '../../core/services/organization-context.service';
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
  let organizationContext: OrganizationContextService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminPageComponent],
      providers: [provideRouter([]), provideHttpClient(withInterceptorsFromDi()), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    organizationContext = TestBed.inject(OrganizationContextService);
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

  it('renders the authenticated organization branding from the selected context instead of stale YSC text', () => {
    seedSession('admin@example.com');
    const fixture = TestBed.createComponent(AdminPageComponent);

    (organizationContext as any).contextSubject.next({
      currentOrganization: {
        id: 7,
        name: 'Headquartor City Center Snooker Club',
        logoUrl: 'https://example.com/headquarter-logo.png',
      },
      currentBranch: { id: 9, name: 'Rewa' },
      currentRole: 'ADMIN',
      hasPersistedContext: true,
      requiresSelection: false,
      availableOrganizations: [],
      accessibleBranches: [],
    });

    fixture.detectChanges();
    flushUserRole('ADMIN');
    rerender(fixture);

    const brandFrame = fixture.nativeElement.querySelector('.authenticated-brand-frame') as HTMLElement | null;
    const brandLogo = fixture.nativeElement.querySelector('.admin-header app-club-logo img') as HTMLImageElement | null;

    expect(brandFrame?.textContent).toContain('Headquartor City Center Snooker Club');
    expect(brandFrame?.textContent).not.toContain('Youngsters Sports Club & Cafe');
    expect(brandLogo?.getAttribute('src')).toBe('https://example.com/headquarter-logo.png');
  });

  it('reloads the monthly earnings report when the selected organization or branch changes', () => {
    seedSession('admin@example.com');
    const fixture = TestBed.createComponent(AdminPageComponent);
    fixture.detectChanges();

    const userRequest = httpMock.expectOne((r) => r.url.startsWith('/api/user?email='));
    expect(userRequest.request.method).toBe('GET');
    userRequest.flush({ id: 101, role: 'ADMIN' });
    fixture.detectChanges();

    fixture.componentInstance.selectedMonth = '08';
    fixture.componentInstance.selectedYear = '2026';
    fixture.componentInstance.toggleMonthlyReport();
    fixture.detectChanges();

    const firstReportRequest = httpMock.expectOne('/api/admin/monthly-earnings?month=08&year=2026');
    expect(firstReportRequest.request.method).toBe('GET');
    firstReportRequest.flush({
      currentMonthTotal: 51094,
      previousMonthTotal: 40000,
      snookerEarnings: 37228.5,
      snookerTableBreakdown: {
        'Sharma S1': 37228.5,
      },
      consumableEarnings: 9000,
      kidsZoneEarnings: 4865.5,
    });
    fixture.detectChanges();

    expect(fixture.componentInstance.monthlyEarnings.currentMonthTotal).toBe(51094);
    expect(fixture.componentInstance.snookerBreakdownEntries).toEqual([
      { tableName: 'Sharma S1', amount: 37228.5 },
    ]);

    (organizationContext as any).contextSubject.next({
      currentOrganization: { id: 7, name: 'Area 7 Snooker Club' },
      currentBranch: { id: 9, name: 'Rewa' },
      currentRole: 'ADMIN',
      hasPersistedContext: true,
      requiresSelection: false,
    });
    fixture.detectChanges();

    const secondReportRequest = httpMock.expectOne('/api/admin/monthly-earnings?month=08&year=2026');
    expect(secondReportRequest.request.method).toBe('GET');
    secondReportRequest.flush({
      currentMonthTotal: 2200,
      previousMonthTotal: 1800,
      snookerEarnings: 1200,
      snookerTableBreakdown: {
        'Area 7 Arena': 1200,
      },
      consumableEarnings: 600,
      kidsZoneEarnings: 400,
    });
    fixture.detectChanges();

    expect(fixture.componentInstance.currentOrganizationName).toBe('Area 7 Snooker Club');
    expect(fixture.componentInstance.monthlyEarnings.currentMonthTotal).toBe(2200);
    expect(fixture.componentInstance.snookerBreakdownEntries).toEqual([
      { tableName: 'Area 7 Arena', amount: 1200 },
    ]);
  });

  it('loads today’s sent WhatsApp message statuses when the new status panel is expanded', () => {
    seedSession('admin@example.com');
    const fixture = TestBed.createComponent(AdminPageComponent);
    fixture.detectChanges();

    flushUserRole('ADMIN');
    rerender(fixture);

    const statusPanelButton = Array.from(
      fixture.nativeElement.querySelectorAll('.panel-header'),
    ).find((element) => (element as HTMLElement).textContent?.includes('Sent Whatsapp Message Status')) as HTMLButtonElement | undefined;

    expect(statusPanelButton).toBeTruthy();
    statusPanelButton?.click();
    fixture.detectChanges();

    const statusRequest = httpMock.expectOne('/api/admin/whatsapp-message-statuses?page=0');
    expect(statusRequest.request.method).toBe('GET');
    statusRequest.flush({
      messages: [
        {
          trackingId: 'accepted:wamid.1',
          customerName: 'Pragyesh',
          customerPhone: '919765657902',
          templateName: 'club_customer_notification_org_wise',
          status: 'DELIVERED',
          branchName: 'Satna',
          sentTime: '2026-08-29T10:30:00',
        },
      ],
      page: 0,
      pageSize: 20,
      hasMore: false,
    });
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Pragyesh');
    expect(text).toContain('club_customer_notification_org_wise');
    expect(text).toContain('Delivered');
    expect(text).toContain('Satna');
  });
});
