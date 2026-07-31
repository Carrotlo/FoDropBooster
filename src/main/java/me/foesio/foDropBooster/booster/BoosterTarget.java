package me.foesio.foDropBooster.booster;

import java.util.Locale;

public enum BoosterTarget {
    DROPS("drops", "Drops"),
    FOLEVELS_XP("folevels_xp", "FoLevels XP");

    private final String key;
    private final String display;

    BoosterTarget(String key, String display) {
        this.key = key;
        this.display = display;
    }

    public String key() {
        return key;
    }

    public String display() {
        return display;
    }

    public boolean isFoLevels() {
        return this == FOLEVELS_XP;
    }

    public static BoosterTarget from(String input) {
        if (input == null || input.isBlank()) {
            return DROPS;
        }
        return switch (input.trim().toLowerCase(Locale.ROOT)) {
            case "xp", "level", "levels", "level-xp", "levelxp", "folevels", "folevels-xp", "folevels_xp" -> FOLEVELS_XP;
            default -> DROPS;
        };
    }
}
