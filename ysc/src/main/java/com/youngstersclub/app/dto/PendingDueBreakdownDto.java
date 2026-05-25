package com.youngstersclub.app.dto;

import java.math.BigDecimal;
import java.util.List;

public class PendingDueBreakdownDto {
    private final List<PendingFrameBreakdownDto> frames;
    private final List<ConsumableDueRowDto> consumables;
    private final List<PendingKidsPlayBreakdownDto> kidsPlay;
    private final BigDecimal frameDue;
    private final BigDecimal consumableDue;
    private final BigDecimal kidsDue;
    private final BigDecimal totalDue;

    public PendingDueBreakdownDto(
            List<PendingFrameBreakdownDto> frames,
            List<ConsumableDueRowDto> consumables,
            List<PendingKidsPlayBreakdownDto> kidsPlay,
            BigDecimal frameDue,
            BigDecimal consumableDue,
            BigDecimal kidsDue,
            BigDecimal totalDue) {
        this.frames = frames == null ? List.of() : frames;
        this.consumables = consumables == null ? List.of() : consumables;
        this.kidsPlay = kidsPlay == null ? List.of() : kidsPlay;
        this.frameDue = frameDue == null ? BigDecimal.ZERO : frameDue;
        this.consumableDue = consumableDue == null ? BigDecimal.ZERO : consumableDue;
        this.kidsDue = kidsDue == null ? BigDecimal.ZERO : kidsDue;
        this.totalDue = totalDue == null ? BigDecimal.ZERO : totalDue;
    }

    public List<PendingFrameBreakdownDto> getFrames() {
        return frames;
    }

    public List<ConsumableDueRowDto> getConsumables() {
        return consumables;
    }

    public List<PendingKidsPlayBreakdownDto> getKidsPlay() {
        return kidsPlay;
    }

    public BigDecimal getFrameDue() {
        return frameDue;
    }

    public BigDecimal getConsumableDue() {
        return consumableDue;
    }

    public BigDecimal getKidsDue() {
        return kidsDue;
    }

    public BigDecimal getTotalDue() {
        return totalDue;
    }
}
