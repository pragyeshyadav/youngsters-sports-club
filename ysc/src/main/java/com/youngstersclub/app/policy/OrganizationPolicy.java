package com.youngstersclub.app.policy;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;

public record OrganizationPolicy(PricingPolicy pricing, WhatsappPaymentReminderPolicy whatsappPaymentReminder) {
  public record PricingPolicy(
      PricingType type,
      @JsonProperty("min-frame-time") Integer minFrameTime,
      @JsonProperty("min-frame-prize") BigDecimal minFramePrize) {}

  public record WhatsappPaymentReminderPolicy(List<DayOfWeek> weekdays, BigDecimal minimumDueThreshold) {}
}
