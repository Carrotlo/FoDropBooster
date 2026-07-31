package me.foesio.foDropBooster.bossbar;

import me.foesio.core.scheduler.FoScheduler;
import me.foesio.foDropBooster.booster.ActiveBoosterService;
import me.foesio.foDropBooster.booster.BoosterTarget;
import me.foesio.foDropBooster.booster.EffectiveBoost;
import me.foesio.foDropBooster.config.ConfigManager;
import me.foesio.core.text.FoText;
import org.bukkit.Bukkit;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BossbarService {
    private static final long ROTATION_INTERVAL_MILLIS = 3_000L;

    private final FoScheduler scheduler;
    private final ConfigManager configManager;
    private final ActiveBoosterService activeBoosterService;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, RotationState> rotationStates = new HashMap<>();
    private final Set<UUID> disabledPlayers = new HashSet<>();
    private boolean running;
    private long updateGeneration;

    public BossbarService(FoScheduler scheduler, ConfigManager configManager, ActiveBoosterService activeBoosterService) {
        this.scheduler = scheduler;
        this.configManager = configManager;
        this.activeBoosterService = activeBoosterService;
    }

    public void start() {
        long updateTicks = configManager.bossbarUpdateTicks();
        running = true;
        scheduleUpdate(++updateGeneration, updateTicks);
    }

    public void restart() {
        start();
        refreshNow();
    }

    public void shutdown() {
        running = false;
        updateGeneration++;
        for (BossBar bar : bars.values()) {
            bar.removeAll();
        }
        bars.clear();
    }

    public void remove(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        rotationStates.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
    }

    public void clearSession(Player player) {
        disabledPlayers.remove(player.getUniqueId());
        remove(player);
    }

    public boolean setEnabled(Player player, boolean enabled) {
        if (enabled) {
            disabledPlayers.remove(player.getUniqueId());
            update(player);
            return true;
        }
        disabledPlayers.add(player.getUniqueId());
        remove(player);
        return false;
    }

    public boolean toggle(Player player) {
        return setEnabled(player, disabledPlayers.contains(player.getUniqueId()));
    }

    public void refreshNow() {
        updateAll();
    }

    private void updateAll() {
        if (!configManager.bossbarEnabled()) {
            for (BossBar bar : bars.values()) {
                bar.removeAll();
            }
            bars.clear();
            rotationStates.clear();
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
        bars.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
        rotationStates.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
    }

    private void update(Player player) {
        if (!configManager.bossbarEnabled() || disabledPlayers.contains(player.getUniqueId())) {
            remove(player);
            return;
        }
        EffectiveBoost boost = displayBoost(player);
        if (!boost.active()) {
            remove(player);
            return;
        }

        BossBar bar = bars.get(player.getUniqueId());
        String title = FoText.color(activeBoosterService.formatActiveTitle(player, boost));
        if (bar == null) {
            bar = Bukkit.createBossBar(title, boost.bossbarColor(), boost.bossbarStyle());
            bar.addPlayer(player);
            bars.put(player.getUniqueId(), bar);
        }
        bar.setTitle(title);
        bar.setColor(boost.bossbarColor());
        bar.setStyle(boost.bossbarStyle());
        bar.setProgress(Math.max(0D, Math.min(1D, boost.progress())));
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
    }

    private EffectiveBoost displayBoost(Player player) {
        EffectiveBoost drops = activeBoosterService.effective(player, BoosterTarget.DROPS, null);
        EffectiveBoost levels = activeBoosterService.effective(player, BoosterTarget.FOLEVELS_XP, null);
        UUID playerId = player.getUniqueId();
        if (!drops.active() || !levels.active()) {
            rotationStates.remove(playerId);
            return drops.active() ? drops : levels;
        }

        long now = System.currentTimeMillis();
        RotationState state = rotationStates.computeIfAbsent(playerId, ignored -> new RotationState(BoosterTarget.DROPS, now));
        long elapsedIntervals = (now - state.switchedAtMillis()) / ROTATION_INTERVAL_MILLIS;
        if (elapsedIntervals > 0L) {
            BoosterTarget target = elapsedIntervals % 2L == 0L ? state.target() : opposite(state.target());
            state = new RotationState(target, state.switchedAtMillis() + elapsedIntervals * ROTATION_INTERVAL_MILLIS);
            rotationStates.put(playerId, state);
        }
        return state.target() == BoosterTarget.FOLEVELS_XP ? levels : drops;
    }

    private BoosterTarget opposite(BoosterTarget target) {
        return target == BoosterTarget.FOLEVELS_XP ? BoosterTarget.DROPS : BoosterTarget.FOLEVELS_XP;
    }

    private void scheduleUpdate(long generation, long updateTicks) {
        scheduler.runGlobalLater(() -> {
            if (!running || generation != updateGeneration) {
                return;
            }
            updateAll();
            scheduleUpdate(generation, updateTicks);
        }, updateTicks);
    }

    private record RotationState(BoosterTarget target, long switchedAtMillis) {
    }
}
