package me.foesio.foDropBooster.api;

import me.foesio.foDropBooster.booster.ActiveBoosterService;
import me.foesio.foDropBooster.booster.ActivationResult;
import me.foesio.foDropBooster.booster.BoosterItemData;
import me.foesio.foDropBooster.booster.BoosterProfile;
import me.foesio.foDropBooster.booster.BoosterTarget;
import me.foesio.foDropBooster.booster.EventFamily;
import me.foesio.foDropBooster.bossbar.BossbarService;
import me.foesio.foDropBooster.config.ConfigManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;

public final class DefaultFoDropBoosterApi implements FoDropBoosterApi {
    private final ActiveBoosterService activeBoosterService;
    private final ConfigManager configManager;
    private final BossbarService bossbarService;

    public DefaultFoDropBoosterApi(ActiveBoosterService activeBoosterService, ConfigManager configManager, BossbarService bossbarService) {
        this.activeBoosterService = activeBoosterService;
        this.configManager = configManager;
        this.bossbarService = bossbarService;
    }

    @Override
    public boolean setBoost(Plugin owner, String boostId, FoDropBoosterTarget target, FoDropBoosterScope scope, String scopeId, double bonusMultiplier) {
        if (owner == null) {
            return false;
        }
        return activeBoosterService.setExternalBoost(owner.getName(), boostId, toInternal(target), toInternal(scope), scopeId, bonusMultiplier);
    }

    @Override
    public boolean removeBoost(Plugin owner, String boostId) {
        if (owner == null) {
            return false;
        }
        return activeBoosterService.removeExternalBoost(owner.getName(), boostId);
    }

    @Override
    public int removeBoosts(Plugin owner) {
        if (owner == null) {
            return 0;
        }
        return activeBoosterService.removeExternalBoosts(owner.getName());
    }

    @Override
    public double teamBoostBonus(String teamId, FoDropBoosterTarget target) {
        return activeBoosterService.externalTeamBoostBonus(teamId, toInternal(target));
    }

    @Override
    public double effectiveDropMultiplier(Player player, FoDropBoosterDropCause cause) {
        if (player == null || cause == null) {
            return 1D;
        }
        return activeBoosterService.effective(player, EventFamily.valueOf(cause.name())).multiplier();
    }

    @Override
    public double effectiveFoLevelsXpMultiplier(Player player) {
        return player == null ? 1D : activeBoosterService.effectiveLevelXp(player).multiplier();
    }

    @Override
    public FoDropBoosterActivationResult activatePreset(Plugin caller, Player player, String presetId) {
        if (caller == null || !caller.isEnabled() || player == null || !player.isOnline() || presetId == null || presetId.isBlank()) {
            return new FoDropBoosterActivationResult(FoDropBoosterActivationStatus.INVALID_REQUEST, null);
        }
        BoosterProfile profile = configManager.profile(presetId);
        if (profile == null) {
            return new FoDropBoosterActivationResult(FoDropBoosterActivationStatus.PRESET_NOT_FOUND, null);
        }
        BoosterItemData data = new BoosterItemData(
                profile.id(), profile.target(), profile.type(), profile.multiplier(), profile.durationSeconds(),
                profile.worldName(), profile.enabledEvents(), profile.bossbarTitle(), profile.bossbarColor(), profile.bossbarStyle()
        );
        ActivationResult activation = activeBoosterService.activate(player, data, FoDropBoosterActivationSource.PRESET_API, caller);
        if (!activation.success()) {
            return new FoDropBoosterActivationResult(failureStatus(activation.messageKey()), null);
        }
        activeBoosterService.publishActivated(player, activation.booster(), FoDropBoosterActivationSource.PRESET_API, caller);
        bossbarService.refreshNow();
        return new FoDropBoosterActivationResult(FoDropBoosterActivationStatus.SUCCESS, activeBoosterService.snapshot(activation.booster()));
    }

    @Override
    public List<FoDropBoosterSnapshot> activeTimedBoosters() {
        return activeBoosterService.activeTimedBoosterSnapshots();
    }

    private FoDropBoosterActivationStatus failureStatus(String key) {
        return switch (key) {
            case "folevels-required" -> FoDropBoosterActivationStatus.FOLEVELS_REQUIRED;
            case "disabled-world" -> FoDropBoosterActivationStatus.DISABLED_WORLD;
            case "teams-unavailable" -> FoDropBoosterActivationStatus.TEAMS_UNAVAILABLE;
            case "no-team" -> FoDropBoosterActivationStatus.NO_TEAM;
            case "active-exists" -> FoDropBoosterActivationStatus.CONFLICT;
            case "activation-cancelled" -> FoDropBoosterActivationStatus.CANCELLED;
            default -> FoDropBoosterActivationStatus.INVALID_REQUEST;
        };
    }

    private BoosterTarget toInternal(FoDropBoosterTarget target) {
        if (target == FoDropBoosterTarget.FOLEVELS_XP) {
            return BoosterTarget.FOLEVELS_XP;
        }
        return BoosterTarget.DROPS;
    }

    private ActiveBoosterService.ExternalBoostScope toInternal(FoDropBoosterScope scope) {
        if (scope == null) {
            return ActiveBoosterService.ExternalBoostScope.GLOBAL;
        }
        return switch (scope) {
            case PERSONAL -> ActiveBoosterService.ExternalBoostScope.PERSONAL;
            case TEAM -> ActiveBoosterService.ExternalBoostScope.TEAM;
            case WORLD -> ActiveBoosterService.ExternalBoostScope.WORLD;
            case GLOBAL -> ActiveBoosterService.ExternalBoostScope.GLOBAL;
        };
    }
}
