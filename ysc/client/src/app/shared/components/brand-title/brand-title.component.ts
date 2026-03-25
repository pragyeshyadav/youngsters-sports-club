import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

export type BrandTitleSize = 'small' | 'medium' | 'large';

@Component({
  selector: 'app-brand-title',
  standalone: true,
  templateUrl: './brand-title.component.html',
  styleUrl: './brand-title.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BrandTitleComponent {
  @Input() size: BrandTitleSize = 'medium';
}
