package com.youngstersclub.app.dto;

import java.util.ArrayList;
import java.util.List;

public class SuperAdminStaffAssignmentResponseDto {
  private Integer userId;
  private String userName;
  private Long organizationId;
  private String organizationName;
  private Long organizationUserId;
  private String assignedRole;
  private boolean membershipCreated;
  private boolean membershipReactivated;
  private Long baseBranchId;
  private String baseBranchName;
  private List<OnboardingBranchDto> activeBranches = new ArrayList<>();

  public Integer getUserId() {
    return userId;
  }

  public void setUserId(Integer userId) {
    this.userId = userId;
  }

  public String getUserName() {
    return userName;
  }

  public void setUserName(String userName) {
    this.userName = userName;
  }

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

  public Long getOrganizationUserId() {
    return organizationUserId;
  }

  public void setOrganizationUserId(Long organizationUserId) {
    this.organizationUserId = organizationUserId;
  }

  public String getAssignedRole() {
    return assignedRole;
  }

  public void setAssignedRole(String assignedRole) {
    this.assignedRole = assignedRole;
  }

  public boolean isMembershipCreated() {
    return membershipCreated;
  }

  public void setMembershipCreated(boolean membershipCreated) {
    this.membershipCreated = membershipCreated;
  }

  public boolean isMembershipReactivated() {
    return membershipReactivated;
  }

  public void setMembershipReactivated(boolean membershipReactivated) {
    this.membershipReactivated = membershipReactivated;
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

  public List<OnboardingBranchDto> getActiveBranches() {
    return activeBranches;
  }

  public void setActiveBranches(List<OnboardingBranchDto> activeBranches) {
    this.activeBranches = activeBranches;
  }
}
