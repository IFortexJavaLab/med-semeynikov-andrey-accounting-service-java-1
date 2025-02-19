package com.ifortex.internship.authservice.stripe.model;

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
