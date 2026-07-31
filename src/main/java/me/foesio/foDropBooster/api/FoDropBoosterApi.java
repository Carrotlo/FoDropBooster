package me.foesio.foDropBooster.api;

import org.bukkit.plugin.Plugin;

import java.util.UUID;

public interface FoDropBoosterApi {
    /**
     * Adds or updates an external drop boost for one player.
     *
     * @param owner plugin that owns this boost
     * @param boostId id scoped to the owner plugin, used later for removal
     * @param playerId player that should receive the boost
     * @param bonusMultiplier additive bonus, so 0.25 means +0.25x
     * @return true when the boost was accepted
     */
    default boolean setPlayerDropBoost(Plugin owner, String boostId, UUID playerId, double bonusMultiplier) {
        return setPlayerBoost(owner, boostId, playerId, FoDropBoosterTarget.DROPS, bonusMultiplier);
    }

    /**
     * Adds or updates an external boost for one player.
     *
     * @param owner plugin that owns this boost
     * @param boostId id scoped to the owner plugin, used later for removal
     * @param playerId player that should receive the boost
     * @param target booster system to affect
     * @param bonusMultiplier additive bonus, so 0.25 means +0.25x
     * @return true when the boost was accepted
     */
    default boolean setPlayerBoost(Plugin owner, String boostId, UUID playerId, FoDropBoosterTarget target, double bonusMultiplier) {
        if (playerId == null) {
            return false;
        }
        return setBoost(owner, boostId, target, FoDropBoosterScope.PERSONAL, playerId.toString(), bonusMultiplier);
    }

    /**
     * Adds or updates an external team drop boost.
     *
     * @param owner plugin that owns this boost
     * @param boostId id scoped to the owner plugin, used later for removal
     * @param teamId FoTeams team id that should receive the boost
     * @param bonusMultiplier additive bonus, so 0.25 means +0.25x
     * @return true when the boost was accepted
     */
    default boolean setTeamDropBoost(Plugin owner, String boostId, String teamId, double bonusMultiplier) {
        return setTeamBoost(owner, boostId, teamId, FoDropBoosterTarget.DROPS, bonusMultiplier);
    }

    /**
     * Adds or updates an external team boost for a specific FoDropBooster target.
     *
     * @param owner plugin that owns this boost
     * @param boostId id scoped to the owner plugin, used later for removal
     * @param teamId FoTeams team id that should receive the boost
     * @param target booster system to affect
     * @param bonusMultiplier additive bonus, so 0.25 means +0.25x
     * @return true when the boost was accepted
     */
    default boolean setTeamBoost(Plugin owner, String boostId, String teamId, FoDropBoosterTarget target, double bonusMultiplier) {
        return setBoost(owner, boostId, target, FoDropBoosterScope.TEAM, teamId, bonusMultiplier);
    }

    /**
     * Adds or updates an external drop boost for all players.
     *
     * @param owner plugin that owns this boost
     * @param boostId id scoped to the owner plugin, used later for removal
     * @param bonusMultiplier additive bonus, so 0.25 means +0.25x
     * @return true when the boost was accepted
     */
    default boolean setGlobalDropBoost(Plugin owner, String boostId, double bonusMultiplier) {
        return setGlobalBoost(owner, boostId, FoDropBoosterTarget.DROPS, bonusMultiplier);
    }

    /**
     * Adds or updates an external boost for all players.
     *
     * @param owner plugin that owns this boost
     * @param boostId id scoped to the owner plugin, used later for removal
     * @param target booster system to affect
     * @param bonusMultiplier additive bonus, so 0.25 means +0.25x
     * @return true when the boost was accepted
     */
    default boolean setGlobalBoost(Plugin owner, String boostId, FoDropBoosterTarget target, double bonusMultiplier) {
        return setBoost(owner, boostId, target, FoDropBoosterScope.GLOBAL, "", bonusMultiplier);
    }

    /**
     * Adds or updates an external drop boost for one world.
     *
     * @param owner plugin that owns this boost
     * @param boostId id scoped to the owner plugin, used later for removal
     * @param worldName world that should receive the boost
     * @param bonusMultiplier additive bonus, so 0.25 means +0.25x
     * @return true when the boost was accepted
     */
    default boolean setWorldDropBoost(Plugin owner, String boostId, String worldName, double bonusMultiplier) {
        return setWorldBoost(owner, boostId, worldName, FoDropBoosterTarget.DROPS, bonusMultiplier);
    }

    /**
     * Adds or updates an external boost for one world.
     *
     * @param owner plugin that owns this boost
     * @param boostId id scoped to the owner plugin, used later for removal
     * @param worldName world that should receive the boost
     * @param target booster system to affect
     * @param bonusMultiplier additive bonus, so 0.25 means +0.25x
     * @return true when the boost was accepted
     */
    default boolean setWorldBoost(Plugin owner, String boostId, String worldName, FoDropBoosterTarget target, double bonusMultiplier) {
        return setBoost(owner, boostId, target, FoDropBoosterScope.WORLD, worldName, bonusMultiplier);
    }

    /**
     * Adds or updates an external boost.
     *
     * <p>External boosts are always stackable. They do not count as normal active boosters and therefore do not block
     * players from activating personal, team, world, or global booster items.</p>
     *
     * @param owner plugin that owns this boost
     * @param boostId id scoped to the owner plugin, used later for removal
     * @param target booster system to affect
     * @param scope who should receive the boost
     * @param scopeId player UUID for PERSONAL, FoTeams team id for TEAM, world name for WORLD, ignored for GLOBAL
     * @param bonusMultiplier additive bonus, so 0.25 means +0.25x
     * @return true when the boost was accepted
     */
    boolean setBoost(Plugin owner, String boostId, FoDropBoosterTarget target, FoDropBoosterScope scope, String scopeId, double bonusMultiplier);

    /**
     * Removes one boost owned by the given plugin.
     *
     * @param owner plugin that owns the boost
     * @param boostId id passed to setTeamBoost or setTeamDropBoost
     * @return true when a boost was removed
     */
    default boolean removeTeamBoost(Plugin owner, String boostId) {
        return removeBoost(owner, boostId);
    }

    /**
     * Removes one boost owned by the given plugin.
     *
     * @param owner plugin that owns the boost
     * @param boostId id passed to any set method
     * @return true when a boost was removed
     */
    boolean removeBoost(Plugin owner, String boostId);

    /**
     * Removes all boosts owned by the given plugin.
     *
     * @param owner plugin whose boosts should be removed
     * @return amount of removed boosts
     */
    default int removeTeamBoosts(Plugin owner) {
        return removeBoosts(owner);
    }

    /**
     * Removes all boosts owned by the given plugin.
     *
     * @param owner plugin whose boosts should be removed
     * @return amount of removed boosts
     */
    int removeBoosts(Plugin owner);

    /**
     * Returns the total external additive drop bonus for a team.
     *
     * @param teamId FoTeams team id
     * @return summed external bonus, so 0.25 means +0.25x
     */
    default double teamDropBoostBonus(String teamId) {
        return teamBoostBonus(teamId, FoDropBoosterTarget.DROPS);
    }

    /**
     * Returns the total external additive bonus for a team and target.
     *
     * @param teamId FoTeams team id
     * @param target booster system to inspect
     * @return summed external bonus, so 0.25 means +0.25x
     */
    double teamBoostBonus(String teamId, FoDropBoosterTarget target);
}
