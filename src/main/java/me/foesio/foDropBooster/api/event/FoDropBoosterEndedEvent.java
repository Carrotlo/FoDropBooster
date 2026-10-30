package me.foesio.foDropBooster.api.event;

import me.foesio.foDropBooster.api.FoDropBoosterEndReason;
import me.foesio.foDropBooster.api.FoDropBoosterSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired once after a stored timed booster expires or is removed by an admin/editor. */
public final class FoDropBoosterEndedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final FoDropBoosterSnapshot booster;
    private final FoDropBoosterEndReason reason;

    public FoDropBoosterEndedEvent(FoDropBoosterSnapshot booster, FoDropBoosterEndReason reason) {
        super(!Bukkit.isPrimaryThread());
        this.booster = booster;
        this.reason = reason;
    }

    public FoDropBoosterSnapshot getBooster() { return booster; }
    public FoDropBoosterEndReason getReason() { return reason; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
