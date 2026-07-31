package me.foesio.foDropBooster.booster;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;

import java.util.Set;
import java.util.UUID;

public record ActiveBooster(
        String id,
        String boosterId,
        BoosterTarget target,
        BoosterType type,
        UUID ownerId,
        String ownerName,
        String worldName,
        String teamId,
        String teamName,
        double multiplier,
        long startedAtMillis,
        long durationMillis,
        Set<EventFamily> enabledEvents,
        String bossbarTitle,
        BarColor bossbarColor,
        BarStyle bossbarStyle
) {
    public long expiresAtMillis() {
        return startedAtMillis + durationMillis;
    }

    public long remainingMillis(long now) {
        return Math.max(0L, expiresAtMillis() - now);
    }

    public boolean expired(long now) {
        return remainingMillis(now) <= 0L;
    }

    public double progress(long now) {
        if (durationMillis <= 0L) {
            return 0D;
        }
        return Math.max(0D, Math.min(1D, remainingMillis(now) / (double) durationMillis));
    }
}
