package com.youngstersclub.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youngstersclub.app.policy.OrganizationPolicy;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrganizationPolicyCache {
  private static final Logger log = LoggerFactory.getLogger(OrganizationPolicyCache.class);
  private static final Duration TTL = Duration.ofHours(24);

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  public OrganizationPolicyCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
  }

  public Optional<OrganizationPolicy> read(Long organizationId) {
    try {
      String value = redisTemplate.opsForValue().get(key(organizationId));
      return value == null || value.isBlank()
          ? Optional.empty()
          : Optional.of(objectMapper.readValue(value, new TypeReference<>() {}));
    } catch (Exception ex) {
      log.warn("Organization policy cache read unavailable. organizationId: {}, reason: {}", organizationId, ex.getMessage());
      return Optional.empty();
    }
  }

  public void write(Long organizationId, OrganizationPolicy policy) {
    try {
      redisTemplate.opsForValue().set(key(organizationId), objectMapper.writeValueAsString(policy), TTL);
    } catch (Exception ex) {
      log.warn("Organization policy cache write unavailable. organizationId: {}, reason: {}", organizationId, ex.getMessage());
    }
  }

  public void invalidate(Long organizationId) {
    try {
      redisTemplate.delete(key(organizationId));
    } catch (Exception ex) {
      log.warn("Organization policy cache invalidation unavailable. organizationId: {}, reason: {}", organizationId, ex.getMessage());
    }
  }

  private String key(Long organizationId) {
    return "ysc:organization-policy:v1:" + organizationId;
  }
}
