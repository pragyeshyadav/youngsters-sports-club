package com.youngstersclub.app.dto;

public class BranchAccessUpdateRequest {

    private Long branchId;
    private Boolean granted;

    public Long getBranchId() {
        return branchId;
    }

    public void setBranchId(Long branchId) {
        this.branchId = branchId;
    }

    public Boolean getGranted() {
        return granted;
    }

    public void setGranted(Boolean granted) {
        this.granted = granted;
    }
}
