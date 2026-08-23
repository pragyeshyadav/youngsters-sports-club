package com.youngstersclub.app.dto;

import java.util.ArrayList;
import java.util.List;

public class CustomerMembershipSummaryDto {
  private Long organizationId;
  private String organizationName;
  private String role;
  private boolean active;
  private Long baseBranchId;
  private String baseBranchName;
  private List<OnboardingBranchDto> accessibleBranches = new ArrayList<>();

  public Long getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(Long organizationId) {
    this.organizationId = organizationId;
  }

  public String getOrganizationName() {
    return organizationName;
  }

  public void setOrganizationName(String organizationName) {
    this.organizationName = organizationName;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public Long getBaseBranchId() {
    return baseBranchId;
  }

  public void setBaseBranchId(Long baseBranchId) {
    this.baseBranchId = baseBranchId;
  }

  public String getBaseBranchName() {
    return baseBranchName;
  }

  public void setBaseBranchName(String baseBranchName) {
    this.baseBranchName = baseBranchName;
  }

  public List<OnboardingBranchDto> getAccessibleBranches() {
    return accessibleBranches;
  }

  public void setAccessibleBranches(List<OnboardingBranchDto> accessibleBranches) {
    this.accessibleBranches = accessibleBranches;
  }
}
