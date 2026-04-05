package com.youngstersclub.app.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "snooker_tables")
public class SnookerTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "table_name", nullable = false)
    private String tableName;

    @Column(name = "rate_per_minute", nullable = false)
    private BigDecimal ratePerMinute;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @JsonIgnore
    @OneToMany(mappedBy = "snookerTable", fetch = FetchType.LAZY)
    private List<Frame> frames;

    public SnookerTable() {}

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public BigDecimal getRatePerMinute() { return ratePerMinute; }
    public void setRatePerMinute(BigDecimal ratePerMinute) { this.ratePerMinute = ratePerMinute; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Boolean getIsAvailable() { return isAvailable; }
    public void setIsAvailable(Boolean isAvailable) { this.isAvailable = isAvailable; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<Frame> getFrames() { return frames; }
    public void setFrames(List<Frame> frames) { this.frames = frames; }
}
