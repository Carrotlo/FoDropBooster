package me.foesio.foDropBooster.util;

import me.foesio.core.number.DurationParser;
import me.foesio.core.number.LargeNumberParser;
import me.foesio.core.number.TickDuration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Optional;

public final class FormatUtil {
    private FormatUtil() {
    }

    public static String multiplier(double value, String pattern) {
        return new DecimalFormat(pattern == null || pattern.isBlank() ? "#,##0.##" : pattern).format(value);
    }

    public static String duration(long seconds) {
        long safeSeconds = Math.max(0L, seconds);
        try {
            return TickDuration.ofSeconds(safeSeconds).compact();
        } catch (ArithmeticException exception) {
            return safeSeconds + "s";
        }
    }

    public static Long parseDurationSeconds(String input) {
        return DurationParser.parse(input)
                .map(TickDuration::secondsFloor)
                .filter(seconds -> seconds > 0L)
                .orElse(null);
    }

    public static Integer parseInteger(String input) {
        Optional<BigDecimal> parsed = LargeNumberParser.parse(input);
        if (parsed.isEmpty()) {
            return null;
        }

        try {
            return parsed.get().setScale(0, RoundingMode.UNNECESSARY).intValueExact();
        } catch (ArithmeticException exception) {
            return null;
        }
    }
}
