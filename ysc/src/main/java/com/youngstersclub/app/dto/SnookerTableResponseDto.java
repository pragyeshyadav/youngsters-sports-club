package com.youngstersclub.app.dto;

import java.math.BigDecimal;

public class SnookerTableResponseDto {

  private Long id;
  private String tableName;
  private BigDecimal ratePerMinute;
  private Boolean active;
  private Boolean available;
  private Long branchId;
  private String branchName;

  public SnookerTableResponseDto() {}

  public SnookerTableResponseDto(
      Long id,
      String tableName,
      BigDecimal ratePerMinute,
      Boolean active,
      Boolean available,
      Long branchId,
      String branchName) {
    this.id = id;
    this.tableName = tableName;
    this.ratePerMinute = ratePerMinute;
    this.active = active;
    this.available = available;
    this.branchId = branchId;
    this.branchName = branchName;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getTableName() {
    return tableName;
  }

  public void setTableName(String tableName) {
    this.tableName = tableName;
  }

  public BigDecimal getRatePerMinute() {
    return ratePerMinute;
  }

  public void setRatePerMinute(BigDecimal ratePerMinute) {
    this.ratePerMinute = ratePerMinute;
  }

  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean active) {
    this.active = active;
  }

  public Boolean getAvailable() {
    return available;
  }

  public void setAvailable(Boolean available) {
    this.available = available;
  }

  public Long getBranchId() {
    return branchId;
  }

  public void setBranchId(Long branchId) {
    this.branchId = branchId;
  }

  public String getBranchName() {
    return branchName;
  }

  public void setBranchName(String branchName) {
    this.branchName = branchName;
  }
}
