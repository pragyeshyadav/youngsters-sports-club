package com.youngstersclub.app.dto;

import java.math.BigDecimal;

public class TournamentResponse {
    private Long id;
    private String name;
    private String eventName;
    private BigDecimal registrationFee;
    
    public TournamentResponse() {}

    public TournamentResponse(Long id, String name, String eventName, BigDecimal registrationFee) {
        this.id = id;
        this.name = name;
        this.eventName = eventName;
        this.registrationFee = registrationFee;
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

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public BigDecimal getRegistrationFee() {
        return registrationFee;
    }

    public void setRegistrationFee(BigDecimal registrationFee) {
        this.registrationFee = registrationFee;
    }
}
