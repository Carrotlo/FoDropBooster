package me.foesio.foDropBooster.booster;

public record ActivationResult(boolean success, String messageKey, ActiveBooster booster) {
    public static ActivationResult success(ActiveBooster booster) {
        return new ActivationResult(true, "activated", booster);
    }

    public static ActivationResult fail(String messageKey) {
        return new ActivationResult(false, messageKey, null);
    }
}
