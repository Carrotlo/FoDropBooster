package me.foesio.foDropBooster.booster;

import me.foesio.core.message.FoMessageService;
import me.foesio.core.scheduler.FoScheduler;
import me.foesio.foDropBooster.config.ConfigManager;
import me.foesio.foDropBooster.hook.FoTeamsHook;
import me.foesio.foDropBooster.util.FormatUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ActiveBoosterService {
    private static final long TEAM_LOOKUP_CACHE_MILLIS = 250L;

    private final JavaPlugin plugin;
    private final FoScheduler scheduler;
    private final ConfigManager configManager;
    private final FoMessageService messages;
    private final FoTeamsHook foTeamsHook;
    private final Map<String, ActiveBooster> active = new LinkedHashMap<>();
    private final Map<ExternalBoostKey, ExternalBoost> externalBoosts = new HashMap<>();
    private final Map<CacheKey, CachedBoost> effectiveCache = new HashMap<>();
    private final Map<UUID, CachedTeamInfo> teamInfoCache = new HashMap<>();
    private boolean cleanupRunning;
    private long cleanupGeneration;

    public ActiveBoosterService(JavaPlugin plugin, FoScheduler scheduler, ConfigManager configManager, FoMessageService messages, FoTeamsHook foTeamsHook) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.configManager = configManager;
        this.messages = messages;
        this.foTeamsHook = foTeamsHook;
    }

    public void start() {
        load();
        cleanupRunning = true;
        scheduleCleanup(++cleanupGeneration);
    }

    public void shutdown() {
        cleanupRunning = false;
        cleanupGeneration++;
        save();
    }

    public void reload() {
        load();
        invalidateAll();
        cleanup(false);
    }

    public ActivationResult activate(Player player, BoosterItemData data) {
        cleanup(false);
        if (data.target().isFoLevels() && !foLevelsAvailable()) {
            return ActivationResult.fail("folevels-required");
        }
        String worldName = resolveWorld(player, data);
        if (data.type() == BoosterType.WORLD && configManager.isWorldDisabled(worldName)) {
            return ActivationResult.fail("disabled-world");
        }

        FoTeamsHook.TeamInfo teamInfo = null;
        if (data.type() == BoosterType.TEAM) {
            if (!foTeamsHook.available()) {
                return ActivationResult.fail("teams-unavailable");
            }
            teamInfo = foTeamsHook.teamOf(player).orElse(null);
            if (teamInfo == null) {
                return ActivationResult.fail("no-team");
            }
        }

        String conflictTeamId = teamInfo == null ? currentTeam(player).map(FoTeamsHook.TeamInfo::id).orElse("") : teamInfo.id();
        if (!configManager.stackBoosters() && conflicts(player, data.target(), data.type(), worldName, conflictTeamId)) {
            return ActivationResult.fail("active-exists");
        }

        long now = System.currentTimeMillis();
        String storedTeamId = teamInfo == null ? "" : teamInfo.id();
        ActiveBooster booster = new ActiveBooster(
                UUID.randomUUID().toString(),
                data.boosterId(),
                data.target(),
                data.type(),
                data.type() == BoosterType.PERSONAL || data.type() == BoosterType.TEAM ? player.getUniqueId() : null,
                data.type() == BoosterType.PERSONAL || data.type() == BoosterType.TEAM ? player.getName() : "",
                data.type() == BoosterType.WORLD ? worldName : "",
                storedTeamId,
                teamInfo == null ? "" : teamInfo.name(),
                data.multiplier(),
                now,
                data.durationSeconds() * 1000L,
                data.enabledEvents(),
                data.bossbarTitle(),
                data.bossbarColor(),
                data.bossbarStyle()
        );
        active.put(booster.id(), booster);
        invalidateAll();
        save();
        return ActivationResult.success(booster);
    }

    public EffectiveBoost effective(Player player, EventFamily family) {
        return effective(player, BoosterTarget.DROPS, family);
    }

    public EffectiveBoost effectiveLevelXp(Player player) {
        return effective(player, BoosterTarget.FOLEVELS_XP, null);
    }

    public EffectiveBoost displayBoost(Player player) {
        EffectiveBoost drops = effective(player, BoosterTarget.DROPS, null);
        EffectiveBoost levels = effective(player, BoosterTarget.FOLEVELS_XP, null);
        if (!drops.active()) {
            return levels;
        }
        if (!levels.active()) {
            return drops;
        }
        if (drops.boosters().isEmpty() && !levels.boosters().isEmpty()) {
            return levels;
        }
        if (!drops.boosters().isEmpty() && levels.boosters().isEmpty()) {
            return drops;
        }
        if (drops.boosters().isEmpty()) {
            return drops;
        }
        return drops.remainingSeconds() <= levels.remainingSeconds() ? drops : levels;
    }

    public EffectiveBoost effective(Player player, BoosterTarget target, EventFamily family) {
        if (player == null || (family != null && !configManager.isEventEnabled(family))) {
            return EffectiveBoost.none();
        }
        String worldName = player.getWorld().getName();
        if (configManager.isWorldDisabled(worldName)) {
            return EffectiveBoost.none();
        }
        if (target.isFoLevels() && !foLevelsAvailable()) {
            return EffectiveBoost.none();
        }

        long now = System.currentTimeMillis();
        boolean stackBoosters = configManager.stackBoosters();
        boolean teamBoostersStackWithOtherBoosters = configManager.teamBoostersStackWithOtherBoosters();
        FoTeamsHook.TeamInfo teamInfo = cachedCurrentTeam(player, now);
        String teamId = teamInfo == null ? "" : teamInfo.id();
        int onlineTeamMembers = teamInfo == null ? 0 : teamInfo.onlineMembers();
        CacheKey key = new CacheKey(player.getUniqueId(), worldName.toLowerCase(Locale.ROOT), teamId, onlineTeamMembers, target, family);
        CachedBoost cached = effectiveCache.get(key);
        if (cached != null && cached.valid(now, stackBoosters)) {
            return cached.toEffective(now);
        }

        double passiveTeamMultiplier = passiveMultiplier(player, onlineTeamMembers, teamId, target);
        List<ActiveBooster> applicable = applicable(player, target, family, now, teamId);
        if (applicable.isEmpty() && passiveTeamMultiplier <= 1D) {
            CachedBoost none = CachedBoost.none(target, stackBoosters);
            effectiveCache.put(key, none);
            return none.toEffective(now);
        }

        CachedBoost resolved;
        if (applicable.isEmpty()) {
            resolved = new CachedBoost(
                    passiveTeamMultiplier,
                    target,
                    1D,
                    passiveTeamMultiplier,
                    onlineTeamMembers,
                    configManager.teamBossbarTitle(target),
                    configManager.globalBossbarColor(),
                    configManager.globalBossbarStyle(),
                    List.of(),
                    Long.MAX_VALUE,
                    stackBoosters
            );
            effectiveCache.put(key, resolved);
            return resolved.toEffective(now);
        }

        if (!stackBoosters) {
            ActiveBooster bestBooster = bestNonTeamBooster(applicable);
            ActiveBooster bestTeamBooster = bestTeamBooster(applicable);
            boolean includeTeamBooster = bestTeamBooster != null && (teamBoostersStackWithOtherBoosters || bestBooster == null);
            List<ActiveBooster> selectedBoosters = selectedBoosters(bestBooster, includeTeamBooster ? bestTeamBooster : null);
            if (selectedBoosters.isEmpty() && passiveTeamMultiplier <= 1D) {
                return EffectiveBoost.none();
            }
            ActiveBooster titleBooster = bestBooster == null ? bestTeamBooster : bestBooster;
            double boosterMultiplier = bestBooster == null ? 1D : bestBooster.multiplier();
            double teamMultiplier = passiveTeamMultiplier * (includeTeamBooster ? bestTeamBooster.multiplier() : 1D);
            resolved = new CachedBoost(
                    boosterMultiplier * teamMultiplier,
                    target,
                    boosterMultiplier,
                    teamMultiplier,
                    onlineTeamMembers,
                    titleBooster == null ? configManager.teamBossbarTitle(target) : titleBooster.bossbarTitle(),
                    titleBooster == null ? configManager.globalBossbarColor() : titleBooster.bossbarColor(),
                    titleBooster == null ? configManager.globalBossbarStyle() : titleBooster.bossbarStyle(),
                    selectedBoosters,
                    earliestExpiresAt(selectedBoosters),
                    false
            );
            effectiveCache.put(key, resolved);
            return resolved.toEffective(now);
        }

        double boosterMultiplier = 1D;
        double timedTeamMultiplier = 1D;
        long expiresAt = Long.MAX_VALUE;
        boolean hasNonTeamBooster = applicable.stream().anyMatch(booster -> booster.type() != BoosterType.TEAM);
        List<ActiveBooster> selectedBoosters = new ArrayList<>();
        for (ActiveBooster booster : applicable) {
            if (booster.type() == BoosterType.TEAM) {
                if (!teamBoostersStackWithOtherBoosters && hasNonTeamBooster) {
                    continue;
                }
                timedTeamMultiplier *= booster.multiplier();
            } else {
                boosterMultiplier *= booster.multiplier();
            }
            expiresAt = Math.min(expiresAt, booster.expiresAtMillis());
            selectedBoosters.add(booster);
        }
        double teamMultiplier = passiveTeamMultiplier * timedTeamMultiplier;
        resolved = new CachedBoost(
                boosterMultiplier * teamMultiplier,
                target,
                boosterMultiplier,
                teamMultiplier,
                onlineTeamMembers,
                configManager.bossbarTitle(target),
                configManager.globalBossbarColor(),
                configManager.globalBossbarStyle(),
                List.copyOf(selectedBoosters),
                expiresAt,
                true
        );
        effectiveCache.put(key, resolved);
        return resolved.toEffective(now);
    }

    public void invalidate(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        effectiveCache.keySet().removeIf(key -> key.playerId().equals(playerId));
        teamInfoCache.remove(playerId);
    }

    public void invalidateAll() {
        effectiveCache.clear();
        teamInfoCache.clear();
    }

    public List<ActiveBooster> activeBoosters() {
        cleanup(false);
        return new ArrayList<>(active.values());
    }

    public int removeById(String id) {
        if (id == null || id.isBlank()) {
            return 0;
        }
        ActiveBooster removed = active.remove(id);
        if (removed == null) {
            String prefix = id.toLowerCase(Locale.ROOT);
            List<String> matches = active.keySet().stream().filter(key -> key.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
            for (String match : matches) {
                active.remove(match);
            }
            if (!matches.isEmpty()) {
                invalidateAll();
                save();
            }
            return matches.size();
        }
        invalidateAll();
        save();
        return 1;
    }

    public int removeGlobal(String target) {
        return removeIf(booster -> booster.type() == BoosterType.GLOBAL
                && (target.equalsIgnoreCase("all") || booster.id().toLowerCase(Locale.ROOT).startsWith(target.toLowerCase(Locale.ROOT))));
    }

    public int removeWorld(String target) {
        return removeIf(booster -> booster.type() == BoosterType.WORLD
                && (target.equalsIgnoreCase("all") || booster.worldName().equalsIgnoreCase(target)));
    }

    public int removePersonal(String target) {
        return removeIf(booster -> {
            if (booster.type() != BoosterType.PERSONAL) {
                return false;
            }
            if (target.equalsIgnoreCase("all")) {
                return true;
            }
            if (booster.ownerName().equalsIgnoreCase(target)) {
                return true;
            }
            try {
                return booster.ownerId() != null && booster.ownerId().equals(UUID.fromString(target));
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        });
    }

    public int removeTeam(String target) {
        String normalized = target.toLowerCase(Locale.ROOT);
        return removeIf(booster -> booster.type() == BoosterType.TEAM
                && (target.equalsIgnoreCase("all")
                || booster.id().toLowerCase(Locale.ROOT).startsWith(normalized)
                || booster.teamId().equalsIgnoreCase(target)
                || booster.teamName().equalsIgnoreCase(target)));
    }

    public boolean setExternalBoost(String ownerName, String boostId, BoosterTarget target, ExternalBoostScope scope, String scopeId, double bonusMultiplier) {
        String normalizedOwner = normalizeExternalKey(ownerName);
        String normalizedBoostId = normalizeExternalKey(boostId);
        ExternalBoostScope resolvedScope = scope == null ? ExternalBoostScope.GLOBAL : scope;
        String normalizedScopeId = normalizeScopeId(resolvedScope, scopeId);
        if (normalizedOwner.isBlank()
                || normalizedBoostId.isBlank()
                || (resolvedScope != ExternalBoostScope.GLOBAL && normalizedScopeId.isBlank())
                || bonusMultiplier <= 0D
                || !Double.isFinite(bonusMultiplier)) {
            return false;
        }

        ExternalBoostKey key = new ExternalBoostKey(normalizedOwner, normalizedBoostId);
        ExternalBoost boost = new ExternalBoost(
                normalizedOwner,
                normalizedBoostId,
                target == null ? BoosterTarget.DROPS : target,
                resolvedScope,
                normalizedScopeId,
                bonusMultiplier
        );
        ExternalBoost previous = externalBoosts.put(key, boost);
        if (!boost.equals(previous)) {
            invalidateAll();
        }
        return true;
    }

    public boolean removeExternalBoost(String ownerName, String boostId) {
        String normalizedOwner = normalizeExternalKey(ownerName);
        String normalizedBoostId = normalizeExternalKey(boostId);
        if (normalizedOwner.isBlank() || normalizedBoostId.isBlank()) {
            return false;
        }
        ExternalBoost removed = externalBoosts.remove(new ExternalBoostKey(normalizedOwner, normalizedBoostId));
        if (removed != null) {
            invalidateAll();
            return true;
        }
        return false;
    }

    public int removeExternalBoosts(String ownerName) {
        String normalizedOwner = normalizeExternalKey(ownerName);
        if (normalizedOwner.isBlank()) {
            return 0;
        }
        List<ExternalBoostKey> matches = externalBoosts.keySet().stream()
                .filter(key -> key.ownerName().equals(normalizedOwner))
                .toList();
        for (ExternalBoostKey match : matches) {
            externalBoosts.remove(match);
        }
        if (!matches.isEmpty()) {
            invalidateAll();
        }
        return matches.size();
    }

    public double externalTeamBoostBonus(String teamId, BoosterTarget target) {
        if (teamId == null || teamId.isBlank()) {
            return 0D;
        }
        BoosterTarget resolvedTarget = target == null ? BoosterTarget.DROPS : target;
        double bonus = 0D;
        for (ExternalBoost boost : externalBoosts.values()) {
            if (boost.target() == resolvedTarget && boost.scope() == ExternalBoostScope.TEAM && boost.scopeId().equalsIgnoreCase(teamId)) {
                bonus += boost.bonusMultiplier();
            }
        }
        return Math.max(0D, bonus);
    }

    public String formatMultiplier(double multiplier) {
        return FormatUtil.multiplier(multiplier, configManager.numberFormat());
    }

    public String formatActiveTitle(Player player, EffectiveBoost boost) {
        String template = boost.bossbarTitle() == null || boost.bossbarTitle().isBlank()
                ? configManager.bossbarTitle(boost.target())
                : boost.bossbarTitle();
        if (boost.boosterMultiplier() > 1D && boost.teamMultiplier() > 1D && !template.contains("{multiplier_parts}")) {
            template = template.replace("{multiplier}x", "{multiplier_parts}");
        }
        return template
                .replace("{multiplier_parts}", multiplierParts(boost))
                .replace("{target}", boost.target().display())
                .replace("{total_multiplier}", formatMultiplier(boost.multiplier()))
                .replace("{booster_multiplier}", formatMultiplier(boost.boosterMultiplier()))
                .replace("{team_multiplier}", formatMultiplier(boost.teamMultiplier()))
                .replace("{online_team_members}", Integer.toString(boost.onlineTeamMembers()))
                .replace("{multiplier}", formatMultiplier(boost.multiplier()))
                .replace("{time}", FormatUtil.duration(boost.remainingSeconds()))
                .replace("{count}", Integer.toString(boost.boosters().size()))
                .replace("{player}", player.getName())
                .replace("{world}", player.getWorld().getName())
                .replace("{team}", activeTeamName(boost));
    }

    private int removeIf(java.util.function.Predicate<ActiveBooster> predicate) {
        List<String> matches = active.values().stream().filter(predicate).map(ActiveBooster::id).toList();
        for (String match : matches) {
            active.remove(match);
        }
        if (!matches.isEmpty()) {
            invalidateAll();
            save();
        }
        return matches.size();
    }

    private boolean conflicts(Player player, BoosterTarget target, BoosterType type, String worldName, String teamId) {
        long now = System.currentTimeMillis();
        if (type == BoosterType.GLOBAL) {
            return active.values().stream().anyMatch(booster -> booster.target() == target
                    && !booster.expired(now)
                    && !canCoexistWithTeamBooster(type, booster.type()));
        }
        String conflictWorld = type == BoosterType.WORLD ? worldName : player.getWorld().getName();
        return active.values().stream().anyMatch(booster -> booster.target() == target && !booster.expired(now)
                && appliesToPlayer(booster, player, conflictWorld, teamId)
                && !canCoexistWithTeamBooster(type, booster.type()));
    }

    private boolean canCoexistWithTeamBooster(BoosterType incomingType, BoosterType activeType) {
        return incomingType == BoosterType.TEAM ^ activeType == BoosterType.TEAM;
    }

    private List<ActiveBooster> applicable(Player player, BoosterTarget target, EventFamily family, long now, String teamId) {
        List<ActiveBooster> applicable = new ArrayList<>();
        String worldName = player.getWorld().getName();
        for (ActiveBooster booster : active.values()) {
            if (booster.target() != target) {
                continue;
            }
            if (booster.expired(now)) {
                continue;
            }
            if (family != null && !booster.enabledEvents().contains(family)) {
                continue;
            }
            if (booster.type() == BoosterType.GLOBAL) {
                applicable.add(booster);
            } else if (booster.type() == BoosterType.PERSONAL && player.getUniqueId().equals(booster.ownerId())) {
                applicable.add(booster);
            } else if (booster.type() == BoosterType.WORLD && booster.worldName().equalsIgnoreCase(worldName)) {
                applicable.add(booster);
            } else if (booster.type() == BoosterType.TEAM && !teamId.isBlank() && booster.teamId().equalsIgnoreCase(teamId)) {
                applicable.add(booster);
            }
        }
        return applicable;
    }

    private boolean appliesToPlayer(ActiveBooster booster, Player player, String worldName, String teamId) {
        return switch (booster.type()) {
            case GLOBAL -> true;
            case PERSONAL -> player.getUniqueId().equals(booster.ownerId());
            case WORLD -> booster.worldName().equalsIgnoreCase(worldName);
            case TEAM -> !teamId.isBlank() && booster.teamId().equalsIgnoreCase(teamId);
        };
    }

    private ActiveBooster bestNonTeamBooster(List<ActiveBooster> boosters) {
        return boosters.stream()
                .filter(booster -> booster.type() != BoosterType.TEAM)
                .max(Comparator.comparingDouble(ActiveBooster::multiplier).thenComparingLong(ActiveBooster::expiresAtMillis))
                .orElse(null);
    }

    private ActiveBooster bestTeamBooster(List<ActiveBooster> boosters) {
        return boosters.stream()
                .filter(booster -> booster.type() == BoosterType.TEAM)
                .max(Comparator.comparingDouble(ActiveBooster::multiplier).thenComparingLong(ActiveBooster::expiresAtMillis))
                .orElse(null);
    }

    private List<ActiveBooster> selectedBoosters(ActiveBooster primaryBooster, ActiveBooster teamBooster) {
        List<ActiveBooster> selected = new ArrayList<>(2);
        if (primaryBooster != null) {
            selected.add(primaryBooster);
        }
        if (teamBooster != null) {
            selected.add(teamBooster);
        }
        return selected;
    }

    private long earliestExpiresAt(List<ActiveBooster> boosters) {
        long expiresAt = Long.MAX_VALUE;
        for (ActiveBooster booster : boosters) {
            expiresAt = Math.min(expiresAt, booster.expiresAtMillis());
        }
        return expiresAt;
    }

    public double teamMultiplier(Player player) {
        if (player == null) {
            return 1D;
        }
        FoTeamsHook.TeamInfo teamInfo = cachedCurrentTeam(player, System.currentTimeMillis());
        return passiveMultiplier(
                player,
                teamInfo == null ? 0 : teamInfo.onlineMembers(),
                teamInfo == null ? "" : teamInfo.id(),
                BoosterTarget.DROPS
        );
    }

    private Optional<FoTeamsHook.TeamInfo> currentTeam(Player player) {
        return foTeamsHook.teamOf(player);
    }

    private FoTeamsHook.TeamInfo cachedCurrentTeam(Player player, long now) {
        UUID playerId = player.getUniqueId();
        CachedTeamInfo cached = teamInfoCache.get(playerId);
        if (cached != null && cached.valid(now)) {
            return cached.teamInfo();
        }
        FoTeamsHook.TeamInfo teamInfo = currentTeam(player).orElse(null);
        teamInfoCache.put(playerId, new CachedTeamInfo(teamInfo, now + TEAM_LOOKUP_CACHE_MILLIS));
        return teamInfo;
    }

    private double passiveMultiplier(Player player, int onlineMembers, String teamId, BoosterTarget target) {
        return Math.max(1D, hiddenTeamMultiplier(onlineMembers) + externalBoostBonus(player, teamId, target));
    }

    private double hiddenTeamMultiplier(int onlineMembers) {
        if (!configManager.teamMultiplierEnabled()) {
            return 1D;
        }
        if (onlineMembers <= 0) {
            return 1D;
        }
        double multiplier = 1D + configManager.teamMultiplierPerOnlineMember() * onlineMembers;
        return Math.max(1D, Math.min(configManager.teamMultiplierCap(), multiplier));
    }

    private String normalizeExternalKey(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeScopeId(ExternalBoostScope scope, String scopeId) {
        if (scope == ExternalBoostScope.GLOBAL) {
            return "";
        }
        if (scopeId == null || scopeId.isBlank()) {
            return "";
        }
        String normalized = scopeId.trim();
        if (scope == ExternalBoostScope.PERSONAL) {
            try {
                return UUID.fromString(normalized).toString();
            } catch (IllegalArgumentException exception) {
                return "";
            }
        }
        return normalized;
    }

    private double externalBoostBonus(Player player, String teamId, BoosterTarget target) {
        if (player == null) {
            return 0D;
        }
        BoosterTarget resolvedTarget = target == null ? BoosterTarget.DROPS : target;
        String playerId = player.getUniqueId().toString();
        String worldName = player.getWorld().getName();
        double bonus = 0D;
        for (ExternalBoost boost : externalBoosts.values()) {
            if (boost.target() != resolvedTarget) {
                continue;
            }
            if (appliesExternalBoost(boost, playerId, teamId, worldName)) {
                bonus += boost.bonusMultiplier();
            }
        }
        return Math.max(0D, bonus);
    }

    private boolean appliesExternalBoost(ExternalBoost boost, String playerId, String teamId, String worldName) {
        return switch (boost.scope()) {
            case GLOBAL -> true;
            case PERSONAL -> boost.scopeId().equalsIgnoreCase(playerId);
            case TEAM -> teamId != null && !teamId.isBlank() && boost.scopeId().equalsIgnoreCase(teamId);
            case WORLD -> worldName != null && boost.scopeId().equalsIgnoreCase(worldName);
        };
    }

    private String multiplierParts(EffectiveBoost boost) {
        boolean hasBooster = boost.boosterMultiplier() > 1D;
        boolean hasTeam = boost.teamMultiplier() > 1D;
        if (hasBooster && hasTeam) {
            return formatMultiplier(boost.boosterMultiplier()) + "x & " + formatMultiplier(boost.teamMultiplier()) + "x";
        }
        if (hasBooster) {
            return formatMultiplier(boost.boosterMultiplier()) + "x";
        }
        if (hasTeam) {
            return formatMultiplier(boost.teamMultiplier()) + "x";
        }
        return formatMultiplier(boost.multiplier()) + "x";
    }

    private String activeTeamName(EffectiveBoost boost) {
        for (ActiveBooster booster : boost.boosters()) {
            if (booster.type() == BoosterType.TEAM && booster.teamName() != null && !booster.teamName().isBlank()) {
                return booster.teamName();
            }
        }
        return "";
    }

    private String resolveWorld(Player player, BoosterItemData data) {
        if (data.type() != BoosterType.WORLD) {
            return "";
        }
        if (data.worldName() != null && !data.worldName().isBlank()) {
            return data.worldName();
        }
        World world = player.getWorld();
        return world == null ? "" : world.getName();
    }

    private void cleanup(boolean notifyPersonal) {
        long now = System.currentTimeMillis();
        List<ActiveBooster> expired = active.values().stream().filter(booster -> booster.expired(now)).toList();
        if (expired.isEmpty()) {
            return;
        }
        for (ActiveBooster booster : expired) {
            active.remove(booster.id());
            if (notifyPersonal && booster.type() == BoosterType.PERSONAL && booster.ownerId() != null) {
                Player player = Bukkit.getPlayer(booster.ownerId());
                if (player != null) {
                    messages.send(player, "messages.expired");
                }
            }
        }
        invalidateAll();
        save();
    }

    private void scheduleCleanup(long generation) {
        scheduler.runGlobalLater(() -> {
            if (!cleanupRunning || generation != cleanupGeneration) {
                return;
            }
            cleanup(true);
            scheduleCleanup(generation);
        }, 20L);
    }

    private void load() {
        active.clear();
        YamlConfiguration file = configManager.active();
        ConfigurationSection section = file.getConfigurationSection("active");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            String path = "active." + id + ".";
            UUID ownerId = parseUuid(file.getString(path + "owner-id", ""));
            ActiveBooster booster = new ActiveBooster(
                    id,
                    file.getString(path + "booster-id", "custom"),
                    BoosterTarget.from(file.getString(path + "target", "DROPS")),
                    BoosterType.from(file.getString(path + "type", "PERSONAL")),
                    ownerId,
                    file.getString(path + "owner-name", ""),
                    file.getString(path + "world", ""),
                    file.getString(path + "team-id", ""),
                    file.getString(path + "team-name", ""),
                    Math.max(1D, file.getDouble(path + "multiplier", 1D)),
                    file.getLong(path + "started-at", System.currentTimeMillis()),
                    Math.max(1L, file.getLong(path + "duration-ms", 1L)),
                    parseEvents(file.getStringList(path + "enabled-events")),
                    file.getString(path + "bossbar-title", configManager.globalBossbarTitle()),
                    configManager.barColor(file.getString(path + "bossbar-color", "GREEN")),
                    configManager.barStyle(file.getString(path + "bossbar-style", "SEGMENTED_10"))
            );
            active.put(id, booster);
        }
    }

    private void save() {
        YamlConfiguration file = configManager.active();
        file.set("active", null);
        for (ActiveBooster booster : active.values()) {
            String path = "active." + booster.id() + ".";
            file.set(path + "booster-id", booster.boosterId());
            file.set(path + "target", booster.target().name());
            file.set(path + "type", booster.type().name());
            file.set(path + "owner-id", booster.ownerId() == null ? "" : booster.ownerId().toString());
            file.set(path + "owner-name", booster.ownerName());
            file.set(path + "world", booster.worldName());
            file.set(path + "team-id", booster.teamId());
            file.set(path + "team-name", booster.teamName());
            file.set(path + "multiplier", booster.multiplier());
            file.set(path + "started-at", booster.startedAtMillis());
            file.set(path + "duration-ms", booster.durationMillis());
            file.set(path + "enabled-events", configManager.eventKeys(booster.enabledEvents()));
            file.set(path + "bossbar-title", booster.bossbarTitle());
            file.set(path + "bossbar-color", booster.bossbarColor().name());
            file.set(path + "bossbar-style", booster.bossbarStyle().name());
        }
        configManager.saveActive();
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Set<EventFamily> parseEvents(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return configManager.defaultBoosterEvents();
        }
        return configManager.enabledEvents(raw);
    }

    private boolean foLevelsAvailable() {
        return plugin.getServer().getPluginManager().isPluginEnabled("FoLevels");
    }

    private record CacheKey(UUID playerId, String worldName, String teamId, int onlineTeamMembers, BoosterTarget target, EventFamily family) {
    }

    private record CachedTeamInfo(FoTeamsHook.TeamInfo teamInfo, long expiresAtMillis) {
        private boolean valid(long now) {
            return now < expiresAtMillis;
        }
    }

    public enum ExternalBoostScope {
        PERSONAL,
        TEAM,
        GLOBAL,
        WORLD
    }

    private record ExternalBoostKey(String ownerName, String boostId) {
    }

    private record ExternalBoost(
            String ownerName,
            String boostId,
            BoosterTarget target,
            ExternalBoostScope scope,
            String scopeId,
            double bonusMultiplier
    ) {
    }

    private record CachedBoost(
            double multiplier,
            BoosterTarget target,
            double boosterMultiplier,
            double teamMultiplier,
            int onlineTeamMembers,
            String bossbarTitle,
            BarColor bossbarColor,
            BarStyle bossbarStyle,
            List<ActiveBooster> boosters,
            long expiresAtMillis,
            boolean stackBoosters
    ) {
        private static CachedBoost none(BoosterTarget target, boolean stackBoosters) {
            return new CachedBoost(1D, target, 1D, 1D, 0, "", BarColor.GREEN, BarStyle.SEGMENTED_10, List.of(), Long.MAX_VALUE, stackBoosters);
        }

        private boolean valid(long now, boolean currentStackBoosters) {
            return stackBoosters == currentStackBoosters && now < expiresAtMillis;
        }

        private EffectiveBoost toEffective(long now) {
            if (multiplier <= 1D) {
                return EffectiveBoost.none();
            }
            if (boosters.isEmpty()) {
                return new EffectiveBoost(
                        multiplier,
                        target,
                        boosterMultiplier,
                        teamMultiplier,
                        onlineTeamMembers,
                        1D,
                        0L,
                        bossbarTitle,
                        bossbarColor,
                        bossbarStyle,
                        boosters
                );
            }

            ActiveBooster shortest = boosters.get(0);
            for (ActiveBooster booster : boosters) {
                if (booster.progress(now) < shortest.progress(now)) {
                    shortest = booster;
                }
            }
            return new EffectiveBoost(
                    multiplier,
                    target,
                    boosterMultiplier,
                    teamMultiplier,
                    onlineTeamMembers,
                    shortest.progress(now),
                    shortest.remainingMillis(now) / 1000L,
                    bossbarTitle,
                    bossbarColor,
                    bossbarStyle,
                    boosters
            );
        }
    }
}
