package com.ifortex.internship.authservice.stripe.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PurchaseSubscriptionRequest {
  private String priceId;
}
