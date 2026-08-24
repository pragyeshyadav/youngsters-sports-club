import { AsyncPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { map, distinctUntilChanged } from 'rxjs';
import { Observable } from 'rxjs';
import { RouterLink } from '@angular/router';
import { AuthUser } from '../../../core/models/auth.models';
import { AuthService } from '../../../core/services/auth.service';
import { OrganizationContextService } from '../../../core/services/organization-context.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [AsyncPipe, RouterLink],
  templateUrl: './header.component.html',
  styleUrl: './header.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HeaderComponent {
  private readonly auth = inject(AuthService);
  private readonly orgContext = inject(OrganizationContextService);

  readonly user$: Observable<AuthUser | null> = this.auth.user$;
  readonly organizationName$: Observable<string> = this.orgContext.context$.pipe(
    map((context) => {
      if (!context || !context.currentOrganization) {
        return 'Youngsters Sports Club & Cafe';
      }
      return context.currentOrganization.name || 'Youngsters Sports Club & Cafe';
    }),
    distinctUntilChanged(),
  );

  logout(): void {
    this.auth.logout();
  }
}