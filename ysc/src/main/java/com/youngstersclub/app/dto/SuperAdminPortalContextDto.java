package com.youngstersclub.app.dto;

import java.util.ArrayList;
import java.util.List;

public class SuperAdminPortalContextDto {
  private List<OrganizationOptionDto> organizations = new ArrayList<>();
  private List<String> assignableRoles = new ArrayList<>();

  public List<OrganizationOptionDto> getOrganizations() {
    return organizations;
  }

  public void setOrganizations(List<OrganizationOptionDto> organizations) {
    this.organizations = organizations;
  }

  public List<String> getAssignableRoles() {
    return assignableRoles;
  }

  public void setAssignableRoles(List<String> assignableRoles) {
    this.assignableRoles = assignableRoles;
  }
}
