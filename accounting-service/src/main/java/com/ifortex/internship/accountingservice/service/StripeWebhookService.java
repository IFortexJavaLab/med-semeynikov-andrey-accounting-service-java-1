package com.ifortex.internship.accountingservice.service;

import com.ifortex.internship.accountingservice.model.Client;
import com.ifortex.internship.accountingservice.model.stripe.StripeSubscription;
import com.ifortex.internship.accountingservice.model.stripe.SubscriptionStatus;
import com.ifortex.internship.accountingservice.repository.ClientRepository;
import com.ifortex.internship.accountingservice.repository.SubscriptionRepository;
import com.stripe.model.Invoice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripeWebhookService {

    private final SubscriptionRepository subscriptionRepository;
    private final ClientRepository clientRepository;

    public void processInvoicePaymentSucceeded(Invoice invoice) {

        log.debug("Processing invoice payment succeeded for invoice id: {} ", invoice.getId());

        String stripeCustomerId = invoice.getCustomer();
        if (stripeCustomerId == null || stripeCustomerId.isEmpty()) {
            log.warn("Stripe invoice does not contain a customer ID.");
            return;
        }

        Optional<Client> clientOpt = clientRepository.findByStripeId(stripeCustomerId);
        if (clientOpt.isEmpty()) {
            log.error("Client with Stripe customer ID {} not found.", stripeCustomerId);
            return;
        }
        Client client = clientOpt.get();

        String stripeSubscriptionId = invoice.getSubscription();
        boolean missingSubscriptionId = stripeSubscriptionId == null || stripeSubscriptionId.isEmpty();
        if (missingSubscriptionId) {
            log.warn("Session {} does not contain a subscription ID.", invoice.getId());
            return;
        }

        boolean subscriptionAlreadyExists =
            subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId).isPresent();
        if (subscriptionAlreadyExists) {
            log.debug("Subscription record for subscription id {} already exists.", stripeSubscriptionId);
        } else {
            StripeSubscription stripeSubscription = new StripeSubscription();
            stripeSubscription.setStripeSubscriptionId(stripeSubscriptionId);
            stripeSubscription.setClient(client);

            subscriptionRepository.save(stripeSubscription);
            log.info(
                "Created new subscription record for client id: {} with subscription id: {}",
                client.getId(),
                stripeSubscriptionId);
        }
    }

    public void processSubscriptionCancellation(com.stripe.model.Subscription subscription) {

        String stripeSubscriptionId = subscription.getId();

        if (stripeSubscriptionId == null || stripeSubscriptionId.isEmpty()) {
            log.warn(
                "Subscription cancellation event {} does not contain a valid subscription ID.",
                subscription.getId());
            return;
        }

        Optional<StripeSubscription> subscriptionOpt =
            subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId);
        if (subscriptionOpt.isEmpty()) {
            log.warn(
                "No local subscription record found for Stripe subscription ID: {}",
                stripeSubscriptionId);
            return;
        }

        StripeSubscription localSubscription = subscriptionOpt.get();
        Client client = localSubscription.getClient();

        Instant startDate =
            Instant.ofEpochSecond(subscription.getCurrentPeriodStart());
        Instant endDate =
            Instant.ofEpochSecond(subscription.getCurrentPeriodEnd());

        localSubscription.setStartDate(startDate);
        localSubscription.setEndDate(endDate);
        localSubscription.setStatus(SubscriptionStatus.CANCELED);

        subscriptionRepository.save(localSubscription);
        log.info(
            "Updated subscription record with Stripe ID: {} for client with stripe customer ID: {}",
            stripeSubscriptionId,
            client.getStripeId());
    }

    public void processSubscriptionUpdated(com.stripe.model.Subscription subscriptionObj) {
        log.debug("Processing subscription update for session ID: {} ", subscriptionObj.getId());

        String stripeSubscriptionId = subscriptionObj.getId();
        String customerId = subscriptionObj.getCustomer();
        String status = subscriptionObj.getStatus();
        Instant startDate = Instant.ofEpochSecond(subscriptionObj.getCurrentPeriodStart());
        Instant endDate = Instant.ofEpochSecond(subscriptionObj.getCurrentPeriodEnd());

        Optional<Client> cleintOpt = clientRepository.findByStripeId(customerId);
        if (cleintOpt.isEmpty()) {
            log.error("User with Stripe customer ID {} not found.", customerId);
            return;
        }
        Client client = cleintOpt.get();
        StripeSubscription stripeSubscription =
            subscriptionRepository
                .findByStripeSubscriptionId(stripeSubscriptionId)
                .orElse(new StripeSubscription());

        stripeSubscription.setClient(client);
        stripeSubscription.setStripeSubscriptionId(stripeSubscriptionId);
        stripeSubscription.setStartDate(startDate);
        stripeSubscription.setEndDate(endDate);
        stripeSubscription.setStatus(SubscriptionStatus.valueOf(status.toUpperCase()));

        subscriptionRepository.save(stripeSubscription);

        log.info(
            "Subscription:{} updated for client with  ID: {}", stripeSubscriptionId, client.getStripeId());
    }
}
