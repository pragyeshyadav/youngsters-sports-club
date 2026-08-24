package com.youngstersclub.app.dto;

public class OrganizationOptionDto {
  private Long id;
  private String name;
  private String logoUrl;

  public OrganizationOptionDto() {}

  public OrganizationOptionDto(Long id, String name) {
    this.id = id;
    this.name = name;
  }

  public OrganizationOptionDto(Long id, String name, String logoUrl) {
    this.id = id;
    this.name = name;
    this.logoUrl = logoUrl;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getLogoUrl() {
    return logoUrl;
  }

  public void setLogoUrl(String logoUrl) {
    this.logoUrl = logoUrl;
  }
}
