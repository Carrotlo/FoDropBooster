package me.foesio.foDropBooster.booster;

import java.util.Locale;

public enum BoosterType {
    PERSONAL("personal"),
    TEAM("team"),
    GLOBAL("global"),
    WORLD("world");

    private final String display;

    BoosterType(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }

    public static BoosterType from(String input) {
        if (input == null) {
            return PERSONAL;
        }
        return switch (input.trim().toLowerCase(Locale.ROOT)) {
            case "team", "foteam", "foteams" -> TEAM;
            case "global" -> GLOBAL;
            case "world", "per-world", "perworld" -> WORLD;
            default -> PERSONAL;
        };
    }
}
