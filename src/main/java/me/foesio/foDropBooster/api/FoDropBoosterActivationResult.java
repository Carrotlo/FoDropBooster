package me.foesio.foDropBooster.api;

/** A successful request has a snapshot; a failed request has a null snapshot. */
public record FoDropBoosterActivationResult(
        FoDropBoosterActivationStatus status,
        FoDropBoosterSnapshot booster
) {
    public boolean success() {
        return status == FoDropBoosterActivationStatus.SUCCESS;
    }
}
