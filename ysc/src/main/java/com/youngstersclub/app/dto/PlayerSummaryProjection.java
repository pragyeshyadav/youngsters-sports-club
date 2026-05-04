package com.youngstersclub.app.dto;

import java.math.BigDecimal;

public interface PlayerSummaryProjection {
    Integer getUserId();
    String getName();
    String getEmail();
    Long getFramesPlayed();
    BigDecimal getTotalDue();
}
