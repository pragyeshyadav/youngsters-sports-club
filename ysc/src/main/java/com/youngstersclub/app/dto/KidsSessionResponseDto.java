package com.youngstersclub.app.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class KidsSessionResponseDto {
    private Long sessionId;
    private Long childId;
    private String childName;
    private Integer parentUserId;
    private String parentName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer durationMinutes;
    private BigDecimal ratePerMinute;
    private BigDecimal totalAmount;
    private String paymentStatus;
    private String status;

    public KidsSessionResponseDto(
            Long sessionId,
            Long childId,
            String childName,
            Integer parentUserId,
            String parentName,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer durationMinutes,
            BigDecimal ratePerMinute,
            BigDecimal totalAmount,
            String paymentStatus,
            String status) {
        this.sessionId = sessionId;
        this.childId = childId;
        this.childName = childName;
        this.parentUserId = parentUserId;
        this.parentName = parentName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationMinutes = durationMinutes;
        this.ratePerMinute = ratePerMinute;
        this.totalAmount = totalAmount;
        this.paymentStatus = paymentStatus;
        this.status = status;
    }

    public Long getSessionId() { return sessionId; }
    public Long getChildId() { return childId; }
    public String getChildName() { return childName; }
    public Integer getParentUserId() { return parentUserId; }
    public String getParentName() { return parentName; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public BigDecimal getRatePerMinute() { return ratePerMinute; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getPaymentStatus() { return paymentStatus; }
    public String getStatus() { return status; }
}
