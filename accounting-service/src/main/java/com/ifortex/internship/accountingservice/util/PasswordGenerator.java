package com.ifortex.internship.accountingservice.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordGenerator {

    static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    static final String DIGITS = "0123456789";
    static final String SPECIAL_CHARACTERS = "@$!%*?&#";
    static final String ALL_ALLOWED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@$!%*?&#";

    final Random random = new Random();

    public String generateOtp() {
        int code = random.nextInt(900000) + 100000;
        return String.valueOf(code);
    }

    public String generateTempPassword() {

        int length = 8;

        char upper = UPPERCASE.charAt(random.nextInt(UPPERCASE.length()));
        char digit = DIGITS.charAt(random.nextInt(DIGITS.length()));
        char special = SPECIAL_CHARACTERS.charAt(random.nextInt(SPECIAL_CHARACTERS.length()));

        StringBuilder password = new StringBuilder();
        password.append(upper).append(digit).append(special);
        for (int i = 3; i < length; i++) {
            password.append(ALL_ALLOWED.charAt(random.nextInt(ALL_ALLOWED.length())));
        }

        log.debug("Temporary password generated successfully");
        return password.toString();
    }
}
