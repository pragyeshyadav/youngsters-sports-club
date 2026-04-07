package com.youngstersclub.app.dto;

import java.util.List;

public class StartFrameRequest {
    private Long tableId;
    private Integer startedBy;
    private List<PlayerDto> players;

    public Long getTableId() {
        return tableId;
    }

    public void setTableId(Long tableId) {
        this.tableId = tableId;
    }

    public Integer getStartedBy() {
        return startedBy;
    }

    public void setStartedBy(Integer startedBy) {
        this.startedBy = startedBy;
    }

    public List<PlayerDto> getPlayers() {
        return players;
    }

    public void setPlayers(List<PlayerDto> players) {
        this.players = players;
    }

    public static class PlayerDto {
        private Integer userId;
        private String name;

        public Integer getUserId() {
            return userId;
        }

        public void setUserId(Integer userId) {
            this.userId = userId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
