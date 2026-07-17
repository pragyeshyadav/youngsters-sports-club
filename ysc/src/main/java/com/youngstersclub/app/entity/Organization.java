package com.youngstersclub.app.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "organizations")
public class Organization {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(name = "logo_url")
  private String logoUrl;

  @Column(length = 20)
  private String phone;

  @Column(length = 150)
  private String email;

  @Column(columnDefinition = "TEXT")
  private String address;

  @Column(length = 100)
  private String city;

  @Column(length = 100)
  private String state;

  @Column(length = 100)
  private String country;

  @Column(name = "is_active")
  private Boolean isActive = true;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @JsonIgnore
  @OneToMany(mappedBy = "organization", fetch = FetchType.LAZY)
  private List<Branch> branches = new ArrayList<>();

  @JsonIgnore
  @OneToMany(mappedBy = "organization", fetch = FetchType.LAZY)
  private List<OrganizationUser> organizationUsers = new ArrayList<>();

  @JsonIgnore
  @OneToOne(mappedBy = "organization", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  private OrganizationSettings settings;

  public Organization() {}

  public Organization(
      Long id,
      String name,
      String logoUrl,
      String phone,
      String email,
      String address,
      String city,
      String state,
      String country,
      Boolean isActive,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      List<Branch> branches,
      List<OrganizationUser> organizationUsers,
      OrganizationSettings settings) {
    this.id = id;
    this.name = name;
    this.logoUrl = logoUrl;
    this.phone = phone;
    this.email = email;
    this.address = address;
    this.city = city;
    this.state = state;
    this.country = country;
    this.isActive = isActive;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.branches = branches;
    this.organizationUsers = organizationUsers;
    this.settings = settings;
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

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }

  public Boolean getIsActive() {
    return isActive;
  }

  public void setIsActive(Boolean isActive) {
    this.isActive = isActive;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public List<Branch> getBranches() {
    return branches;
  }

  public void setBranches(List<Branch> branches) {
    this.branches = branches;
  }

  public List<OrganizationUser> getOrganizationUsers() {
    return organizationUsers;
  }

  public void setOrganizationUsers(List<OrganizationUser> organizationUsers) {
    this.organizationUsers = organizationUsers;
  }

  public OrganizationSettings getSettings() {
    return settings;
  }

  public void setSettings(OrganizationSettings settings) {
    this.settings = settings;
  }
}
