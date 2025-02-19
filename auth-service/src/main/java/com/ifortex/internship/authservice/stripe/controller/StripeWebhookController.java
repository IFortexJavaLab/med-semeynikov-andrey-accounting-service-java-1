package com.ifortex.internship.authservice.stripe.controller;

import com.ifortex.internship.authservice.stripe.service.StripeWebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.*;
import com.stripe.net.Webhook;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/subscription")
@RequiredArgsConstructor
public class StripeWebhookController {

  private final StripeWebhookService stripeWebhookService;

  @Value("${app.stripe.api.webhook.secret}")
  private String endpointSecret;

  @PostMapping("/webhooks")
  public ResponseEntity<String> handleStripeWebhook(
      HttpServletRequest request, @RequestBody String payload) {
    String sigHeader = request.getHeader("Stripe-Signature");
    Event event;

    try {
      event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
    } catch (SignatureVerificationException e) {
      log.error("Stripe webhook signature verification failed: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Webhook error: " + e.getMessage());
    }

    switch (event.getType()) {
      case "invoice.payment_succeeded":
        Invoice invoice = (Invoice) event.getDataObjectDeserializer().getObject().orElse(null);
        if (invoice != null) {
          stripeWebhookService.processInvoicePaymentSucceeded(invoice);
        } else {
          log.warn("Unable to deserialize Stripe invoice object from event.");
        }
        break;

      case "customer.subscription.deleted":
        Subscription subscription =
            (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
        if (subscription != null) {
          stripeWebhookService.processSubscriptionCancellation(subscription);
        } else {
          log.warn("Unable to deserialize Stripe invoice object from event.");
        }
        break;

      case "customer.subscription.updated":
        Subscription subscriptionObj =
            (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
        if (subscriptionObj != null) {
          stripeWebhookService.processSubscriptionUpdated(subscriptionObj);
        } else {
          log.warn("Unable to deserialize Stripe subscription object from event.");
        }
        break;
    }

    return ResponseEntity.status(HttpStatus.OK).build();
  }
}
