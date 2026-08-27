import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { OrganizationContextService } from '../../../core/services/organization-context.service';
import { ConsumableItemsComponent } from './consumable-items.component';

describe('ConsumableItemsComponent', () => {
  let httpMock: HttpTestingController;
  let currentBranchId$: BehaviorSubject<number | null>;

  beforeEach(async () => {
    currentBranchId$ = new BehaviorSubject<number | null>(7);

    await TestBed.configureTestingModule({
      imports: [ConsumableItemsComponent],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useValue: {
            getSnapshot: () => ({ user: { email: 'manager@test.com' } }),
          },
        },
        {
          provide: OrganizationContextService,
          useValue: {
            getSnapshot: () => ({ currentBranch: { id: 7, name: 'Satna' } }),
            currentBranchId$: currentBranchId$.asObservable(),
          },
        },
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('searches users through the current-branch endpoint with actor headers', () => {
    const fixture = TestBed.createComponent(ConsumableItemsComponent);
    const component = fixture.componentInstance;

    component.consumableUserSearchText = 'test';
    component.searchConsumableUsers();

    const req = httpMock.expectOne('/api/users/search/current-branch?query=test');
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.get('X-User-Email')).toBe('manager@test.com');

    req.flush([{ id: 11, name: 'Test User', email: 'test@example.com' }]);

    expect(component.consumableUsers).toEqual([{ id: 11, name: 'Test User', email: 'test@example.com' }]);
    expect(component.isLoadingConsumableUsers).toBeFalse();
  });
});
