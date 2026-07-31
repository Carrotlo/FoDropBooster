package me.foesio.foDropBooster.booster;

import java.util.Set;

public record BoosterItemData(
        String boosterId,
        BoosterTarget target,
        BoosterType type,
        double multiplier,
        long durationSeconds,
        String worldName,
        Set<EventFamily> enabledEvents,
        String bossbarTitle,
        org.bukkit.boss.BarColor bossbarColor,
        org.bukkit.boss.BarStyle bossbarStyle
) {
}
