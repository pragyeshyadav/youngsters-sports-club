package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.CustomerBranchDue;
import com.youngstersclub.app.dto.UserPaymentSummaryDto;
import com.youngstersclub.app.repository.FrameRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PendingDueService {

    private final FrameRepository frameRepository;
    private final ConsumableService consumableService;
    private final KidsPlayService kidsPlayService;
    private final CustomerBranchDueCalculatorService customerBranchDueCalculatorService;

    public PendingDueService(
            FrameRepository frameRepository,
            ConsumableService consumableService,
            KidsPlayService kidsPlayService,
            CustomerBranchDueCalculatorService customerBranchDueCalculatorService) {
        this.frameRepository = frameRepository;
        this.consumableService = consumableService;
        this.kidsPlayService = kidsPlayService;
        this.customerBranchDueCalculatorService = customerBranchDueCalculatorService;
    }

    public UserPaymentSummaryDto getPaymentSummary(Integer userId) {
        if (userId == null) {
            return new UserPaymentSummaryDto(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        return getPaymentSummaries(List.of(userId)).getOrDefault(
                userId,
                new UserPaymentSummaryDto(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    public Map<Integer, UserPaymentSummaryDto> getPaymentSummaries(List<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new LinkedHashMap<>();
        }

        Map<Integer, BigDecimal> frameDueByUserId = frameRepository.getTotalDueForUsers(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        FrameRepository.UserDueProjection::getUserId,
                        projection -> projection.getAmount() == null ? BigDecimal.ZERO : projection.getAmount()));
        Map<Integer, BigDecimal> consumableDueByUserId = consumableService.getConsumableDueMap(userIds);
        Map<Integer, BigDecimal> kidsDueByUserId = kidsPlayService.getKidsDueMap(userIds);

        return composeSummaries(userIds, frameDueByUserId, consumableDueByUserId, kidsDueByUserId);
    }

    public UserPaymentSummaryDto getBranchPaymentSummary(Integer userId, Long branchId) {
        if (userId == null || branchId == null) {
            return new UserPaymentSummaryDto(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        return getBranchPaymentSummaries(List.of(userId), branchId).getOrDefault(
                userId,
                new UserPaymentSummaryDto(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    public Map<Integer, UserPaymentSummaryDto> getBranchPaymentSummaries(List<Integer> userIds, Long branchId) {
        if (userIds == null || userIds.isEmpty() || branchId == null) {
            return new LinkedHashMap<>();
        }

        Map<Long, CustomerBranchDue> branchDues = calculateCustomerDues(
                userIds.stream().map(Integer::longValue).toList(),
                branchId);
        Map<Integer, UserPaymentSummaryDto> summaries = new LinkedHashMap<>();
        for (Integer userId : userIds) {
            CustomerBranchDue due = branchDues.get(userId.longValue());
            if (due == null) {
                summaries.put(userId, new UserPaymentSummaryDto(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
                continue;
            }

            summaries.put(
                    userId,
                    new UserPaymentSummaryDto(
                            due.frameDue(),
                            due.consumableDue(),
                            due.kidsPlayDue().add(due.gameActivityDue())));
        }
        return summaries;
    }

    public Map<Integer, BigDecimal> getBranchPendingDueMap(List<Integer> userIds, Long branchId) {
        Map<Integer, BigDecimal> totalDueMap = new LinkedHashMap<>();
        customerBranchDueCalculatorService
                .calculateCustomerDues(userIds == null ? List.of() : userIds.stream().map(Integer::longValue).toList(), branchId)
                .forEach((customerId, due) -> totalDueMap.put(customerId.intValue(), due == null ? BigDecimal.ZERO : due.totalDue()));
        return totalDueMap;
    }

    public CustomerBranchDue calculateCustomerDue(Long customerId, Long branchId) {
        return customerBranchDueCalculatorService.calculateCustomerDue(customerId, branchId);
    }

    public Map<Long, CustomerBranchDue> calculateCustomerDues(List<Long> customerIds, Long branchId) {
        return customerBranchDueCalculatorService.calculateCustomerDues(customerIds, branchId);
    }

    private Map<Integer, UserPaymentSummaryDto> composeSummaries(
            List<Integer> userIds,
            Map<Integer, BigDecimal> frameDueByUserId,
            Map<Integer, BigDecimal> consumableDueByUserId,
            Map<Integer, BigDecimal> kidsDueByUserId) {
        Map<Integer, UserPaymentSummaryDto> summaries = new LinkedHashMap<>();
        for (Integer userId : userIds) {
            summaries.put(
                    userId,
                    new UserPaymentSummaryDto(
                            frameDueByUserId.getOrDefault(userId, BigDecimal.ZERO),
                            consumableDueByUserId.getOrDefault(userId, BigDecimal.ZERO),
                            kidsDueByUserId.getOrDefault(userId, BigDecimal.ZERO)));
        }
        return summaries;
    }
}
