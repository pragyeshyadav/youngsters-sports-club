package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youngstersclub.app.policy.OrganizationPolicy;
import com.youngstersclub.app.policy.PricingType;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrganizationPolicyServiceTest {
  private final OrganizationPolicyService service =
      new OrganizationPolicyService(null, null, new ObjectMapper().findAndRegisterModules());

  @Test
  void nullPolicyUsesExistingDynamicPricingAndReminderDefaults() {
    OrganizationPolicy policy = service.parseEffective(null);

    assertEquals(PricingType.DYNAMIC, policy.pricing().type());
    assertEquals(List.of(DayOfWeek.values()), policy.whatsappPaymentReminder().weekdays());
    assertEquals(new BigDecimal("500"), policy.whatsappPaymentReminder().minimumDueThreshold());
  }

  @Test
  void partialPolicyDefaultsMissingSectionsButPreservesExplicitEmptyDays() {
    OrganizationPolicy policy = service.parseEffective("""
        {"whatsappPaymentReminder":{"weekdays":[],"minimumDueThreshold":0}}
        """);

    assertEquals(PricingType.DYNAMIC, policy.pricing().type());
    assertEquals(List.of(), policy.whatsappPaymentReminder().weekdays());
    assertEquals(BigDecimal.ZERO, policy.whatsappPaymentReminder().minimumDueThreshold());
  }

  @Test
  void parsesFixedPricingAndNormalizesWeekdayOrder() {
    OrganizationPolicy policy = service.parseEffective("""
        {"pricing":{"type":"FIXED"},"whatsappPaymentReminder":{"weekdays":["SUNDAY","MONDAY"],"minimumDueThreshold":501}}
        """);

    assertEquals(PricingType.FIXED, policy.pricing().type());
    assertEquals(List.of(DayOfWeek.MONDAY, DayOfWeek.SUNDAY), policy.whatsappPaymentReminder().weekdays());
    assertEquals(new BigDecimal("501"), policy.whatsappPaymentReminder().minimumDueThreshold());
  }

  @Test
  void rejectsInvalidPricingWeekdaysAndNegativeThreshold() {
    assertThrows(IllegalArgumentException.class,
        () -> service.parseEffective("{\"pricing\":{\"type\":\"UNKNOWN\"}}"));
    assertThrows(IllegalArgumentException.class,
        () -> service.parseEffective("{\"whatsappPaymentReminder\":{\"weekdays\":[\"FUNDAY\"]}}"));
    assertThrows(IllegalArgumentException.class,
        () -> service.parseEffective("{\"whatsappPaymentReminder\":{\"minimumDueThreshold\":-1}}"));
  }
}
