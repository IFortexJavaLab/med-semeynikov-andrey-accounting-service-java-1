package com.ifortex.internship.accountingservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChangeEmailResponse {
    private String message;
    private String link;
    private Integer expirationMinutes;

    public ChangeEmailResponse(String message, String link) {
        this.message = message;
        this.link = link;
        this.expirationMinutes = null;
    }
}
