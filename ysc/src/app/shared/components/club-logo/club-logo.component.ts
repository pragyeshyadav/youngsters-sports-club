import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CLUB_LOGO_URL } from '../../constants/branding.constants';

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

  readonly src = CLUB_LOGO_URL;
  readonly alt = 'Youngsters Sports Club, Satna';
}
