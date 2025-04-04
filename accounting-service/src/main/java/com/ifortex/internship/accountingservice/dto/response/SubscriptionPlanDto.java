package com.ifortex.internship.accountingservice.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class SubscriptionPlanDto {
    private String id;
    private String productId;
    private Long amount;
    private String currency;
    private String interval;
    private Long intervalCount;
}
