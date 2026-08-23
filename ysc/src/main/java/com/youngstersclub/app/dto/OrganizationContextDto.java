package com.youngstersclub.app.dto;

import java.util.ArrayList;
import java.util.List;

public class OrganizationContextDto {
  private boolean hasPersistedContext;
  private boolean requiresSelection;
  private Integer userId;
  private String currentRole;
  private OrganizationOptionDto currentOrganization;
  private BranchOptionDto currentBranch;
  private List<OrganizationOptionDto> availableOrganizations = new ArrayList<>();
  private List<BranchOptionDto> accessibleBranches = new ArrayList<>();

  public boolean isHasPersistedContext() {
    return hasPersistedContext;
  }

  public void setHasPersistedContext(boolean hasPersistedContext) {
    this.hasPersistedContext = hasPersistedContext;
  }

  public boolean isRequiresSelection() {
    return requiresSelection;
  }

  public void setRequiresSelection(boolean requiresSelection) {
    this.requiresSelection = requiresSelection;
  }

  public Integer getUserId() {
    return userId;
  }

  public void setUserId(Integer userId) {
    this.userId = userId;
  }

  public String getCurrentRole() {
    return currentRole;
  }

  public void setCurrentRole(String currentRole) {
    this.currentRole = currentRole;
  }

  public OrganizationOptionDto getCurrentOrganization() {
    return currentOrganization;
  }

  public void setCurrentOrganization(OrganizationOptionDto currentOrganization) {
    this.currentOrganization = currentOrganization;
  }

  public BranchOptionDto getCurrentBranch() {
    return currentBranch;
  }

  public void setCurrentBranch(BranchOptionDto currentBranch) {
    this.currentBranch = currentBranch;
  }

  public List<OrganizationOptionDto> getAvailableOrganizations() {
    return availableOrganizations;
  }

  public void setAvailableOrganizations(List<OrganizationOptionDto> availableOrganizations) {
    this.availableOrganizations = availableOrganizations;
  }

  public List<BranchOptionDto> getAccessibleBranches() {
    return accessibleBranches;
  }

  public void setAccessibleBranches(List<BranchOptionDto> accessibleBranches) {
    this.accessibleBranches = accessibleBranches;
  }
}
