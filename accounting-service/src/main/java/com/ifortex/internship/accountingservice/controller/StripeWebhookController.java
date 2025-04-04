package com.ifortex.internship.accountingservice.controller;

import com.ifortex.internship.accountingservice.service.StripeWebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.net.Webhook;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@RestController
@RequestMapping("/api/v1/subscription")
@RequiredArgsConstructor
public class StripeWebhookController {

    static final String EVENT_TYPE_PAYMENT_SUCCEEDED = "invoice.payment_succeeded";
    static final String EVENT_TYPE_SUBSCRIPTION_DELETED = "customer.subscription.deleted";
    static final String EVENT_TYPE_SUBSCRIPTION_UPDATED = "customer.subscription.updated";
    static final String LOG_STRIPE_ERROR = "Unable to deserialize Stripe invoice object from event.";

    StripeWebhookService stripeWebhookService;

    @Value("${app.stripe.api.webhook.secret}") String endpointSecret;

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
            case EVENT_TYPE_PAYMENT_SUCCEEDED:
                Invoice invoice = (Invoice) event.getDataObjectDeserializer().getObject().orElse(null);
                if (invoice != null) {
                    stripeWebhookService.processInvoicePaymentSucceeded(invoice);
                } else {
                    log.error(LOG_STRIPE_ERROR);
                }
                break;

            case EVENT_TYPE_SUBSCRIPTION_DELETED:
                Subscription subscription =
                    (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
                if (subscription != null) {
                    stripeWebhookService.processSubscriptionCancellation(subscription);
                } else {
                    log.error(LOG_STRIPE_ERROR);
                }
                break;

            case EVENT_TYPE_SUBSCRIPTION_UPDATED:
                Subscription subscriptionObj =
                    (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
                if (subscriptionObj != null) {
                    stripeWebhookService.processSubscriptionUpdated(subscriptionObj);
                } else {
                    log.error(LOG_STRIPE_ERROR);
                }
                break;
            default:
                break;
        }

        return ResponseEntity.status(HttpStatus.OK).build();
    }
}
