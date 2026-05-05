package com.youngstersclub.app.dto;

public class KidsSessionStartRequest {
    private Long childId;
    private Integer parentUserId;

    public Long getChildId() {
        return childId;
    }

    public void setChildId(Long childId) {
        this.childId = childId;
    }

    public Integer getParentUserId() {
        return parentUserId;
    }

    public void setParentUserId(Integer parentUserId) {
        this.parentUserId = parentUserId;
    }
}
