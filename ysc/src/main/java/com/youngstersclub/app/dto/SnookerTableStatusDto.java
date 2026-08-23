package com.youngstersclub.app.dto;

import java.util.ArrayList;
import java.util.List;

public class SnookerTableStatusDto {

  private Long id;
  private String tableName;
  private Boolean available;
  private Long branchId;
  private String branchName;
  private List<String> players = new ArrayList<>();

  public SnookerTableStatusDto() {}

  public SnookerTableStatusDto(
      Long id,
      String tableName,
      Boolean available,
      Long branchId,
      String branchName,
      List<String> players) {
    this.id = id;
    this.tableName = tableName;
    this.available = available;
    this.branchId = branchId;
    this.branchName = branchName;
    this.players = players;
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

  public List<String> getPlayers() {
    return players;
  }

  public void setPlayers(List<String> players) {
    this.players = players;
  }
}
