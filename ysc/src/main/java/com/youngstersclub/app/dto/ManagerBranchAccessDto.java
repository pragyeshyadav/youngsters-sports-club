package com.youngstersclub.app.dto;

public class ManagerBranchAccessDto {

    private Long branchId;
    private String branchName;
    private boolean baseBranch;
    private boolean granted;

    public ManagerBranchAccessDto() {}

    public ManagerBranchAccessDto(Long branchId, String branchName, boolean baseBranch, boolean granted) {
        this.branchId = branchId;
        this.branchName = branchName;
        this.baseBranch = baseBranch;
        this.granted = granted;
    }

    public Long getBranchId() {
        return branchId;
    }

    public String getBranchName() {
        return branchName;
    }

    public boolean isBaseBranch() {
        return baseBranch;
    }

    public boolean isGranted() {
        return granted;
    }
}
