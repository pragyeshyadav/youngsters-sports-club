export interface OrganizationOption {
  id: number;
  name: string;
}

export interface BranchOption {
  id: number;
  name: string;
}

export interface OrganizationContext {
  hasPersistedContext: boolean;
  requiresSelection: boolean;
  userId: number | null;
  currentRole: string | null;
  currentOrganization: OrganizationOption | null;
  currentBranch: BranchOption | null;
  availableOrganizations: OrganizationOption[];
  accessibleBranches: BranchOption[];
}
