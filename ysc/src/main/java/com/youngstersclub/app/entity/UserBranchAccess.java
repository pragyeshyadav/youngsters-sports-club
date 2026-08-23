package com.youngstersclub.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "user_branch_access",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_user_branch_access_membership_branch",
          columnNames = {"organization_user_id", "branch_id"})
    })
public class UserBranchAccess {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organization_user_id", nullable = false)
  private OrganizationUser organizationUser;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "branch_id", nullable = false)
  private Branch branch;

  @Column(name = "is_active")
  private Boolean isActive = true;

  @Column(name = "granted_at")
  private LocalDateTime grantedAt;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  public UserBranchAccess() {}

  public UserBranchAccess(
      Long id,
      OrganizationUser organizationUser,
      Branch branch,
      Boolean isActive,
      LocalDateTime grantedAt,
      LocalDateTime createdAt) {
    this.id = id;
    this.organizationUser = organizationUser;
    this.branch = branch;
    this.isActive = isActive;
    this.grantedAt = grantedAt;
    this.createdAt = createdAt;
  }

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public OrganizationUser getOrganizationUser() { return organizationUser; }
  public void setOrganizationUser(OrganizationUser organizationUser) { this.organizationUser = organizationUser; }
  public Branch getBranch() { return branch; }
  public void setBranch(Branch branch) { this.branch = branch; }
  public Boolean getIsActive() { return isActive; }
  public void setIsActive(Boolean isActive) { this.isActive = isActive; }
  public LocalDateTime getGrantedAt() { return grantedAt; }
  public void setGrantedAt(LocalDateTime grantedAt) { this.grantedAt = grantedAt; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
