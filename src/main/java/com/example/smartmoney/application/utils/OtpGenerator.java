package com.example.smartmoney.application.utils;

import java.security.SecureRandom;

public final class OtpGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int OTP_LENGTH = 6;

    private OtpGenerator() {}

    public static String generate() {
        int min = (int) Math.pow(10, OTP_LENGTH - 1);
        int max = (int) Math.pow(10, OTP_LENGTH) - 1;
        int otp = RANDOM.nextInt(max - min + 1) + min;
        return String.valueOf(otp);
    }
}