package com.youngstersclub.app.dto;

import java.time.LocalDateTime;

public class WhatsAppTrackedMessageDto {

    private String trackingId;
    private String wamid;
    private Long organizationId;
    private Long branchId;
    private String branchName;
    private Integer userId;
    private String customerName;
    private String customerPhone;
    private String templateName;
    private String status;
    private LocalDateTime sentTime;
    private LocalDateTime lastStatusUpdatedTime;
    private Integer metaErrorCode;
    private String metaErrorMessage;

    public String getTrackingId() {
        return trackingId;
    }

    public void setTrackingId(String trackingId) {
        this.trackingId = trackingId;
    }

    public String getWamid() {
        return wamid;
    }

    public void setWamid(String wamid) {
        this.wamid = wamid;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(Long organizationId) {
        this.organizationId = organizationId;
    }

    public Long getBranchId() {
        return branchId;
    }

    public void setBranchId(Long branchId) {
        this.branchId = branchId;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getSentTime() {
        return sentTime;
    }

    public void setSentTime(LocalDateTime sentTime) {
        this.sentTime = sentTime;
    }

    public LocalDateTime getLastStatusUpdatedTime() {
        return lastStatusUpdatedTime;
    }

    public void setLastStatusUpdatedTime(LocalDateTime lastStatusUpdatedTime) {
        this.lastStatusUpdatedTime = lastStatusUpdatedTime;
    }

    public Integer getMetaErrorCode() {
        return metaErrorCode;
    }

    public void setMetaErrorCode(Integer metaErrorCode) {
        this.metaErrorCode = metaErrorCode;
    }

    public String getMetaErrorMessage() {
        return metaErrorMessage;
    }

    public void setMetaErrorMessage(String metaErrorMessage) {
        this.metaErrorMessage = metaErrorMessage;
    }
}
