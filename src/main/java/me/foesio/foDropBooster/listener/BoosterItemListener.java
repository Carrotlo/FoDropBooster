package me.foesio.foDropBooster.listener;

import me.foesio.core.message.FoMessageService;
import me.foesio.core.sound.FoSoundService;
import me.foesio.foDropBooster.booster.ActivationResult;
import me.foesio.foDropBooster.booster.ActiveBooster;
import me.foesio.foDropBooster.booster.ActiveBoosterService;
import me.foesio.foDropBooster.booster.BoosterItemData;
import me.foesio.foDropBooster.booster.BoosterItemService;
import me.foesio.foDropBooster.booster.BoosterType;
import me.foesio.foDropBooster.bossbar.BossbarService;
import me.foesio.foDropBooster.config.ConfigManager;
import me.foesio.foDropBooster.util.FormatUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;

public final class BoosterItemListener implements Listener {
    private final ConfigManager configManager;
    private final FoMessageService messages;
    private final FoSoundService sounds;
    private final BoosterItemService boosterItemService;
    private final ActiveBoosterService activeBoosterService;
    private final BossbarService bossbarService;

    public BoosterItemListener(
            ConfigManager configManager,
            FoMessageService messages,
            FoSoundService sounds,
            BoosterItemService boosterItemService,
            ActiveBoosterService activeBoosterService,
            BossbarService bossbarService
    ) {
        this.configManager = configManager;
        this.messages = messages;
        this.sounds = sounds;
        this.boosterItemService = boosterItemService;
        this.activeBoosterService = activeBoosterService;
        this.bossbarService = bossbarService;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        Optional<BoosterItemData> data = boosterItemService.read(item);
        if (data.isEmpty()) {
            return;
        }

        event.setCancelled(true);
        ActivationResult result = activeBoosterService.activate(player, data.get());
        if (!result.success()) {
            messages.send(player, "messages." + result.messageKey(), activationFallback(result.messageKey()));
            sounds.play(player, "booster.activate-error");
            return;
        }

        consumeOne(player, item);
        ActiveBooster booster = result.booster();
        sounds.playWithPitchVariation(player, "booster.activate", 0.05f);
        Map<String, String> replacements = replacements(player, booster);
        messages.send(player, "messages.activated", replacements);
        if (booster.type() == BoosterType.GLOBAL && configManager.config().getBoolean("broadcast.global", true)) {
            messages.broadcastConfigured("messages.activated-global", replacements);
        } else if (booster.type() == BoosterType.WORLD && configManager.config().getBoolean("broadcast.world", true)) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getWorld().getName().equalsIgnoreCase(booster.worldName())) {
                    messages.send(
                            online,
                            "messages.activated-world",
                            "{prefix}:experience_bottle: {theme}{player} {muted}activated a {theme}{multiplier}x {muted}{target} booster in {theme}{world} {muted}for {theme}{duration}{muted}.",
                            replacements
                    );
                }
            }
        }
        bossbarService.refreshNow();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        bossbarService.clearSession(event.getPlayer());
    }

    private Map<String, String> replacements(Player player, ActiveBooster booster) {
        return Map.of(
                "player", player.getName(),
                "target", booster.target().display(),
                "type", booster.type().display(),
                "team", booster.teamName() == null || booster.teamName().isBlank() ? "team" : booster.teamName(),
                "world", booster.worldName() == null || booster.worldName().isBlank() ? player.getWorld().getName() : booster.worldName(),
                "multiplier", activeBoosterService.formatMultiplier(booster.multiplier()),
                "duration", FormatUtil.duration(booster.durationMillis() / 1000L)
        );
    }

    private String activationFallback(String key) {
        return switch (key) {
            case "folevels-required" -> "{prefix}{bad}FoLevels is required for the leveling booster system.";
            case "disabled-world" -> "{prefix}{bad}Boosters are disabled in this world.";
            case "teams-unavailable" -> "{prefix}{bad}Team boosters require FoTeams.";
            case "no-team" -> "{prefix}{bad}You must be in a team to activate this booster.";
            case "active-exists" -> "{prefix}{bad}A conflicting booster is already active.";
            default -> "{prefix}{bad}Could not activate that booster.";
        };
    }

    private void consumeOne(Player player, ItemStack item) {
        if (item.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            return;
        }
        item.setAmount(item.getAmount() - 1);
        player.getInventory().setItemInMainHand(item);
    }
}
