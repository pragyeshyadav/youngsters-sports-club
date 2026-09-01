import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { OrganizationContextService } from '../../../core/services/organization-context.service';
import { OrganizationContext } from '../../../core/models/organization-context.models';
import { OngoingFramesTodayComponent } from './ongoing-frames-today.component';

describe('OngoingFramesTodayComponent', () => {
  const branchOneContext: OrganizationContext = {
    hasPersistedContext: true,
    requiresSelection: false,
    userId: 1,
    currentRole: 'MANAGER',
    currentOrganization: { id: 1, name: 'Organization A' },
    currentBranch: { id: 1, name: 'Branch A' },
    availableOrganizations: [],
    accessibleBranches: [],
  };
  let contextSubject: BehaviorSubject<OrganizationContext | null>;
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    contextSubject = new BehaviorSubject<OrganizationContext | null>(branchOneContext);
    await TestBed.configureTestingModule({
      imports: [OngoingFramesTodayComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useValue: { getSnapshot: () => ({ user: { email: 'manager@example.com' } }) },
        },
        {
          provide: OrganizationContextService,
          useValue: {
            getSnapshot: () => contextSubject.value,
            context$: contextSubject.asObservable(),
          },
        },
      ],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('loads the existing branch-scoped endpoint only when expanded', () => {
    const fixture = TestBed.createComponent(OngoingFramesTodayComponent);
    fixture.detectChanges();

    httpTesting.expectNone('/api/frame/ongoing/today');
    fixture.componentInstance.toggleExpand();

    const request = httpTesting.expectOne('/api/frame/ongoing/today');
    expect(request.request.headers.get('X-User-Email')).toBe('manager@example.com');
    request.flush([{ id: 10, tableId: 2, tableName: 'S1', startTime: '2026-09-01T09:00:00', status: 'STARTED', startedBy: 'Manager', players: ['Player A'] }]);

    expect(fixture.componentInstance.ongoingFrames.map((frame) => frame.id)).toEqual([10]);
  });

  it('clears stale frames and reloads when the active branch changes while expanded', () => {
    const fixture = TestBed.createComponent(OngoingFramesTodayComponent);
    fixture.detectChanges();
    fixture.componentInstance.toggleExpand();
    httpTesting.expectOne('/api/frame/ongoing/today').flush([{ id: 10, tableId: 2, tableName: 'S1', startTime: '2026-09-01T09:00:00', status: 'STARTED', startedBy: 'Manager', players: ['Player A'] }]);

    contextSubject.next({ ...branchOneContext, currentBranch: { id: 2, name: 'Branch B' } });

    expect(fixture.componentInstance.ongoingFrames).toEqual([]);
    httpTesting.expectOne('/api/frame/ongoing/today').flush([]);
  });
});
