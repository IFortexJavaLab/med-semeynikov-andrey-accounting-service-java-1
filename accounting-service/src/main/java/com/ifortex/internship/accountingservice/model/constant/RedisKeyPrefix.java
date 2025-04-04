package com.ifortex.internship.accountingservice.model.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RedisKeyPrefix {
    LOGIN_OTP("login_otp:"),
    PASSWORD_RESET("password_reset_otp:"),
    EMAIL_CHANGE("email_change:");

    private final String prefix;
}
