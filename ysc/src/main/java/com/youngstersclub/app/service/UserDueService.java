package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.CustomerBranchDue;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.entity.UserDue;
import com.youngstersclub.app.repository.UserDueRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDueService {

    private final UserDueRepository userDueRepository;
    private final CustomerBranchDueCalculatorService customerBranchDueCalculatorService;

    public UserDueService(
            UserDueRepository userDueRepository,
            CustomerBranchDueCalculatorService customerBranchDueCalculatorService) {
        this.userDueRepository = userDueRepository;
        this.customerBranchDueCalculatorService = customerBranchDueCalculatorService;
    }

    @Transactional
    public BigDecimal syncBranchDue(User user, Branch branch) {
        if (user == null || user.getId() == null || branch == null || branch.getId() == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        CustomerBranchDue calculatedDue =
                customerBranchDueCalculatorService.calculateCustomerDue(user.getId().longValue(), branch.getId());
        BigDecimal totalDue = normalize(calculatedDue.totalDue());
        UserDue userDue = userDueRepository
                .findByUserIdAndBranchId(user.getId(), branch.getId())
                .orElse(null);

        if (userDue == null && totalDue.compareTo(BigDecimal.ZERO) <= 0) {
            return totalDue;
        }

        if (userDue == null) {
            userDue = new UserDue();
            userDue.setUser(user);
            userDue.setBranch(branch);
        }

        userDue.setDueAmount(totalDue);
        userDueRepository.save(userDue);
        return totalDue;
    }

    private BigDecimal normalize(BigDecimal amount) {
        return amount == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : amount.setScale(2, RoundingMode.HALF_UP);
    }
}
