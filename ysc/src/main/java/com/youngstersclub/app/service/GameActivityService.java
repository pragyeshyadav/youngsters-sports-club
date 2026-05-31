package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.GameActivityOptionDto;
import com.youngstersclub.app.dto.GameActivityOrderCreateRequest;
import com.youngstersclub.app.dto.GameActivityOrderResponseDto;
import com.youngstersclub.app.dto.PendingKidsPlayBreakdownDto;
import com.youngstersclub.app.entity.Game;
import com.youngstersclub.app.entity.GameActivityOrder;
import com.youngstersclub.app.entity.Payment;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.PaymentMethod;
import com.youngstersclub.app.enums.PaymentStatus;
import com.youngstersclub.app.repository.GameActivityOrderRepository;
import com.youngstersclub.app.repository.GameRepository;
import com.youngstersclub.app.repository.PaymentRepository;
import com.youngstersclub.app.repository.UserRepository;
import com.youngstersclub.app.util.TimeUtil;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class GameActivityService {

    private static final String SOFT_PLAY_ZONE_NAME = "Soft Play Zone";
    private static final List<Integer> ALLOWED_DURATIONS = List.of(10, 15, 20, 30, 45, 50, 60, 70, 80, 90, 120);

    private final GameRepository gameRepository;
    private final GameActivityOrderRepository gameActivityOrderRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;

    public GameActivityService(
            GameRepository gameRepository,
            GameActivityOrderRepository gameActivityOrderRepository,
            UserRepository userRepository,
            PaymentRepository paymentRepository) {
        this.gameRepository = gameRepository;
        this.gameActivityOrderRepository = gameActivityOrderRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
    }

    public List<GameActivityOptionDto> searchActiveGames(String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() < 3) {
            return List.of();
        }

        return gameRepository.findTop10ByIsActiveTrueAndGameNameContainingIgnoreCaseOrderByGameNameAsc(normalizedQuery)
                .stream()
                .map(game -> new GameActivityOptionDto(game.getId(), game.getGameName(), game.getBasePricePerMinute()))
                .toList();
    }

    @Transactional
    public GameActivityOrderResponseDto createOrders(GameActivityOrderCreateRequest request) {
        if (request == null || request.getParentUserId() == null) {
            throw new IllegalArgumentException("Parent customer is required");
        }
        if (request.getCreatedBy() == null) {
            throw new IllegalArgumentException("Created by user is required");
        }
        if (request.getActivities() == null || request.getActivities().isEmpty()) {
            throw new IllegalArgumentException("At least one activity is required");
        }

        User parentUser = userRepository.findById(request.getParentUserId())
                .orElseThrow(() -> new IllegalArgumentException("Parent customer not found"));
        User createdBy = userRepository.findById(request.getCreatedBy())
                .orElseThrow(() -> new IllegalArgumentException("Created by user not found"));

        Map<Long, List<GameActivityOrderCreateRequest.ActivityRequest>> groupedActivities = new LinkedHashMap<>();
        for (GameActivityOrderCreateRequest.ActivityRequest activity : request.getActivities()) {
            if (activity == null || activity.getGameId() == null) {
                throw new IllegalArgumentException("Game activity is required");
            }
            validateDuration(activity.getDurationMinutes());
            groupedActivities.computeIfAbsent(activity.getGameId(), ignored -> new java.util.ArrayList<>()).add(activity);
        }

        List<Long> gameIds = groupedActivities.keySet().stream().toList();
        List<Game> activeGames = gameRepository.findByIdInAndIsActiveTrue(gameIds);
        if (activeGames.size() != gameIds.size()) {
            throw new IllegalArgumentException("One or more selected game activities are unavailable");
        }

        Map<Long, Game> gameMap = activeGames.stream()
                .collect(java.util.stream.Collectors.toMap(Game::getId, game -> game));

        BigDecimal totalAmount = BigDecimal.ZERO;
        int createdOrders = 0;
        for (GameActivityOrderCreateRequest.ActivityRequest activity : request.getActivities()) {
            Game game = gameMap.get(activity.getGameId());
            int numberOfChildren = normalizeChildCount(game, activity.getNumberOfChildren());
            BigDecimal lineTotal = game.getBasePricePerMinute()
                    .multiply(BigDecimal.valueOf(activity.getDurationMinutes()))
                    .multiply(BigDecimal.valueOf(numberOfChildren));

            GameActivityOrder order = new GameActivityOrder();
            order.setParentUser(parentUser);
            order.setGame(game);
            order.setNumberOfChildren(numberOfChildren);
            order.setDurationMinutes(activity.getDurationMinutes());
            order.setRatePerMinute(game.getBasePricePerMinute());
            order.setTotalAmount(lineTotal);
            order.setIsPaid(false);
            order.setCreatedBy(createdBy);
            gameActivityOrderRepository.save(order);

            totalAmount = totalAmount.add(lineTotal);
            createdOrders++;
        }

        return new GameActivityOrderResponseDto(createdOrders, totalAmount);
    }

    public BigDecimal getActivityDue(Integer parentUserId) {
        if (parentUserId == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal due = gameActivityOrderRepository.getTotalUnpaidDueByParentUserId(parentUserId);
        return due == null ? BigDecimal.ZERO : due;
    }

    public BigDecimal getActivityDueByDate(Integer parentUserId, LocalDate selectedDate) {
        if (parentUserId == null || selectedDate == null) {
            return BigDecimal.ZERO;
        }
        return getUnpaidOrdersByDate(parentUserId, selectedDate).stream()
                .map(GameActivityOrder::getTotalAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Map<Integer, BigDecimal> getActivityDueMap(List<Integer> userIds) {
        Map<Integer, BigDecimal> dues = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return dues;
        }

        gameActivityOrderRepository.getTotalUnpaidDueByParentUserIds(userIds).forEach(projection ->
                dues.put(projection.getUserId(), projection.getAmount() == null ? BigDecimal.ZERO : projection.getAmount()));
        return dues;
    }

    public List<GameActivityOrder> getUnpaidOrders(Integer parentUserId) {
        if (parentUserId == null) {
            return List.of();
        }
        return gameActivityOrderRepository.findUnpaidByParentUserIdOrderByCreatedAt(parentUserId);
    }

    public List<GameActivityOrder> getUnpaidOrdersByDate(Integer parentUserId, LocalDate selectedDate) {
        if (parentUserId == null || selectedDate == null) {
            return List.of();
        }
        return getUnpaidOrders(parentUserId).stream()
                .filter(order -> order.getCreatedAt() != null && selectedDate.equals(order.getCreatedAt().toLocalDate()))
                .toList();
    }

    public List<PendingKidsPlayBreakdownDto> getActivityDueBreakdownByDate(Integer parentUserId, LocalDate selectedDate) {
        if (parentUserId == null || selectedDate == null) {
            return List.of();
        }

        return getUnpaidOrdersByDate(parentUserId, selectedDate).stream()
                .map(order -> new PendingKidsPlayBreakdownDto(
                        -order.getId(),
                        buildActivityLabel(order),
                        order.getCreatedAt(),
                        order.getTotalAmount()))
                .toList();
    }

    @Transactional
    public BigDecimal settleActivityOrders(
            Integer parentUserId,
            BigDecimal amount,
            BigDecimal discount,
            User user,
            PaymentMethod paymentMethod) {
        return settleActivityOrdersByDate(parentUserId, null, amount, discount, user, paymentMethod);
    }

    @Transactional
    public BigDecimal settleActivityOrdersByDate(
            Integer parentUserId,
            LocalDate selectedDate,
            BigDecimal amount,
            BigDecimal discount,
            User user,
            PaymentMethod paymentMethod) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            amount = BigDecimal.ZERO;
        }
        if (discount == null || discount.compareTo(BigDecimal.ZERO) < 0) {
            discount = BigDecimal.ZERO;
        }
        if (amount.add(discount).compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        List<GameActivityOrder> orders = selectedDate == null
                ? getUnpaidOrders(parentUserId)
                : getUnpaidOrdersByDate(parentUserId, selectedDate);

        BigDecimal remainingCash = amount;
        BigDecimal remainingDiscount = discount;
        BigDecimal remainingSettlement = amount.add(discount);

        for (GameActivityOrder order : orders) {
            if (remainingSettlement.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal due = order.getTotalAmount();
            if (due == null || due.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal settlementAmount = remainingSettlement.min(due);
            BigDecimal cashAmount = remainingCash.min(settlementAmount);
            BigDecimal discountAmount = settlementAmount.subtract(cashAmount);

            Payment payment = new Payment();
            payment.setFrame(null);
            payment.setUser(user);
            payment.setAmount(cashAmount);
            payment.setDiscount(discountAmount);
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaymentMethod(paymentMethod);
            payment.setPaymentTime(TimeUtil.nowIST());
            paymentRepository.save(payment);

            BigDecimal updatedDue = due.subtract(settlementAmount);
            order.setTotalAmount(updatedDue);
            order.setIsPaid(updatedDue.compareTo(BigDecimal.ZERO) == 0);
            order.setPaymentId(payment.getId());
            gameActivityOrderRepository.save(order);

            remainingCash = remainingCash.subtract(cashAmount);
            remainingDiscount = remainingDiscount.subtract(discountAmount);
            remainingSettlement = remainingCash.add(remainingDiscount);
        }

        return amount.add(discount).subtract(remainingSettlement);
    }

    public BigDecimal getPaidEarningsBetween(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        BigDecimal amount = gameActivityOrderRepository.getPaidEarningsBetween(startDateTime, endDateTime);
        return amount == null ? BigDecimal.ZERO : amount;
    }

    public BigDecimal getGrossEarningsBetween(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        BigDecimal amount = gameActivityOrderRepository.getGrossEarningsBetween(startDateTime, endDateTime);
        return amount == null ? BigDecimal.ZERO : amount;
    }

    public BigDecimal getTotalUnpaidDueBetween(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        BigDecimal amount = gameActivityOrderRepository.getTotalUnpaidDueBetween(startDateTime, endDateTime);
        return amount == null ? BigDecimal.ZERO : amount;
    }

    public Map<Integer, BigDecimal> getUnpaidDueByUserForDate(LocalDate selectedDate) {
        if (selectedDate == null) {
            return Map.of();
        }

        Map<Integer, BigDecimal> dues = new LinkedHashMap<>();
        LocalDateTime startDateTime = selectedDate.atStartOfDay();
        LocalDateTime endDateTime = selectedDate.plusDays(1).atStartOfDay();
        gameActivityOrderRepository.getUnpaidDueByUserForDate(startDateTime, endDateTime).forEach(projection ->
                dues.put(projection.getUserId(), projection.getAmount() == null ? BigDecimal.ZERO : projection.getAmount()));
        return dues;
    }

    private void validateDuration(Integer durationMinutes) {
        if (durationMinutes == null || durationMinutes <= 0) {
            throw new IllegalArgumentException("Duration is required");
        }
        if (!ALLOWED_DURATIONS.contains(durationMinutes)) {
            throw new IllegalArgumentException("Invalid duration selected");
        }
    }

    private int normalizeChildCount(Game game, Integer requestedCount) {
        boolean isSoftPlayZone = game != null && isSoftPlayZone(game.getGameName());

        int childCount = requestedCount == null ? 1 : requestedCount;
        if (isSoftPlayZone) {
            if (childCount < 1 || childCount > 30) {
                throw new IllegalArgumentException("Soft Play Zone requires 1 to 30 kids");
            }
            return childCount;
        }

        if (childCount != 1) {
            throw new IllegalArgumentException("Only Soft Play Zone supports multiple kids");
        }
        return 1;
    }

    private String buildActivityLabel(GameActivityOrder order) {
        String gameName = order.getGame() != null && order.getGame().getGameName() != null
                ? order.getGame().getGameName()
                : "Play Zone Activity";
        int childCount = order.getNumberOfChildren() == null || order.getNumberOfChildren() <= 0
                ? 1
                : order.getNumberOfChildren();
        if (isSoftPlayZone(gameName)) {
            return gameName + " (" + childCount + (childCount == 1 ? " kid" : " kids") + ")";
        }
        return gameName;
    }

    private boolean isSoftPlayZone(String gameName) {
        return gameName != null && SOFT_PLAY_ZONE_NAME.equalsIgnoreCase(gameName.trim());
    }
}
