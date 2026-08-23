package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.ConsumableDueRowDto;
import com.youngstersclub.app.dto.ConsumableHistoryRowDto;
import com.youngstersclub.app.dto.ConsumableItemOptionDto;
import com.youngstersclub.app.dto.ConsumableOrderCreateRequest;
import com.youngstersclub.app.dto.ConsumableOrderResponseDto;
import com.youngstersclub.app.dto.ConsumableStockCreateRequest;
import com.youngstersclub.app.dto.ConsumableStockCreateResponseDto;
import com.youngstersclub.app.dto.ConsumableStockReportRowDto;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.ConsumableItem;
import com.youngstersclub.app.entity.ConsumableItemStock;
import com.youngstersclub.app.entity.ConsumableOrder;
import com.youngstersclub.app.entity.ConsumableOrderItem;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.ConsumableItemRepository;
import com.youngstersclub.app.repository.ConsumableItemStockRepository;
import com.youngstersclub.app.repository.ConsumableOrderRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
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
    private final UserDueService userDueService;
    private final OrganizationContextService organizationContextService;
    private final BranchRepository branchRepository;
    private final OrganizationUserRepository organizationUserRepository;
    private final UserBranchAccessRepository userBranchAccessRepository;

    public ConsumableService(
            ConsumableItemRepository consumableItemRepository,
            ConsumableItemStockRepository consumableItemStockRepository,
            ConsumableOrderRepository consumableOrderRepository,
            UserRepository userRepository,
            UserDueService userDueService,
            OrganizationContextService organizationContextService,
            BranchRepository branchRepository,
            OrganizationUserRepository organizationUserRepository,
            UserBranchAccessRepository userBranchAccessRepository) {
        this.consumableItemRepository = consumableItemRepository;
        this.consumableItemStockRepository = consumableItemStockRepository;
        this.consumableOrderRepository = consumableOrderRepository;
        this.userRepository = userRepository;
        this.userDueService = userDueService;
        this.organizationContextService = organizationContextService;
        this.branchRepository = branchRepository;
        this.organizationUserRepository = organizationUserRepository;
        this.userBranchAccessRepository = userBranchAccessRepository;
    }

    public List<ConsumableItemOptionDto> searchActiveItems(String query, String actorEmail) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() < 3) {
            return List.of();
        }

        ConsumableBranchContext context = resolveConsumableContext(actorEmail);

        return consumableItemRepository.findTop10ByBranch_IdAndIsActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc(
                        context.branch().getId(),
                        normalizedQuery)
                .stream()
                .map(item -> new ConsumableItemOptionDto(item.getId(), item.getName(), item.getPrice()))
                .toList();
    }

    @Transactional
    public ConsumableOrderResponseDto createOrder(ConsumableOrderCreateRequest request, String actorEmail) {
        if (request == null || request.getUserId() == null) {
            throw new IllegalArgumentException("User is required");
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("At least one consumable item is required");
        }

        ConsumableBranchContext context = resolveConsumableContext(actorEmail);
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
        List<ConsumableItem> items = consumableItemRepository.findByIdInAndBranch_IdAndIsActiveTrue(
                itemIds,
                context.branch().getId());
        if (items.size() != itemIds.size()) {
            throw new IllegalArgumentException("One or more consumable items are unavailable");
        }

        Map<Long, ConsumableItem> itemMap = items.stream()
                .collect(java.util.stream.Collectors.toMap(ConsumableItem::getId, item -> item));

        ConsumableOrder order = new ConsumableOrder();
        order.setUser(user);
        order.setBranch(context.branch());
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
        userDueService.syncBranchDue(savedOrder.getUser(), savedOrder.getBranch());
        return new ConsumableOrderResponseDto(savedOrder.getId(), totalAmount);
    }

    @Transactional
    public ConsumableStockCreateResponseDto addStock(ConsumableStockCreateRequest request, String actorEmail) {
        if (request == null || request.getItemId() == null) {
            throw new IllegalArgumentException("Consumable item is required");
        }
        if (request.getQuantityAdded() == null || request.getQuantityAdded() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }

        ConsumableBranchContext context = resolveConsumableContext(actorEmail);

        ConsumableItem item = consumableItemRepository.findByIdAndBranch_Id(request.getItemId(), context.branch().getId())
                .orElseThrow(() -> new IllegalArgumentException("Consumable item not found"));

        ConsumableItemStock stock = new ConsumableItemStock();
        stock.setItem(item);
        stock.setBranch(context.branch());
        stock.setQuantityAdded(request.getQuantityAdded());
        stock.setAddedBy(context.actor());

        ConsumableItemStock savedStock = consumableItemStockRepository.save(stock);
        return new ConsumableStockCreateResponseDto(savedStock.getId(), "Stock added successfully");
    }

    public List<ConsumableStockReportRowDto> getStockReport(int month, int year, String actorEmail) {
        validateMonthYear(month, year);
        ConsumableBranchContext context = resolveConsumableContext(actorEmail);

        return consumableItemRepository.getConsumableStockReport(context.branch().getId(), month, year).stream()
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
        return getUnpaidOrdersByDate(userId, selectedDate).stream()
                .map(ConsumableOrder::getTotalAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Map<Integer, BigDecimal> getConsumableDueMap(List<Integer> userIds) {
        Map<Integer, BigDecimal> dues = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return dues;
        }

        consumableOrderRepository.getTotalUnpaidDueByUserIds(userIds).forEach(projection ->
                dues.put(projection.getUserId(), projection.getAmount() == null ? BigDecimal.ZERO : projection.getAmount()));
        return dues;
    }

    public Map<Integer, BigDecimal> getConsumableDueMap(List<Integer> userIds, Long branchId) {
        Map<Integer, BigDecimal> dues = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty() || branchId == null) {
            return dues;
        }

        consumableOrderRepository.getTotalUnpaidDueByUserIdsAndBranchId(userIds, branchId).forEach(projection ->
                dues.put(projection.getUserId(), projection.getAmount() == null ? BigDecimal.ZERO : projection.getAmount()));
        return dues;
    }

    public List<ConsumableOrder> getUnpaidOrdersByDate(Integer userId, LocalDate selectedDate) {
        if (userId == null || selectedDate == null) {
            return List.of();
        }
        return consumableOrderRepository.findByUserIdAndPaymentStatus(userId, "UNPAID").stream()
                .filter(order -> order.getCreatedAt() != null && selectedDate.equals(order.getCreatedAt().toLocalDate()))
                .toList();
    }

    public List<ConsumableOrder> getUnpaidOrders(Integer userId, Long branchId) {
        if (userId == null || branchId == null) {
            return List.of();
        }
        return consumableOrderRepository.findByUser_IdAndBranch_IdAndPaymentStatusOrderByCreatedAtAsc(
                userId,
                branchId,
                "UNPAID");
    }

    public List<ConsumableOrder> getUnpaidOrdersByDate(Integer userId, LocalDate selectedDate, Long branchId) {
        if (userId == null || selectedDate == null || branchId == null) {
            return List.of();
        }
        return getUnpaidOrders(userId, branchId).stream()
                .filter(order -> order.getCreatedAt() != null && selectedDate.equals(order.getCreatedAt().toLocalDate()))
                .toList();
    }

    public List<ConsumableDueRowDto> getDueConsumablesByDate(Integer userId, LocalDate selectedDate) {
        if (userId == null || selectedDate == null) {
            return List.of();
        }

        return getUnpaidOrdersByDate(userId, selectedDate).stream()
                .flatMap(order -> order.getItems().stream()
                        .map(item -> new ConsumableDueRowDto(
                                order.getId(),
                                item.getItem() != null ? item.getItem().getName() : null,
                                item.getQuantity(),
                                item.getPrice(),
                                item.getTotalCost(),
                                order.getCreatedAt())))
                        .toList();
    }

    public List<ConsumableDueRowDto> getDueConsumablesByDate(Integer userId, LocalDate selectedDate, Long branchId) {
        if (userId == null || selectedDate == null || branchId == null) {
            return List.of();
        }

        return getUnpaidOrdersByDate(userId, selectedDate, branchId).stream()
                .flatMap(order -> order.getItems().stream()
                        .map(item -> new ConsumableDueRowDto(
                                order.getId(),
                                item.getItem() != null ? item.getItem().getName() : null,
                                item.getQuantity(),
                                item.getPrice(),
                                item.getTotalCost(),
                                order.getCreatedAt())))
                .toList();
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

    public List<ConsumableDueRowDto> getDueConsumables(Integer userId, Long branchId) {
        if (userId == null || branchId == null) {
            return List.of();
        }

        return consumableOrderRepository.findUnpaidOrderItemsByUserIdAndBranchId(userId, branchId).stream()
                .map(row -> new ConsumableDueRowDto(
                        row.getOrderId(),
                        row.getItemName(),
                        row.getQuantity(),
                        row.getPrice(),
                        row.getTotalCost(),
                        row.getCreatedAt()))
                .toList();
    }

    public List<ConsumableHistoryRowDto> getConsumableHistory(Integer userId, Long branchId) {
        if (userId == null || branchId == null) {
            return List.of();
        }

        return consumableOrderRepository.findConsumableHistoryByUserIdAndBranchId(userId, branchId).stream()
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

    private ConsumableBranchContext resolveConsumableContext(String actorEmail) {
        String normalizedEmail = actorEmail == null ? "" : actorEmail.trim().toLowerCase();
        if (normalizedEmail.isEmpty()) {
            throw new SecurityException("Authenticated user email is required");
        }

        User actor = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new SecurityException("Authenticated user not found"));
        OrganizationContextDto context = organizationContextService.resolveContext(normalizedEmail);
        if (context.getCurrentOrganization() == null || context.getCurrentBranch() == null) {
            throw new SecurityException("Active organization and branch context are required");
        }

        Long organizationId = context.getCurrentOrganization().getId();
        Long branchId = context.getCurrentBranch().getId();

        OrganizationUser membership = organizationUserRepository
                .findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organizationId)
                .orElseThrow(() -> new SecurityException("Active organization membership not found"));
        Branch branch = branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(branchId, organizationId)
                .orElseThrow(() -> new SecurityException("Current branch is unavailable"));

        boolean hasAccess = membership.getBaseBranch() != null
                && branchId.equals(membership.getBaseBranch().getId());
        if (!hasAccess) {
            hasAccess = userBranchAccessRepository.existsByOrganizationUserIdAndBranchIdAndIsActiveTrue(
                    membership.getId(),
                    branchId);
        }

        if (!hasAccess) {
            throw new SecurityException("You do not have access to the current branch");
        }

        return new ConsumableBranchContext(actor, branch);
    }

    private record ConsumableBranchContext(User actor, Branch branch) {}
}
