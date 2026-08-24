import { TestBed } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { BrandTitleComponent } from './brand-title.component';
import { OrganizationContextService } from '../../../core/services/organization-context.service';
import { OrganizationContext } from '../../../core/models/organization-context.models';

describe('BrandTitleComponent', () => {
  let contextSubject: BehaviorSubject<OrganizationContext | null>;

  beforeEach(async () => {
    contextSubject = new BehaviorSubject<OrganizationContext | null>(null);

    await TestBed.configureTestingModule({
      imports: [BrandTitleComponent],
      providers: [
        {
          provide: OrganizationContextService,
          useValue: {
            context$: contextSubject.asObservable(),
            getSnapshot: () => contextSubject.value,
          },
        },
      ],
    }).compileComponents();
  });

  it('renders the current organization name from context by default', () => {
    contextSubject.next(buildContext('Headquartor City Center Snooker Club'));
    const fixture = TestBed.createComponent(BrandTitleComponent);
    fixture.componentInstance.size = 'small';
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Headquartor City Center Snooker Club');
  });

  it('uses the static YSC brand on the landing-page mode', () => {
    contextSubject.next(buildContext('Headquartor City Center Snooker Club'));
    const fixture = TestBed.createComponent(BrandTitleComponent);
    fixture.componentInstance.size = 'small';
    fixture.componentInstance.staticBranding = true;
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Youngsters Sports Club & Cafe');
    expect(fixture.nativeElement.textContent).not.toContain('Headquartor City Center Snooker Club');
  });

  it('updates when the selected organization changes', () => {
    const fixture = TestBed.createComponent(BrandTitleComponent);
    fixture.componentInstance.size = 'small';
    contextSubject.next(buildContext('Area 7 Snooker Club'));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Area 7 Snooker Club');

    contextSubject.next(buildContext('Headquartor City Center Snooker Club'));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Headquartor City Center Snooker Club');
  });

  function buildContext(name: string): OrganizationContext {
    return {
      hasPersistedContext: true,
      requiresSelection: false,
      userId: 1,
      currentRole: 'ADMIN',
      currentOrganization: { id: 99, name, logoUrl: 'https://example.com/logo.png' },
      currentBranch: { id: 8, name: 'Rewa' },
      availableOrganizations: [],
      accessibleBranches: [],
    };
  }
});
