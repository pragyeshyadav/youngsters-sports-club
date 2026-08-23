package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.CustomerBranchDue;
import com.youngstersclub.app.repository.ConsumableOrderRepository;
import com.youngstersclub.app.repository.FrameRepository;
import com.youngstersclub.app.repository.GameActivityOrderRepository;
import com.youngstersclub.app.repository.KidsPlaySessionRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CustomerBranchDueCalculatorService {

    private final FrameRepository frameRepository;
    private final ConsumableOrderRepository consumableOrderRepository;
    private final KidsPlaySessionRepository kidsPlaySessionRepository;
    private final GameActivityOrderRepository gameActivityOrderRepository;

    public CustomerBranchDueCalculatorService(
            FrameRepository frameRepository,
            ConsumableOrderRepository consumableOrderRepository,
            KidsPlaySessionRepository kidsPlaySessionRepository,
            GameActivityOrderRepository gameActivityOrderRepository) {
        this.frameRepository = frameRepository;
        this.consumableOrderRepository = consumableOrderRepository;
        this.kidsPlaySessionRepository = kidsPlaySessionRepository;
        this.gameActivityOrderRepository = gameActivityOrderRepository;
    }

    public CustomerBranchDue calculateCustomerDue(Long customerId, Long branchId) {
        if (customerId == null || branchId == null) {
            return new CustomerBranchDue(customerId, branchId, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        return calculateCustomerDues(List.of(customerId), branchId).getOrDefault(
                customerId,
                new CustomerBranchDue(customerId, branchId, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    public Map<Long, CustomerBranchDue> calculateCustomerDues(List<Long> customerIds, Long branchId) {
        Map<Long, CustomerBranchDue> dueMap = new LinkedHashMap<>();
        if (customerIds == null || customerIds.isEmpty() || branchId == null) {
            return dueMap;
        }

        List<Integer> userIds = customerIds.stream()
                .filter(java.util.Objects::nonNull)
                .map(Long::intValue)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return dueMap;
        }

        Map<Integer, BigDecimal> frameDueByUserId = frameRepository.getTotalDueForUsersByBranch(userIds, branchId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        FrameRepository.UserDueProjection::getUserId,
                        projection -> projection.getAmount() == null ? BigDecimal.ZERO : projection.getAmount()));
        Map<Integer, BigDecimal> consumableDueByUserId = consumableOrderRepository.getTotalUnpaidDueByUserIdsAndBranchId(userIds, branchId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        ConsumableOrderRepository.UserConsumableDueProjection::getUserId,
                        projection -> projection.getAmount() == null ? BigDecimal.ZERO : projection.getAmount()));
        Map<Integer, BigDecimal> kidsPlayDueByUserId = kidsPlaySessionRepository.getTotalUnpaidDueByParentUserIdsAndBranchId(userIds, branchId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        KidsPlaySessionRepository.UserKidsDueProjection::getUserId,
                        projection -> projection.getAmount() == null ? BigDecimal.ZERO : projection.getAmount()));
        Map<Integer, BigDecimal> gameActivityDueByUserId = gameActivityOrderRepository.getTotalUnpaidDueByParentUserIdsAndBranchId(userIds, branchId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        GameActivityOrderRepository.UserActivityDueProjection::getUserId,
                        projection -> projection.getAmount() == null ? BigDecimal.ZERO : projection.getAmount()));

        for (Long customerId : customerIds) {
            if (customerId == null) {
                continue;
            }

            Integer userId = customerId.intValue();
            BigDecimal frameDue = frameDueByUserId.getOrDefault(userId, BigDecimal.ZERO);
            BigDecimal consumableDue = consumableDueByUserId.getOrDefault(userId, BigDecimal.ZERO);
            BigDecimal kidsPlayDue = kidsPlayDueByUserId.getOrDefault(userId, BigDecimal.ZERO);
            BigDecimal gameActivityDue = gameActivityDueByUserId.getOrDefault(userId, BigDecimal.ZERO);
            BigDecimal totalDue = frameDue
                    .add(consumableDue)
                    .add(kidsPlayDue)
                    .add(gameActivityDue);

            dueMap.put(customerId, new CustomerBranchDue(
                    customerId,
                    branchId,
                    frameDue,
                    consumableDue,
                    kidsPlayDue,
                    gameActivityDue,
                    totalDue));
        }

        return dueMap;
    }
}
