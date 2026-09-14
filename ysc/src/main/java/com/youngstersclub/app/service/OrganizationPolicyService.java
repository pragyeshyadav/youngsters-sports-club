package com.youngstersclub.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.youngstersclub.app.entity.Organization;
import com.youngstersclub.app.policy.OrganizationPolicy;
import com.youngstersclub.app.policy.PricingType;
import com.youngstersclub.app.repository.OrganizationRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationPolicyService {
  public static final BigDecimal DEFAULT_MINIMUM_DUE_THRESHOLD = new BigDecimal("500");
  private static final List<DayOfWeek> DEFAULT_WEEKDAYS = List.of(DayOfWeek.values());

  private final OrganizationRepository organizationRepository;
  private final OrganizationPolicyCache cache;
  private final ObjectMapper objectMapper;

  public OrganizationPolicyService(
      OrganizationRepository organizationRepository,
      OrganizationPolicyCache cache,
      ObjectMapper objectMapper) {
    this.organizationRepository = organizationRepository;
    this.cache = cache;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public OrganizationPolicy getEffectivePolicy(Long organizationId) {
    return cache.read(organizationId).map(this::validate).orElseGet(() -> {
      Organization organization = organizationRepository.findByIdAndIsActiveTrue(organizationId)
          .orElseThrow(() -> new IllegalArgumentException("Organization not found"));
      OrganizationPolicy policy = parseEffective(organization.getPolicy());
      cache.write(organizationId, policy);
      return policy;
    });
  }

  @Transactional
  public OrganizationPolicy updatePolicy(Long organizationId, OrganizationPolicy requested) {
    Organization organization = organizationRepository.findByIdAndIsActiveTrue(organizationId)
        .orElseThrow(() -> new IllegalArgumentException("Organization not found"));
    OrganizationPolicy policy = validate(requested);
    try {
      JsonNode existing = organization.getPolicy() == null || organization.getPolicy().isBlank()
          ? objectMapper.createObjectNode()
          : objectMapper.readTree(organization.getPolicy());
      ObjectNode root = existing != null && existing.isObject()
          ? (ObjectNode) existing
          : objectMapper.createObjectNode();
      root.set("pricing", objectMapper.valueToTree(policy.pricing()));
      root.set("whatsappPaymentReminder", objectMapper.valueToTree(policy.whatsappPaymentReminder()));
      organization.setPolicy(objectMapper.writeValueAsString(root));
      organizationRepository.save(organization);
      cache.invalidate(organizationId);
      return policy;
    } catch (Exception ex) {
      throw new IllegalArgumentException("Unable to save organization policy", ex);
    }
  }

  public OrganizationPolicy parseEffective(String rawPolicy) {
    try {
      JsonNode root = rawPolicy == null || rawPolicy.isBlank()
          ? objectMapper.createObjectNode()
          : objectMapper.readTree(rawPolicy);
      if (root == null || !root.isObject()) {
        throw new IllegalArgumentException("Organization policy must be a JSON object");
      }

      JsonNode pricing = root.path("pricing");
      String typeValue = pricing.path("type").isMissingNode() || pricing.path("type").isNull()
          ? null : pricing.path("type").asText();
      PricingType type = typeValue == null || typeValue.isBlank() ? PricingType.DYNAMIC : parsePricingType(typeValue);

      JsonNode reminder = root.path("whatsappPaymentReminder");
      JsonNode weekdaysNode = reminder.path("weekdays");
      List<DayOfWeek> weekdays = weekdaysNode.isMissingNode() || weekdaysNode.isNull()
          ? DEFAULT_WEEKDAYS : parseWeekdays(weekdaysNode);
      JsonNode thresholdNode = reminder.path("minimumDueThreshold");
      BigDecimal threshold = thresholdNode.isMissingNode() || thresholdNode.isNull()
          ? DEFAULT_MINIMUM_DUE_THRESHOLD : parseThreshold(thresholdNode);
      return new OrganizationPolicy(
          new OrganizationPolicy.PricingPolicy(type),
          new OrganizationPolicy.WhatsappPaymentReminderPolicy(weekdays, threshold));
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("Invalid organization policy", ex);
    }
  }

  protected OrganizationPolicy validate(OrganizationPolicy policy) {
    if (policy == null || policy.pricing() == null || policy.pricing().type() == null
        || policy.whatsappPaymentReminder() == null
        || policy.whatsappPaymentReminder().weekdays() == null
        || policy.whatsappPaymentReminder().minimumDueThreshold() == null) {
      throw new IllegalArgumentException("Complete organization policy is required");
    }
    List<DayOfWeek> weekdays = normalizeWeekdays(policy.whatsappPaymentReminder().weekdays());
    BigDecimal threshold = policy.whatsappPaymentReminder().minimumDueThreshold();
    if (threshold.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("Minimum due threshold cannot be negative");
    }
    return new OrganizationPolicy(
        new OrganizationPolicy.PricingPolicy(policy.pricing().type()),
        new OrganizationPolicy.WhatsappPaymentReminderPolicy(weekdays, threshold));
  }

  private PricingType parsePricingType(String value) {
    try {
      return PricingType.valueOf(value.trim().toUpperCase());
    } catch (Exception ex) {
      throw new IllegalArgumentException("Pricing type must be FIXED or DYNAMIC");
    }
  }

  private List<DayOfWeek> parseWeekdays(JsonNode node) {
    if (!node.isArray()) {
      throw new IllegalArgumentException("Weekdays must be an array");
    }
    List<DayOfWeek> values = new ArrayList<>();
    node.forEach(item -> {
      if (!item.isTextual()) {
        throw new IllegalArgumentException("Weekday must be a valid day name");
      }
      try {
        values.add(DayOfWeek.valueOf(item.asText().trim().toUpperCase()));
      } catch (Exception ex) {
        throw new IllegalArgumentException("Weekday must be a valid day name");
      }
    });
    return normalizeWeekdays(values);
  }

  private BigDecimal parseThreshold(JsonNode node) {
    if (!node.isNumber() && !node.isTextual()) {
      throw new IllegalArgumentException("Minimum due threshold must be numeric");
    }
    try {
      BigDecimal value = new BigDecimal(node.asText());
      if (value.compareTo(BigDecimal.ZERO) < 0) {
        throw new IllegalArgumentException("Minimum due threshold cannot be negative");
      }
      return value;
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Minimum due threshold must be numeric");
    }
  }

  private List<DayOfWeek> normalizeWeekdays(List<DayOfWeek> weekdays) {
    Set<DayOfWeek> unique = EnumSet.noneOf(DayOfWeek.class);
    for (DayOfWeek weekday : weekdays) {
      if (weekday == null || !unique.add(weekday)) {
        throw new IllegalArgumentException("Weekdays must be valid and unique");
      }
    }
    return Arrays.stream(DayOfWeek.values()).filter(unique::contains).toList();
  }
}
