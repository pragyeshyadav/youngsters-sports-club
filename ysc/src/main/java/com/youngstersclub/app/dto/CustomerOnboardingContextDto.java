package com.youngstersclub.app.dto;

import java.util.ArrayList;
import java.util.List;

public class CustomerOnboardingContextDto {
  private String actorRole;
  private boolean organizationSelectable;
  private boolean multipleBranchSelectionAllowed;
  private Long currentOrganizationId;
  private String currentOrganizationName;
  private Long currentBranchId;
  private String currentBranchName;
  private List<OrganizationOptionDto> organizations = new ArrayList<>();
  private List<OnboardingBranchDto> branches = new ArrayList<>();

  public String getActorRole() {
    return actorRole;
  }

  public void setActorRole(String actorRole) {
    this.actorRole = actorRole;
  }

  public boolean isOrganizationSelectable() {
    return organizationSelectable;
  }

  public void setOrganizationSelectable(boolean organizationSelectable) {
    this.organizationSelectable = organizationSelectable;
  }

  public boolean isMultipleBranchSelectionAllowed() {
    return multipleBranchSelectionAllowed;
  }

  public void setMultipleBranchSelectionAllowed(boolean multipleBranchSelectionAllowed) {
    this.multipleBranchSelectionAllowed = multipleBranchSelectionAllowed;
  }

  public Long getCurrentOrganizationId() {
    return currentOrganizationId;
  }

  public void setCurrentOrganizationId(Long currentOrganizationId) {
    this.currentOrganizationId = currentOrganizationId;
  }

  public String getCurrentOrganizationName() {
    return currentOrganizationName;
  }

  public void setCurrentOrganizationName(String currentOrganizationName) {
    this.currentOrganizationName = currentOrganizationName;
  }

  public Long getCurrentBranchId() {
    return currentBranchId;
  }

  public void setCurrentBranchId(Long currentBranchId) {
    this.currentBranchId = currentBranchId;
  }

  public String getCurrentBranchName() {
    return currentBranchName;
  }

  public void setCurrentBranchName(String currentBranchName) {
    this.currentBranchName = currentBranchName;
  }

  public List<OrganizationOptionDto> getOrganizations() {
    return organizations;
  }

  public void setOrganizations(List<OrganizationOptionDto> organizations) {
    this.organizations = organizations;
  }

  public List<OnboardingBranchDto> getBranches() {
    return branches;
  }

  public void setBranches(List<OnboardingBranchDto> branches) {
    this.branches = branches;
  }
}
