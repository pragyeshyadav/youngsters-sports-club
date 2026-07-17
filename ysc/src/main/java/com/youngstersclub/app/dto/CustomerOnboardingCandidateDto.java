package com.youngstersclub.app.dto;

import java.util.ArrayList;
import java.util.List;

public class CustomerOnboardingCandidateDto {
  private Integer userId;
  private String name;
  private String email;
  private String phone;
  private List<CustomerMembershipSummaryDto> memberships = new ArrayList<>();

  public Integer getUserId() {
    return userId;
  }

  public void setUserId(Integer userId) {
    this.userId = userId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public List<CustomerMembershipSummaryDto> getMemberships() {
    return memberships;
  }

  public void setMemberships(List<CustomerMembershipSummaryDto> memberships) {
    this.memberships = memberships;
  }
}
