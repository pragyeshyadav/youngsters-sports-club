package com.youngstersclub.app.api;

import com.youngstersclub.app.entity.CustomerFeedback;
import com.youngstersclub.app.repository.CustomerFeedbackRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final CustomerFeedbackRepository repository;

    public FeedbackController(CustomerFeedbackRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<String> saveFeedback(@RequestBody CustomerFeedback feedback) {
        if (feedback.getUserId() == null
                || feedback.getStarRating() == null
                || feedback.getStarRating() < 1
                || feedback.getStarRating() > 5
                || feedback.getFeedback() == null
                || feedback.getFeedback().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Invalid feedback");
        }

        feedback.setFeedback(feedback.getFeedback().trim());
        repository.save(feedback);
        return ResponseEntity.ok("Feedback saved");
    }
}
