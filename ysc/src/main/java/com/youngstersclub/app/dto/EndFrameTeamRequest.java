package com.youngstersclub.app.dto;

import java.util.List;

public class EndFrameTeamRequest {
    private String mode;
    private Integer winnerId;
    private Integer looserId;
    private List<Integer> winnerIds;
    private List<Integer> loserIds;

    public EndFrameTeamRequest() {}

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public Integer getWinnerId() { return winnerId; }
    public void setWinnerId(Integer winnerId) { this.winnerId = winnerId; }

    public Integer getLooserId() { return looserId; }
    public void setLooserId(Integer looserId) { this.looserId = looserId; }

    public List<Integer> getWinnerIds() { return winnerIds; }
    public void setWinnerIds(List<Integer> winnerIds) { this.winnerIds = winnerIds; }

    public List<Integer> getLoserIds() { return loserIds; }
    public void setLoserIds(List<Integer> loserIds) { this.loserIds = loserIds; }
}
