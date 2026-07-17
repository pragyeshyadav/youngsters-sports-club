package com.youngstersclub.app.dto;

import java.util.List;

public class CustomerOnboardingRequest {
  private String actorEmail;
  private Integer userId;
  private Long organizationId;
  private List<Long> branchIds;
  private Long baseBranchId;

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

  public List<Long> getBranchIds() {
    return branchIds;
  }

  public void setBranchIds(List<Long> branchIds) {
    this.branchIds = branchIds;
  }

  public Long getBaseBranchId() {
    return baseBranchId;
  }

  public void setBaseBranchId(Long baseBranchId) {
    this.baseBranchId = baseBranchId;
  }
}
