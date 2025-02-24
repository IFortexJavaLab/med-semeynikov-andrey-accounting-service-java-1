package com.ifortex.internship.authservice.stripe.controller;

import com.ifortex.internship.authservice.stripe.dto.request.PurchaseSubscriptionRequest;
import com.ifortex.internship.authservice.stripe.dto.response.PurchaseSubscriptionResponse;
import com.ifortex.internship.authservice.stripe.service.StripeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

  private final StripeService stripeService;

  @GetMapping("/plans")
  public ResponseEntity<?> getAvailableSubscriptions() {
    var subscriptions = stripeService.getAvailablePlans();
    return ResponseEntity.ok(subscriptions);
  }

  @PostMapping("/subscribe")
  public ResponseEntity<PurchaseSubscriptionResponse> subscribe(
      @RequestBody PurchaseSubscriptionRequest request) {
    PurchaseSubscriptionResponse response =
        stripeService.createSubscriptionCheckoutSession(request);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/cancel")
  public ResponseEntity<?> cancel() {
    stripeService.cancelSubscriptionForUser();
    return ResponseEntity.status(HttpStatus.OK).build();
  }
}
