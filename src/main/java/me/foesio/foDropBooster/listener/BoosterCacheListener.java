package me.foesio.foDropBooster.listener;

import me.foesio.foDropBooster.booster.ActiveBoosterService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;

public final class BoosterCacheListener implements Listener {
    private final ActiveBoosterService activeBoosterService;

    public BoosterCacheListener(ActiveBoosterService activeBoosterService) {
        this.activeBoosterService = activeBoosterService;
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        activeBoosterService.invalidate(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        activeBoosterService.invalidateAll();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        activeBoosterService.invalidateAll();
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        activeBoosterService.removeExternalBoosts(event.getPlugin().getName());
    }
}
