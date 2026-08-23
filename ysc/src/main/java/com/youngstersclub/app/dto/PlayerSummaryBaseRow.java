package com.youngstersclub.app.dto;

public class PlayerSummaryBaseRow implements PlayerSummaryBaseProjection {

    private final Integer userId;
    private final String name;
    private final String email;
    private final Long framesPlayed;

    public PlayerSummaryBaseRow(Integer userId, String name, String email, Long framesPlayed) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.framesPlayed = framesPlayed;
    }

    @Override
    public Integer getUserId() {
        return userId;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public Long getFramesPlayed() {
        return framesPlayed;
    }
}
