package com.youngstersclub.app.util;

import java.time.LocalDateTime;
import java.time.ZoneId;

public final class TimeUtil {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    private TimeUtil() {}

    public static LocalDateTime nowIST() {
        return LocalDateTime.now(IST_ZONE);
    }
}
