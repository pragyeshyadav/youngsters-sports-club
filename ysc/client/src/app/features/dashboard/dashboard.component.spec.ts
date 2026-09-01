import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { DashboardComponent } from './dashboard.component';

describe('DashboardComponent - Kids Ocean Dreamland visibility', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [provideRouter([]), provideHttpClient(withInterceptorsFromDi())],
    }).compileComponents();
  });

  it('shows the Kids Ocean Dreamland panel when the current context enables kids play', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    const component = fixture.componentInstance;

    component.organizationContext = {
      hasPersistedContext: true,
      requiresSelection: false,
      kidsPlayEnabled: true,
      userId: 1,
      currentRole: 'CUSTOMER',
      currentOrganization: { id: 1, name: 'Youngsters Sports Club & Kids Ocean Dreamland' },
      currentBranch: { id: 2, name: 'Satna' },
      availableOrganizations: [],
      accessibleBranches: [],
    };
    component.showPhoneInput = false;
    component.showOrganizationSetup = false;

    expect((component as DashboardComponent & { shouldShowKidsPlayCard(): boolean }).shouldShowKidsPlayCard()).toBeTrue();
  });

  it('hides the Kids Ocean Dreamland panel when the current context does not enable kids play', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    const component = fixture.componentInstance;

    component.organizationContext = {
      hasPersistedContext: true,
      requiresSelection: false,
      kidsPlayEnabled: false,
      userId: 1,
      currentRole: 'CUSTOMER',
      currentOrganization: { id: 7, name: 'Area 7 Snooker Club' },
      currentBranch: { id: 9, name: 'Rewa' },
      availableOrganizations: [],
      accessibleBranches: [],
    };
    component.showPhoneInput = false;
    component.showOrganizationSetup = false;

    expect((component as DashboardComponent & { shouldShowKidsPlayCard(): boolean }).shouldShowKidsPlayCard()).toBeFalse();
  });

  it('allows the Super Admin portal for the legacy super admin user id', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    const component = fixture.componentInstance;

    expect(
      (component as DashboardComponent & {
        isSuperAdminUser(user: { id?: number | null; role?: string | null } | null): boolean;
      }).isSuperAdminUser({ id: 2, role: 'CUSTOMER' }),
    ).toBeTrue();
  });

  it('allows the Super Admin portal for users with the SUPER_ADMIN role', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    const component = fixture.componentInstance;

    expect(
      (component as DashboardComponent & {
        isSuperAdminUser(user: { id?: number | null; role?: string | null } | null): boolean;
      }).isSuperAdminUser({ id: 99, role: 'SUPER_ADMIN' }),
    ).toBeTrue();
  });

  it('renders the available-tables slot only for the CUSTOMER organization role', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    const component = fixture.componentInstance;
    component.userRole = 'ADMIN';
    component.organizationContext = {
      hasPersistedContext: true,
      requiresSelection: false,
      userId: 1,
      currentRole: 'CUSTOMER',
      currentOrganization: { id: 1, name: 'Organization A' },
      currentBranch: { id: 1, name: 'Branch A' },
      availableOrganizations: [],
      accessibleBranches: [],
    };

    const roleVisibility = component as DashboardComponent & {
      shouldShowOngoingFramesToday(): boolean;
      shouldShowAvailableTables(): boolean;
    };
    expect(roleVisibility.shouldShowOngoingFramesToday()).toBeFalse();
    expect(roleVisibility.shouldShowAvailableTables()).toBeTrue();
  });

  for (const role of ['ADMIN', 'MANAGER', 'SUPER_ADMIN']) {
    it(`renders the ongoing-frames slot for the ${role} organization role`, () => {
      const fixture = TestBed.createComponent(DashboardComponent);
      const component = fixture.componentInstance;
      component.organizationContext = {
        hasPersistedContext: true,
        requiresSelection: false,
        userId: 1,
        currentRole: role,
        currentOrganization: { id: 1, name: 'Organization A' },
        currentBranch: { id: 1, name: 'Branch A' },
        availableOrganizations: [],
        accessibleBranches: [],
      };

      expect((component as DashboardComponent & { shouldShowOngoingFramesToday(): boolean }).shouldShowOngoingFramesToday()).toBeTrue();
    });
  }
});
