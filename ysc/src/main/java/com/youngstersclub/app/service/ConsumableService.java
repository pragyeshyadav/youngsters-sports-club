package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.ConsumableDueRowDto;
import com.youngstersclub.app.dto.ConsumableHistoryRowDto;
import com.youngstersclub.app.dto.ConsumableItemOptionDto;
import com.youngstersclub.app.dto.ConsumableOrderCreateRequest;
import com.youngstersclub.app.dto.ConsumableOrderResponseDto;
import com.youngstersclub.app.dto.ConsumableStockCreateRequest;
import com.youngstersclub.app.dto.ConsumableStockCreateResponseDto;
import com.youngstersclub.app.dto.ConsumableStockReportRowDto;
import com.youngstersclub.app.entity.ConsumableItem;
import com.youngstersclub.app.entity.ConsumableItemStock;
import com.youngstersclub.app.entity.ConsumableOrder;
import com.youngstersclub.app.entity.ConsumableOrderItem;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.repository.ConsumableItemRepository;
import com.youngstersclub.app.repository.ConsumableItemStockRepository;
import com.youngstersclub.app.repository.ConsumableOrderRepository;
import com.youngstersclub.app.repository.UserRepository;
import com.youngstersclub.app.util.TimeUtil;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ConsumableService {

    private final ConsumableItemRepository consumableItemRepository;
    private final ConsumableItemStockRepository consumableItemStockRepository;
    private final ConsumableOrderRepository consumableOrderRepository;
    private final UserRepository userRepository;

    public ConsumableService(
            ConsumableItemRepository consumableItemRepository,
            ConsumableItemStockRepository consumableItemStockRepository,
            ConsumableOrderRepository consumableOrderRepository,
            UserRepository userRepository) {
        this.consumableItemRepository = consumableItemRepository;
        this.consumableItemStockRepository = consumableItemStockRepository;
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

    @Transactional
    public ConsumableStockCreateResponseDto addStock(ConsumableStockCreateRequest request) {
        if (request == null || request.getItemId() == null) {
            throw new IllegalArgumentException("Consumable item is required");
        }
        if (request.getQuantityAdded() == null || request.getQuantityAdded() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        if (request.getAddedBy() == null) {
            throw new IllegalArgumentException("Added by user is required");
        }

        ConsumableItem item = consumableItemRepository.findById(request.getItemId())
                .orElseThrow(() -> new IllegalArgumentException("Consumable item not found"));
        User addedBy = userRepository.findById(request.getAddedBy())
                .orElseThrow(() -> new IllegalArgumentException("Added by user not found"));

        ConsumableItemStock stock = new ConsumableItemStock();
        stock.setItem(item);
        stock.setQuantityAdded(request.getQuantityAdded());
        stock.setAddedBy(addedBy);

        ConsumableItemStock savedStock = consumableItemStockRepository.save(stock);
        return new ConsumableStockCreateResponseDto(savedStock.getId(), "Stock added successfully");
    }

    public List<ConsumableStockReportRowDto> getStockReport(int month, int year) {
        validateMonthYear(month, year);

        return consumableItemRepository.getConsumableStockReport(month, year).stream()
                .map(row -> new ConsumableStockReportRowDto(
                        row.getItemId(),
                        row.getItemName(),
                        row.getStockAdded() == null ? 0L : row.getStockAdded(),
                        row.getSoldQuantity() == null ? 0L : row.getSoldQuantity(),
                        row.getAvailableStock() == null ? 0L : row.getAvailableStock()))
                .toList();
    }

    public BigDecimal getConsumableDue(Integer userId) {
        if (userId == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = consumableOrderRepository.getTotalUnpaidDueByUserId(userId);
        return total == null ? BigDecimal.ZERO : total;
    }

    public BigDecimal getConsumableDueByDate(Integer userId, LocalDate selectedDate) {
        if (userId == null || selectedDate == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = consumableOrderRepository.getTotalUnpaidDueByUserIdAndDate(userId, selectedDate);
        return total == null ? BigDecimal.ZERO : total;
    }

    public List<ConsumableOrder> getUnpaidOrdersByDate(Integer userId, LocalDate selectedDate) {
        if (userId == null || selectedDate == null) {
            return List.of();
        }
        return consumableOrderRepository.findByUserIdAndPaymentStatusAndCreatedDate(userId, "UNPAID", selectedDate);
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

    private void validateMonthYear(int month, int year) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }

        int currentYear = TimeUtil.nowIST().getYear();
        if (year < currentYear - 1 || year > currentYear) {
            throw new IllegalArgumentException("Year must be current year or previous year");
        }
    }
}
