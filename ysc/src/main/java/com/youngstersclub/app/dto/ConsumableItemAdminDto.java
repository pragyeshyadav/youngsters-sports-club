package com.youngstersclub.app.dto;

import java.math.BigDecimal;

public class ConsumableItemAdminDto {

    private Long id;
    private String name;
    private BigDecimal price;
    private Boolean active;

    public ConsumableItemAdminDto() {}

    public ConsumableItemAdminDto(Long id, String name, BigDecimal price, Boolean active) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Boolean getActive() {
        return active;
    }
}
