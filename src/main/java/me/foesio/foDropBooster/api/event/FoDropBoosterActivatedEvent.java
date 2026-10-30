package me.foesio.foDropBooster.api.event;

import me.foesio.foDropBooster.api.FoDropBoosterActivationSource;
import me.foesio.foDropBooster.api.FoDropBoosterSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;

/** Fired once after a timed booster starts; item activations have already consumed the item. */
public final class FoDropBoosterActivatedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final FoDropBoosterSnapshot booster;
    private final FoDropBoosterActivationSource source;
    private final Plugin caller;

    public FoDropBoosterActivatedEvent(Player player, FoDropBoosterSnapshot booster, FoDropBoosterActivationSource source, Plugin caller) {
        super(!Bukkit.isPrimaryThread());
        this.player = player;
        this.booster = booster;
        this.source = source;
        this.caller = caller;
    }

    public Player getPlayer() { return player; }
    public FoDropBoosterSnapshot getBooster() { return booster; }
    public FoDropBoosterActivationSource getSource() { return source; }
    /** Null for item use. */
    public Plugin getCaller() { return caller; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
