package me.foesio.foDropBooster.hook;

import me.foesio.foDropBooster.booster.EventFamily;
import me.foesio.foDropBooster.listener.DropBoostListener;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

public final class FoDropsRewardBridge implements Listener {
    private static final String EVENT_CLASS_NAME = "me.foesio.foDrops.api.FoDropRewardEvent";

    private final JavaPlugin plugin;
    private final DropBoostListener dropBoostListener;
    private MethodHandle getPlayer;
    private MethodHandle getItemStack;
    private MethodHandle setItemStack;
    private MethodHandle getEventType;

    public FoDropsRewardBridge(JavaPlugin plugin, DropBoostListener dropBoostListener) {
        this.plugin = plugin;
        this.dropBoostListener = dropBoostListener;
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
            getItemStack = lookup.unreflect(rawEventClass.getMethod("getItemStack"));
            setItemStack = lookup.unreflect(rawEventClass.getMethod("setItemStack", ItemStack.class));
            getEventType = lookup.unreflect(rawEventClass.getMethod("getEventType"));

            Class<? extends Event> eventClass = (Class<? extends Event>) rawEventClass;
            EventExecutor executor = (listener, event) -> handleRewardEvent(event);
            plugin.getServer().getPluginManager().registerEvent(eventClass, this, EventPriority.HIGH, executor, plugin, true);
            plugin.getLogger().info("Hooked into FoDrops reward events.");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("FoDrops reward integration is unavailable: " + exception.getMessage());
            return false;
        }
    }

    private void handleRewardEvent(Event event) {
        try {
            Player player = (Player) getPlayer.invoke(event);
            if (player == null || !player.isOnline()) {
                return;
            }
            EventFamily family = EventFamily.fromFoDrops(String.valueOf(getEventType.invoke(event)));
            if (family == null) {
                return;
            }
            ItemStack itemStack = (ItemStack) getItemStack.invoke(event);
            ItemStack boosted = dropBoostListener.boostedCopy(player, family, itemStack);
            if (boosted != itemStack) {
                setItemStack.invoke(event, boosted);
            }
        } catch (Throwable exception) {
            rethrowFatal(exception);
            plugin.getLogger().warning("Failed to handle FoDrops reward event: " + exception.getMessage());
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
