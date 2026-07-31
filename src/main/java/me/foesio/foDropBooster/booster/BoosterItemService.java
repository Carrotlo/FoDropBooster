package me.foesio.foDropBooster.booster;

import me.foesio.core.text.FoText;
import me.foesio.foDropBooster.config.ConfigManager;
import me.foesio.foDropBooster.util.FormatUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class BoosterItemService {
    private final ConfigManager configManager;
    private final NamespacedKey boosterIdKey;
    private final NamespacedKey targetKey;
    private final NamespacedKey typeKey;
    private final NamespacedKey multiplierKey;
    private final NamespacedKey durationKey;
    private final NamespacedKey worldKey;
    private final NamespacedKey eventsKey;
    private final NamespacedKey bossbarTitleKey;
    private final NamespacedKey bossbarColorKey;
    private final NamespacedKey bossbarStyleKey;

    public BoosterItemService(JavaPlugin plugin, ConfigManager configManager) {
        this.configManager = configManager;
        boosterIdKey = new NamespacedKey(plugin, "booster_id");
        targetKey = new NamespacedKey(plugin, "target");
        typeKey = new NamespacedKey(plugin, "type");
        multiplierKey = new NamespacedKey(plugin, "multiplier");
        durationKey = new NamespacedKey(plugin, "duration_seconds");
        worldKey = new NamespacedKey(plugin, "world");
        eventsKey = new NamespacedKey(plugin, "events");
        bossbarTitleKey = new NamespacedKey(plugin, "bossbar_title");
        bossbarColorKey = new NamespacedKey(plugin, "bossbar_color");
        bossbarStyleKey = new NamespacedKey(plugin, "bossbar_style");
    }

    public ItemStack createBooster(String id) {
        BoosterProfile profile = configManager.profile(id);
        if (profile == null) {
            profile = configManager.firstProfile();
        }
        if (profile == null) {
            return new ItemStack(Material.CLOCK);
        }
        return createItem(
                profile.id(),
                profile.target(),
                profile.type(),
                profile.multiplier(),
                profile.durationSeconds(),
                profile.worldName(),
                profile.enabledEvents(),
                profile.material(),
                profile.glow(),
                profile.customModelData(),
                profile.name(),
                profile.lore(),
                profile.bossbarTitle(),
                profile.bossbarColor().name(),
                profile.bossbarStyle().name()
        );
    }

    public ItemStack createCustom(BoosterTarget target, BoosterType type, double multiplier, long durationSeconds, String worldName) {
        return createItem(
                "custom",
                target,
                type,
                multiplier,
                durationSeconds,
                worldName,
                configManager.defaultBoosterEvents(),
                configManager.customMaterial(),
                configManager.customGlow(),
                configManager.customModelData(),
                configManager.customName(),
                configManager.customLore(),
                configManager.bossbarTitle(target),
                configManager.globalBossbarColor().name(),
                configManager.globalBossbarStyle().name()
        );
    }

    public Optional<BoosterItemData> read(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return Optional.empty();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String id = pdc.get(boosterIdKey, PersistentDataType.STRING);
        String targetRaw = pdc.get(targetKey, PersistentDataType.STRING);
        String typeRaw = pdc.get(typeKey, PersistentDataType.STRING);
        Double multiplier = pdc.get(multiplierKey, PersistentDataType.DOUBLE);
        Long duration = pdc.get(durationKey, PersistentDataType.LONG);
        if (id == null || typeRaw == null || multiplier == null || duration == null) {
            return Optional.empty();
        }

        String eventsRaw = pdc.get(eventsKey, PersistentDataType.STRING);
        Set<EventFamily> events = eventsRaw == null || eventsRaw.isBlank()
                ? configManager.defaultBoosterEvents()
                : configManager.enabledEvents(List.of(eventsRaw.split(",")));
        return Optional.of(new BoosterItemData(
                id,
                BoosterTarget.from(targetRaw),
                BoosterType.from(typeRaw),
                Math.max(1D, multiplier),
                Math.max(1L, duration),
                defaultString(pdc.get(worldKey, PersistentDataType.STRING)),
                events,
                defaultString(pdc.get(bossbarTitleKey, PersistentDataType.STRING)),
                configManager.barColor(pdc.get(bossbarColorKey, PersistentDataType.STRING)),
                configManager.barStyle(pdc.get(bossbarStyleKey, PersistentDataType.STRING))
        ));
    }

    public boolean isBoosterItem(ItemStack item) {
        return read(item).isPresent();
    }

    private ItemStack createItem(
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
            List<String> lore,
            String bossbarTitle,
            String bossbarColor,
            String bossbarStyle
    ) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        Map<String, String> replacements = replacements(target, type, multiplier, durationSeconds, worldName);
        meta.setDisplayName(FoText.color(replace(name, replacements)));

        List<String> renderedLore = new ArrayList<>();
        for (String line : lore) {
            renderedLore.add(replace(line, replacements));
        }
        meta.setLore(FoText.color(renderedLore));

        if (glow) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(boosterIdKey, PersistentDataType.STRING, id);
        pdc.set(targetKey, PersistentDataType.STRING, target.name());
        pdc.set(typeKey, PersistentDataType.STRING, type.name());
        pdc.set(multiplierKey, PersistentDataType.DOUBLE, multiplier);
        pdc.set(durationKey, PersistentDataType.LONG, durationSeconds);
        pdc.set(worldKey, PersistentDataType.STRING, worldName == null ? "" : worldName);
        pdc.set(eventsKey, PersistentDataType.STRING, serializeEvents(enabledEvents));
        pdc.set(bossbarTitleKey, PersistentDataType.STRING, bossbarTitle == null ? "" : bossbarTitle);
        pdc.set(bossbarColorKey, PersistentDataType.STRING, bossbarColor == null ? "GREEN" : bossbarColor);
        pdc.set(bossbarStyleKey, PersistentDataType.STRING, bossbarStyle == null ? "SEGMENTED_10" : bossbarStyle);
        item.setItemMeta(meta);
        return item;
    }

    private Map<String, String> replacements(BoosterTarget target, BoosterType type, double multiplier, long durationSeconds, String worldName) {
        String scope = scope(type, worldName);
        String world = type == BoosterType.GLOBAL ? "server wide" : scope;
        return Map.of(
                "{target}", target.display(),
                "{type}", type.display(),
                "{scope}", scope,
                "{multiplier}", FormatUtil.multiplier(multiplier, configManager.numberFormat()),
                "{duration}", FormatUtil.duration(durationSeconds),
                "{world}", world
        );
    }

    private String scope(BoosterType type, String worldName) {
        if (type == BoosterType.GLOBAL) {
            return "server wide";
        }
        if (type == BoosterType.TEAM) {
            return "team wide";
        }
        if (type == BoosterType.WORLD) {
            return worldName == null || worldName.isBlank() ? "current world on use" : worldName;
        }
        return "personal";
    }

    private String replace(String input, Map<String, String> replacements) {
        String output = input == null ? "" : input;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            output = output.replace(entry.getKey(), entry.getValue());
        }
        return output;
    }

    private String defaultString(String input) {
        return input == null ? "" : input;
    }

    private String serializeEvents(Set<EventFamily> events) {
        if (events == null || events.isEmpty()) {
            return "none";
        }
        return events.stream().map(EventFamily::configKey).collect(Collectors.joining(","));
    }
}
