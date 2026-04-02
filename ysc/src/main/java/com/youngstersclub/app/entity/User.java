package com.youngstersclub.app.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.youngstersclub.app.enums.UserRole;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;

import java.util.List;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;

    @Email
    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "google_id", unique = true)
    private String googleId;

    @Column(name = "profile_pic")
    private String profilePic;

    @Column(name = "phone")
    private String phone;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Enumerated(EnumType.STRING)
    private UserRole role;

    @JsonIgnore
    @OneToMany(mappedBy = "startedBy", fetch = FetchType.LAZY)
    private List<Frame> framesStarted;

    @JsonIgnore
    @OneToMany(mappedBy = "approvedBy", fetch = FetchType.LAZY)
    private List<Frame> framesApproved;

    @JsonIgnore
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Payment> payments;

    @JsonIgnore
    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private UserDue userDue;

    @JsonIgnore
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<FramePlayer> framePlayers;

    public User() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getGoogleId() { return googleId; }
    public void setGoogleId(String googleId) { this.googleId = googleId; }
    public String getProfilePic() { return profilePic; }
    public void setProfilePic(String profilePic) { this.profilePic = profilePic; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    public List<Frame> getFramesStarted() { return framesStarted; }
    public void setFramesStarted(List<Frame> framesStarted) { this.framesStarted = framesStarted; }
    public List<Frame> getFramesApproved() { return framesApproved; }
    public void setFramesApproved(List<Frame> framesApproved) { this.framesApproved = framesApproved; }
    public List<Payment> getPayments() { return payments; }
    public void setPayments(List<Payment> payments) { this.payments = payments; }
    public UserDue getUserDue() { return userDue; }
    public void setUserDue(UserDue userDue) { this.userDue = userDue; }
    public List<FramePlayer> getFramePlayers() { return framePlayers; }
    public void setFramePlayers(List<FramePlayer> framePlayers) { this.framePlayers = framePlayers; }
}
