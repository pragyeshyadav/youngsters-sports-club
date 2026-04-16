package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.ConsumableDueRowDto;
import com.youngstersclub.app.dto.ConsumableHistoryRowDto;
import com.youngstersclub.app.dto.ConsumableItemOptionDto;
import com.youngstersclub.app.dto.ConsumableOrderCreateRequest;
import com.youngstersclub.app.dto.ConsumableOrderResponseDto;
import com.youngstersclub.app.entity.ConsumableItem;
import com.youngstersclub.app.entity.ConsumableOrder;
import com.youngstersclub.app.entity.ConsumableOrderItem;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.repository.ConsumableItemRepository;
import com.youngstersclub.app.repository.ConsumableOrderRepository;
import com.youngstersclub.app.repository.UserRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ConsumableService {

    private final ConsumableItemRepository consumableItemRepository;
    private final ConsumableOrderRepository consumableOrderRepository;
    private final UserRepository userRepository;

    public ConsumableService(
            ConsumableItemRepository consumableItemRepository,
            ConsumableOrderRepository consumableOrderRepository,
            UserRepository userRepository) {
        this.consumableItemRepository = consumableItemRepository;
        this.consumableOrderRepository = consumableOrderRepository;
        this.userRepository = userRepository;
    }

    public List<ConsumableItemOptionDto> searchActiveItems(String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() < 3) {
            return List.of();
        }

        return consumableItemRepository.findTop10ByIsActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc(normalizedQuery)
                .stream()
                .map(item -> new ConsumableItemOptionDto(item.getId(), item.getName(), item.getPrice()))
                .toList();
    }

    @Transactional
    public ConsumableOrderResponseDto createOrder(ConsumableOrderCreateRequest request) {
        if (request == null || request.getUserId() == null) {
            throw new IllegalArgumentException("User is required");
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("At least one consumable item is required");
        }

        User user = userRepository.findById(request.getUserId()).orElseThrow();

        Map<Long, Integer> quantitiesByItemId = new LinkedHashMap<>();
        for (ConsumableOrderCreateRequest.ItemRequest itemRequest : request.getItems()) {
            if (itemRequest == null || itemRequest.getItemId() == null || itemRequest.getQuantity() == null) {
                throw new IllegalArgumentException("Consumable item and quantity are required");
            }
            if (itemRequest.getQuantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be greater than zero");
            }
            quantitiesByItemId.merge(itemRequest.getItemId(), itemRequest.getQuantity(), Integer::sum);
        }

        List<Long> itemIds = quantitiesByItemId.keySet().stream().toList();
        List<ConsumableItem> items = consumableItemRepository.findByIdInAndIsActiveTrue(itemIds);
        if (items.size() != itemIds.size()) {
            throw new IllegalArgumentException("One or more consumable items are unavailable");
        }

        Map<Long, ConsumableItem> itemMap = items.stream()
                .collect(java.util.stream.Collectors.toMap(ConsumableItem::getId, item -> item));

        ConsumableOrder order = new ConsumableOrder();
        order.setUser(user);
        order.setPaymentStatus("UNPAID");
        order.setTotalAmount(BigDecimal.ZERO);

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : quantitiesByItemId.entrySet()) {
            ConsumableItem item = itemMap.get(entry.getKey());
            Integer quantity = entry.getValue();
            BigDecimal lineTotal = item.getPrice().multiply(BigDecimal.valueOf(quantity));

            ConsumableOrderItem orderItem = new ConsumableOrderItem();
            orderItem.setItem(item);
            orderItem.setQuantity(quantity);
            orderItem.setPrice(item.getPrice());
            orderItem.setTotalCost(lineTotal);

            order.addItem(orderItem);
            totalAmount = totalAmount.add(lineTotal);
        }

        order.setTotalAmount(totalAmount);
        ConsumableOrder savedOrder = consumableOrderRepository.save(order);
        return new ConsumableOrderResponseDto(savedOrder.getId(), totalAmount);
    }

    public BigDecimal getConsumableDue(Integer userId) {
        if (userId == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = consumableOrderRepository.getTotalUnpaidDueByUserId(userId);
        return total == null ? BigDecimal.ZERO : total;
    }

    public List<ConsumableDueRowDto> getDueConsumables(Integer userId) {
        if (userId == null) {
            return List.of();
        }

        return consumableOrderRepository.findUnpaidOrderItemsByUserId(userId).stream()
                .map(row -> new ConsumableDueRowDto(
                        row.getOrderId(),
                        row.getItemName(),
                        row.getQuantity(),
                        row.getPrice(),
                        row.getTotalCost(),
                        row.getCreatedAt()))
                .toList();
    }

    public List<ConsumableHistoryRowDto> getConsumableHistory(Integer userId) {
        if (userId == null) {
            return List.of();
        }

        return consumableOrderRepository.findConsumableHistoryByUserId(userId).stream()
                .map(row -> new ConsumableHistoryRowDto(
                        row.getItemName(),
                        row.getQuantity(),
                        row.getDate(),
                        row.getAmount(),
                        row.getPaymentStatus()))
                .toList();
    }
}
