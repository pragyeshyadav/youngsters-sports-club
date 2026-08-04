package com.youngstersclub.app.api;

import com.youngstersclub.app.entity.CustomerFeedback;
import com.youngstersclub.app.service.FeedbackService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public ResponseEntity<String> saveFeedback(
            @RequestBody CustomerFeedback feedback,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        try {
            feedbackService.saveFeedback(feedback, actorEmail);
            return ResponseEntity.ok("Feedback saved");
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        } catch (SecurityException exception) {
            return ResponseEntity.status(403).body(exception.getMessage());
        }
    }
}
