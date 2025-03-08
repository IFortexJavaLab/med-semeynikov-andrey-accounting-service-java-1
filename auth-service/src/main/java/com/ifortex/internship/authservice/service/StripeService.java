package com.ifortex.internship.authservice.service;

import com.ifortex.internship.authservice.dto.request.PurchaseSubscriptionRequest;
import com.ifortex.internship.authservice.dto.response.PurchaseSubscriptionResponse;
import com.ifortex.internship.authservice.dto.response.SubscriptionPlanDto;
import com.ifortex.internship.authservice.exception.custom.ActiveSubscriptionAlreadyExistsException;
import com.ifortex.internship.authservice.exception.custom.EntityNotFoundException;
import com.ifortex.internship.authservice.exception.custom.InvalidRequestException;
import com.ifortex.internship.authservice.exception.custom.StripeServiceException;
import com.ifortex.internship.authservice.model.Account;
import com.ifortex.internship.authservice.model.Client;
import com.ifortex.internship.authservice.model.stripe.StripeSubscription;
import com.ifortex.internship.authservice.model.stripe.SubscriptionStatus;
import com.ifortex.internship.authservice.repository.ClientRepository;
import com.ifortex.internship.authservice.repository.SubscriptionRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Price;
import com.stripe.model.PriceCollection;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PriceListParams;
import com.stripe.param.SubscriptionCancelParams;
import com.stripe.param.SubscriptionListParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@Service
public class StripeService {

    static final String LOG_STRIPE_FAILED = "Stripe API call failed: {}. Error code: {}. StackTrace: ";
    static final int CENTS_IN_DOLLAR = 100;

    AuthService authService;
    ClientRepository clientRepository;
    SubscriptionRepository subscriptionRepository;

    @Value("${app.stripe.url.cancel}") String cancelLink;
    @Value("${app.stripe.url.success}") String successLink;

    public StripeService(final AuthService authService,
                         final SubscriptionRepository subscriptionRepository,
                         final ClientRepository clientRepository,
                         @Value("${app.stripe.api.key}") final String stripeApiKey,
                         @Value("${app.stripe.url.cancel}") final String cancelLink,
                         @Value("${app.stripe.url.success}") final String successLink) {
        this.authService = authService;
        this.subscriptionRepository = subscriptionRepository;
        this.clientRepository = clientRepository;
        this.cancelLink = cancelLink;
        this.successLink = successLink;
        Stripe.apiKey = stripeApiKey;
    }

    public List<SubscriptionPlanDto> getAvailablePlans() {

        List<SubscriptionPlanDto> planList = new ArrayList<>();

        PriceListParams params = PriceListParams.builder().setActive(true).setLimit(100L).build();

        PriceCollection prices;
        try {
            prices = Price.list(params);
        } catch (StripeException e) {
            log.error(LOG_STRIPE_FAILED, e.getMessage(), e.getCode(), e);
            throw new StripeServiceException(
                "Error occurred while getting available plans. Please try again later");
        }

        for (Price price : prices.getData()) {
            if (price.getRecurring() != null) {

                Long amountInDollars = price.getUnitAmount() / CENTS_IN_DOLLAR;
                SubscriptionPlanDto plan =
                    new SubscriptionPlanDto()
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

    public List<com.stripe.model.Subscription> fetchAllSubscriptionsFromStripe()
        throws StripeException {

        log.debug("Fetching all subscriptions from Stripe");
        List<com.stripe.model.Subscription> allSubscriptions = new ArrayList<>();
        String lastSubscriptionId = null;

        do {
            SubscriptionListParams.Builder params =
                SubscriptionListParams.builder()
                    .setLimit(100L)
                    .setStatus(SubscriptionListParams.Status.ALL);
            if (lastSubscriptionId != null) {
                params.setStartingAfter(lastSubscriptionId);
            }
            List<com.stripe.model.Subscription> subscriptions =
                com.stripe.model.Subscription.list(params.build()).getData();
            if (subscriptions.isEmpty()) {
                break;
            }

            allSubscriptions.addAll(subscriptions);
            lastSubscriptionId = subscriptions.getLast().getId();
        } while (lastSubscriptionId != null);

        log.info("Fetched all subscriptions from Stripe");
        return allSubscriptions;
    }

    public PurchaseSubscriptionResponse createSubscriptionCheckoutSession(
        PurchaseSubscriptionRequest request) {

        UUID accountId = authService.getAccountIdFromAuthentication();
        log.info("Starting subscription checkout session for account ID: {}", accountId);

        Client client =
            clientRepository.findByAccountId(accountId)
                .orElseThrow(
                    () -> {
                        log.error("Client with account: {} not found", accountId);
                        return new EntityNotFoundException(
                            String.format("Client with account: %s not found", accountId));
                    });

        boolean hasActiveSubscription =
            client.getSubscriptions() != null
            && client.getSubscriptions().stream()
                .anyMatch(subscription -> subscription.getStatus() == SubscriptionStatus.ACTIVE);

        if (hasActiveSubscription) {
            log.error(
                "Client with ID: {} attempt to purchase another subscription while an active subscription already exists.",
                client.getId());
            throw new ActiveSubscriptionAlreadyExistsException("You already have active subscription");
        }

        boolean isNotStripeCustomer =
            client.getStripeId() == null || client.getStripeId().isEmpty();
        if (isNotStripeCustomer) {
            log.info("Client with ID: {} does not have a Stripe Customer ID. Creating one...", client.getId());

            CustomerCreateParams customerParams =
                CustomerCreateParams.builder().setEmail(client.getAccount().getEmail()).build();
            Customer customer;
            try {
                customer = Customer.create(customerParams);
                log.debug("Successfully created Stripe Customer ID: {} for client ID: {}", customer.getId(), client.getId());
            } catch (StripeException e) {
                log.error(LOG_STRIPE_FAILED, e.getMessage(), e.getCode(), e);
                throw new StripeServiceException(
                    "Error occurred while processing your payment. Please try again later");
            }
            client.setStripeId(customer.getId());

            clientRepository.save(client);
            log.info("Created and saved new Stripe Customer ID: {} for client ID: {}", customer.getId(), client.getId());

        }

        log.debug("Creating Stripe subscription session for client ID: {} with price ID: {}", client.getId(), request.getPriceId());
        SessionCreateParams.LineItem lineItem =
            SessionCreateParams.LineItem.builder()
                .setPrice(request.getPriceId())
                .setQuantity(1L)
                .build();

        SessionCreateParams params =
            SessionCreateParams.builder()
                .setCustomer(client.getStripeId())
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(successLink)
                .setCancelUrl(cancelLink)
                .addLineItem(lineItem)
                .build();

        Session session;
        try {
            session = Session.create(params);
            log.debug("Successfully created Stripe session ID: {} for client ID: {}", session.getId(), client.getId());
        } catch (StripeException e) {
            if ("resource_missing".equals(e.getCode())) {
                log.debug("Invalid subscription Price ID: {}", request.getPriceId());
                throw new InvalidRequestException("Invalid subscription price ID: " + request.getPriceId());
            }
            log.error(LOG_STRIPE_FAILED, e.getMessage(), e.getCode(), e);
            throw new StripeServiceException(
                "Error occurred while processing your payment. Please try again later");
        }

        PurchaseSubscriptionResponse response = new PurchaseSubscriptionResponse();
        response.setSessionId(session.getId());
        response.setSessionUrl(session.getUrl());
        log.info("Subscription checkout session created successfully for client ID: {}. Session URL: {}", client.getId(), session.getUrl());
        return response;
    }

    public void cancelSubscriptionForUser() {

        UUID accountId = authService.getAccountIdFromAuthentication();

        Client client =
            clientRepository
                .findByAccountId(accountId)
                .orElseThrow(
                    () -> {
                        log.debug("Client with account ID: {} not found ", accountId);
                        return new EntityNotFoundException(
                            String.format("Client with account ID %s not found", accountId));
                    });

        Optional<StripeSubscription> subscriptionOpt =
            subscriptionRepository.findActiveSubscriptionByUserId(client.getId());
        if (subscriptionOpt.isEmpty()) {
            log.error("No active subscription found for client with account ID: {}", accountId);
            throw new EntityNotFoundException("You have no active subscriptions");
        }
        StripeSubscription stripeSubscription = subscriptionOpt.get();
        String stripeSubscriptionId = stripeSubscription.getStripeSubscriptionId();

        try {
            com.stripe.model.Subscription resource =
                com.stripe.model.Subscription.retrieve(stripeSubscriptionId);

            SubscriptionCancelParams params = SubscriptionCancelParams.builder().build();

            resource.cancel(params);
            log.info("Stripe subscription {} cancelled successfully.", stripeSubscriptionId);
        } catch (StripeException e) {
            log.error(
                "Error cancelling Stripe subscription: {}. Code: {}", e.getMessage(), e.getCode(), e);
            throw new StripeServiceException("Failed to cancel subscription");
        }
    }

    public String registerCustomer(Account account) {

        log.info("Registering client with email: {} in Stripe", account.getEmail());

        CustomerCreateParams customerParams = CustomerCreateParams.builder().setEmail(account.getEmail()).build();
        Customer customer;
        try {
            customer = Customer.create(customerParams);
        } catch (StripeException e) {
            log.error(LOG_STRIPE_FAILED, e.getMessage(), e.getCode(), e);
            throw new StripeServiceException("Error occurred while registration. Please try again later");
        }
        log.debug("Generated StripeCustomerId: {} for account with ID: {}", customer.getId(), account.getAccountId());
        log.info("Client with email: {} has been registered in Stripe successfully", account.getEmail());
        return customer.getId();
    }

    public void deleteUser(UUID accountId) throws StripeException {

        log.info("Deleting customer with account: {} from stripe", accountId);
        String stripeCustomerId = clientRepository.findStripeIdByAccountId(accountId).orElseThrow(() -> {
            log.error("Stripe ID not found for account ID: {}", accountId);
            return new EntityNotFoundException(String.format("Stripe ID not found for account ID: %s", accountId.toString()));
        });
        Customer resource = Customer.retrieve(stripeCustomerId);
        resource.delete();
        log.info("Customer with account: {} deleted successfully", accountId);
    }
}
