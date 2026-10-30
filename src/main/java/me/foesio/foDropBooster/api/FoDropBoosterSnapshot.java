package me.foesio.foDropBooster.api;

import java.util.Set;
import java.util.UUID;

/** Immutable description of one stored timed booster, not an effective player boost. */
public record FoDropBoosterSnapshot(
        String id,
        String presetId,
        FoDropBoosterTarget target,
        FoDropBoosterScope scope,
        UUID ownerPlayerId,
        String ownerPlayerName,
        String worldName,
        String teamId,
        String teamName,
        double multiplier,
        long startedAtMillis,
        long expiresAtMillis,
        Set<FoDropBoosterDropCause> enabledDropCauses
) {
    /** Owner player fields are populated for personal and team boosters only. */
    public FoDropBoosterSnapshot {
        enabledDropCauses = Set.copyOf(enabledDropCauses);
    }
}
