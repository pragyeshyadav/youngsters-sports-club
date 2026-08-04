package com.youngstersclub.app.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(
    name = "user_dues",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_user_dues_user_branch", columnNames = {"user_id", "branch_id"})
    })
public class UserDue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "total_due", precision = 10, scale = 2, nullable = false)
    private BigDecimal totalDueAmount;

    public UserDue() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }
    public BigDecimal getDueAmount() { return totalDueAmount; }
    public void setDueAmount(BigDecimal dueAmount) { this.totalDueAmount = dueAmount; }
}
