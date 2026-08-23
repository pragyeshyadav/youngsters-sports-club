package com.youngstersclub.app.dto;

public class CreateCustomerResponseDto {
  private String message;
  private Integer userId;
  private String customerName;
  private String phone;
  private Long organizationId;
  private String organizationName;
  private Long organizationUserId;
  private boolean membershipCreated;
  private boolean membershipReactivated;
  private Long baseBranchId;
  private String baseBranchName;
  private boolean branchAccessCreated;
  private boolean branchAccessReactivated;

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

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

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
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

  public boolean isBranchAccessCreated() {
    return branchAccessCreated;
  }

  public void setBranchAccessCreated(boolean branchAccessCreated) {
    this.branchAccessCreated = branchAccessCreated;
  }

  public boolean isBranchAccessReactivated() {
    return branchAccessReactivated;
  }

  public void setBranchAccessReactivated(boolean branchAccessReactivated) {
    this.branchAccessReactivated = branchAccessReactivated;
  }
}
