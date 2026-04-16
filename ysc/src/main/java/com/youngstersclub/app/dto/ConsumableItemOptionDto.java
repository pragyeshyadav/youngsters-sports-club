package com.youngstersclub.app.dto;

import java.math.BigDecimal;

public class ConsumableItemOptionDto {
    private Long id;
    private String name;
    private BigDecimal price;

    public ConsumableItemOptionDto() {
    }

    public ConsumableItemOptionDto(Long id, String name, BigDecimal price) {
        this.id = id;
        this.name = name;
        this.price = price;
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

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}
