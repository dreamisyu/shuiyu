package com.shuiyu.game.util;

import java.security.SecureRandom;

public final class RoomCodeGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();

    private RoomCodeGenerator() {
    }

    public static String generate() {
        int value = 100000 + RANDOM.nextInt(900000);
        return String.valueOf(value);
    }
}
