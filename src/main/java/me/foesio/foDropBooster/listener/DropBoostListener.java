package me.foesio.foDropBooster.listener;

import me.foesio.core.scheduler.FoScheduler;
import me.foesio.foDropBooster.booster.ActiveBoosterService;
import me.foesio.foDropBooster.booster.EffectiveBoost;
import me.foesio.foDropBooster.booster.EventFamily;
import me.foesio.foDropBooster.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class DropBoostListener implements Listener {
    private final FoScheduler scheduler;
    private final ConfigManager configManager;
    private final ActiveBoosterService activeBoosterService;
    private final Map<UUID, ShearContext> recentShears = new ConcurrentHashMap<>();
    private final Map<UUID, List<PickedUpItem>> recentMobPickups = new ConcurrentHashMap<>();
    private final List<ExplosionSource> recentExplosionSources = new ArrayList<>();
    private final List<ExplosionCollectContext> recentExplosionCollections = new ArrayList<>();

    public DropBoostListener(FoScheduler scheduler, ConfigManager configManager, ActiveBoosterService activeBoosterService) {
        this.scheduler = scheduler;
        this.configManager = configManager;
        this.activeBoosterService = activeBoosterService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        if (!configManager.isEventEnabled(EventFamily.BLOCK_BREAK)) {
            return;
        }
        if (isContainerBlock(event.getBlockState().getType(), event.getBlock())) {
            return;
        }
        Player player = event.getPlayer();
        EffectiveBoost boost = activeBoosterService.effective(player, EventFamily.BLOCK_BREAK);
        if (!boost.active()) {
            return;
        }
        for (Item item : event.getItems()) {
            if (item == null) {
                continue;
            }
            ItemStack stack = item.getItemStack();
            if (boostStack(stack, boost)) {
                item.setItemStack(stack);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!configManager.isEventEnabled(EventFamily.MOB_KILL)) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        List<ItemStack> protectedDrops = consumeRecentMobPickups(mob);
        if (mob instanceof org.bukkit.entity.ChestedHorse chestedHorse && chestedHorse.isCarryingChest()) {
            return;
        }
        Player player = event.getEntity().getKiller();
        if (player == null || event.getDrops().isEmpty()) {
            return;
        }
        multiplyDrops(player, EventFamily.MOB_KILL, event.getDrops(), protectedDrops);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMobPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Mob)) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return;
        }
        recentMobPickups.computeIfAbsent(event.getEntity().getUniqueId(), ignored -> new ArrayList<>())
                .add(new PickedUpItem(stack.clone(), System.currentTimeMillis() + 60_000L));
        cleanupMobPickups();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (!configManager.isEventEnabled(EventFamily.FISHING)) {
            return;
        }
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item item)) {
            return;
        }
        ItemStack stack = item.getItemStack();
        EffectiveBoost boost = activeBoosterService.effective(event.getPlayer(), EventFamily.FISHING);
        if (boostStack(stack, boost)) {
            item.setItemStack(stack);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void trackShearing(PlayerShearEntityEvent event) {
        if (!configManager.isEventEnabled(EventFamily.SHEARING)) {
            return;
        }
        recentShears.put(event.getEntity().getUniqueId(), new ShearContext(event.getPlayer().getUniqueId(), System.currentTimeMillis() + 2_000L));
        cleanupShears();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDropItem(EntityDropItemEvent event) {
        if (!configManager.isEventEnabled(EventFamily.SHEARING)) {
            return;
        }
        cleanupShears();
        ShearContext context = recentShears.get(event.getEntity().getUniqueId());
        if (context == null) {
            return;
        }
        Player player = Bukkit.getPlayer(context.playerId());
        if (player == null) {
            return;
        }
        ItemStack stack = event.getItemDrop().getItemStack();
        EffectiveBoost boost = activeBoosterService.effective(player, EventFamily.SHEARING);
        if (boostStack(stack, boost)) {
            event.getItemDrop().setItemStack(stack);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!configManager.isEventEnabled(EventFamily.CRAFTING)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player) || event.isShiftClick()) {
            return;
        }
        ItemStack stack = event.getCurrentItem();
        if (stack == null || stack.getType() == Material.AIR) {
            return;
        }
        EffectiveBoost boost = activeBoosterService.effective(player, EventFamily.CRAFTING);
        if (boostStack(stack, boost)) {
            int max = Math.max(1, stack.getMaxStackSize());
            if (stack.getAmount() > max) {
                stack.setAmount(max);
            }
            event.setCurrentItem(stack);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTntPrime(TNTPrimeEvent event) {
        if (!configManager.isEventEnabled(EventFamily.EXPLOSIONS)) {
            return;
        }
        Player player = resolveExplosionSourcePlayer(event.getPrimingEntity(), centered(event.getBlock().getLocation()));
        if (player == null && event.getPrimingBlock() != null) {
            player = findRecentExplosionSource(centered(event.getPrimingBlock().getLocation()));
        }
        if (player != null) {
            rememberExplosionSource(player, centered(event.getBlock().getLocation()), 30_000L);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (!configManager.isEventEnabled(EventFamily.EXPLOSIONS)) {
            return;
        }
        if (event.getPlayer() != null) {
            rememberExplosionSource(event.getPlayer(), centered(event.getBlock().getLocation()), 10_000L);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onExplosiveInteract(PlayerInteractEvent event) {
        if (!configManager.isEventEnabled(EventFamily.EXPLOSIONS)) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null
                || !isPlayerTriggeredExplosive(event.getClickedBlock().getType())) {
            return;
        }
        rememberExplosionSource(event.getPlayer(), centered(event.getClickedBlock().getLocation()), 10_000L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!configManager.isEventEnabled(EventFamily.EXPLOSIONS)) {
            return;
        }
        Player player = resolveExplosionSourcePlayer(event.getEntity(), event.getLocation());
        rememberExplosionCollection(player, event.getLocation(), event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!configManager.isEventEnabled(EventFamily.EXPLOSIONS)) {
            return;
        }
        Player player = findRecentExplosionSource(centered(event.getBlock().getLocation()));
        rememberExplosionCollection(player, centered(event.getBlock().getLocation()), event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosionItemSpawn(ItemSpawnEvent event) {
        if (!configManager.isEventEnabled(EventFamily.EXPLOSIONS)) {
            return;
        }
        cleanupExplosions();
        ExplosionCollectContext context = findExplosionCollection(event.getLocation());
        if (context == null) {
            return;
        }
        Player player = Bukkit.getPlayer(context.playerId());
        if (player == null) {
            return;
        }
        BlockKey spawnBlock = BlockKey.from(event.getLocation());
        if (context.entityDropBlocks().contains(spawnBlock)
                || context.containerBlocks().contains(spawnBlock)) {
            return;
        }
        ItemStack stack = event.getEntity().getItemStack();
        if (boostStack(player, EventFamily.EXPLOSIONS, stack)) {
            event.getEntity().setItemStack(stack);
        }
    }

    public ItemStack boostedCopy(Player player, EventFamily family, ItemStack original) {
        if (original == null || original.getType() == Material.AIR) {
            return original;
        }
        if (isContainerItem(original)) {
            return original;
        }
        if (family != null && !configManager.isEventEnabled(family)) {
            return original;
        }
        EffectiveBoost boost = activeBoosterService.effective(player, family);
        if (!boost.active()) {
            return original;
        }
        int amount = boostedAmount(original.getAmount(), boost.multiplier());
        if (amount == original.getAmount()) {
            return original;
        }
        ItemStack copy = original.clone();
        copy.setAmount(amount);
        return copy;
    }

    private boolean boostStack(Player player, EventFamily family, ItemStack stack) {
        return boostStack(stack, activeBoosterService.effective(player, family));
    }

    private boolean boostStack(ItemStack stack, EffectiveBoost boost) {
        if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
            return false;
        }
        if (isContainerItem(stack)) {
            return false;
        }
        if (!boost.active()) {
            return false;
        }
        int amount = boostedAmount(stack.getAmount(), boost.multiplier());
        if (amount == stack.getAmount()) {
            return false;
        }
        stack.setAmount(amount);
        return true;
    }

    private void multiplyDrops(Player player, EventFamily family, List<ItemStack> drops, List<ItemStack> protectedDrops) {
        EffectiveBoost boost = activeBoosterService.effective(player, family);
        if (!boost.active()) {
            return;
        }

        List<ItemStack> multiplied = new ArrayList<>();
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType() == Material.AIR || drop.getAmount() <= 0) {
                continue;
            }
            if (takeProtectedDrop(drop, protectedDrops)) {
                multiplied.add(drop);
                continue;
            }
            if (isContainerItem(drop)) {
                multiplied.add(drop);
                continue;
            }
            ItemStack copy = drop.clone();
            copy.setAmount(boostedAmount(copy.getAmount(), boost.multiplier()));
            addSplit(copy, multiplied);
        }
        drops.clear();
        drops.addAll(multiplied);
    }

    private boolean takeProtectedDrop(ItemStack drop, List<ItemStack> protectedDrops) {
        if (protectedDrops == null || protectedDrops.isEmpty()) {
            return false;
        }
        Iterator<ItemStack> iterator = protectedDrops.iterator();
        while (iterator.hasNext()) {
            ItemStack protectedDrop = iterator.next();
            if (protectedDrop != null
                    && protectedDrop.isSimilar(drop)
                    && protectedDrop.getAmount() == drop.getAmount()) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    private int boostedAmount(int baseAmount, double multiplier) {
        double raw = Math.max(0D, baseAmount * multiplier);
        int amount = (int) Math.floor(raw);
        double fraction = raw - amount;
        if (fraction > 0D && ThreadLocalRandom.current().nextDouble() < fraction) {
            amount++;
        }
        amount = Math.max(0, amount);
        return Math.min(configManager.maxMultipliedAmount(), amount);
    }

    private void addSplit(ItemStack stack, List<ItemStack> target) {
        int remaining = stack.getAmount();
        int max = Math.max(1, stack.getMaxStackSize());
        while (remaining > 0) {
            ItemStack chunk = stack.clone();
            chunk.setAmount(Math.min(max, remaining));
            remaining -= chunk.getAmount();
            target.add(chunk);
        }
    }

    private boolean isContainerItem(ItemStack stack) {
        if (isStorageMaterial(stack.getType())) {
            return true;
        }
        if (stack.hasItemMeta() && stack.getItemMeta() instanceof BlockStateMeta meta) {
            return meta.getBlockState() instanceof InventoryHolder;
        }
        return false;
    }

    private boolean isContainerBlock(Material material, Block block) {
        return isStorageMaterial(material) || (block != null && block.getState() instanceof InventoryHolder);
    }

    private boolean isStorageMaterial(Material material) {
        String name = material.name();
        if (name.endsWith("SHULKER_BOX")) {
            return true;
        }
        return switch (name) {
            case "CHEST",
                 "TRAPPED_CHEST",
                 "BARREL",
                 "FURNACE",
                 "BLAST_FURNACE",
                 "SMOKER",
                 "HOPPER",
                 "DISPENSER",
                 "DROPPER",
                 "BREWING_STAND",
                 "CRAFTER",
                 "DECORATED_POT",
                 "FLOWER_POT",
                 "CHISELED_BOOKSHELF",
                 "LECTERN",
                 "JUKEBOX" -> true;
            default -> false;
        };
    }

    private void rememberExplosionCollection(Player player, Location location, Collection<Block> blocks) {
        cleanupExplosions();
        if (player == null || location == null || blocks == null || blocks.isEmpty()) {
            return;
        }
        if (!activeBoosterService.effective(player, EventFamily.EXPLOSIONS).active()) {
            return;
        }
        Set<BlockKey> affectedBlocks = new HashSet<>();
        Set<BlockKey> entityDropBlocks = explosionEntityDropBlocks(location, blocks);
        Set<BlockKey> containerBlocks = new HashSet<>();
        for (Block block : blocks) {
            affectedBlocks.add(BlockKey.from(block.getLocation()));
            if (isContainerBlock(block.getType(), block)) {
                containerBlocks.add(BlockKey.from(block.getLocation()));
            }
        }
        ExplosionCollectContext context = new ExplosionCollectContext(
                player.getUniqueId(),
                affectedBlocks,
                entityDropBlocks,
                containerBlocks,
                System.currentTimeMillis() + 1_000L
        );
        recentExplosionCollections.add(context);
        scheduler.runGlobal(() -> recentExplosionCollections.remove(context));
    }

    private void cleanupShears() {
        long now = System.currentTimeMillis();
        recentShears.values().removeIf(context -> context.expiresAt() < now);
    }

    private void cleanupMobPickups() {
        long now = System.currentTimeMillis();
        recentMobPickups.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(pickup -> pickup.expiresAt() < now);
            return entry.getValue().isEmpty();
        });
    }

    private List<ItemStack> consumeRecentMobPickups(Mob mob) {
        cleanupMobPickups();
        List<PickedUpItem> pickups = recentMobPickups.remove(mob.getUniqueId());
        if (pickups == null || pickups.isEmpty()) {
            return List.of();
        }
        return pickups.stream().map(PickedUpItem::stack).map(ItemStack::clone).toList();
    }

    private void rememberExplosionSource(Player player, Location location, long ttlMillis) {
        if (player == null || location == null || location.getWorld() == null) {
            return;
        }
        cleanupExplosions();
        recentExplosionSources.add(new ExplosionSource(player.getUniqueId(), location.clone(), System.currentTimeMillis() + ttlMillis));
    }

    private Player resolveExplosionSourcePlayer(Entity entity, Location location) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof TNTPrimed tnt && tnt.getSource() != null) {
            Player player = resolveExplosionSourcePlayer(tnt.getSource(), tnt.getLocation());
            if (player != null) {
                rememberExplosionSource(player, tnt.getLocation(), 10_000L);
                return player;
            }
        }
        if (entity instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                rememberExplosionSource(player, projectile.getLocation(), 10_000L);
                return player;
            }
            if (shooter instanceof Entity shooterEntity) {
                return resolveExplosionSourcePlayer(shooterEntity, projectile.getLocation());
            }
        }
        return findRecentExplosionSource(location);
    }

    private Player findRecentExplosionSource(Location location) {
        cleanupExplosions();
        ExplosionSource source = findNearestExplosionSource(location);
        return source == null ? null : Bukkit.getPlayer(source.playerId());
    }

    private ExplosionSource findNearestExplosionSource(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        ExplosionSource nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (ExplosionSource source : recentExplosionSources) {
            if (!source.location().getWorld().equals(location.getWorld())) {
                continue;
            }
            double distance = source.location().distanceSquared(location);
            if (distance <= 144D && distance < nearestDistance) {
                nearest = source;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private ExplosionCollectContext findExplosionCollection(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        BlockKey spawnBlock = BlockKey.from(location);
        for (ExplosionCollectContext context : recentExplosionCollections) {
            if (context.affectedBlocks().contains(spawnBlock)) {
                return context;
            }
        }
        return null;
    }

    private Set<BlockKey> explosionEntityDropBlocks(Location location, Collection<Block> blocks) {
        Set<BlockKey> entityDropBlocks = new HashSet<>();
        if (location == null || location.getWorld() == null) {
            return entityDropBlocks;
        }

        double radius = 4D;
        for (Block block : blocks) {
            if (block == null || !block.getWorld().equals(location.getWorld())) {
                continue;
            }
            radius = Math.max(radius, Math.sqrt(location.distanceSquared(block.getLocation())) + 1.5D);
        }
        radius = Math.min(16D, radius);
        for (Entity entity : location.getWorld().getNearbyEntities(location, radius, radius, radius)) {
            if (entity instanceof Item || entity instanceof Player) {
                continue;
            }
            entityDropBlocks.add(BlockKey.from(entity.getLocation()));
        }
        return entityDropBlocks;
    }

    private void cleanupExplosions() {
        long now = System.currentTimeMillis();
        Iterator<ExplosionSource> sourceIterator = recentExplosionSources.iterator();
        while (sourceIterator.hasNext()) {
            if (sourceIterator.next().expiresAt() < now) {
                sourceIterator.remove();
            }
        }
        Iterator<ExplosionCollectContext> collectionIterator = recentExplosionCollections.iterator();
        while (collectionIterator.hasNext()) {
            if (collectionIterator.next().expiresAt() < now) {
                collectionIterator.remove();
            }
        }
    }

    private boolean isPlayerTriggeredExplosive(Material material) {
        String name = material.name();
        return name.endsWith("_BED") || name.equals("RESPAWN_ANCHOR");
    }

    private Location centered(Location location) {
        return location.clone().add(0.5D, 0.5D, 0.5D);
    }

    private record ShearContext(UUID playerId, long expiresAt) {
    }

    private record PickedUpItem(ItemStack stack, long expiresAt) {
    }

    private record ExplosionSource(UUID playerId, Location location, long expiresAt) {
    }

    private record ExplosionCollectContext(
            UUID playerId,
            Set<BlockKey> affectedBlocks,
            Set<BlockKey> entityDropBlocks,
            Set<BlockKey> containerBlocks,
            long expiresAt
    ) {
    }

    private record BlockKey(String worldName, int x, int y, int z) {
        private static BlockKey from(Location location) {
            return new BlockKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }
}
