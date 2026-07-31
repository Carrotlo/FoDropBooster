package me.foesio.foDropBooster.booster;

import java.util.Locale;

public enum EventFamily {
    BLOCK_BREAK("block-break"),
    MOB_KILL("mob-kill"),
    FISHING("fishing"),
    SHEARING("shearing"),
    CRAFTING("crafting"),
    EXPLOSIONS("explosions");

    private final String configKey;

    EventFamily(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }

    public static EventFamily fromConfig(String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.trim().replace('_', '-').toLowerCase(Locale.ROOT);
        for (EventFamily family : values()) {
            if (family.configKey.equals(normalized)) {
                return family;
            }
        }
        return null;
    }

    public static EventFamily fromFoDrops(String input) {
        if (input == null) {
            return null;
        }
        return switch (input.trim().toUpperCase(Locale.ROOT)) {
            case "BLOCK_BREAK", "BLOCK_DROP_ITEM" -> BLOCK_BREAK;
            case "MOB_KILL" -> MOB_KILL;
            case "PLAYER_FISH" -> FISHING;
            case "PLAYER_SHEAR_ENTITY" -> SHEARING;
            case "PLAYER_CRAFT" -> CRAFTING;
            case "BLOCK_EXPLOSION", "ENTITY_EXPLOSION" -> EXPLOSIONS;
            default -> null;
        };
    }
}
