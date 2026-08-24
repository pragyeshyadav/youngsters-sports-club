import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

export type ClubLogoSize = 'sm' | 'md' | 'lg';

@Component({
  selector: 'app-club-logo',
  standalone: true,
  templateUrl: './club-logo.component.html',
  styleUrl: './club-logo.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClubLogoComponent {
  @Input() size: ClubLogoSize = 'md';
  @Input() organizationName: string = 'Youngsters Sports Club & Cafe';
  @Input() logoUrl: string | null = null;
  @Input() authenticated: boolean = false;

  readonly alt = this.getAltText();
  readonly src = this.getLogoSrc();

  private getAltText(): string {
    if (this.logoUrl) {
      return ` ${this.organizationName}`;
    }
    return 'Youngsters Sports Club, Satna';
  }

  private getLogoSrc(): string {
    if (this.logoUrl) {
      return this.logoUrl;
    }
    return '/images/logo.png';
  }
}