package com.youngstersclub.app.dto;

import java.util.List;

public class GameActivityOrderCreateRequest {
    private Integer parentUserId;
    private Integer createdBy;
    private List<ActivityRequest> activities;

    public Integer getParentUserId() {
        return parentUserId;
    }

    public void setParentUserId(Integer parentUserId) {
        this.parentUserId = parentUserId;
    }

    public Integer getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Integer createdBy) {
        this.createdBy = createdBy;
    }

    public List<ActivityRequest> getActivities() {
        return activities;
    }

    public void setActivities(List<ActivityRequest> activities) {
        this.activities = activities;
    }

    public static class ActivityRequest {
        private Long gameId;
        private Integer numberOfChildren;
        private Integer durationMinutes;

        public Long getGameId() {
            return gameId;
        }

        public void setGameId(Long gameId) {
            this.gameId = gameId;
        }

        public Integer getNumberOfChildren() {
            return numberOfChildren;
        }

        public void setNumberOfChildren(Integer numberOfChildren) {
            this.numberOfChildren = numberOfChildren;
        }

        public Integer getDurationMinutes() {
            return durationMinutes;
        }

        public void setDurationMinutes(Integer durationMinutes) {
            this.durationMinutes = durationMinutes;
        }
    }
}
