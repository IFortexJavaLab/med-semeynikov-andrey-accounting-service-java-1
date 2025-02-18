package com.ifortex.internship.authservice.stripe.service;

import com.ifortex.internship.authservice.model.User;
import com.ifortex.internship.authservice.repository.UserRepository;
import com.ifortex.internship.authservice.stripe.model.StripeSubscription;
import com.ifortex.internship.authservice.stripe.model.SubscriptionStatus;
import com.ifortex.internship.authservice.stripe.repository.SubscriptionRepository;
import com.stripe.model.Invoice;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripeWebhookService {

  private final UserRepository userRepository;
  private final SubscriptionRepository subscriptionRepository;

  public void processInvoicePaymentSucceeded(Invoice invoice) {

    log.debug("Processing invoice payment succeeded for invoice id: {} ", invoice.getId());

    String stripeCustomerId = invoice.getCustomer();
    if (stripeCustomerId == null || stripeCustomerId.isEmpty()) {
      log.warn("Stripe invoice does not contain a customer ID.");
      return;
    }

    Optional<User> userOpt = userRepository.findByStripeCustomerId(stripeCustomerId);
    if (userOpt.isEmpty()) {
      log.warn("User with Stripe customer ID {} not found.", stripeCustomerId);
      return;
    }
    User user = userOpt.get();

    // save subscription details to the db

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
      stripeSubscription.setUser(user);

      subscriptionRepository.save(stripeSubscription);
      log.info(
          "Created new subscription record for user id: {} with subscription id: {}",
          user.getId(),
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
    User user = localSubscription.getUser();

    LocalDateTime startDate =
        LocalDateTime.ofEpochSecond(subscription.getCurrentPeriodStart(), 0, ZoneOffset.UTC);
    LocalDateTime endDate =
        LocalDateTime.ofEpochSecond(subscription.getCurrentPeriodEnd(), 0, ZoneOffset.UTC);

    localSubscription.setStartDate(startDate);
    localSubscription.setEndDate(endDate);
    localSubscription.setStatus(SubscriptionStatus.CANCELED);

    subscriptionRepository.save(localSubscription);
    log.info(
        "Updated subscription record with Stripe ID: {} for user with ID: {}",
        stripeSubscriptionId,
        user.getUserId());
  }

  public void processSubscriptionUpdated(com.stripe.model.Subscription subscriptionObj) {
    log.debug("Processing subscription update for session ID: {} ", subscriptionObj.getId());

    String stripeSubscriptionId = subscriptionObj.getId();
    String customerId = subscriptionObj.getCustomer();
    String status = subscriptionObj.getStatus();
    LocalDateTime startDate =
        LocalDateTime.ofEpochSecond(subscriptionObj.getCurrentPeriodStart(), 0, ZoneOffset.UTC);
    LocalDateTime endDate =
        LocalDateTime.ofEpochSecond(subscriptionObj.getCurrentPeriodEnd(), 0, ZoneOffset.UTC);

    Optional<User> userOpt = userRepository.findByStripeCustomerId(customerId);
    if (userOpt.isEmpty()) {
      log.warn("User with Stripe customer ID {} not found.", customerId);
      return;
    }
    User user = userOpt.get();
    StripeSubscription stripeSubscription =
        subscriptionRepository
            .findByStripeSubscriptionId(stripeSubscriptionId)
            .orElse(new StripeSubscription());

    stripeSubscription.setUser(user);
    stripeSubscription.setStripeSubscriptionId(stripeSubscriptionId);
    stripeSubscription.setStartDate(startDate);
    stripeSubscription.setEndDate(endDate);
    stripeSubscription.setStatus(SubscriptionStatus.valueOf(status.toUpperCase()));

    subscriptionRepository.save(stripeSubscription);

    log.info(
        "Subscription:{} updated for user with ID: {}", stripeSubscriptionId, user.getUserId());
  }
}
