package me.foesio.foDropBooster.booster;

import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;

import java.util.Set;

public record BoosterProfile(
        String id,
        BoosterTarget target,
        BoosterType type,
        double multiplier,
        long durationSeconds,
        String worldName,
        Set<EventFamily> enabledEvents,
        Material material,
        boolean glow,
        int customModelData,
        String name,
        java.util.List<String> lore,
        String bossbarTitle,
        BarColor bossbarColor,
        BarStyle bossbarStyle
) {
}
