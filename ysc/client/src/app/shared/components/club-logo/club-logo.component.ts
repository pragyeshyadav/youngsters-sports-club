import { ChangeDetectionStrategy, Component, Input, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { OrganizationContextService } from '../../../core/services/organization-context.service';

export type ClubLogoSize = 'sm' | 'md' | 'lg';

@Component({
  selector: 'app-club-logo',
  standalone: true,
  templateUrl: './club-logo.component.html',
  styleUrl: './club-logo.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClubLogoComponent {
  private static readonly DEFAULT_LOGO_SRC = '/images/logo.png';
  private static readonly DEFAULT_ORGANIZATION_NAME = 'Youngsters Sports Club & Cafe';
  private readonly organizationContextService = inject(OrganizationContextService);
  private readonly organizationContext = toSignal(this.organizationContextService.context$, {
    initialValue: this.organizationContextService.getSnapshot(),
  });
  private imageLoadFailed = false;
  private lastResolvedCandidate: string | null = null;

  @Input() size: ClubLogoSize = 'md';
  @Input() organizationName: string | null = null;
  @Input() logoUrl: string | null = null;
  @Input() authenticated: boolean = false;
  @Input() staticBranding = false;

  protected get resolvedAlt(): string {
    return this.resolveOrganizationName();
  }

  protected get resolvedSrc(): string {
    const candidate = this.resolvePreferredLogoUrl();
    if (candidate !== this.lastResolvedCandidate) {
      this.lastResolvedCandidate = candidate;
      this.imageLoadFailed = false;
    }

    if (this.imageLoadFailed || !candidate) {
      return ClubLogoComponent.DEFAULT_LOGO_SRC;
    }

    return candidate;
  }

  protected handleImageError(): void {
    if (this.resolvedSrc === ClubLogoComponent.DEFAULT_LOGO_SRC) {
      return;
    }
    this.imageLoadFailed = true;
  }

  protected resolveOrganizationName(): string {
    if (this.staticBranding) {
      return this.normalizeName(this.organizationName) ?? ClubLogoComponent.DEFAULT_ORGANIZATION_NAME;
    }

    return this.normalizeName(this.organizationName)
        ?? this.normalizeName(this.organizationContext()?.currentOrganization?.name)
        ?? ClubLogoComponent.DEFAULT_ORGANIZATION_NAME;
  }

  protected resolvePreferredLogoUrl(): string | null {
    if (this.staticBranding) {
      return this.normalizeLogoUrl(this.logoUrl);
    }

    return this.normalizeLogoUrl(this.logoUrl)
        ?? this.normalizeLogoUrl(this.organizationContext()?.currentOrganization?.logoUrl);
  }

  protected normalizeName(name: string | null | undefined): string | null {
    const normalized = name?.trim();
    return normalized ? normalized : null;
  }

  protected normalizeLogoUrl(logoUrl: string | null | undefined): string | null {
    const normalized = logoUrl?.trim();
    return normalized ? normalized : null;
  }
}
