package com.ifortex.internship.authservice.stripe.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class PlanDto {
  private String id;
  private String productId;
  private Long amount;
  private String currency;
  private String interval;
  private Long intervalCount;
}
