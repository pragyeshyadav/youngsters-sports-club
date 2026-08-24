import { ChangeDetectionStrategy, Component, Input, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { OrganizationContextService } from '../../../core/services/organization-context.service';

export type BrandTitleSize = 'small' | 'medium' | 'large';

@Component({
  selector: 'app-brand-title',
  standalone: true,
  templateUrl: './brand-title.component.html',
  styleUrl: './brand-title.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BrandTitleComponent {
  private static readonly DEFAULT_ORGANIZATION_NAME = 'Youngsters Sports Club & Cafe';
  private readonly organizationContextService = inject(OrganizationContextService);
  private readonly organizationContext = toSignal(this.organizationContextService.context$, {
    initialValue: this.organizationContextService.getSnapshot(),
  });

  @Input() size: BrandTitleSize = 'medium';
  @Input() organizationName: string | null = null;
  @Input() staticBranding = false;

  protected get resolvedOrganizationName(): string {
    if (this.staticBranding) {
      return this.normalizeName(this.organizationName) ?? BrandTitleComponent.DEFAULT_ORGANIZATION_NAME;
    }

    return this.normalizeName(this.organizationName)
        ?? this.normalizeName(this.organizationContext()?.currentOrganization?.name)
        ?? BrandTitleComponent.DEFAULT_ORGANIZATION_NAME;
  }

  protected normalizeName(name: string | null | undefined): string | null {
    const normalized = name?.trim();
    return normalized ? normalized : null;
  }
}
