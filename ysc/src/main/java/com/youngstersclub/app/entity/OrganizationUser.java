package com.youngstersclub.app.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.youngstersclub.app.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "organization_users",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_organization_user_membership", columnNames = {"organization_id", "user_id"})
    })
public class OrganizationUser {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private UserRole role;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "base_branch_id")
  private Branch baseBranch;

  @Column(name = "last_selected_organization_id")
  private Long lastSelectedOrganizationId;

  @Column(name = "last_selected_branch_id")
  private Long lastSelectedBranchId;

  @Column(name = "is_active")
  private Boolean isActive = true;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @JsonIgnore
  @OneToMany(mappedBy = "organizationUser", fetch = FetchType.LAZY)
  private List<UserBranchAccess> branchAccesses = new ArrayList<>();

  public OrganizationUser() {}

  public OrganizationUser(
      Long id,
      Organization organization,
      User user,
      UserRole role,
      Branch baseBranch,
      Long lastSelectedOrganizationId,
      Long lastSelectedBranchId,
      Boolean isActive,
      LocalDateTime createdAt,
      List<UserBranchAccess> branchAccesses) {
    this.id = id;
    this.organization = organization;
    this.user = user;
    this.role = role;
    this.baseBranch = baseBranch;
    this.lastSelectedOrganizationId = lastSelectedOrganizationId;
    this.lastSelectedBranchId = lastSelectedBranchId;
    this.isActive = isActive;
    this.createdAt = createdAt;
    this.branchAccesses = branchAccesses;
  }

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Organization getOrganization() { return organization; }
  public void setOrganization(Organization organization) { this.organization = organization; }
  public User getUser() { return user; }
  public void setUser(User user) { this.user = user; }
  public UserRole getRole() { return role; }
  public void setRole(UserRole role) { this.role = role; }
  public Branch getBaseBranch() { return baseBranch; }
  public void setBaseBranch(Branch baseBranch) { this.baseBranch = baseBranch; }
  public Long getLastSelectedOrganizationId() { return lastSelectedOrganizationId; }
  public void setLastSelectedOrganizationId(Long lastSelectedOrganizationId) { this.lastSelectedOrganizationId = lastSelectedOrganizationId; }
  public Long getLastSelectedBranchId() { return lastSelectedBranchId; }
  public void setLastSelectedBranchId(Long lastSelectedBranchId) { this.lastSelectedBranchId = lastSelectedBranchId; }
  public Boolean getIsActive() { return isActive; }
  public void setIsActive(Boolean isActive) { this.isActive = isActive; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public List<UserBranchAccess> getBranchAccesses() { return branchAccesses; }
  public void setBranchAccesses(List<UserBranchAccess> branchAccesses) { this.branchAccesses = branchAccesses; }
}
