package me.foesio.foDropBooster.command;

import me.foesio.core.command.FoAdminArguments;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.sound.FoAdminSounds;
import me.foesio.foDropBooster.bossbar.BossbarService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public final class DropBoosterBossbarCommand implements CommandExecutor, TabCompleter {
    private final FoMessageService messages;
    private final BossbarService bossbarService;
    private final FoAdminSounds adminSounds;

    public DropBoosterBossbarCommand(FoMessageService messages, BossbarService bossbarService, FoAdminSounds adminSounds) {
        this.messages = messages;
        this.bossbarService = bossbarService;
        this.adminSounds = adminSounds;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("fodropbooster.togglebossbar")) {
            messages.send(sender, "messages.no-permission");
            adminSounds.updateError(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "messages.player-only");
            return true;
        }
        if (args.length > 1) {
            messages.send(sender, "messages.bossbar-usage");
            adminSounds.updateError(sender);
            return true;
        }

        boolean enabled;
        if (args.length == 0) {
            enabled = bossbarService.toggle(player);
        } else {
            var requested = FoAdminArguments.onOff().parse(args[0]);
            if (requested.isEmpty()) {
                messages.send(sender, "messages.bossbar-usage");
                adminSounds.updateError(sender);
                return true;
            }
            enabled = bossbarService.setEnabled(player, requested.get());
        }

        messages.send(sender, enabled ? "messages.bossbar-enabled" : "messages.bossbar-disabled");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("fodropbooster.togglebossbar")) {
            return List.of();
        }
        if (args.length != 1) {
            return List.of();
        }
        return FoAdminArguments.onOff().complete(args[0]);
    }
}
