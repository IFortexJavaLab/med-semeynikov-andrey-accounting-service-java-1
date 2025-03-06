package com.ifortex.internship.authservice.model.stripe;

public enum SubscriptionStatus {
    INCOMPLETE,
    INCOMPLETE_EXPIRED,
    TRIALING,
    ACTIVE,
    PAST_DUE,
    CANCELED,
    UNPAID,
    PAUSED
}
