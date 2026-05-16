package com.youngstersclub.app.dto;

public class TriggerWhatsappRequest {
    private boolean dryRun;

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }
}
