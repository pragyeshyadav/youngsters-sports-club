import { Component } from '@angular/core';
import { BrandTitleComponent } from '../../shared/components/brand-title/brand-title.component';
import { ClubLogoComponent } from '../../shared/components/club-logo/club-logo.component';

@Component({
  selector: 'app-admin-page',
  standalone: true,
  imports: [BrandTitleComponent, ClubLogoComponent],
  templateUrl: './admin-page.component.html',
  styleUrl: './admin-page.component.scss',
})
export class AdminPageComponent {}
