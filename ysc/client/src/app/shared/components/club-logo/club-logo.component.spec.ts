import { TestBed } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { ClubLogoComponent } from './club-logo.component';
import { OrganizationContextService } from '../../../core/services/organization-context.service';
import { OrganizationContext } from '../../../core/models/organization-context.models';

describe('ClubLogoComponent', () => {
  let contextSubject: BehaviorSubject<OrganizationContext | null>;

  beforeEach(async () => {
    contextSubject = new BehaviorSubject<OrganizationContext | null>(null);

    await TestBed.configureTestingModule({
      imports: [ClubLogoComponent],
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

  it('renders the current organization logo url from context by default', () => {
    contextSubject.next(buildContext('Headquartor City Center Snooker Club', 'https://example.com/headquarter.png'));
    const fixture = TestBed.createComponent(ClubLogoComponent);
    fixture.detectChanges();

    const img: HTMLImageElement = fixture.nativeElement.querySelector('img');
    expect(img.getAttribute('src')).toBe('https://example.com/headquarter.png');
    expect(img.getAttribute('alt')).toBe('Headquartor City Center Snooker Club');
  });

  it('falls back to the YSC logo when logo url is missing but keeps the organization name', () => {
    contextSubject.next(buildContext('Area 7 Snooker Club', null));
    const fixture = TestBed.createComponent(ClubLogoComponent);
    fixture.detectChanges();

    const img: HTMLImageElement = fixture.nativeElement.querySelector('img');
    expect(img.getAttribute('src')).toBe('/images/logo.png');
    expect(img.getAttribute('alt')).toBe('Area 7 Snooker Club');
  });

  it('falls back to the YSC logo when the organization logo fails to load', () => {
    contextSubject.next(buildContext('Area 7 Snooker Club', 'https://example.com/broken.png'));
    const fixture = TestBed.createComponent(ClubLogoComponent);
    fixture.detectChanges();

    const img: HTMLImageElement = fixture.nativeElement.querySelector('img');
    img.dispatchEvent(new Event('error'));
    fixture.detectChanges();

    expect(img.getAttribute('src')).toBe('/images/logo.png');
    expect(img.getAttribute('alt')).toBe('Area 7 Snooker Club');
  });

  it('keeps the landing page static when static branding mode is enabled', () => {
    contextSubject.next(buildContext('Headquartor City Center Snooker Club', 'https://example.com/headquarter.png'));
    const fixture = TestBed.createComponent(ClubLogoComponent);
    fixture.componentInstance.staticBranding = true;
    fixture.detectChanges();

    const img: HTMLImageElement = fixture.nativeElement.querySelector('img');
    expect(img.getAttribute('src')).toBe('/images/logo.png');
    expect(img.getAttribute('alt')).toBe('Youngsters Sports Club & Cafe');
  });

  function buildContext(name: string, logoUrl: string | null): OrganizationContext {
    return {
      hasPersistedContext: true,
      requiresSelection: false,
      userId: 1,
      currentRole: 'ADMIN',
      currentOrganization: { id: 99, name, logoUrl: logoUrl ?? undefined },
      currentBranch: { id: 8, name: 'Rewa' },
      availableOrganizations: [],
      accessibleBranches: [],
    };
  }
});
