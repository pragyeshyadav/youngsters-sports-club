import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { LandingPageComponent } from './landing-page.component';

class AuthServiceStub {
  readonly isAuthenticatedSubject = new BehaviorSubject<boolean>(false);
  readonly isAuthenticated$ = this.isAuthenticatedSubject.asObservable();
}

describe('LandingPageComponent', () => {
  let fixture: ComponentFixture<LandingPageComponent>;
  let authService: AuthServiceStub;

  beforeEach(async () => {
    authService = new AuthServiceStub();

    await TestBed.configureTestingModule({
      imports: [LandingPageComponent],
      providers: [{ provide: AuthService, useValue: authService }],
    }).compileComponents();

    fixture = TestBed.createComponent(LandingPageComponent);
    fixture.detectChanges();
  });

  it('shows the login call to action for unauthenticated users', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Login');
    expect(text).not.toContain('Open Dashboard');
  });

  it('switches the primary call to action for authenticated users', () => {
    authService.isAuthenticatedSubject.next(true);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Open Dashboard');
  });

  it('renders the verified branch direction links', () => {
    const host = fixture.nativeElement as HTMLElement;
    const links = Array.from(host.querySelectorAll('.branch-card a')).map(
      (link) => (link as HTMLAnchorElement).href,
    );

    expect(links).toEqual([
      'https://maps.app.goo.gl/BcYn3kgfs1yXTf1LA',
      'https://maps.app.goo.gl/keJGzz3mPN86GoQG6',
      'https://maps.app.goo.gl/M7PgqUnT8s8gr2X29',
      'tel:+919765657902',
    ]);
  });

  it('renders curated testimonial content', () => {
    const host = fixture.nativeElement as HTMLElement;
    const testimonialCards = host.querySelectorAll('.testimonial-card');

    expect(testimonialCards.length).toBe(3);
    expect(host.textContent).toContain('4.9 / 5');
    expect(host.textContent).toContain('Call +91 97656 57902');
  });

  it('renders the image-led activity cards including Carrom, Chess, PS5, VR Games and Cricket Academy', () => {
    const host = fixture.nativeElement as HTMLElement;
    const activityCards = host.querySelectorAll('.activity-card');
    const text = host.textContent ?? '';

    expect(activityCards.length).toBe(9);
    expect(text).toContain('Snooker');
    expect(text).toContain('8 Ball Pool');
    expect(text).toContain('Table Tennis');
    expect(text).toContain('Carrom');
    expect(text).toContain('Chess');
    expect(text).toContain('PS5');
    expect(text).toContain('VR Games');
    expect(text).toContain('Youngsters Cricket Academy');
    expect(text).toContain('Kids Play');
  });

  it('renders the upcoming Vindhya Olympics 2K26 section before activities', () => {
    const host = fixture.nativeElement as HTMLElement;
    const text = host.textContent ?? '';
    const pageMarkup = host.innerHTML;

    expect(text).toContain('Upcoming Event');
    expect(text).toContain('Vindhya Olympics 2K26 starts from 30th August 2026.');
    expect(text).toContain('Login to Register');
    expect(text).toContain('₹10,000 + trophy');
    expect(pageMarkup.indexOf('id="upcoming-event"')).toBeLessThan(pageMarkup.indexOf('id="activities"'));
  });

  it('switches gallery slides when a different thumbnail is selected', () => {
    const component = fixture.componentInstance as any;

    component.selectGallerySlide(2);
    fixture.detectChanges();

    const activeThumb = fixture.nativeElement.querySelector('.gallery-thumb--active span') as HTMLElement;
    expect(activeThumb.textContent).toContain('Champions with trophies');
  });

  it('renders Winter Olympics 2K25 results and keeps 8 Ball Pool without fabricated winners', () => {
    const component = fixture.componentInstance as any;
    component.toggleWinterSummary();
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const text = host.textContent ?? '';

    expect(text).toContain('Winter Olympics 2K25');
    expect(text).toContain('Rajesh Maghlani');
    expect(text).toContain('Neeraj Soni');
    expect(text).toContain('Shubham Singh');
    expect(text).toContain('Abdul');
    expect(text).toContain('Achyut Mishra');
    expect(text).toContain('Aryan Shukla');
    expect(text).toContain('Tanmay Gupta');
    expect(text).toContain('Akarsh Gupta');
    expect(text).toContain('Rudra Shukla');
    expect(text).toContain('Arnav Jha');
    expect(text).toContain('8 Ball Pool');
    expect(text).not.toContain('8 Ball Pool Winner');
  });

  it('renders the Youngsters Cricket Academy Satna section with coach highlight', () => {
    const host = fixture.nativeElement as HTMLElement;
    const text = host.textContent ?? '';

    expect(text).toContain('Youngsters Cricket Academy Satna');
    expect(text).toContain('Mr Rishabh Yadav');
    expect(text).toContain('represented MP Rewa division');
  });

  it('switches cricket academy carousel images', () => {
    const component = fixture.componentInstance as any;

    component.selectCricketImage(3);
    fixture.detectChanges();

    const activeCaption = fixture.nativeElement.querySelector('.cricket-carousel__frame figcaption span') as HTMLElement;
    expect(activeCaption.textContent).toContain('Coach Rishabh Yadav with trophy and tournament recognition.');
  });

  it('renders the Kids Ocean Dreamland carousel and switches images', () => {
    const component = fixture.componentInstance as any;
    component.selectKidsOceanImage(4);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent ?? '';
    const captions = Array.from(
      fixture.nativeElement.querySelectorAll('.landing-section--kids .cricket-carousel__frame figcaption span'),
    ).map((node) => (node as HTMLElement).textContent ?? '');

    expect(text).toContain('Kids Ocean Dreamland');
    expect(captions.join(' ')).toContain('Soft-play movement zones designed for younger children.');
  });

  it('renders the founder section after testimonials content is present', () => {
    const host = fixture.nativeElement as HTMLElement;
    const text = host.textContent ?? '';
    const founderImages = host.querySelectorAll('.founder-showcase__image img');

    expect(text).toContain('Meet the Founder');
    expect(text).toContain('Pragyesh & Anushka Yadav');
    expect(text).toContain('Founder — Youngsters Sports Club & Kids Ocean Dreamland');
    expect(text).toContain('Software Engineer • Entrepreneur • Sports & Recreation Enthusiast');
    expect(founderImages.length).toBe(2);
  });

  it('renders the club manager section after the founder section', () => {
    const host = fixture.nativeElement as HTMLElement;
    const text = host.textContent ?? '';
    const managerImages = host.querySelectorAll('.club-manager-card__image img');
    const pageMarkup = host.innerHTML;

    expect(text).toContain('Meet Our Club Manager');
    expect(text).toContain('Prince Singh');
    expect(text).toContain('Manager — Satna');
    expect(text).toContain('Raghuwansh Yadav');
    expect(text).toContain('RTO & Club Manager — Rewa');
    expect(managerImages.length).toBe(2);
    expect(pageMarkup.indexOf('id="founder"')).toBeLessThan(pageMarkup.indexOf('id="club-managers"'));
  });
});
