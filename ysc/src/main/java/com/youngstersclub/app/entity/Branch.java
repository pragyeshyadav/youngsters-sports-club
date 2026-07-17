package com.youngstersclub.app.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "branches")
public class Branch {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @Column(nullable = false, length = 150)
  private String name;

  @Column(name = "branch_code", length = 20)
  private String branchCode;

  @Column(columnDefinition = "TEXT")
  private String address;

  @Column(length = 100)
  private String city;

  @Column(length = 100)
  private String state;

  @Column(length = 20)
  private String phone;

  @Column(length = 150)
  private String email;

  @Column(precision = 10, scale = 7)
  private BigDecimal latitude;

  @Column(precision = 10, scale = 7)
  private BigDecimal longitude;

  @Column(name = "is_active")
  private Boolean isActive = true;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @JsonIgnore
  @OneToMany(mappedBy = "branch", fetch = FetchType.LAZY)
  private List<UserBranchAccess> userBranchAccesses = new ArrayList<>();

  @JsonIgnore
  @OneToMany(mappedBy = "baseBranch", fetch = FetchType.LAZY)
  private List<OrganizationUser> baseOrganizationUsers = new ArrayList<>();

  public Branch() {}

  public Branch(
      Long id,
      Organization organization,
      String name,
      String branchCode,
      String address,
      String city,
      String state,
      String phone,
      String email,
      BigDecimal latitude,
      BigDecimal longitude,
      Boolean isActive,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      List<UserBranchAccess> userBranchAccesses,
      List<OrganizationUser> baseOrganizationUsers) {
    this.id = id;
    this.organization = organization;
    this.name = name;
    this.branchCode = branchCode;
    this.address = address;
    this.city = city;
    this.state = state;
    this.phone = phone;
    this.email = email;
    this.latitude = latitude;
    this.longitude = longitude;
    this.isActive = isActive;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.userBranchAccesses = userBranchAccesses;
    this.baseOrganizationUsers = baseOrganizationUsers;
  }

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Organization getOrganization() { return organization; }
  public void setOrganization(Organization organization) { this.organization = organization; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getBranchCode() { return branchCode; }
  public void setBranchCode(String branchCode) { this.branchCode = branchCode; }
  public String getAddress() { return address; }
  public void setAddress(String address) { this.address = address; }
  public String getCity() { return city; }
  public void setCity(String city) { this.city = city; }
  public String getState() { return state; }
  public void setState(String state) { this.state = state; }
  public String getPhone() { return phone; }
  public void setPhone(String phone) { this.phone = phone; }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public BigDecimal getLatitude() { return latitude; }
  public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
  public BigDecimal getLongitude() { return longitude; }
  public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
  public Boolean getIsActive() { return isActive; }
  public void setIsActive(Boolean isActive) { this.isActive = isActive; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
  public List<UserBranchAccess> getUserBranchAccesses() { return userBranchAccesses; }
  public void setUserBranchAccesses(List<UserBranchAccess> userBranchAccesses) { this.userBranchAccesses = userBranchAccesses; }
  public List<OrganizationUser> getBaseOrganizationUsers() { return baseOrganizationUsers; }
  public void setBaseOrganizationUsers(List<OrganizationUser> baseOrganizationUsers) { this.baseOrganizationUsers = baseOrganizationUsers; }
}
