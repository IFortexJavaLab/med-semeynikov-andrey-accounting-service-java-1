package com.ifortex.internship.authservice.stripe.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PurchaseSubscriptionResponse {
  private String sessionId;
  private String sessionUrl;
}
