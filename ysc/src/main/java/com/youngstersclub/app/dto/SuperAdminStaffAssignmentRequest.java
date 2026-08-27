package com.youngstersclub.app.dto;

import java.util.List;

public class SuperAdminStaffAssignmentRequest {
  private String actorEmail;
  private Integer userId;
  private Long organizationId;
  private String role;
  private Long baseBranchId;
  private List<Long> branchIds;

  public String getActorEmail() {
    return actorEmail;
  }

  public void setActorEmail(String actorEmail) {
    this.actorEmail = actorEmail;
  }

  public Integer getUserId() {
    return userId;
  }

  public void setUserId(Integer userId) {
    this.userId = userId;
  }

  public Long getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(Long organizationId) {
    this.organizationId = organizationId;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public Long getBaseBranchId() {
    return baseBranchId;
  }

  public void setBaseBranchId(Long baseBranchId) {
    this.baseBranchId = baseBranchId;
  }

  public List<Long> getBranchIds() {
    return branchIds;
  }

  public void setBranchIds(List<Long> branchIds) {
    this.branchIds = branchIds;
  }
}
