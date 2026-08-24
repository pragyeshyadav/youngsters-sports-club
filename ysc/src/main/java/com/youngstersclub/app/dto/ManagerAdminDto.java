package com.youngstersclub.app.dto;

import java.util.ArrayList;
import java.util.List;

public class ManagerAdminDto {

    private Long organizationUserId;
    private Integer userId;
    private String name;
    private String email;
    private String phone;
    private String role;
    private boolean active;
    private Long baseBranchId;
    private List<ManagerBranchAccessDto> branchAccesses = new ArrayList<>();

    public Long getOrganizationUserId() {
        return organizationUserId;
    }

    public void setOrganizationUserId(Long organizationUserId) {
        this.organizationUserId = organizationUserId;
    }

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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Long getBaseBranchId() {
        return baseBranchId;
    }

    public void setBaseBranchId(Long baseBranchId) {
        this.baseBranchId = baseBranchId;
    }

    public List<ManagerBranchAccessDto> getBranchAccesses() {
        return branchAccesses;
    }

    public void setBranchAccesses(List<ManagerBranchAccessDto> branchAccesses) {
        this.branchAccesses = branchAccesses;
    }
}
