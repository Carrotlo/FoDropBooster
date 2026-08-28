package me.foesio.foDropBooster.command;

import me.foesio.core.command.FoAdminArguments;
import me.foesio.core.inventory.InventoryDepositService;
import me.foesio.core.inventory.OverflowPolicy;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.number.LargeNumberParser;
import me.foesio.core.sound.FoAdminSounds;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.foDropBooster.FoDropBooster;
import me.foesio.foDropBooster.booster.ActiveBoosterService;
import me.foesio.foDropBooster.booster.BoosterItemService;
import me.foesio.foDropBooster.booster.BoosterTarget;
import me.foesio.foDropBooster.booster.BoosterType;
import me.foesio.foDropBooster.config.ConfigManager;
import me.foesio.foDropBooster.gui.EditorManager;
import me.foesio.foDropBooster.util.FormatUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.logging.Level;

public final class FoDropBoosterAdminCommand implements CommandExecutor, TabCompleter {
    private final FoDropBooster plugin;
    private final ConfigManager configManager;
    private final FoMessageService messages;
    private final FoAdminSounds adminSounds;
    private final BoosterItemService boosterItemService;
    private final ActiveBoosterService activeBoosterService;
    private final EditorManager editorManager;
    private final UpdateNoticeService updateNotices;
    private final InventoryDepositService inventoryDeposits;

    public FoDropBoosterAdminCommand(
            FoDropBooster plugin,
            ConfigManager configManager,
            FoMessageService messages,
            FoAdminSounds adminSounds,
            BoosterItemService boosterItemService,
            ActiveBoosterService activeBoosterService,
            EditorManager editorManager,
            UpdateNoticeService updateNotices,
            InventoryDepositService inventoryDeposits
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messages = messages;
        this.adminSounds = adminSounds;
        this.boosterItemService = boosterItemService;
        this.activeBoosterService = activeBoosterService;
        this.editorManager = editorManager;
        this.updateNotices = updateNotices;
        this.inventoryDeposits = inventoryDeposits;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("fodropbooster.admin")) {
            messages.send(sender, "messages.no-permission");
            adminSounds.updateError(sender);
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "version" -> version(sender);
            case "reload" -> reload(sender);
            case "editor" -> editor(sender);
            case "give" -> give(sender, args);
            case "removeactive" -> removeActive(sender, args);
            default -> {
                adminSounds.updateError(sender);
                help(sender);
            }
        }
        return true;
    }

    private void version(CommandSender sender) {
        updateNotices.checkAndSendVersion(sender);
    }

    private void reload(CommandSender sender) {
        try {
            plugin.reloadFiles();
            messages.send(sender, "messages.reload");
            if (sender instanceof Player player) {
                adminSounds.reload(player);
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to reload FoDropBooster.", exception);
            messages.send(sender, "messages.reload-failed", "{prefix}{bad}Reload failed: {error}", Map.of("error", reloadError(exception)));
            if (sender instanceof Player player) {
                adminSounds.reloadError(player);
            }
        }
    }

    private String reloadError(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private void editor(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "messages.player-only");
            return;
        }
        editorManager.openMain(player);
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 4) {
            error(sender, "messages.usage");
            return;
        }
        Player target = FoAdminArguments.onlinePlayer().parse(args[1]).orElse(null);
        if (target == null) {
            error(sender, "messages.invalid-player");
            return;
        }

        String mode = args[2].toLowerCase(Locale.ROOT);
        ItemStack item;
        int amount;
        if (mode.equals("preset")) {
            String boosterId = configManager.normalizeId(args[3]);
            if (!configManager.hasBooster(boosterId)) {
                error(sender, "messages.booster-missing");
                return;
            }
            if (configManager.profile(boosterId).target().isFoLevels() && !foLevelsAvailable()) {
                error(sender, "messages.folevels-required");
                return;
            }
            amount = parseAmount(args, 4);
            if (amount < 1) {
                error(sender, "messages.invalid-number");
                return;
            }
            item = boosterItemService.createBooster(boosterId);
        } else if (mode.equals("custom")) {
            CustomGive custom = parseCustom(sender, args);
            if (custom == null) {
                return;
            }
            amount = custom.amount();
            item = boosterItemService.createCustom(custom.target(), custom.type(), custom.multiplier(), custom.durationSeconds(), custom.worldName());
        } else {
            error(sender, "messages.usage");
            return;
        }

        if (amount < 1 || amount > 2304) {
            error(sender, "messages.invalid-number");
            return;
        }
        for (int i = 0; i < amount; i++) {
            inventoryDeposits.deposit(target, item, target.getLocation(), OverflowPolicy.DROP_OVERFLOW, false);
        }
        messages.send(sender, "messages.give", Map.of("amount", Integer.toString(amount), "player", target.getName()));
        if (!sender.equals(target)) {
            messages.send(target, "messages.received", Map.of("amount", Integer.toString(amount)));
        }
    }

    private CustomGive parseCustom(CommandSender sender, String[] args) {
        if (args.length < 6) {
            error(sender, "messages.usage");
            return null;
        }
        BoosterTarget target = BoosterTarget.DROPS;
        int typeIndex = 3;
        if (isTargetToken(args[3])) {
            target = BoosterTarget.from(args[3]);
            typeIndex = 4;
        }
        if (args.length <= typeIndex + 2) {
            error(sender, "messages.usage");
            return null;
        }
        if (target.isFoLevels() && !foLevelsAvailable()) {
            error(sender, "messages.folevels-required");
            return null;
        }
        BoosterType type = switch (args[typeIndex].toLowerCase(Locale.ROOT)) {
            case "personal" -> BoosterType.PERSONAL;
            case "team", "foteam", "foteams" -> BoosterType.TEAM;
            case "global" -> BoosterType.GLOBAL;
            case "world", "per-world", "perworld" -> BoosterType.WORLD;
            default -> null;
        };
        if (type == null) {
            error(sender, "messages.invalid-type");
            return null;
        }

        OptionalDouble multiplier = LargeNumberParser.parseDouble(args[typeIndex + 1]);
        if (multiplier.isEmpty() || multiplier.getAsDouble() <= 1D) {
            error(sender, "messages.invalid-number");
            return null;
        }
        Long duration = FormatUtil.parseDurationSeconds(args[typeIndex + 2]);
        if (duration == null) {
            error(sender, "messages.invalid-duration");
            return null;
        }

        String worldName = "";
        int amountIndex = typeIndex + 3;
        if (type == BoosterType.WORLD && args.length > amountIndex && FormatUtil.parseInteger(args[amountIndex]) == null) {
            if (args[amountIndex].equalsIgnoreCase("current")) {
                worldName = "";
            } else {
                World world = Bukkit.getWorld(args[amountIndex]);
                if (world == null) {
                    error(sender, "messages.invalid-world");
                    return null;
                }
                worldName = world.getName();
            }
            amountIndex++;
        }
        int amount = parseAmount(args, amountIndex);
        if (amount < 1) {
            error(sender, "messages.invalid-number");
            return null;
        }
        return new CustomGive(target, type, multiplier.getAsDouble(), duration, worldName, amount);
    }

    private void removeActive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            error(sender, "messages.usage");
            return;
        }
        String scope = args[1].toLowerCase(Locale.ROOT);
        String target = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        int removed = switch (scope) {
            case "personal" -> activeBoosterService.removePersonal(target);
            case "team" -> activeBoosterService.removeTeam(target);
            case "world" -> activeBoosterService.removeWorld(target);
            case "global" -> activeBoosterService.removeGlobal(target);
            case "id" -> activeBoosterService.removeById(target);
            default -> -1;
        };
        if (removed < 0) {
            error(sender, "messages.usage");
            return;
        }
        if (removed == 0) {
            error(sender, "messages.no-active");
            return;
        }
        messages.send(sender, "messages.removed-active", Map.of("amount", Integer.toString(removed)));
    }

    private void help(CommandSender sender) {
        messages.sendList(sender, "messages.help", List.of(
                "{prefix}{theme}/dbadmin editor {muted}- Open the editor.",
                "{prefix}{theme}/dbadmin reload {muted}- Reload files.",
                "{prefix}{theme}/dbadmin version {muted}- Check version.",
                "{prefix}{theme}/dbadmin give <player> preset <id> [amount] {muted}- Give a preset booster.",
                "{prefix}{theme}/dbadmin give <player> custom [drop|xp] <personal|team|global|world> <multiplier> <duration> [world|current] [amount] {muted}- Give a custom booster.",
                "{prefix}{theme}/dbadmin removeactive <personal|team|world|global|id> <target> {muted}- Remove active boosters."
        ), Map.of());
    }

    private void error(CommandSender sender, String key) {
        messages.send(sender, key);
        adminSounds.updateError(sender);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("fodropbooster.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return FoAdminArguments.completeOptions(List.of("version", "reload", "editor", "give", "removeactive"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return FoAdminArguments.onlinePlayer().complete(args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return FoAdminArguments.completeOptions(List.of("preset", "custom"), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give") && args[2].equalsIgnoreCase("preset")) {
            return FoAdminArguments.completeOptions(new ArrayList<>(configManager.boosterIds()), args[3]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give") && args[2].equalsIgnoreCase("custom")) {
            return FoAdminArguments.completeOptions(List.of("drop", "xp", "personal", "team", "global", "world"), args[3]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("give") && args[2].equalsIgnoreCase("custom") && isTargetToken(args[3])) {
            return FoAdminArguments.completeOptions(List.of("personal", "team", "global", "world"), args[4]);
        }
        if (isCustomMultiplierArg(args, 5)) {
            return FoAdminArguments.completeOptions(List.of("<multiplier>", "1.5", "2", "3"), args[4]);
        }
        if (isCustomMultiplierArg(args, 6)) {
            return FoAdminArguments.completeOptions(List.of("<multiplier>", "1.5", "2", "3"), args[5]);
        }
        if (isCustomDurationArg(args, 6)) {
            return FoAdminArguments.completeOptions(List.of("<duration>", "10m", "15m", "30m", "1h"), args[5]);
        }
        if (isCustomDurationArg(args, 7)) {
            return FoAdminArguments.completeOptions(List.of("<duration>", "10m", "15m", "30m", "1h"), args[6]);
        }
        if (args.length == 7 && args[0].equalsIgnoreCase("give") && args[2].equalsIgnoreCase("custom") && args[3].equalsIgnoreCase("world")) {
            List<String> options = new ArrayList<>(List.of("<world|current|amount>", "current"));
            options.addAll(Bukkit.getWorlds().stream().map(World::getName).toList());
            options.addAll(List.of("<amount>", "1", "8", "16", "32"));
            return FoAdminArguments.completeOptions(options, args[6]);
        }
        if (args.length == 8 && args[0].equalsIgnoreCase("give") && args[2].equalsIgnoreCase("custom") && isTargetToken(args[3]) && args[4].equalsIgnoreCase("world")) {
            List<String> options = new ArrayList<>(List.of("<world|current|amount>", "current"));
            options.addAll(Bukkit.getWorlds().stream().map(World::getName).toList());
            options.addAll(List.of("<amount>", "1", "8", "16", "32"));
            return FoAdminArguments.completeOptions(options, args[7]);
        }
        if (args.length == 7 && args[0].equalsIgnoreCase("give") && args[2].equalsIgnoreCase("custom")) {
            return FoAdminArguments.completeOptions(List.of("<amount>", "1", "8", "16", "32"), args[6]);
        }
        if (args.length == 8 && args[0].equalsIgnoreCase("give") && args[2].equalsIgnoreCase("custom") && args[3].equalsIgnoreCase("world")) {
            return FoAdminArguments.completeOptions(List.of("<amount>", "1", "8", "16", "32"), args[7]);
        }
        if (args.length == 8 && args[0].equalsIgnoreCase("give") && args[2].equalsIgnoreCase("custom") && isTargetToken(args[3])) {
            return FoAdminArguments.completeOptions(List.of("<amount>", "1", "8", "16", "32"), args[7]);
        }
        if (args.length == 9 && args[0].equalsIgnoreCase("give") && args[2].equalsIgnoreCase("custom") && isTargetToken(args[3]) && args[4].equalsIgnoreCase("world")) {
            return FoAdminArguments.completeOptions(List.of("<amount>", "1", "8", "16", "32"), args[8]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("give") && args[2].equalsIgnoreCase("preset")) {
            return FoAdminArguments.completeOptions(List.of("<amount>", "1", "8", "16", "32"), args[4]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("removeactive")) {
            return FoAdminArguments.completeOptions(List.of("personal", "team", "world", "global", "id"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("removeactive")) {
            return FoAdminArguments.completeOptions(removeTargets(args[1]), args[2]);
        }
        return List.of();
    }

    private List<String> removeTargets(String scope) {
        List<String> options = new ArrayList<>();
        options.add("all");
        switch (scope.toLowerCase(Locale.ROOT)) {
            case "personal" -> {
                options.addAll(activeBoosterService.activeBoosters().stream()
                        .filter(booster -> booster.type() == BoosterType.PERSONAL)
                        .map(booster -> booster.ownerName().isBlank() ? booster.id().substring(0, 8) : booster.ownerName())
                        .distinct()
                        .toList());
            }
            case "team" -> {
                for (var booster : activeBoosterService.activeBoosters()) {
                    if (booster.type() != BoosterType.TEAM) {
                        continue;
                    }
                    if (!booster.teamName().isBlank()) {
                        options.add(booster.teamName());
                    }
                    if (!booster.teamId().isBlank()) {
                        options.add(booster.teamId());
                    }
                    options.add(booster.id().substring(0, Math.min(8, booster.id().length())));
                }
            }
            case "world" -> options.addAll(Bukkit.getWorlds().stream().map(World::getName).toList());
            case "global", "id" -> options.addAll(activeBoosterService.activeBoosters().stream()
                    .map(booster -> booster.id().substring(0, Math.min(8, booster.id().length())))
                    .toList());
            default -> {
            }
        }
        return options;
    }

    private int parseAmount(String[] args, int index) {
        if (args.length <= index) {
            return 1;
        }
        Integer parsed = FormatUtil.parseInteger(args[index]);
        return parsed == null ? -1 : parsed;
    }

    private boolean isTargetToken(String input) {
        if (input == null) {
            return false;
        }
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "drop", "drops", "xp", "level", "levels", "level-xp", "levelxp", "folevels", "folevels-xp", "folevels_xp" -> true;
            default -> false;
        };
    }

    private boolean isCustomMultiplierArg(String[] args, int length) {
        if (args.length != length || !args[0].equalsIgnoreCase("give") || !args[2].equalsIgnoreCase("custom")) {
            return false;
        }
        return length == 5 || isTargetToken(args[3]);
    }

    private boolean isCustomDurationArg(String[] args, int length) {
        if (args.length != length || !args[0].equalsIgnoreCase("give") || !args[2].equalsIgnoreCase("custom")) {
            return false;
        }
        return length == 6 || isTargetToken(args[3]);
    }

    private boolean foLevelsAvailable() {
        return plugin.getServer().getPluginManager().isPluginEnabled("FoLevels");
    }

    private record CustomGive(BoosterTarget target, BoosterType type, double multiplier, long durationSeconds, String worldName, int amount) {
    }
}
