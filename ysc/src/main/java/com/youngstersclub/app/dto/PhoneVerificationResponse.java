package com.youngstersclub.app.dto;

public class PhoneVerificationResponse {
    private boolean exists;
    private UserPreviewDto user;

    public PhoneVerificationResponse() {
    }

    public PhoneVerificationResponse(boolean exists, UserPreviewDto user) {
        this.exists = exists;
        this.user = user;
    }

    public boolean isExists() {
        return exists;
    }

    public void setExists(boolean exists) {
        this.exists = exists;
    }

    public UserPreviewDto getUser() {
        return user;
    }

    public void setUser(UserPreviewDto user) {
        this.user = user;
    }
}
