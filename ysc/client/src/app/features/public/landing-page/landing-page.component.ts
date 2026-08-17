import { isPlatformBrowser, NgClass } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnDestroy, OnInit, PLATFORM_ID, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { BrandTitleComponent } from '../../../shared/components/brand-title/brand-title.component';
import { ClubLogoComponent } from '../../../shared/components/club-logo/club-logo.component';
import {
  ClubManagerProfile,
  CricketAcademyShowcase,
  FounderShowcase,
  KIDS_OCEAN_DREAMLAND_SHOWCASE,
  LANDING_FOUNDER_SHOWCASE,
  LANDING_ACTIVITIES,
  LANDING_BRANCHES,
  LANDING_CLUB_MANAGERS,
  LANDING_GALLERY_ITEMS,
  LANDING_NAV_ITEMS,
  LANDING_SOCIAL_LINKS,
  LANDING_STATS,
  LANDING_TESTIMONIALS,
  UPCOMING_VINDHYA_OLYMPICS_2K26,
  WINTER_OLYMPICS_2K25,
  YOUNGSTERS_CRICKET_ACADEMY_SATNA,
  LandingActivity,
  LandingBranch,
  LandingCarouselImage,
  LandingGalleryItem,
  LandingLink,
  LandingNavItem,
  LandingStat,
  LandingTestimonial,
  KidsOceanShowcase,
  UpcomingEventShowcase,
  TournamentCard,
  TournamentShowcase,
} from './landing-page.content';

@Component({
  selector: 'app-landing-page',
  standalone: true,
  imports: [RouterLink, NgClass, BrandTitleComponent, ClubLogoComponent],
  templateUrl: './landing-page.component.html',
  styleUrl: './landing-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LandingPageComponent implements OnInit, OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly platformId = inject(PLATFORM_ID);
  private galleryAutoAdvanceId: number | null = null;
  private touchStartX: number | null = null;
  private authStateSubscription: Subscription | null = null;

  readonly navItems: readonly LandingNavItem[] = LANDING_NAV_ITEMS;
  readonly stats: readonly LandingStat[] = LANDING_STATS;
  readonly activities: readonly LandingActivity[] = LANDING_ACTIVITIES;
  readonly branches: readonly LandingBranch[] = LANDING_BRANCHES;
  readonly galleryItems: readonly LandingGalleryItem[] = LANDING_GALLERY_ITEMS;
  readonly socialLinks: readonly LandingLink[] = LANDING_SOCIAL_LINKS;
  readonly testimonials: readonly LandingTestimonial[] = LANDING_TESTIMONIALS;
  readonly upcomingEvent: UpcomingEventShowcase = UPCOMING_VINDHYA_OLYMPICS_2K26;
  readonly winterOlympics: TournamentShowcase = WINTER_OLYMPICS_2K25;
  readonly winterOlympicsCards: readonly TournamentCard[] = WINTER_OLYMPICS_2K25.cards;
  readonly cricketAcademy: CricketAcademyShowcase = YOUNGSTERS_CRICKET_ACADEMY_SATNA;
  readonly cricketAcademyImages: readonly LandingCarouselImage[] = YOUNGSTERS_CRICKET_ACADEMY_SATNA.images;
  readonly kidsOcean: KidsOceanShowcase = KIDS_OCEAN_DREAMLAND_SHOWCASE;
  readonly kidsOceanImages: readonly LandingCarouselImage[] = KIDS_OCEAN_DREAMLAND_SHOWCASE.images;
  readonly founder: FounderShowcase = LANDING_FOUNDER_SHOWCASE;
  readonly clubManagers: readonly ClubManagerProfile[] = LANDING_CLUB_MANAGERS;

  protected readonly isAuthenticated = signal(false);
  protected readonly mobileMenuOpen = signal(false);
  protected readonly activeGalleryIndex = signal(0);
  protected readonly winterSummaryOpen = signal(false);
  protected readonly activeCricketImageIndex = signal(0);
  protected readonly activeKidsOceanImageIndex = signal(0);

  ngOnInit(): void {
    this.authStateSubscription = this.auth.isAuthenticated$.subscribe((isAuthenticated) => {
      this.isAuthenticated.set(isAuthenticated);
    });
    this.startGalleryAutoplay();
  }

  ngOnDestroy(): void {
    this.stopGalleryAutoplay();
    this.authStateSubscription?.unsubscribe();
  }

  protected toggleMobileMenu(): void {
    this.mobileMenuOpen.update((open) => !open);
  }

  protected closeMobileMenu(): void {
    this.mobileMenuOpen.set(false);
  }

  protected toggleWinterSummary(): void {
    this.winterSummaryOpen.update((open) => !open);
  }

  protected selectGallerySlide(index: number): void {
    if (index < 0 || index >= this.galleryItems.length) {
      return;
    }
    this.activeGalleryIndex.set(index);
    this.restartGalleryAutoplay();
  }

  protected showNextGallerySlide(): void {
    this.activeGalleryIndex.update((index) => (index + 1) % this.galleryItems.length);
  }

  protected showPreviousGallerySlide(): void {
    this.activeGalleryIndex.update((index) => (index - 1 + this.galleryItems.length) % this.galleryItems.length);
    this.restartGalleryAutoplay();
  }

  protected handleGalleryTouchStart(event: TouchEvent): void {
    this.touchStartX = event.touches[0]?.clientX ?? null;
  }

  protected handleGalleryTouchEnd(event: TouchEvent): void {
    if (this.touchStartX == null) {
      return;
    }

    const touchEndX = event.changedTouches[0]?.clientX ?? this.touchStartX;
    const delta = touchEndX - this.touchStartX;
    this.touchStartX = null;

    if (Math.abs(delta) < 40) {
      return;
    }

    if (delta < 0) {
      this.showNextGallerySlide();
    } else {
      this.showPreviousGallerySlide();
    }
    this.restartGalleryAutoplay();
  }

  protected selectCricketImage(index: number): void {
    if (index < 0 || index >= this.cricketAcademyImages.length) {
      return;
    }
    this.activeCricketImageIndex.set(index);
  }

  protected showNextCricketImage(): void {
    this.activeCricketImageIndex.update((index) => (index + 1) % this.cricketAcademyImages.length);
  }

  protected showPreviousCricketImage(): void {
    this.activeCricketImageIndex.update(
      (index) => (index - 1 + this.cricketAcademyImages.length) % this.cricketAcademyImages.length,
    );
  }

  protected selectKidsOceanImage(index: number): void {
    if (index < 0 || index >= this.kidsOceanImages.length) {
      return;
    }
    this.activeKidsOceanImageIndex.set(index);
  }

  protected showNextKidsOceanImage(): void {
    this.activeKidsOceanImageIndex.update((index) => (index + 1) % this.kidsOceanImages.length);
  }

  protected showPreviousKidsOceanImage(): void {
    this.activeKidsOceanImageIndex.update(
      (index) => (index - 1 + this.kidsOceanImages.length) % this.kidsOceanImages.length,
    );
  }

  protected getPrimaryCtaLabel(isAuthenticated: boolean | null): string {
    return isAuthenticated ? 'Open Dashboard' : 'Login';
  }

  protected trackByLabel(_: number, item: { label: string }): string {
    return item.label;
  }

  protected trackByTitle(_: number, item: { title: string }): string {
    return item.title;
  }

  protected trackByGalleryId(_: number, item: LandingGalleryItem): string {
    return item.id;
  }

  protected trackByCarouselId(_: number, item: LandingCarouselImage): string {
    return item.id;
  }

  private startGalleryAutoplay(): void {
    if (!isPlatformBrowser(this.platformId) || this.galleryItems.length <= 1) {
      return;
    }

    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      return;
    }

    this.galleryAutoAdvanceId = window.setInterval(() => {
      this.showNextGallerySlide();
    }, 4500);
  }

  private stopGalleryAutoplay(): void {
    if (this.galleryAutoAdvanceId != null && isPlatformBrowser(this.platformId)) {
      window.clearInterval(this.galleryAutoAdvanceId);
      this.galleryAutoAdvanceId = null;
    }
  }

  private restartGalleryAutoplay(): void {
    this.stopGalleryAutoplay();
    this.startGalleryAutoplay();
  }
}
