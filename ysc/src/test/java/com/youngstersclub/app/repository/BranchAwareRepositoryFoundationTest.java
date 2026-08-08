package com.youngstersclub.app.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BranchAwareRepositoryFoundationTest {

  @Test
  void foundationalBranchAwareMethodsExistForPhaseTwoServices() throws Exception {
    assertMethod(SnookerTableRepository.class, "findByBranch_IdAndIsActiveTrueOrderByIdAsc", java.util.List.class, Long.class);
    assertMethod(SnookerTableRepository.class, "findByBranch_IdAndIsAvailableTrueOrderByIdAsc", java.util.List.class, Long.class);
    assertMethod(SnookerTableRepository.class, "findByIdAndBranch_Id", Optional.class, Long.class, Long.class);
    assertMethod(FrameRepository.class, "findByIdAndBranch_Id", Optional.class, Integer.class, Long.class);
    assertMethod(FrameRepository.class, "findTopPlayersOfMonthByBranch", java.util.List.class, Long.class, LocalDateTime.class, LocalDateTime.class);
    assertMethod(FrameRepository.class, "getTotalDueForUsersByBranch", java.util.List.class, java.util.List.class, Long.class);
    assertMethod(PaymentRepository.class, "findByBranch_Id", java.util.List.class, Long.class);
    assertMethod(PaymentRepository.class, "findByIdAndBranch_Id", Optional.class, Integer.class, Long.class);
    assertMethod(UserDueRepository.class, "findByUser_IdAndBranch_Id", Optional.class, Integer.class, Long.class);
    assertMethod(UserDueRepository.class, "findByUserIdAndBranchId", Optional.class, Integer.class, Long.class);
    assertMethod(KidsPlaySessionRepository.class, "findByIdAndBranch_Id", Optional.class, Long.class, Long.class);
    assertMethod(KidsPlaySessionRepository.class, "getTotalUnpaidDueByParentUserIdsAndBranchId", java.util.List.class, java.util.List.class, Long.class);
    assertMethod(GameRepository.class, "findByIdAndBranch_Id", Optional.class, Long.class, Long.class);
    assertMethod(GameActivityOrderRepository.class, "findByIdAndBranch_Id", Optional.class, Long.class, Long.class);
    assertMethod(GameActivityOrderRepository.class, "getTotalUnpaidDueByParentUserIdsAndBranchId", java.util.List.class, java.util.List.class, Long.class);
    assertMethod(ConsumableItemRepository.class, "findByIdAndBranch_Id", Optional.class, Long.class, Long.class);
    assertMethod(ConsumableItemStockRepository.class, "findByIdAndBranch_Id", Optional.class, Long.class, Long.class);
    assertMethod(ConsumableOrderRepository.class, "findByIdAndBranch_Id", Optional.class, Long.class, Long.class);
    assertMethod(ConsumableOrderRepository.class, "getTotalUnpaidDueByUserIdsAndBranchId", java.util.List.class, java.util.List.class, Long.class);
    assertMethod(CustomerFeedbackRepository.class, "findByIdAndBranch_Id", Optional.class, Long.class, Long.class);
    assertMethod(TournamentRepository.class, "findByIdAndBranch_Id", Optional.class, Long.class, Long.class);
  }

  private void assertMethod(
      Class<?> repositoryClass, String methodName, Class<?> returnType, Class<?>... parameterTypes)
      throws Exception {
    Method method = repositoryClass.getMethod(methodName, parameterTypes);
    assertNotNull(method, repositoryClass.getSimpleName() + " should expose " + methodName);
    assertEquals(returnType, method.getReturnType(), repositoryClass.getSimpleName() + " return type mismatch for " + methodName);
  }
}
