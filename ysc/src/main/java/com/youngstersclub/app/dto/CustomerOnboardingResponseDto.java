package com.youngstersclub.app.dto;

import java.util.ArrayList;
import java.util.List;

public class CustomerOnboardingResponseDto {
  private Integer userId;
  private String customerName;
  private Long organizationId;
  private String organizationName;
  private Long organizationUserId;
  private boolean membershipCreated;
  private boolean membershipReactivated;
  private Long baseBranchId;
  private String baseBranchName;
  private List<OnboardingBranchDto> branchesAdded = new ArrayList<>();
  private List<OnboardingBranchDto> alreadyAccessibleBranches = new ArrayList<>();

  public Integer getUserId() {
    return userId;
  }

  public void setUserId(Integer userId) {
    this.userId = userId;
  }

  public String getCustomerName() {
    return customerName;
  }

  public void setCustomerName(String customerName) {
    this.customerName = customerName;
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

  public List<OnboardingBranchDto> getBranchesAdded() {
    return branchesAdded;
  }

  public void setBranchesAdded(List<OnboardingBranchDto> branchesAdded) {
    this.branchesAdded = branchesAdded;
  }

  public List<OnboardingBranchDto> getAlreadyAccessibleBranches() {
    return alreadyAccessibleBranches;
  }

  public void setAlreadyAccessibleBranches(List<OnboardingBranchDto> alreadyAccessibleBranches) {
    this.alreadyAccessibleBranches = alreadyAccessibleBranches;
  }
}
