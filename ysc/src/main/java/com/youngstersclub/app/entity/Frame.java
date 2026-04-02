package com.youngstersclub.app.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.youngstersclub.app.enums.FrameStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "frames")
public class Frame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id")
    private SnookerTable snookerTable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "started_by_id")
    private User startedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private User approvedBy;

    @Enumerated(EnumType.STRING)
    private FrameStatus status;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(precision = 10, scale = 2)
    private BigDecimal cost;

    @JsonIgnore
    @OneToMany(mappedBy = "frame", fetch = FetchType.LAZY)
    private List<FramePlayer> framePlayers;

    @JsonIgnore
    @OneToMany(mappedBy = "frame", fetch = FetchType.LAZY)
    private List<Payment> payments;

    public Frame() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public SnookerTable getSnookerTable() { return snookerTable; }
    public void setSnookerTable(SnookerTable snookerTable) { this.snookerTable = snookerTable; }
    public User getStartedBy() { return startedBy; }
    public void setStartedBy(User startedBy) { this.startedBy = startedBy; }
    public User getApprovedBy() { return approvedBy; }
    public void setApprovedBy(User approvedBy) { this.approvedBy = approvedBy; }
    public FrameStatus getStatus() { return status; }
    public void setStatus(FrameStatus status) { this.status = status; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }
    public List<FramePlayer> getFramePlayers() { return framePlayers; }
    public void setFramePlayers(List<FramePlayer> framePlayers) { this.framePlayers = framePlayers; }
    public List<Payment> getPayments() { return payments; }
    public void setPayments(List<Payment> payments) { this.payments = payments; }
}
