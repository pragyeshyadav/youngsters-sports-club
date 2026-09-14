package com.youngstersclub.app.policy;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;

public record OrganizationPolicy(PricingPolicy pricing, WhatsappPaymentReminderPolicy whatsappPaymentReminder) {
  public record PricingPolicy(PricingType type) {}

  public record WhatsappPaymentReminderPolicy(List<DayOfWeek> weekdays, BigDecimal minimumDueThreshold) {}
}
