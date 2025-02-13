package com.ifortex.internship.authservice.stripe.controller;

import com.ifortex.internship.authservice.stripe.dto.request.PurchaseSubscriptionRequest;
import com.ifortex.internship.authservice.stripe.dto.response.PurchaseSubscriptionResponse;
import com.ifortex.internship.authservice.stripe.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

  private final SubscriptionService subscriptionService;

  @GetMapping("/plans")
  public ResponseEntity<?> getAvailableSubscriptions() {
    var subscriptions = subscriptionService.getAvailablePlans();
    return ResponseEntity.ok(subscriptions);
  }

  @PostMapping("/subscribe")
  public ResponseEntity<PurchaseSubscriptionResponse> subscribe(
      @RequestBody PurchaseSubscriptionRequest request) {
    PurchaseSubscriptionResponse response =
        subscriptionService.createSubscriptionCheckoutSession(request);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/cancel")
  public ResponseEntity<?> cancel() {
    subscriptionService.cancelSubscriptionForUser();
    return ResponseEntity.status(HttpStatus.OK).build();
  }
}
