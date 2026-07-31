package me.foesio.foDropBooster.config;

import me.foesio.core.config.ConfigValueLists;
import me.foesio.core.config.ResourceFiles;
import me.foesio.core.dialog.NativeDialogConfigDefaults;
import me.foesio.core.material.MaterialTypes;
import me.foesio.foDropBooster.booster.BoosterProfile;
import me.foesio.foDropBooster.booster.BoosterTarget;
import me.foesio.foDropBooster.booster.BoosterType;
import me.foesio.foDropBooster.booster.EventFamily;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ConfigManager {
    private static final List<String> DEFAULT_LORE = List.of(
            "",
            "#03fc88ɪɴғᴏ",
            " &8❙ &fTarget: #03fc88{target}",
            " &8❙ &fType: #03fc88{type}",
            " &8❙ &fScope: #03fc88{scope}",
            " &8❙ &fMultiplier: #03fc88{multiplier}x",
            " &8❙ &fDuration: #03fc88{duration}",
            "",
            "#03fc88▶ #a7b8b0Right click &8- &fActivate"
    );

    private final JavaPlugin plugin;
    private File activeFile;
    private File boostersFolder;
    private YamlConfiguration active;
    private final Map<String, YamlConfiguration> boosters = new LinkedHashMap<>();
    private final Map<String, File> boosterFiles = new LinkedHashMap<>();
    private Set<EventFamily> enabledEvents = EnumSet.noneOf(EventFamily.class);
    private Set<String> disabledWorlds = Set.of();
    private String numberFormat = "#,##0.##";
    private int maxMultipliedAmount = 4096;
    private boolean stackBoosters;
    private boolean teamBoostersStackWithOtherBoosters = true;
    private boolean teamMultiplierEnabled = true;
    private double teamMultiplierPerOnlineMember = 0.05D;
    private double teamMultiplierCap = 2D;
    private boolean bossbarEnabled = true;
    private long bossbarUpdateTicks = 20L;
    private BarColor globalBossbarColor = BarColor.GREEN;
    private BarStyle globalBossbarStyle = BarStyle.SEGMENTED_10;
    private String globalBossbarTitle = "#03fc88{multiplier}x #a7b8b0ᴅʀᴏᴘ ʙᴏᴏsᴛᴇʀ &8- #ffffff{time}";
    private String levelXpBossbarTitle = "#03fc88{multiplier_parts} #a7b8b0ʟᴇᴠᴇʟ xᴘ ʙᴏᴏsᴛᴇʀ &8- #ffffff{time}";
    private String teamBossbarTitle = "#03fc88{team_multiplier}x #a7b8b0ᴛᴇᴀᴍ ᴅʀᴏᴘ ʙᴏᴏsᴛ";
    private String teamLevelXpBossbarTitle = "#03fc88{team_multiplier}x #a7b8b0ᴛᴇᴀᴍ ʟᴇᴠᴇʟ xᴘ ʙᴏᴏsᴛ";

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        ensureConfigDefaults();

        activeFile = ResourceFiles.dataFile(plugin, "active.yml");
        boostersFolder = ResourceFiles.dataFile(plugin, "boosters");
        active = YamlConfiguration.loadConfiguration(activeFile);
        refreshCachedSettings();
        loadBoosters();
    }

    public FileConfiguration config() {
        return plugin.getConfig();
    }

    public YamlConfiguration active() {
        return active;
    }

    public Set<String> boosterIds() {
        return new LinkedHashSet<>(boosters.keySet());
    }

    public YamlConfiguration booster(String id) {
        return boosters.get(normalizeId(id));
    }

    public boolean hasBooster(String id) {
        return boosters.containsKey(normalizeId(id));
    }

    public BoosterProfile profile(String id) {
        String normalized = normalizeId(id);
        YamlConfiguration file = boosters.get(normalized);
        if (file == null) {
            return null;
        }

        return new BoosterProfile(
                normalized,
                BoosterTarget.from(file.getString("target", "DROPS")),
                BoosterType.from(file.getString("type", "PERSONAL")),
                Math.max(1D, file.getDouble("multiplier", 2D)),
                Math.max(1L, file.getLong("duration-seconds", 900L)),
                file.getString("world", ""),
                enabledEvents(file.getStringList("enabled-events")),
                material(file.getString("material", "CLOCK"), Material.CLOCK),
                file.getBoolean("glow", true),
                Math.max(0, file.getInt("custom-model-data", 0)),
                file.getString("name", "#03fc88" + normalized + " ᴅʀᴏᴘ ʙᴏᴏsᴛᴇʀ &8(&f{multiplier}x&8)"),
                file.getStringList("lore").isEmpty() ? DEFAULT_LORE : file.getStringList("lore"),
                file.getString("bossbar.title", config().getString("bossbar.title", "#03fc88{multiplier}x")),
                barColor(file.getString("bossbar.color", config().getString("bossbar.color", "GREEN"))),
                barStyle(file.getString("bossbar.style", config().getString("bossbar.style", "SEGMENTED_10")))
        );
    }

    public BoosterProfile firstProfile() {
        return firstProfile(BoosterTarget.DROPS);
    }

    public BoosterProfile firstProfile(BoosterTarget target) {
        return boosterIds().stream()
                .map(this::profile)
                .filter(profile -> profile != null && profile.target() == target)
                .findFirst()
                .orElseGet(() -> boosterIds().stream().map(this::profile).filter(profile -> profile != null).findFirst().orElse(null));
    }

    public void createBooster(String id) {
        createBooster(id, BoosterTarget.DROPS);
    }

    public void createBooster(String id, BoosterTarget target) {
        String normalized = normalizeId(id);
        YamlConfiguration file = new YamlConfiguration();
        file.set("target", target.name());
        file.set("type", "PERSONAL");
        file.set("multiplier", 2D);
        file.set("duration-seconds", 900L);
        file.set("world", "");
        file.set("enabled-events", defaultBoosterEventKeys());
        file.set("material", "CLOCK");
        file.set("glow", true);
        file.set("custom-model-data", 0);
        file.set("name", "#03fc88" + normalized + (target.isFoLevels() ? " ʟᴇᴠᴇʟ xᴘ ʙᴏᴏsᴛᴇʀ" : " ᴅʀᴏᴘ ʙᴏᴏsᴛᴇʀ") + " &8(&f{multiplier}x&8)");
        file.set("lore", DEFAULT_LORE);
        file.set("bossbar.title", bossbarTitle(target));
        file.set("bossbar.color", "GREEN");
        file.set("bossbar.style", "SEGMENTED_10");
        boosters.put(normalized, file);
        boosterFiles.put(normalized, new File(boostersFolder, normalized + ".yml"));
        saveBooster(normalized);
    }

    public void deleteBooster(String id) {
        String normalized = normalizeId(id);
        File file = boosterFiles.remove(normalized);
        boosters.remove(normalized);
        if (file != null && file.exists() && !file.delete()) {
            plugin.getLogger().warning("Could not delete booster file " + file.getName());
        }
    }

    public void setBoosterValue(String id, String path, Object value) {
        String normalized = normalizeId(id);
        YamlConfiguration file = boosters.get(normalized);
        if (file == null) {
            return;
        }
        file.set(path, value);
        saveBooster(normalized);
    }

    public void setConfigValue(String path, Object value) {
        config().set(path, value);
        plugin.saveConfig();
        refreshCachedSettings();
    }

    public void saveActive() {
        try {
            active.save(activeFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save active.yml: " + exception.getMessage());
        }
    }

    public boolean isEventEnabled(EventFamily family) {
        return family != null && enabledEvents.contains(family);
    }

    public boolean isWorldDisabled(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return false;
        }
        return disabledWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    public String numberFormat() {
        return numberFormat;
    }

    public int maxMultipliedAmount() {
        return maxMultipliedAmount;
    }

    public boolean stackBoosters() {
        return stackBoosters;
    }

    public boolean teamBoostersStackWithOtherBoosters() {
        return teamBoostersStackWithOtherBoosters;
    }

    public boolean teamMultiplierEnabled() {
        return teamMultiplierEnabled;
    }

    public double teamMultiplierPerOnlineMember() {
        return teamMultiplierPerOnlineMember;
    }

    public double teamMultiplierCap() {
        return teamMultiplierCap;
    }

    public boolean bossbarEnabled() {
        return bossbarEnabled;
    }

    public long bossbarUpdateTicks() {
        return bossbarUpdateTicks;
    }

    public Material customMaterial() {
        return material(config().getString("custom-item.material", "CLOCK"), Material.CLOCK);
    }

    public boolean customGlow() {
        return config().getBoolean("custom-item.glow", true);
    }

    public int customModelData() {
        return Math.max(0, config().getInt("custom-item.custom-model-data", 0));
    }

    public String customName() {
        return config().getString("custom-item.name", "#03fc88ᴄᴜsᴛᴏᴍ {target} ʙᴏᴏsᴛᴇʀ &8(&f{multiplier}x&8)");
    }

    public List<String> customLore() {
        List<String> lore = config().getStringList("custom-item.lore");
        return lore.isEmpty() ? DEFAULT_LORE : lore;
    }

    public Set<EventFamily> enabledEvents(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return defaultBoosterEvents();
        }
        Set<EventFamily> parsed = EnumSet.noneOf(EventFamily.class);
        for (String value : raw) {
            if (value.equalsIgnoreCase("all")) {
                return EnumSet.allOf(EventFamily.class);
            }
            if (value.equalsIgnoreCase("none")) {
                return EnumSet.noneOf(EventFamily.class);
            }
            EventFamily family = EventFamily.fromConfig(value);
            if (family != null) {
                parsed.add(family);
            }
        }
        return parsed.isEmpty() ? defaultBoosterEvents() : parsed;
    }

    public Set<EventFamily> defaultBoosterEvents() {
        Set<EventFamily> events = EnumSet.allOf(EventFamily.class);
        events.remove(EventFamily.CRAFTING);
        return events;
    }

    public List<String> defaultBoosterEventKeys() {
        return eventKeys(defaultBoosterEvents());
    }

    public List<String> eventKeys(Set<EventFamily> events) {
        if (events == null || events.isEmpty()) {
            return List.of("none");
        }
        return java.util.Arrays.stream(EventFamily.values())
                .filter(events::contains)
                .map(EventFamily::configKey)
                .toList();
    }

    public BarColor globalBossbarColor() {
        return globalBossbarColor;
    }

    public BarStyle globalBossbarStyle() {
        return globalBossbarStyle;
    }

    public String globalBossbarTitle() {
        return globalBossbarTitle;
    }

    public String levelXpBossbarTitle() {
        return levelXpBossbarTitle;
    }

    public String bossbarTitle(BoosterTarget target) {
        return target.isFoLevels() ? levelXpBossbarTitle() : globalBossbarTitle();
    }

    public String teamBossbarTitle() {
        return teamBossbarTitle;
    }

    public String teamBossbarTitle(BoosterTarget target) {
        return target.isFoLevels() ? teamLevelXpBossbarTitle : teamBossbarTitle;
    }

    public BarColor barColor(String input) {
        try {
            return BarColor.valueOf((input == null ? "GREEN" : input).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return BarColor.GREEN;
        }
    }

    public BarStyle barStyle(String input) {
        try {
            return BarStyle.valueOf((input == null ? "SEGMENTED_10" : input).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return BarStyle.SEGMENTED_10;
        }
    }

    public Material material(String raw, Material fallback) {
        Material material = MaterialTypes.match(raw);
        return material == null || material.isAir() ? fallback : material;
    }

    private void loadBoosters() {
        boosters.clear();
        boosterFiles.clear();
        if (!boostersFolder.exists() && !boostersFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create boosters folder.");
        }
        saveBundledBooster("basic.yml");
        saveBundledBooster("strong.yml");
        saveBundledBooster("xp-basic.yml");
        saveBundledBooster("xp-strong.yml");

        File[] files = boostersFolder.listFiles((directory, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String id = normalizeId(file.getName().substring(0, file.getName().length() - 4));
                boosters.put(id, YamlConfiguration.loadConfiguration(file));
                boosterFiles.put(id, file);
            }
        }
        if (boosters.isEmpty()) {
            createBooster("basic");
            createBooster("strong");
            setBoosterValue("strong", "multiplier", 3D);
            setBoosterValue("strong", "duration-seconds", 1800L);
        }
    }

    private void saveBundledBooster(String name) {
        try {
            ResourceFiles.saveDefault(plugin, "boosters/" + name);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Could not save bundled booster " + name + ": " + exception.getMessage());
        }
    }

    private void saveBooster(String id) {
        YamlConfiguration file = boosters.get(normalizeId(id));
        File target = boosterFiles.get(normalizeId(id));
        if (file == null || target == null) {
            return;
        }
        try {
            file.save(target);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save booster " + id + ": " + exception.getMessage());
        }
    }

    public String normalizeId(String id) {
        return id == null ? "" : id.toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private void ensureConfigDefaults() {
        FileConfiguration config = config();
        boolean changed = false;
        boolean missingNativeDialogDefaults = !config.contains(NativeDialogConfigDefaults.ENABLED_PATH)
                || !config.contains(NativeDialogConfigDefaults.WARN_ON_FALLBACK_PATH);
        NativeDialogConfigDefaults.addDefaults(config);
        if (!config.contains("settings.team-boosters.stack-with-other-boosters")) {
            config.set("settings.team-boosters.stack-with-other-boosters", true);
            changed = true;
        }
        changed |= missingNativeDialogDefaults;
        if (changed) {
            plugin.saveConfig();
        }
    }

    private void refreshCachedSettings() {
        FileConfiguration config = config();
        EnumSet<EventFamily> events = EnumSet.noneOf(EventFamily.class);
        for (EventFamily family : EventFamily.values()) {
            if (config.getBoolean("events." + family.configKey(), family != EventFamily.CRAFTING)) {
                events.add(family);
            }
        }
        enabledEvents = events;

        Set<String> worlds = ConfigValueLists.lowerStrings(config, "disabled-worlds");
        disabledWorlds = worlds.isEmpty() ? Set.of() : Set.copyOf(worlds);

        numberFormat = config.getString("settings.number-format", "#,##0.##");
        maxMultipliedAmount = Math.max(1, config.getInt("settings.max-multiplied-amount", 4096));
        stackBoosters = config.getBoolean("settings.stack-boosters", false);
        teamBoostersStackWithOtherBoosters = config.getBoolean("settings.team-boosters.stack-with-other-boosters", true);
        teamMultiplierEnabled = config.getBoolean("settings.team-multiplier.enabled", true);
        teamMultiplierPerOnlineMember = Math.max(0D, config.getDouble("settings.team-multiplier.per-online-member", 0.05D));
        teamMultiplierCap = Math.max(1D, config.getDouble("settings.team-multiplier.cap", 2D));
        bossbarEnabled = config.getBoolean("bossbar.enabled", true);
        bossbarUpdateTicks = Math.max(5L, config.getLong("bossbar.update-ticks", 20L));
        globalBossbarColor = barColor(config.getString("bossbar.color", "GREEN"));
        globalBossbarStyle = barStyle(config.getString("bossbar.style", "SEGMENTED_10"));
        globalBossbarTitle = config.getString("bossbar.title", "#03fc88{multiplier}x #a7b8b0ᴅʀᴏᴘ ʙᴏᴏsᴛᴇʀ &8- #ffffff{time}");
        levelXpBossbarTitle = config.getString("bossbar.level-xp-title", "#03fc88{multiplier_parts} #a7b8b0ʟᴇᴠᴇʟ xᴘ ʙᴏᴏsᴛᴇʀ &8- #ffffff{time}");
        teamBossbarTitle = config.getString("bossbar.team-title", "#03fc88{team_multiplier}x #a7b8b0ᴛᴇᴀᴍ ᴅʀᴏᴘ ʙᴏᴏsᴛ");
        teamLevelXpBossbarTitle = config.getString("bossbar.team-level-xp-title", "#03fc88{team_multiplier}x #a7b8b0ᴛᴇᴀᴍ ʟᴇᴠᴇʟ xᴘ ʙᴏᴏsᴛ");
    }
}
