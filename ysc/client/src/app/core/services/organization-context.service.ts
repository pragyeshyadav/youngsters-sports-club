import { isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { ORGANIZATION_CONTEXT_STORAGE_KEY } from '../constants/storage.constants';
import { BranchOption, OrganizationContext, OrganizationOption } from '../models/organization-context.models';

@Injectable({ providedIn: 'root' })
export class OrganizationContextService {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly http = inject(HttpClient);

  private readonly contextSubject = new BehaviorSubject<OrganizationContext | null>(this.readStoredContext());
  readonly context$: Observable<OrganizationContext | null> = this.contextSubject.asObservable();

  getSnapshot(): OrganizationContext | null {
    return this.contextSubject.value;
  }

  loadContext(email: string): Observable<OrganizationContext> {
    return this.http.get<OrganizationContext>(`/api/context?email=${encodeURIComponent(email)}`).pipe(
      tap((context) => this.persistContext(context)),
    );
  }

  getOrganizations(email: string): Observable<OrganizationOption[]> {
    return this.http.get<OrganizationOption[]>(`/api/organizations?email=${encodeURIComponent(email)}`);
  }

  getBranches(email: string, organizationId: number): Observable<BranchOption[]> {
    return this.http.get<BranchOption[]>(
      `/api/branches?email=${encodeURIComponent(email)}&organizationId=${organizationId}`,
    );
  }

  changeContext(email: string, organizationId: number, branchId: number): Observable<OrganizationContext> {
    return this.http.post<OrganizationContext>('/api/context/change', {
      email,
      organizationId,
      branchId,
    }).pipe(
      tap((context) => this.persistContext(context)),
    );
  }

  clearContext(): void {
    if (isPlatformBrowser(this.platformId)) {
      localStorage.removeItem(ORGANIZATION_CONTEXT_STORAGE_KEY);
    }
    this.contextSubject.next(null);
  }

  private persistContext(context: OrganizationContext): void {
    if (isPlatformBrowser(this.platformId)) {
      localStorage.setItem(ORGANIZATION_CONTEXT_STORAGE_KEY, JSON.stringify(context));
    }
    this.contextSubject.next(context);
  }

  private readStoredContext(): OrganizationContext | null {
    if (!isPlatformBrowser(this.platformId)) {
      return null;
    }
    const raw = localStorage.getItem(ORGANIZATION_CONTEXT_STORAGE_KEY);
    if (!raw) {
      return null;
    }

    try {
      return JSON.parse(raw) as OrganizationContext;
    } catch {
      localStorage.removeItem(ORGANIZATION_CONTEXT_STORAGE_KEY);
      return null;
    }
  }
}
