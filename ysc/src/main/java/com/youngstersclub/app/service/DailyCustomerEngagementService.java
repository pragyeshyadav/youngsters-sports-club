package com.youngstersclub.app.service;

import com.youngstersclub.app.repository.UserRepository;
import com.youngstersclub.app.util.TimeUtil;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DailyCustomerEngagementService {

    private static final Logger log = LoggerFactory.getLogger(DailyCustomerEngagementService.class);

    private final UserRepository userRepository;
    private final WhatsAppService whatsAppService;

    public DailyCustomerEngagementService(UserRepository userRepository, WhatsAppService whatsAppService) {
        this.userRepository = userRepository;
        this.whatsAppService = whatsAppService;
    }

    @Scheduled(cron = "0 30 21 * * *", zone = "Asia/Kolkata")
    public void sendDailyVisitThankYouMessages() {
        LocalDate today = TimeUtil.nowIST().toLocalDate();
        List<UserRepository.DailyVisitedCustomerProjection> visitedCustomers = userRepository.findDailyVisitedCustomers(today);

        int totalUsers = visitedCustomers.size();
        int sentCount = 0;
        int failedCount = 0;

        log.info("Daily visit thank-you job started for date: {}. Total users identified: {}", today, totalUsers);

        for (UserRepository.DailyVisitedCustomerProjection customer : visitedCustomers) {
            boolean sent = whatsAppService.sendDailyVisitThankYouMessage(customer.getPhone(), customer.getName());
            if (sent) {
                sentCount++;
            } else {
                failedCount++;
                log.warn("Daily visit thank-you message failed or skipped for userId: {}", customer.getUserId());
            }
        }

        log.info(
                "Daily visit thank-you job completed for date: {}. Total users processed: {}, messages sent successfully: {}, failures: {}",
                today,
                totalUsers,
                sentCount,
                failedCount);
    }
}
