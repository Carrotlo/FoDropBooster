package me.foesio.foDropBooster.booster;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;

import java.util.List;

public record EffectiveBoost(
        double multiplier,
        BoosterTarget target,
        double boosterMultiplier,
        double teamMultiplier,
        int onlineTeamMembers,
        double progress,
        long remainingSeconds,
        String bossbarTitle,
        BarColor bossbarColor,
        BarStyle bossbarStyle,
        List<ActiveBooster> boosters
) {
    public static EffectiveBoost none() {
        return new EffectiveBoost(1D, BoosterTarget.DROPS, 1D, 1D, 0, 0D, 0L, "", BarColor.GREEN, BarStyle.SEGMENTED_10, List.of());
    }

    public boolean active() {
        return multiplier > 1D;
    }
}
