package com.youngstersclub.app.dto;

public class BranchOptionDto {
  private Long id;
  private String name;

  public BranchOptionDto() {}

  public BranchOptionDto(Long id, String name) {
    this.id = id;
    this.name = name;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
