package me.foesio.foDropBooster.hook;

import me.foesio.foDropBooster.booster.ActiveBoosterService;
import me.foesio.foDropBooster.booster.EffectiveBoost;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

public final class FoLevelsXpBridge implements Listener {
    private static final String EVENT_CLASS_NAME = "me.foesio.foLevels.api.event.FoLevelsXpGainEvent";

    private final JavaPlugin plugin;
    private final ActiveBoosterService activeBoosterService;
    private MethodHandle getPlayer;
    private MethodHandle multiply;

    public FoLevelsXpBridge(JavaPlugin plugin, ActiveBoosterService activeBoosterService) {
        this.plugin = plugin;
        this.activeBoosterService = activeBoosterService;
    }

    public boolean available() {
        return plugin.getServer().getPluginManager().getPlugin("FoLevels") != null;
    }

    @SuppressWarnings("unchecked")
    public boolean register() {
        try {
            Class<?> rawEventClass = Class.forName(EVENT_CLASS_NAME);
            if (!Event.class.isAssignableFrom(rawEventClass)) {
                return false;
            }

            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            getPlayer = lookup.unreflect(rawEventClass.getMethod("getPlayer"));
            multiply = lookup.unreflect(rawEventClass.getMethod("multiply", double.class));

            Class<? extends Event> eventClass = (Class<? extends Event>) rawEventClass;
            EventExecutor executor = (listener, event) -> handleXpGain(event);
            plugin.getServer().getPluginManager().registerEvent(eventClass, this, EventPriority.HIGH, executor, plugin, true);
            plugin.getLogger().info("Hooked into FoLevels XP events.");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("FoLevels XP integration is unavailable: " + exception.getMessage());
            return false;
        }
    }

    private void handleXpGain(Event event) {
        try {
            Player player = (Player) getPlayer.invoke(event);
            if (player == null || !player.isOnline()) {
                return;
            }
            EffectiveBoost boost = activeBoosterService.effectiveLevelXp(player);
            if (!boost.active()) {
                return;
            }
            multiply.invoke(event, boost.multiplier());
        } catch (Throwable exception) {
            rethrowFatal(exception);
            plugin.getLogger().warning("Failed to handle FoLevels XP event: " + exception.getMessage());
        }
    }

    private void rethrowFatal(Throwable exception) {
        if (exception instanceof VirtualMachineError error) {
            throw error;
        }
        if (exception instanceof ThreadDeath threadDeath) {
            throw threadDeath;
        }
    }
}
