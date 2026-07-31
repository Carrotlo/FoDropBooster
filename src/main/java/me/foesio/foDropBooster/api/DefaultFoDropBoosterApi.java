package me.foesio.foDropBooster.api;

import me.foesio.foDropBooster.booster.ActiveBoosterService;
import me.foesio.foDropBooster.booster.BoosterTarget;
import org.bukkit.plugin.Plugin;

public final class DefaultFoDropBoosterApi implements FoDropBoosterApi {
    private final ActiveBoosterService activeBoosterService;

    public DefaultFoDropBoosterApi(ActiveBoosterService activeBoosterService) {
        this.activeBoosterService = activeBoosterService;
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
