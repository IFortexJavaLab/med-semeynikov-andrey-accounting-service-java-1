package com.ifortex.internship.accountingservice.controller;

import com.ifortex.internship.accountingservice.dto.request.PurchaseSubscriptionRequest;
import com.ifortex.internship.accountingservice.dto.response.PurchaseSubscriptionResponse;
import com.ifortex.internship.accountingservice.dto.response.SubscriptionPlanDto;
import com.ifortex.internship.accountingservice.service.StripeService;
import com.ifortex.internship.medstarter.security.service.AuthenticationFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final StripeService stripeService;
    private final AuthenticationFacade authenticationFacade;

    @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionPlanDto>> getAvailableSubscriptions() {
        var subscriptions = stripeService.getAvailablePlans();
        return ResponseEntity.ok(subscriptions);
    }

    @PostMapping("/subscribe")
    public ResponseEntity<PurchaseSubscriptionResponse> subscribe(
        @RequestBody PurchaseSubscriptionRequest request) {
        log.info("Request for purchasing subscription from account: {}", authenticationFacade.getAccountIdFromAuthentication());
        return ResponseEntity.ok(stripeService.createSubscriptionCheckoutSession(request));
    }

    @PostMapping("/cancel")
    public ResponseEntity<Void> cancel() {
        stripeService.cancelSubscriptionForUser();
        return ResponseEntity.status(HttpStatus.OK).build();
    }
}
