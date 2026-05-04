import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
  },
  {
    path: 'snooker-frame',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/snooker-frame/snooker-frame.component').then((m) => m.SnookerFrameComponent),
  },
  {
    path: 'start-frame',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/start-frame/start-frame.component').then((m) => m.StartFrameComponent),
  },
  {
    path: 'my-game-history',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/my-game-history/my-game-history.component').then((m) => m.MyGameHistoryComponent),
  },
  {
    path: 'admin-page',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/admin-page/admin-page.component').then((m) => m.AdminPageComponent),
  },
  {
    path: 'managers-portal',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/managers-portal/managers-portal.component').then((m) => m.ManagersPortalComponent),
  },
  {
    path: 'payment-settlement',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/payment-settlement/payment-settlement.component').then((m) => m.PaymentSettlementComponent),
  },
  {
    path: 'kids-play',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/kids-play/kids-play.component').then((m) => m.KidsPlayComponent),
  },
  {
    path: 'tournament-registration',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/summer-olympics-registration/summer-olympics-registration.component').then((m) => m.SummerOlympicsRegistrationComponent),
  },
  { path: '**', redirectTo: 'dashboard' },
];
