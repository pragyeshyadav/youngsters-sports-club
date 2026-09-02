import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SportsClubManagementSoftwareComponent } from './sports-club-management-software.component';

describe('SportsClubManagementSoftwareComponent', () => {
  let fixture: ComponentFixture<SportsClubManagementSoftwareComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SportsClubManagementSoftwareComponent],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(SportsClubManagementSoftwareComponent);
    fixture.detectChanges();
  });

  it('renders the product promise, demo and WhatsApp CTAs', () => {
    const host = fixture.nativeElement as HTMLElement;
    const text = host.textContent ?? '';

    expect(text).toContain('Sports Club Management Software for Modern Sports Clubs');
    expect(text).toContain('See Youngsters Sports Club Management System in Action');
    expect(text).toContain('Frequently Asked Questions');
    expect(host.querySelector('iframe')?.getAttribute('src')).toBe('https://www.youtube.com/embed/w50KSLIlVzI');
    expect(host.querySelector('a[href="https://wa.me/919765657902"]')).not.toBeNull();
  });

  it('publishes the canonical metadata and both structured-data schemas', () => {
    expect(document.title).toContain('Sports Club Management Software');
    expect(document.querySelector('link[data-product-canonical="true"]')?.getAttribute('href')).toBe(
      'https://youngsterssportsclub.com/sports-club-management-software',
    );

    const schema = document.getElementById('product-page-structured-data')?.textContent ?? '';
    expect(schema).toContain('SoftwareApplication');
    expect(schema).toContain('FAQPage');
  });

  it('keeps FAQ answers accessible after opening an item', () => {
    const component = fixture.componentInstance as any;
    component.toggleFaq(0);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Sports club management software brings daily club operations');
    expect(fixture.nativeElement.querySelector('.faq-item button')?.getAttribute('aria-expanded')).toBe('true');
  });
});
