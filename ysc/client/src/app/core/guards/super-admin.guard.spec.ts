import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { superAdminGuard } from './super-admin.guard';

describe('superAdminGuard', () => {
  let httpMock: HttpTestingController;
  let router: Router;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['getSnapshot']);

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceSpy },
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('allows navigation when the backend confirms super admin access', () => {
    authServiceSpy.getSnapshot.and.returnValue({
      user: {
        id: '2',
        name: 'Super Admin',
        email: 'superadmin@example.com',
        profileImageUrl: '',
      },
      idToken: 'token',
      accessToken: 'token',
    });

    let result: unknown;
    TestBed.runInInjectionContext(() => {
      superAdminGuard({} as never, {} as never).subscribe((value) => {
        result = value;
      });
    });

    const req = httpMock.expectOne('/api/super-admin/context?email=superadmin%40example.com');
    expect(req.request.method).toBe('GET');
    req.flush({ organizations: [], assignableRoles: ['ADMIN', 'MANAGER'] });

    expect(result).toBeTrue();
  });

  it('redirects to dashboard when the backend denies access', () => {
    authServiceSpy.getSnapshot.and.returnValue({
      user: {
        id: '5',
        name: 'Regular Admin',
        email: 'admin@example.com',
        profileImageUrl: '',
      },
      idToken: 'token',
      accessToken: 'token',
    });

    const expected = router.createUrlTree(['/dashboard']);
    let result: unknown;
    TestBed.runInInjectionContext(() => {
      superAdminGuard({} as never, {} as never).subscribe((value) => {
        result = value;
      });
    });

    const req = httpMock.expectOne('/api/super-admin/context?email=admin%40example.com');
    req.flush({ message: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });

    expect((result as ReturnType<typeof router.createUrlTree>).toString()).toBe(expected.toString());
  });
});
