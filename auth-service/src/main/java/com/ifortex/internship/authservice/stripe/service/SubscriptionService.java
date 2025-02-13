package com.ifortex.internship.authservice.stripe.service;

import com.ifortex.internship.authservice.exception.custom.EntityNotFoundException;
import com.ifortex.internship.authservice.model.User;
import com.ifortex.internship.authservice.repository.UserRepository;
import com.ifortex.internship.authservice.service.AuthService;
import com.ifortex.internship.authservice.stripe.dto.request.PurchaseSubscriptionRequest;
import com.ifortex.internship.authservice.stripe.dto.response.PlanDto;
import com.ifortex.internship.authservice.stripe.dto.response.PurchaseSubscriptionResponse;
import com.ifortex.internship.authservice.stripe.exception.ActiveSubscriptionExistsException;
import com.ifortex.internship.authservice.stripe.exception.ActiveSubscriptionNotFoundException;
import com.ifortex.internship.authservice.stripe.exception.StripeServiceException;
import com.ifortex.internship.authservice.stripe.model.Subscription;
import com.ifortex.internship.authservice.stripe.model.SubscriptionStatus;
import com.ifortex.internship.authservice.stripe.repository.SubscriptionRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Price;
import com.stripe.model.PriceCollection;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PriceListParams;
import com.stripe.param.SubscriptionCancelParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

  private final UserRepository userRepository;
  private final AuthService authService;
  private final SubscriptionRepository subscriptionRepository;

  @Value("${app.stripe.api.key}")
  private String stripeApiKey;

  @Value("${app.stripe.url.cancel}")
  private String cancelLink;

  @Value("${app.stripe.url.success}")
  private String successLink;

  @PostConstruct
  public void init() {
    Stripe.apiKey = stripeApiKey;
  }

  public List<PlanDto> getAvailablePlans() {

    List<PlanDto> planList = new ArrayList<>();

    PriceListParams params = PriceListParams.builder().setActive(true).setLimit(100L).build();

    PriceCollection prices;
    try {
      prices = Price.list(params);
    } catch (StripeException e) {
      log.error(
          "Stripe API call failed: {}. Error code: {}. StackTrace: ",
          e.getMessage(),
          e.getCode(),
          e);
      throw new StripeServiceException(
          "Error occurred while getting available plans. Please try again later");
    }

    for (Price price : prices.getData()) {
      if (price.getRecurring() != null) {

        Long amountInDollars = price.getUnitAmount() / 100; // todo refactor
        PlanDto plan =
            new PlanDto()
                .setId(price.getId())
                .setProductId(price.getProduct())
                .setAmount(amountInDollars)
                .setCurrency(price.getCurrency().toUpperCase())
                .setInterval(price.getRecurring().getInterval())
                .setIntervalCount(price.getRecurring().getIntervalCount());
        planList.add(plan);
      }
    }
    return planList;
  }

  public PurchaseSubscriptionResponse createSubscriptionCheckoutSession(
      PurchaseSubscriptionRequest request) {

    String userEmail = authService.getUserEmailFromAuthentication();

    User user =
        userRepository
            .findByEmail(userEmail)
            .orElseThrow(
                () -> {
                  log.debug("User with email: {} not found", userEmail);
                  return new EntityNotFoundException(
                      String.format("User with email: %s not found", userEmail));
                });

    boolean hasActiveSubscription =
        user.getSubscriptions() != null
            && user.getSubscriptions().stream()
                .anyMatch(subscription -> subscription.getStatus() == SubscriptionStatus.ACTIVE);

    if (hasActiveSubscription) {
      // todo refactor
      log.debug(
          "User with ID: {} attempt to purchase another subscription while an active subscription already exists.",
          user.getUserId());
      throw new ActiveSubscriptionExistsException("You already have active subscription");
    }

    boolean missingStripeCustomerId =
        user.getStripeCustomerId() == null || user.getStripeCustomerId().isEmpty();
    if (missingStripeCustomerId) {
      CustomerCreateParams customerParams =
          CustomerCreateParams.builder().setEmail(userEmail).build();
      Customer customer;
      try {
        customer = Customer.create(customerParams);
      } catch (StripeException e) {
        log.error(
            "Stripe API call failed: {}. Error code: {}. StackTrace: ",
            e.getMessage(),
            e.getCode(),
            e);
        throw new StripeServiceException(
            "Error occurred while processing your payment. Please try again later");
      }
      user.setStripeCustomerId(customer.getId());
      userRepository.save(user);
      log.debug(
          "Generated and saved StripeCustomerId: {} for user with ID: {}",
          customer.getId(),
          user.getUserId());
    }

    SessionCreateParams.LineItem lineItem =
        SessionCreateParams.LineItem.builder()
            .setPrice(request.getPriceId())
            .setQuantity(1L)
            .build();
    // todo how to process invalid data?

    SessionCreateParams params =
        SessionCreateParams.builder()
            .setCustomer(user.getStripeCustomerId())
            .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setSuccessUrl(successLink)
            .setCancelUrl(cancelLink)
            .addLineItem(lineItem)
            .build();

    Session session;
    try {
      session = Session.create(params);
    } catch (StripeException e) {
      log.error(
          "Stripe API call failed: {}. Error code: {}. StackTrace: ",
          e.getMessage(),
          e.getCode(),
          e);
      throw new StripeServiceException(
          "Error occurred while processing your payment. Please try again later");
    }

    PurchaseSubscriptionResponse response = new PurchaseSubscriptionResponse();
    response.setSessionId(session.getId());
    response.setSessionUrl(session.getUrl());

    return response;
  }

  public void cancelSubscriptionForUser() {

    String userEmail = authService.getUserEmailFromAuthentication();

    User user =
        userRepository
            .findByEmail(userEmail)
            .orElseThrow(
                () -> // todo add log debug
                new EntityNotFoundException(
                        String.format("User with email %s not found", userEmail)));

    Optional<Subscription> subscriptionOpt =
        subscriptionRepository.findActiveSubscriptionByUserId(user.getId());
    if (subscriptionOpt.isEmpty()) {
      log.debug("No active subscription found for user with ID: {}", user.getUserId());
      throw new ActiveSubscriptionNotFoundException("You have no active subscriptions");
    }
    Subscription subscription = subscriptionOpt.get();
    String stripeSubscriptionId = subscription.getStripeSubscriptionId();

    try {
      com.stripe.model.Subscription resource =
          com.stripe.model.Subscription.retrieve(stripeSubscriptionId);

      SubscriptionCancelParams params = SubscriptionCancelParams.builder().build();

      com.stripe.model.Subscription stripeSubscription = resource.cancel(params);
      log.info("Stripe subscription {} cancelled successfully.", stripeSubscriptionId);
    } catch (StripeException e) {
      log.error(
          "Error cancelling Stripe subscription: {}. Code: {}", e.getMessage(), e.getCode(), e);
      throw new StripeServiceException("Failed to cancel subscription");
    }

    // todo ask the question about deleting and cancelling subscription locally
    /*// Update the local subscription record status to "CANCELED"
    subscription.setStatus(
        SubscriptionStatus
            .CANCELED);
    subscriptionRepository.save(subscription);
    log.debug("Local subscription record {} updated to CANCELED.", stripeSubscriptionId);*/

    /*// Remove the subscriber role from the user
    boolean roleRemoved =
        user.getRoles().removeIf(role -> UserRole.ROLE_SUBSCRIBED_USER.equals(role.getName()));
    if (roleRemoved) {
      userRepository.save(user);
      log.info("Removed subscriber role from user with id: {}", user.getId());
    } else {
      log.warn("User with id {} did not have subscriber role assigned.", user.getId());
    }*/
  }
}
