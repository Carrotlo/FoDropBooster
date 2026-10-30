package me.foesio.foDropBooster.api;

/** Result of a preset activation request. */
public enum FoDropBoosterActivationStatus {
    SUCCESS,
    INVALID_REQUEST,
    PRESET_NOT_FOUND,
    FOLEVELS_REQUIRED,
    DISABLED_WORLD,
    TEAMS_UNAVAILABLE,
    NO_TEAM,
    CONFLICT,
    CANCELLED
}
