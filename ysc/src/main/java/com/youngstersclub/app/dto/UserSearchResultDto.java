package com.youngstersclub.app.dto;

public class UserSearchResultDto {

    private Integer id;
    private String name;
    private String email;
    private String googleId;
    private String profilePic;
    private String phone;
    private Boolean isActive;
    private String role;

    public UserSearchResultDto() {
    }

    public UserSearchResultDto(
            Integer id,
            String name,
            String email,
            String googleId,
            String profilePic,
            String phone,
            Boolean isActive,
            String role) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.googleId = googleId;
        this.profilePic = profilePic;
        this.phone = phone;
        this.isActive = isActive;
        this.role = role;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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

    public String getGoogleId() {
        return googleId;
    }

    public void setGoogleId(String googleId) {
        this.googleId = googleId;
    }

    public String getProfilePic() {
        return profilePic;
    }

    public void setProfilePic(String profilePic) {
        this.profilePic = profilePic;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
