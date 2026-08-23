package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.CustomerBranchDue;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.entity.UserDue;
import com.youngstersclub.app.repository.UserDueRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserDueServiceTest {

    @Mock
    private UserDueRepository userDueRepository;

    @Mock
    private CustomerBranchDueCalculatorService customerBranchDueCalculatorService;

    @InjectMocks
    private UserDueService userDueService;

    @Test
    void syncBranchDueCreatesOrUpdatesOnlyRequestedBranchRow() {
        User user = new User();
        user.setId(25);
        Branch branch = new Branch();
        branch.setId(2L);

        UserDue existingDue = new UserDue();
        existingDue.setUser(user);
        existingDue.setBranch(branch);
        existingDue.setDueAmount(BigDecimal.valueOf(10));

        when(customerBranchDueCalculatorService.calculateCustomerDue(25L, 2L))
                .thenReturn(new CustomerBranchDue(
                        25L,
                        2L,
                        BigDecimal.valueOf(120),
                        BigDecimal.valueOf(30),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(150)));
        when(userDueRepository.findByUserIdAndBranchId(25, 2L)).thenReturn(Optional.of(existingDue));

        BigDecimal synced = userDueService.syncBranchDue(user, branch);

        ArgumentCaptor<UserDue> captor = ArgumentCaptor.forClass(UserDue.class);
        verify(userDueRepository).save(captor.capture());
        assertSame(existingDue, captor.getValue());
        assertEquals(0, BigDecimal.valueOf(150).compareTo(captor.getValue().getDueAmount()));
        assertEquals(0, BigDecimal.valueOf(150).compareTo(synced));
    }

    @Test
    void syncBranchDueSkipsCreatingZeroRowWhenNoOutstandingDueExists() {
        User user = new User();
        user.setId(25);
        Branch branch = new Branch();
        branch.setId(2L);

        when(customerBranchDueCalculatorService.calculateCustomerDue(25L, 2L))
                .thenReturn(new CustomerBranchDue(
                        25L,
                        2L,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO));
        when(userDueRepository.findByUserIdAndBranchId(25, 2L)).thenReturn(Optional.empty());

        BigDecimal synced = userDueService.syncBranchDue(user, branch);

        verify(userDueRepository, never()).save(org.mockito.ArgumentMatchers.any(UserDue.class));
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(synced));
    }
}
