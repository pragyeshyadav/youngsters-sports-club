package com.youngstersclub.app.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;

class BranchAwareEntityMappingTest {

  @Test
  void affectedEntitiesExposeMandatoryLazyBranchRelation() throws Exception {
    assertBranchMapping(SnookerTable.class);
    assertBranchMapping(Frame.class);
    assertBranchMapping(Payment.class);
    assertBranchMapping(UserDue.class);
    assertBranchMapping(KidsPlaySession.class);
    assertBranchMapping(Game.class);
    assertBranchMapping(GameActivityOrder.class);
    assertBranchMapping(ConsumableItem.class);
    assertBranchMapping(ConsumableItemStock.class);
    assertBranchMapping(ConsumableOrder.class);
    assertBranchMapping(CustomerFeedback.class);
    assertBranchMapping(Tournament.class);
  }

  @Test
  void userDueUsesUserBranchUniquenessInsteadOfGlobalUserUniqueness() {
    Table table = UserDue.class.getAnnotation(Table.class);
    assertNotNull(table);
    List<UniqueConstraint> constraints = List.of(table.uniqueConstraints());
    assertTrue(
        constraints.stream()
            .anyMatch(
                constraint ->
                    List.of(constraint.columnNames()).contains("user_id")
                        && List.of(constraint.columnNames()).contains("branch_id")));
  }

  @Test
  void branchEntityDoesNotGrowReverseOperationalCollectionsInThisPhase() {
    List<String> collectionFields =
        List.of(Branch.class.getDeclaredFields()).stream()
            .map(Field::getName)
            .filter(
                name ->
                    !name.equals("userBranchAccesses")
                        && !name.equals("baseOrganizationUsers")
                        && java.util.Collection.class.isAssignableFrom(getFieldType(name)))
            .toList();

    assertTrue(collectionFields.isEmpty(), "Branch should stay lightweight in Phase 1");
  }

  private void assertBranchMapping(Class<?> entityClass) throws Exception {
    Field branchField = entityClass.getDeclaredField("branch");
    assertEquals(Branch.class, branchField.getType(), entityClass.getSimpleName() + " branch type mismatch");
    assertNotNull(branchField.getAnnotation(JsonIgnore.class), entityClass.getSimpleName() + " branch should stay out of direct JSON payloads");

    ManyToOne manyToOne = branchField.getAnnotation(ManyToOne.class);
    assertNotNull(manyToOne, entityClass.getSimpleName() + " branch must be @ManyToOne");
    assertEquals(FetchType.LAZY, manyToOne.fetch(), entityClass.getSimpleName() + " branch must be lazy");
    assertFalse(manyToOne.optional(), entityClass.getSimpleName() + " branch must be required");

    JoinColumn joinColumn = branchField.getAnnotation(JoinColumn.class);
    assertNotNull(joinColumn, entityClass.getSimpleName() + " branch must be @JoinColumn");
    assertEquals("branch_id", joinColumn.name(), entityClass.getSimpleName() + " branch column mismatch");
    assertFalse(joinColumn.nullable(), entityClass.getSimpleName() + " branch column must be non-null");
  }

  private Class<?> getFieldType(String name) {
    try {
      return Branch.class.getDeclaredField(name).getType();
    } catch (NoSuchFieldException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
