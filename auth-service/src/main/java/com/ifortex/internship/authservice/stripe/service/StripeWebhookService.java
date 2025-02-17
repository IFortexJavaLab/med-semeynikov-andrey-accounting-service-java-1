package com.ifortex.internship.authservice.stripe.service;

import com.ifortex.internship.authservice.model.User;
import com.ifortex.internship.authservice.repository.UserRepository;
import com.ifortex.internship.authservice.stripe.model.Subscription;
import com.ifortex.internship.authservice.stripe.model.SubscriptionStatus;
import com.ifortex.internship.authservice.stripe.repository.SubscriptionRepository;
import com.stripe.model.checkout.Session;
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

  public void processCheckoutSessionCompleted(Session session) {

    log.debug("Processing checkout session completed for session id: {} ", session.getId());

    String stripeCustomerId = session.getCustomer();
    if (stripeCustomerId == null || stripeCustomerId.isEmpty()) {
      log.warn("Stripe session does not contain a customer ID.");
      return;
    }

    Optional<User> userOpt = userRepository.findByStripeCustomerId(stripeCustomerId);
    if (userOpt.isEmpty()) {
      log.warn("User with Stripe customer ID {} not found.", stripeCustomerId);
      return;
    }
    User user = userOpt.get();

    // save subscription details to the db

    String stripeSubscriptionId = session.getSubscription();
    boolean missingSubscriptionId = stripeSubscriptionId == null || stripeSubscriptionId.isEmpty();
    if (missingSubscriptionId) {
      log.warn("Session {} does not contain a subscription ID.", session.getId());
      return;
    }

    boolean subscriptionAlreadyExists =
        subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId).isPresent();
    if (subscriptionAlreadyExists) {
      log.debug("Subscription record for subscription id {} already exists.", stripeSubscriptionId);
    } else {
      com.ifortex.internship.authservice.stripe.model.Subscription subscription =
          new Subscription();
      subscription.setStripeSubscriptionId(stripeSubscriptionId);
      subscription.setUser(user);

      subscriptionRepository.save(subscription);
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

    Optional<Subscription> subscriptionOpt =
        subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId);
    if (subscriptionOpt.isEmpty()) {
      log.warn(
          "No local subscription record found for Stripe subscription ID: {}",
          stripeSubscriptionId);
      return;
    }

    Subscription localSubscription = subscriptionOpt.get();
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
    Subscription subscription =
        subscriptionRepository
            .findByStripeSubscriptionId(stripeSubscriptionId)
            .orElse(new Subscription());

    subscription.setUser(user);
    subscription.setStripeSubscriptionId(stripeSubscriptionId);
    subscription.setStartDate(startDate);
    subscription.setEndDate(endDate);
    subscription.setStatus(SubscriptionStatus.valueOf(status.toUpperCase()));

    subscriptionRepository.save(subscription);

    log.info(
        "Subscription:{} updated for user with ID: {}", stripeSubscriptionId, user.getUserId());
  }
}
