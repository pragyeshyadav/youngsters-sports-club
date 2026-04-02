package com.youngstersclub.app.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "user_dues")
public class UserDue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    @Column(name = "total_due", precision = 10, scale = 2, nullable = false)
    private BigDecimal totalDueAmount;

    public UserDue() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public BigDecimal getDueAmount() { return totalDueAmount; }
    public void setDueAmount(BigDecimal dueAmount) { this.totalDueAmount = dueAmount; }
}
