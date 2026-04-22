package com.youngstersclub.app.dto;

import java.util.ArrayList;
import java.util.List;

public class TournamentRegistrationResult {
    private List<String> successfullyRegistered = new ArrayList<>();
    private List<String> alreadyRegistered = new ArrayList<>();

    public List<String> getSuccessfullyRegistered() {
        return successfullyRegistered;
    }

    public void setSuccessfullyRegistered(List<String> successfullyRegistered) {
        this.successfullyRegistered = successfullyRegistered;
    }

    public List<String> getAlreadyRegistered() {
        return alreadyRegistered;
    }

    public void setAlreadyRegistered(List<String> alreadyRegistered) {
        this.alreadyRegistered = alreadyRegistered;
    }
}
