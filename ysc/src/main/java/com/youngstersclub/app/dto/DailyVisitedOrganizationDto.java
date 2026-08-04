package com.youngstersclub.app.dto;

public class DailyVisitedOrganizationDto {

    private final Integer userId;
    private final String name;
    private final String phone;
    private final Long organizationId;
    private final String organizationName;
    private final Long branchId;
    private final String branchName;

    public DailyVisitedOrganizationDto(
            Integer userId,
            String name,
            String phone,
            Long organizationId,
            String organizationName,
            Long branchId,
            String branchName) {
        this.userId = userId;
        this.name = name;
        this.phone = phone;
        this.organizationId = organizationId;
        this.organizationName = organizationName;
        this.branchId = branchId;
        this.branchName = branchName;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public Long getBranchId() {
        return branchId;
    }

    public String getBranchName() {
        return branchName;
    }
}
