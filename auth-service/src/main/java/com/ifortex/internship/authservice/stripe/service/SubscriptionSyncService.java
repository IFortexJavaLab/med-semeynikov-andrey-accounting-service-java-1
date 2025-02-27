package com.ifortex.internship.authservice.stripe.service;

import com.ifortex.internship.authservice.model.User;
import com.ifortex.internship.authservice.repository.UserRepository;
import com.ifortex.internship.authservice.stripe.exception.StripeServiceException;
import com.ifortex.internship.authservice.stripe.model.StripeSubscription;
import com.ifortex.internship.authservice.stripe.model.SubscriptionStatus;
import com.ifortex.internship.authservice.stripe.repository.SubscriptionRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Profile("syncMod")
@Component
@RequiredArgsConstructor
public class SubscriptionSyncService {
    private final StripeService stripeService;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    @PostConstruct
    @Transactional
    public void syncSubscriptionsOnStartup() {

        //todo refactor to correspond sonarCube


        log.debug("Syncing stripe subscriptions with local db");

        List<Subscription> stripeSubscriptions;
        try {
            stripeSubscriptions = stripeService.fetchAllSubscriptionsFromStripe();
        } catch (StripeException e) {
            log.error(
                    "Stripe API call failed: {}. Error code: {}. StackTrace: ",
                    e.getMessage(),
                    e.getCode(),
                    e);
            throw new StripeServiceException(
                    "Error occurred while sync with stripe. Please try again later");
        }

        List<StripeSubscription> localSubscriptions =
                subscriptionRepository.findAllByStripeSubscriptionIdIn(
                        stripeSubscriptions.stream().map(Subscription::getId).toList());

        Map<String, StripeSubscription> localSubscriptionsMap =
                localSubscriptions.stream()
                        .collect(
                                Collectors.toMap(StripeSubscription::getStripeSubscriptionId, Function.identity()));

        List<StripeSubscription> toUpdateOrSave = new ArrayList<>();

        for (Subscription stripeSub : stripeSubscriptions) {
            StripeSubscription localSub = localSubscriptionsMap.get(stripeSub.getId());

            SubscriptionStatus newStatus =
                    SubscriptionStatus.valueOf(stripeSub.getStatus().toUpperCase());

            LocalDateTime startDate =
                    stripeSub.getStartDate() != null
                            ? LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(stripeSub.getStartDate()), ZoneOffset.UTC)
                            : null;

            LocalDateTime endDate =
                    stripeSub.getCurrentPeriodEnd() != null
                            ? LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(stripeSub.getCurrentPeriodEnd()), ZoneOffset.UTC)
                            : null;

            if (localSub != null) {
                boolean isUpdated = false;

                boolean isStatusUpdated = !localSub.getStatus().equals(newStatus);
                boolean isStartDateUpdated = !localSub.getStartDate().equals(startDate);
                boolean isEndDateUpdated = !localSub.getEndDate().equals(endDate);

                if (isStatusUpdated) {
                    localSub.setStatus(newStatus);
                    isUpdated = true;
                }

                if (isStartDateUpdated) {
                    localSub.setStartDate(startDate);
                    isUpdated = true;
                }

                if (isEndDateUpdated) {
                    localSub.setEndDate(endDate);
                    isUpdated = true;
                }

                if (isUpdated) {
                    toUpdateOrSave.add(localSub);
                }
            } else {
                StripeSubscription newSub = new StripeSubscription();
                newSub.setStripeSubscriptionId(stripeSub.getId());
                newSub.setStatus(newStatus);
                newSub.setStartDate(startDate);
                newSub.setEndDate(endDate);

                String customerId = stripeSub.getCustomer();
                Optional<User> user = userRepository.findByStripeCustomerId(customerId);
                if (user.isEmpty()) {
                    log.debug("User with stripe customer ID: {} not found", customerId);
                    break;
                }
                newSub.setUser(user.get());
                toUpdateOrSave.add(newSub);
            }
        }

        if (!toUpdateOrSave.isEmpty()) {
            subscriptionRepository.saveAll(toUpdateOrSave);
        }

        log.debug("Synchronization completed successfully.");
    }
}
