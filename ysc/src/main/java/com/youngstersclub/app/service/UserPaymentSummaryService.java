package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.UserPaymentSummaryDto;
import com.youngstersclub.app.repository.FrameRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class UserPaymentSummaryService {

    private final FrameRepository frameRepository;
    private final ConsumableService consumableService;
    private final KidsPlayService kidsPlayService;

    public UserPaymentSummaryService(
            FrameRepository frameRepository,
            ConsumableService consumableService,
            KidsPlayService kidsPlayService) {
        this.frameRepository = frameRepository;
        this.consumableService = consumableService;
        this.kidsPlayService = kidsPlayService;
    }

    public UserPaymentSummaryDto getPaymentSummary(Integer userId) {
        BigDecimal frameDue = userId == null ? BigDecimal.ZERO : frameRepository.getTotalDueForUser(userId);
        BigDecimal consumableDue = consumableService.getConsumableDue(userId);
        BigDecimal kidsDue = kidsPlayService.getKidsDue(userId);
        return new UserPaymentSummaryDto(frameDue, consumableDue, kidsDue);
    }
}
