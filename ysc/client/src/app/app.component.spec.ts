import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { provideRouter, Router } from '@angular/router';
import { AppComponent } from './app.component';
import { AuthService } from './core/services/auth.service';

class AuthServiceStub {
  readonly isAuthenticatedSubject = new BehaviorSubject<boolean>(false);
  readonly isAuthenticated$ = this.isAuthenticatedSubject.asObservable();
}

describe('AppComponent', () => {
  let fixture: ComponentFixture<AppComponent>;
  let component: AppComponent;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useClass: AuthServiceStub },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AppComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it('treats the root route as public shell space', () => {
    expect(component['isPublicShellRoute']('/')).toBeTrue();
    expect(component['isPublicShellRoute']('')).toBeTrue();
  });

  it('does not treat dashboard routes as public shell space', () => {
    expect(component['isPublicShellRoute']('/dashboard')).toBeFalse();
  });

  it('falls back to the current router url when no url argument is provided', () => {
    spyOnProperty(router, 'url', 'get').and.returnValue('/');
    expect(component['isPublicShellRoute']()).toBeTrue();
  });
});
