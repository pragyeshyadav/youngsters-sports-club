package com.youngstersclub.app.entity;

import com.youngstersclub.app.enums.PaymentStatus;
import com.youngstersclub.app.util.TimeUtil;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "frame_players")
public class FramePlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "frame_id", nullable = false)
    private Frame frame;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "player_name")
    private String playerName;

    @Column(name = "amount_due", precision = 10, scale = 2)
    private BigDecimal amountDue;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status")
    private PaymentStatus paymentStatus;

    @Column(name = "is_winner")
    private Boolean isWinner;

    @Column(name = "is_loser")
    private Boolean isLoser;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public FramePlayer() {}

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = TimeUtil.nowIST();
        }
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Frame getFrame() { return frame; }
    public void setFrame(Frame frame) { this.frame = frame; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public BigDecimal getAmountDue() { return amountDue; }
    public void setAmountDue(BigDecimal amountDue) { this.amountDue = amountDue; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }
    public Boolean getIsWinner() { return isWinner; }
    public void setIsWinner(Boolean isWinner) { this.isWinner = isWinner; }
    public Boolean getIsLoser() { return isLoser; }
    public void setIsLoser(Boolean isLoser) { this.isLoser = isLoser; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
