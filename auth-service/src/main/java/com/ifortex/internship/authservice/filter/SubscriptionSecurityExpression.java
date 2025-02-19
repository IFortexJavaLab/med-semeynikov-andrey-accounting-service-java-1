package com.ifortex.internship.authservice.filter;

import com.ifortex.internship.authservice.exception.custom.AuthorizationException;
import com.ifortex.internship.authservice.model.UserDetailsImpl;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Slf4j
@Component("subscriptionSecurity")
public class SubscriptionSecurityExpression {

  public boolean hasActiveSubscription(Authentication authentication) {
    if (authentication == null) {
      return false;
    }

    Object principle = authentication.getPrincipal();
    if ("anonymousUser".equals(principle.toString())) {
      log.debug("Attempt to get user details by anonymous or unauthenticated user.");
      throw new AuthorizationException("User is not authenticated. Please log in.");
    }
    UserDetailsImpl user = (UserDetailsImpl) principle;

    boolean hasActiveSubscription = user.isHasActiveSubscription();
    boolean isSubscriptionValid = false;
    if (hasActiveSubscription) {
      LocalDateTime subscriptionEndDate = user.getSubscriptionEndDate();
      if (subscriptionEndDate != null) {
        isSubscriptionValid = subscriptionEndDate.isAfter(LocalDateTime.now(Clock.systemUTC()));
      }
    }

    return hasActiveSubscription && isSubscriptionValid;
  }
}
