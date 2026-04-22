package com.youngstersclub.app.dto;

import java.util.List;

public class TournamentRegistrationRequest {
    private Integer userId;
    private List<Long> tournamentIds;

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public List<Long> getTournamentIds() {
        return tournamentIds;
    }

    public void setTournamentIds(List<Long> tournamentIds) {
        this.tournamentIds = tournamentIds;
    }
}
