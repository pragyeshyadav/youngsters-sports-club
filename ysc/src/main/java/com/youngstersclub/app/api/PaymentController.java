package com.youngstersclub.app.api;

import com.youngstersclub.app.dto.PaymentRequest;
import com.youngstersclub.app.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/settle")
    public ResponseEntity<String> settlePayment(
            @RequestBody PaymentRequest request,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        paymentService.settlePayment(request, actorEmail);
        return ResponseEntity.ok("SUCCESS");
    }

    @PostMapping("/settle-by-date")
    public ResponseEntity<String> settlePaymentByDate(
            @RequestBody com.youngstersclub.app.dto.PaymentByDateRequest request,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        paymentService.settlePaymentByDate(request, actorEmail);
        return ResponseEntity.ok("SUCCESS");
    }
}
