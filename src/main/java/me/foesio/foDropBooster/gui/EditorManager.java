package me.foesio.foDropBooster.gui;

import me.foesio.core.FoCoreContext;
import me.foesio.core.dialog.DialogButton;
import me.foesio.core.dialog.DialogService;
import me.foesio.core.dialog.FallbackDialogService;
import me.foesio.core.dialog.NativeDialogAvailability;
import me.foesio.core.dialog.TextDialogRequest;
import me.foesio.core.editor.EditorDialogInputs;
import me.foesio.core.editor.EditorItemFactory;
import me.foesio.core.gui.EntryBrowserClick;
import me.foesio.core.gui.EntryBrowserHolder;
import me.foesio.core.gui.EntryBrowserMenus;
import me.foesio.core.gui.EntryBrowserRequest;
import me.foesio.core.gui.GuiButtonConfig;
import me.foesio.core.gui.GuiSlots;
import me.foesio.core.gui.GuiTitles;
import me.foesio.core.material.MaterialChooserActionType;
import me.foesio.core.material.MaterialChooserClick;
import me.foesio.core.material.MaterialChooserHolder;
import me.foesio.core.material.MaterialChooserMenus;
import me.foesio.core.material.MaterialChooserMode;
import me.foesio.core.material.MaterialChooserRequest;
import me.foesio.core.material.MaterialTypes;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.message.FoStyle;
import me.foesio.core.number.LargeNumberParser;
import me.foesio.core.selector.TriStateSelectionActionType;
import me.foesio.core.selector.TriStateSelectionClick;
import me.foesio.core.selector.TriStateSelectionEntry;
import me.foesio.core.selector.TriStateSelectionHolder;
import me.foesio.core.selector.TriStateSelectionMenus;
import me.foesio.core.selector.TriStateSelectionRequest;
import me.foesio.core.selector.TriStateSelectionState;
import me.foesio.core.selector.TriStateSelections;
import me.foesio.core.selector.WorldSelectionEntries;
import me.foesio.core.sound.FoEditorSounds;
import me.foesio.foDropBooster.booster.ActiveBooster;
import me.foesio.foDropBooster.booster.ActiveBoosterService;
import me.foesio.foDropBooster.booster.BoosterItemService;
import me.foesio.foDropBooster.booster.BoosterProfile;
import me.foesio.foDropBooster.booster.BoosterTarget;
import me.foesio.foDropBooster.booster.BoosterType;
import me.foesio.foDropBooster.booster.EventFamily;
import me.foesio.foDropBooster.config.ConfigManager;
import me.foesio.foDropBooster.util.FormatUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class EditorManager implements Listener {
    private static final int TEXT_MAX_LENGTH = 256;
    private static final int NUMBER_MAX_LENGTH = 32;
    private static final int DURATION_MAX_LENGTH = 32;
    private static final int CONFIRM_MAX_LENGTH = 16;
    private static final int DIALOG_BODY_WIDTH = 300;
    private static final int DIALOG_INPUT_WIDTH = 300;
    private static final int DIALOG_BUTTON_WIDTH = 100;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final FoMessageService messages;
    private final BoosterItemService boosterItemService;
    private final ActiveBoosterService activeBoosterService;
    private final GuiButtonConfig buttons = GuiButtonConfig.defaults();
    private FoEditorSounds editorSounds;
    private final Set<UUID> warnedDialogFallbackPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PendingPrompt> pendingPrompts = new ConcurrentHashMap<>();
    private final Map<UUID, BoosterTarget> worldSelectorTargets = new ConcurrentHashMap<>();
    private final Map<UUID, String> materialSelectorBoosters = new ConcurrentHashMap<>();
    private final Map<UUID, String> eventSelectorBoosters = new ConcurrentHashMap<>();
    private final Map<UUID, BoosterTarget> eventSelectorTargets = new ConcurrentHashMap<>();
    private FoCoreContext core;
    private DialogService dialogService;

    public EditorManager(
            JavaPlugin plugin,
            FoCoreContext core,
            FoEditorSounds editorSounds,
            ConfigManager configManager,
            FoMessageService messages,
            BoosterItemService boosterItemService,
            ActiveBoosterService activeBoosterService
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messages = messages;
        this.boosterItemService = boosterItemService;
        this.activeBoosterService = activeBoosterService;
        this.core = core;
        this.editorSounds = editorSounds;
        this.dialogService = createDialogService(core);
    }

    public void close() {
        warnedDialogFallbackPlayers.clear();
        pendingPrompts.clear();
        worldSelectorTargets.clear();
        materialSelectorBoosters.clear();
        eventSelectorBoosters.clear();
        eventSelectorTargets.clear();
    }

    public void reload(FoCoreContext core, FoEditorSounds editorSounds) {
        this.core = core;
        this.editorSounds = editorSounds;
        this.dialogService = createDialogService(core);
        warnedDialogFallbackPlayers.clear();
        pendingPrompts.clear();
        worldSelectorTargets.clear();
        materialSelectorBoosters.clear();
        eventSelectorBoosters.clear();
        eventSelectorTargets.clear();
    }

    public void openMain(Player player) {
        openMain(player, true);
    }

    private void openMain(Player player, boolean playSound) {
        MenuHolder holder = new MenuHolder(MenuType.SELECTOR, null);
        Inventory inventory = Bukkit.createInventory(holder, 27, title("editor", "FoDropBooster"));
        holder.inventory = inventory;
        fill(inventory);

        inventory.setItem(11, item(Material.CLOCK, "#03fc88Drop Boosters", List.of(
                "#ffffffBoost vanilla, FoDrops, and FoAutoCollect drops.",
                "#a7b8b0Click to manage drop boosters."
        )));

        boolean foLevels = foLevelsAvailable();
        inventory.setItem(15, item(foLevels ? Material.EXPERIENCE_BOTTLE : Material.BARRIER,
                (foLevels ? "#03fc88" : "#ff5d73") + "FoLevels XP Boosters",
                List.of(
                        "#ffffffBoost FoLevels XP gains.",
                        foLevels ? "#a7b8b0Click to manage level XP boosters." : "#ff5d73FoLevels is required for the leveling system."
        )));
        player.openInventory(inventory);
        if (playSound) {
            editorSounds.open(player);
        }
    }

    private void openTargetMain(Player player, BoosterTarget target) {
        MenuHolder holder = new MenuHolder(MenuType.MAIN, target.name());
        Inventory inventory = Bukkit.createInventory(holder, 27, title("editor", "FoDropBooster"));
        holder.inventory = inventory;
        fill(inventory);

        inventory.setItem(10, item(target.isFoLevels() ? Material.EXPERIENCE_BOTTLE : Material.CLOCK, "#03fc88Boosters", List.of(
                "#ffffffAdd, remove, and edit " + target.display() + " presets."
        )));
        inventory.setItem(11, item(Material.BEACON, "#03fc88Active Boosters", List.of("#ffffffView and remove active boosters.")));
        inventory.setItem(12, item(Material.GRASS_BLOCK, "#03fc88Disabled Worlds", List.of(
                "#ffffffCurrent: #03fc88" + disabledWorldsDisplay(),
                "#a7b8b0Click to manage worlds."
        )));
        inventory.setItem(13, toggleItem("Stack Boosters", configManager.stackBoosters(), "Combine applicable multipliers."));
        inventory.setItem(14, teamMultiplierItem());
        inventory.setItem(15, toggleItem("Team Booster Stacking", configManager.teamBoostersStackWithOtherBoosters(), "Timed team boosters stack with personal, world, and global boosters."));

        if (target.isFoLevels()) {
            inventory.setItem(16, item(Material.BOOK, "#03fc88FoLevels XP", List.of(
                    "#ffffffBoosts all FoLevels XP gain events.",
                    "#a7b8b0Requires FoLevels to be installed.",
                    "#a7b8b0There are no per-event toggles for XP boosters."
            )));
        } else {
            inventory.setItem(16, item(Material.HOPPER, "#03fc88Events", List.of(
                    "#ffffffCurrent: #03fc88" + eventList(globalEnabledEvents()),
                    "#a7b8b0Click to manage boosted events."
            )));
        }
        inventory.setItem(GuiSlots.bottomMiddleSlot(3), buttons.back());
        player.openInventory(inventory);
    }

    private void openBoosters(Player player, BoosterTarget target) {
        openBoosters(player, target, 0, "");
    }

    private void openBoosters(Player player, BoosterTarget target, int page, String filter) {
        List<EntryBrowserRequest.Entry> entries = new ArrayList<>();
        for (String id : configManager.boosterIds()) {
            BoosterProfile profile = configManager.profile(id);
            if (profile != null && profile.target() == target && matchesBoosterFilter(id, profile, filter)) {
                entries.add(EntryBrowserRequest.Entry.of(id, boosterEntryItem(id, profile)));
            }
        }
        EntryBrowserRequest request = EntryBrowserRequest.builder()
                .title(title("boosters", "Boosters"))
                .entries(entries)
                .page(page)
                .filter(filter)
                .buttons(buttons)
                .showBack(true)
                .addButton(item(Material.ANVIL, "#3ecf8eAdd Booster", List.of("#ffffffCreate a new booster preset.")))
                .context(new BoosterBrowserContext(target))
                .build();
        EntryBrowserMenus.open(player, request);
    }

    private void openBooster(Player player, String id) {
        BoosterProfile profile = configManager.profile(id);
        if (profile == null) {
            openMain(player, false);
            return;
        }
        MenuHolder holder = new MenuHolder(MenuType.BOOSTER, id);
        Inventory inventory = Bukkit.createInventory(holder, 45, title("booster", id + " Editor"));
        holder.inventory = inventory;
        fill(inventory);

        inventory.setItem(10, item(profile.material(), "#03fc88Material", List.of("#ffffffCurrent: #03fc88" + profile.material().name())));
        inventory.setItem(11, toggleItem("Glow", profile.glow(), "Adds an enchant glint."));
        inventory.setItem(12, item(Material.ITEM_FRAME, "#03fc88Custom Model Data", List.of("#ffffffCurrent: #03fc88" + profile.customModelData())));
        inventory.setItem(13, item(Material.COMPASS, "#03fc88Type", List.of("#ffffffCurrent: #03fc88" + profile.type().display(), "#a7b8b0Click to cycle.")));
        inventory.setItem(14, item(Material.EMERALD, "#03fc88Multiplier", List.of("#ffffffCurrent: #03fc88" + activeBoosterService.formatMultiplier(profile.multiplier()) + "x")));
        inventory.setItem(15, item(Material.CLOCK, "#03fc88Duration", List.of("#ffffffCurrent: #03fc88" + FormatUtil.duration(profile.durationSeconds()))));
        inventory.setItem(16, item(Material.GRASS_BLOCK, "#03fc88World", List.of(
                "#ffffffCurrent: #03fc88" + blankAsCurrent(profile.worldName()),
                "#a7b8b0Use current to bind on claim."
        )));
        inventory.setItem(20, item(Material.NAME_TAG, "#03fc88Item Name", List.of("#ffffffClick to edit display name.")));
        inventory.setItem(21, item(Material.ENDER_EYE, "#03fc88Bossbar Title", List.of("#ffffffClick to edit title.")));
        inventory.setItem(22, boosterItemService.createBooster(id));
        inventory.setItem(23, item(Material.WRITABLE_BOOK, "#03fc88Lore", List.of("#ffffffUse | between lore lines.")));
        if (profile.target().isFoLevels()) {
            inventory.setItem(24, item(Material.EXPERIENCE_BOTTLE, "#03fc88FoLevels XP", List.of(
                    "#ffffffTarget: #03fc88FoLevels XP",
                    "#a7b8b0Requires FoLevels.",
                    "#a7b8b0No per-event toggles."
            )));
        } else {
            inventory.setItem(24, item(Material.HOPPER, "#03fc88Enabled Events", List.of("#ffffffCurrent: #03fc88" + eventList(profile.enabledEvents()), "#a7b8b0Click to configure events.")));
        }
        inventory.setItem(31, item(Material.RED_DYE, "#ff5d73Delete Booster", List.of("#ffffffClick and type delete to confirm.")));
        inventory.setItem(GuiSlots.bottomMiddleSlot(5), buttons.back());
        player.openInventory(inventory);
    }

    private void openBoosterEvents(Player player, String id) {
        BoosterProfile profile = configManager.profile(id);
        if (profile == null) {
            openMain(player, false);
            return;
        }
        TriStateSelectionRequest request = TriStateSelectionRequest.builder()
                .title("Booster Events")
                .entries(eventEntries("Allow this booster to affect "))
                .states(eventStates(profile.enabledEvents()))
                .cycleOrder(List.of(TriStateSelectionState.ENABLED, TriStateSelectionState.DISABLED))
                .enabledLabel("Enabled")
                .disabledLabel("Disabled")
                .clickHint("Click to toggle this event.")
                .emptyTitle("No Events")
                .emptyLore(List.of(FoStyle.WHITE + "No event families are available."))
                .showSearch(false)
                .showBack(true)
                .buttons(buttons)
                .build();
        openEventSelector(player, id, request);
    }

    private void openGlobalEvents(Player player, BoosterTarget target) {
        if (target != BoosterTarget.DROPS) {
            openTargetMain(player, target);
            return;
        }
        TriStateSelectionRequest request = TriStateSelectionRequest.builder()
                .title("Events")
                .entries(eventEntries("Allow drop boosters to affect "))
                .states(globalEventStates())
                .cycleOrder(List.of(TriStateSelectionState.ENABLED, TriStateSelectionState.DISABLED))
                .enabledLabel("Enabled")
                .disabledLabel("Disabled")
                .clickHint("Click to toggle this event.")
                .emptyTitle("No Events")
                .emptyLore(List.of(FoStyle.WHITE + "No event families are available."))
                .showSearch(false)
                .showBack(true)
                .buttons(buttons)
                .build();
        openGlobalEventSelector(player, target, request);
    }

    private void openDisabledWorlds(Player player, BoosterTarget target) {
        Set<String> disabled = disabledWorlds();
        TriStateSelectionRequest request = TriStateSelectionRequest.builder()
                .worldSelection()
                .entries(WorldSelectionEntries.loadedAndConfigured(plugin, disabled))
                .states(disabledWorldStates(disabled))
                .cycleOrder(List.of(TriStateSelectionState.NEUTRAL, TriStateSelectionState.DISABLED))
                .neutralLabel("Enabled")
                .disabledLabel("Disabled")
                .clickHint("Click to toggle boosters in this world.")
                .emptyTitle("No Worlds")
                .emptyLore(List.of(FoStyle.WHITE + "No loaded or configured worlds were found."))
                .showBack(true)
                .buttons(buttons)
                .build();
        openWorldSelector(player, target, request);
    }

    private void openActive(Player player, BoosterTarget filter) {
        openActive(player, filter, 0, "");
    }

    private void openActive(Player player, BoosterTarget target, int page, String filter) {
        List<EntryBrowserRequest.Entry> entries = activeBoosterService.activeBoosters().stream()
                .filter(booster -> booster.target() == target)
                .filter(booster -> matchesActiveFilter(booster, filter))
                .map(booster -> EntryBrowserRequest.Entry.of(booster.id(), activeBoosterEntryItem(booster)))
                .toList();
        EntryBrowserRequest request = EntryBrowserRequest.builder()
                .title(title("active", "Active Boosters"))
                .entries(entries)
                .page(page)
                .filter(filter)
                .buttons(buttons)
                .showBack(true)
                .extraButton(item(Material.SUNFLOWER, "#03fc88Refresh", List.of("#ffffffRefresh active boosters.")))
                .withoutAddButton()
                .context(new ActiveBoosterBrowserContext(target))
                .build();
        EntryBrowserMenus.open(player, request);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryHolder inventoryHolder = event.getInventory().getHolder();
        if (inventoryHolder instanceof EntryBrowserHolder holder) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
                handleEntryBrowserClick(player, holder, event);
            }
            return;
        }
        if (!(inventoryHolder instanceof MaterialChooserHolder
                || inventoryHolder instanceof TriStateSelectionHolder
                || inventoryHolder instanceof MenuHolder)) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }

        if (inventoryHolder instanceof MaterialChooserHolder holder) {
            handleMaterialChooserClick(player, slot, holder);
            return;
        }
        if (inventoryHolder instanceof TriStateSelectionHolder holder) {
            handleTriStateSelectionClick(player, slot, holder);
            return;
        }
        if (!(inventoryHolder instanceof MenuHolder holder)) {
            return;
        }

        switch (holder.type) {
            case SELECTOR -> clickSelector(player, slot);
            case MAIN -> clickMain(player, target(holder.value), slot);
            case BOOSTER -> clickBooster(player, holder.value, slot);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof EntryBrowserHolder
                || holder instanceof MenuHolder
                || holder instanceof MaterialChooserHolder
                || holder instanceof TriStateSelectionHolder)) {
            return;
        }
        int topSize = event.getInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void handleEntryBrowserClick(Player player, EntryBrowserHolder holder, InventoryClickEvent event) {
        EntryBrowserClick click = EntryBrowserMenus.handleClick(event.getRawSlot(), holder, event.getClick());
        if (holder.request().context() instanceof BoosterBrowserContext context) {
            handleBoosterBrowserClick(player, holder, click, context);
        } else if (holder.request().context() instanceof ActiveBoosterBrowserContext context) {
            handleActiveBoosterBrowserClick(player, holder, click, context);
        }
    }

    private void handleBoosterBrowserClick(Player player, EntryBrowserHolder holder, EntryBrowserClick click, BoosterBrowserContext context) {
        int page = holder.request().page();
        String filter = holder.request().filter();
        switch (click.action()) {
            case ENTRY -> {
                if (click.entryId() != null && configManager.hasBooster(click.entryId())) {
                    openBooster(player, click.entryId());
                    editorSounds.open(player);
                }
            }
            case ADD -> prompt(player, "editor-text-input", "New Booster ID", "new booster id, example weekend", "",
                    () -> openBoosters(player, context.target(), page, filter),
                    input -> createBooster(player, context.target(), input, page, filter));
            case BACK -> {
                editorSounds.back(player);
                openTargetMain(player, context.target());
            }
            case SEARCH -> {
                editorSounds.search(player);
                openSearchPrompt(
                        player,
                        "Search Boosters",
                        "booster id or name",
                        filter,
                        () -> openBoosters(player, context.target(), page, filter),
                        input -> openBoosters(player, context.target(), 0, input));
            }
            case CLEAR_SEARCH -> {
                editorSounds.clearSearch(player);
                openBoosters(player, context.target(), 0, "");
            }
            case PREVIOUS_PAGE -> {
                editorSounds.previousPage(player);
                openBoosters(player, context.target(), page - 1, filter);
            }
            case NEXT_PAGE -> {
                editorSounds.nextPage(player);
                openBoosters(player, context.target(), page + 1, filter);
            }
            case EXTRA, NONE -> {
            }
        }
    }

    private void handleActiveBoosterBrowserClick(Player player, EntryBrowserHolder holder, EntryBrowserClick click, ActiveBoosterBrowserContext context) {
        int page = holder.request().page();
        String filter = holder.request().filter();
        switch (click.action()) {
            case ENTRY -> {
                if (click.entryId() == null) {
                    return;
                }
                int removed = activeBoosterService.removeById(click.entryId());
                if (removed > 0) {
                    messages.send(player, "messages.removed-active", Map.of("amount", Integer.toString(removed)));
                    editorSounds.delete(player);
                }
                openActive(player, context.target(), page, filter);
            }
            case BACK -> {
                editorSounds.back(player);
                openTargetMain(player, context.target());
            }
            case EXTRA -> openActive(player, context.target(), page, filter);
            case SEARCH -> {
                editorSounds.search(player);
                openSearchPrompt(
                        player,
                        "Search Active Boosters",
                        "booster id, owner, team, or world",
                        filter,
                        () -> openActive(player, context.target(), page, filter),
                        input -> openActive(player, context.target(), 0, input));
            }
            case CLEAR_SEARCH -> {
                editorSounds.clearSearch(player);
                openActive(player, context.target(), 0, "");
            }
            case PREVIOUS_PAGE -> {
                editorSounds.previousPage(player);
                openActive(player, context.target(), page - 1, filter);
            }
            case NEXT_PAGE -> {
                editorSounds.nextPage(player);
                openActive(player, context.target(), page + 1, filter);
            }
            case ADD, NONE -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player
                && core != null
                && core.inventoryCloseSuppressor().consumeSuppressedClose(player)) {
            return;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!pendingPrompts.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();
        FoCoreContext currentCore = core;
        if (currentCore != null) {
            currentCore.scheduler().runForPlayer(event.getPlayer(), () -> handleChatPrompt(event.getPlayer(), message));
        }
    }

    private void clickSelector(Player player, int slot) {
        switch (slot) {
            case 11 -> {
                openTargetMain(player, BoosterTarget.DROPS);
                editorSounds.open(player);
            }
            case 15 -> {
                if (!foLevelsAvailable()) {
                    messages.send(player, "messages.folevels-required");
                    editorSounds.error(player);
                    openMain(player, false);
                    return;
                }
                openTargetMain(player, BoosterTarget.FOLEVELS_XP);
                editorSounds.open(player);
            }
            default -> {
            }
        }
    }

    private void clickMain(Player player, BoosterTarget target, int slot) {
        switch (slot) {
            case 10 -> {
                openBoosters(player, target);
                editorSounds.open(player);
            }
            case 11 -> {
                openActive(player, target);
                editorSounds.open(player);
            }
            case 12 -> {
                openDisabledWorlds(player, target);
                editorSounds.open(player);
            }
            case 13 -> toggleConfig(player, "settings.stack-boosters", "stack-boosters", () -> openTargetMain(player, target));
            case 14 -> {
                toggleConfig(player, "settings.team-multiplier.enabled", "team-multiplier.enabled", () -> openTargetMain(player, target));
            }
            case 15 -> {
                toggleConfig(player, "settings.team-boosters.stack-with-other-boosters", "team-boosters.stack-with-other-boosters", () -> openTargetMain(player, target));
            }
            case 16 -> {
                if (target == BoosterTarget.DROPS) {
                    openGlobalEvents(player, target);
                    editorSounds.open(player);
                }
            }
            case 22 -> {
                editorSounds.back(player);
                openMain(player, false);
            }
            default -> {
            }
        }
    }

    private void clickBooster(Player player, String id, int slot) {
        if (id == null || !configManager.hasBooster(id)) {
            openMain(player, false);
            return;
        }
        BoosterProfile profile = configManager.profile(id);
        switch (slot) {
            case 10 -> {
                openMaterialSelector(player, id);
                editorSounds.open(player);
            }
            case 11 -> toggleBooster(player, id, "glow", id + ".glow", () -> openBooster(player, id));
            case 12 -> prompt(player, "editor-number-input", "Custom Model Data", "custom model data number, or 0 to disable", Integer.toString(profile.customModelData()), () -> openBooster(player, id), input -> {
                Integer value = FormatUtil.parseInteger(input);
                if (value != null && value >= 0) {
                    saveBooster(player, id, "custom-model-data", value, id + ".custom-model-data");
                } else {
                    messages.send(player, "messages.invalid-number");
                    editorSounds.error(player);
                }
                openBooster(player, id);
            });
            case 13 -> {
                BoosterType next = switch (profile.type()) {
                    case PERSONAL -> BoosterType.TEAM;
                    case TEAM -> BoosterType.GLOBAL;
                    case GLOBAL -> BoosterType.WORLD;
                    case WORLD -> BoosterType.PERSONAL;
                };
                configManager.setBoosterValue(id, "type", next.name());
                messages.send(player, "messages.editor-saved", Map.of("setting", id + ".type"));
                editorSounds.cycle(player);
                openBooster(player, id);
            }
            case 14 -> prompt(player, "editor-number-input", "Multiplier", "decimal multiplier above 1, example 2.5", Double.toString(profile.multiplier()), () -> openBooster(player, id), input -> {
                OptionalDouble value = LargeNumberParser.parseDouble(input);
                if (value.isPresent() && value.getAsDouble() > 1D) {
                    saveBooster(player, id, "multiplier", value.getAsDouble(), id + ".multiplier");
                } else {
                    messages.send(player, "messages.invalid-number");
                    editorSounds.error(player);
                }
                openBooster(player, id);
            });
            case 15 -> prompt(player, "editor-duration-input", "Duration", "duration, example 15m or 1h", Long.toString(profile.durationSeconds()), () -> openBooster(player, id), input -> {
                Long value = FormatUtil.parseDurationSeconds(input);
                if (value != null) {
                    saveBooster(player, id, "duration-seconds", value, id + ".duration");
                } else {
                    messages.send(player, "messages.invalid-duration");
                    editorSounds.error(player);
                }
                openBooster(player, id);
            });
            case 16 -> prompt(player, "editor-text-input", "World", "world name, or current to use the claimer's world", profile.worldName().isBlank() ? "current" : profile.worldName(), () -> openBooster(player, id), input -> {
                saveBooster(player, id, "world", input.equalsIgnoreCase("current") ? "" : input, id + ".world");
                openBooster(player, id);
            });
            case 20 -> prompt(player, "editor-text-input", "Item Name", "item display name with color codes", profile.name(), () -> openBooster(player, id), input -> {
                saveBooster(player, id, "name", input, id + ".name");
                openBooster(player, id);
            });
            case 21 -> prompt(player, "editor-text-input", "Bossbar Title", "bossbar title using {multiplier} and {time}", profile.bossbarTitle(), () -> openBooster(player, id), input -> {
                saveBooster(player, id, "bossbar.title", input, id + ".bossbar");
                openBooster(player, id);
            });
            case 23 -> prompt(player, "editor-text-input", "Lore", "lore lines separated by |", String.join("|", profile.lore()), () -> openBooster(player, id), input -> {
                saveBooster(player, id, "lore", Arrays.stream(input.split("\\|")).toList(), id + ".lore");
                openBooster(player, id);
            });
            case 24 -> {
                if (!profile.target().isFoLevels()) {
                    openBoosterEvents(player, id);
                    editorSounds.open(player);
                }
            }
            case 31 -> prompt(player, "editor-confirm-input", "Delete Booster", "delete", id, () -> openBooster(player, id), input -> deleteBooster(player, id, input));
            case 40 -> {
                editorSounds.back(player);
                openBoosters(player, profile.target());
            }
            default -> {
            }
        }
    }

    private void openMaterialSelector(Player player, String id) {
        BoosterProfile profile = configManager.profile(id);
        if (profile == null) {
            openMain(player, false);
            return;
        }
        MaterialChooserRequest request = MaterialChooserRequest.builder()
                .title("Booster Material")
                .mode(MaterialChooserMode.SINGLE_SELECT)
                .availableMaterials(MaterialTypes.allUsable())
                .selectedMaterials(Set.of(profile.material()))
                .showBack(true)
                .showSearch(true)
                .buttons(buttons)
                .extraLore(material -> List.of(FoStyle.WHITE + "Click to use this material for the booster item."))
                .build();
        openMaterialSelector(player, id, request);
    }

    private void openMaterialSelector(Player player, String id, MaterialChooserRequest request) {
        materialSelectorBoosters.put(player.getUniqueId(), id);
        worldSelectorTargets.remove(player.getUniqueId());
        eventSelectorBoosters.remove(player.getUniqueId());
        eventSelectorTargets.remove(player.getUniqueId());
        MaterialChooserMenus.open(player, request);
    }

    private void handleMaterialChooserClick(Player player, int slot, MaterialChooserHolder holder) {
        MaterialChooserClick click = MaterialChooserMenus.handleClick(slot, holder);
        String id = materialSelectorBoosters.get(player.getUniqueId());
        if (id == null || !configManager.hasBooster(id)) {
            materialSelectorBoosters.remove(player.getUniqueId());
            openMain(player, false);
            return;
        }
        MaterialChooserActionType action = click.action();
        if (action == MaterialChooserActionType.NONE) {
            return;
        }
        if (action == MaterialChooserActionType.BACK) {
            materialSelectorBoosters.remove(player.getUniqueId());
            editorSounds.back(player);
            openBooster(player, id);
            return;
        }
        if (action == MaterialChooserActionType.SEARCH) {
            editorSounds.search(player);
            openMaterialSearch(player, id, click.nextRequest());
            return;
        }
        if (action == MaterialChooserActionType.PREVIOUS_PAGE
                || action == MaterialChooserActionType.NEXT_PAGE
                || action == MaterialChooserActionType.CLEAR_SEARCH) {
            playMaterialNavigationSound(player, action);
            openMaterialSelector(player, id, click.nextRequest());
            return;
        }
        if ((action == MaterialChooserActionType.SELECT || action == MaterialChooserActionType.TOGGLE) && click.material() != null) {
            saveBooster(player, id, "material", click.material().name(), id + ".material");
            materialSelectorBoosters.remove(player.getUniqueId());
            openBooster(player, id);
        }
    }

    private void openMaterialSearch(Player player, String id, MaterialChooserRequest request) {
        openSearchPrompt(
                player,
                "Search Materials",
                "material name",
                request.filter(),
                () -> openMaterialSelector(player, id, request),
                input -> openMaterialSelector(player, id, request.withFilter(input))
        );
    }

    private void openWorldSelector(Player player, BoosterTarget target, TriStateSelectionRequest request) {
        worldSelectorTargets.put(player.getUniqueId(), target);
        materialSelectorBoosters.remove(player.getUniqueId());
        eventSelectorBoosters.remove(player.getUniqueId());
        eventSelectorTargets.remove(player.getUniqueId());
        TriStateSelectionMenus.open(player, request);
    }

    private void openEventSelector(Player player, String id, TriStateSelectionRequest request) {
        eventSelectorBoosters.put(player.getUniqueId(), id);
        materialSelectorBoosters.remove(player.getUniqueId());
        worldSelectorTargets.remove(player.getUniqueId());
        eventSelectorTargets.remove(player.getUniqueId());
        TriStateSelectionMenus.open(player, request);
    }

    private void openGlobalEventSelector(Player player, BoosterTarget target, TriStateSelectionRequest request) {
        eventSelectorTargets.put(player.getUniqueId(), target);
        materialSelectorBoosters.remove(player.getUniqueId());
        worldSelectorTargets.remove(player.getUniqueId());
        eventSelectorBoosters.remove(player.getUniqueId());
        TriStateSelectionMenus.open(player, request);
    }

    private void handleTriStateSelectionClick(Player player, int slot, TriStateSelectionHolder holder) {
        UUID playerId = player.getUniqueId();
        BoosterTarget worldTarget = worldSelectorTargets.get(playerId);
        if (worldTarget != null) {
            handleWorldSelectionClick(player, worldTarget, TriStateSelectionMenus.handleClick(slot, holder));
            return;
        }

        BoosterTarget eventTarget = eventSelectorTargets.get(playerId);
        if (eventTarget != null) {
            handleGlobalEventSelectionClick(player, eventTarget, TriStateSelectionMenus.handleClick(slot, holder));
            return;
        }

        String boosterId = eventSelectorBoosters.get(playerId);
        if (boosterId != null) {
            handleEventSelectionClick(player, boosterId, TriStateSelectionMenus.handleClick(slot, holder));
        }
    }

    private void handleWorldSelectionClick(Player player, BoosterTarget target, TriStateSelectionClick click) {
        TriStateSelectionActionType action = click.action();
        if (action == TriStateSelectionActionType.NONE) {
            return;
        }
        if (action == TriStateSelectionActionType.BACK) {
            worldSelectorTargets.remove(player.getUniqueId());
            editorSounds.back(player);
            openTargetMain(player, target);
            return;
        }
        if (action == TriStateSelectionActionType.SEARCH) {
            editorSounds.search(player);
            openWorldSearch(player, target, click.nextRequest());
            return;
        }
        if (action == TriStateSelectionActionType.TOGGLE) {
            saveDisabledWorlds(player, click.nextRequest());
            editorSounds.cycle(player);
        }
        if (action == TriStateSelectionActionType.PREVIOUS_PAGE
                || action == TriStateSelectionActionType.NEXT_PAGE
                || action == TriStateSelectionActionType.CLEAR_SEARCH
                || action == TriStateSelectionActionType.TOGGLE) {
            if (action != TriStateSelectionActionType.TOGGLE) {
                playTriStateNavigationSound(player, action);
            }
            openWorldSelector(player, target, click.nextRequest());
        }
    }

    private void handleEventSelectionClick(Player player, String id, TriStateSelectionClick click) {
        if (!configManager.hasBooster(id)) {
            eventSelectorBoosters.remove(player.getUniqueId());
            openMain(player, false);
            return;
        }
        TriStateSelectionActionType action = click.action();
        if (action == TriStateSelectionActionType.NONE) {
            return;
        }
        if (action == TriStateSelectionActionType.BACK) {
            eventSelectorBoosters.remove(player.getUniqueId());
            editorSounds.back(player);
            openBooster(player, id);
            return;
        }
        if (action == TriStateSelectionActionType.TOGGLE) {
            configManager.setBoosterValue(id, "enabled-events", eventKeys(click.nextRequest()));
            messages.send(player, "messages.editor-saved", Map.of("setting", id + ".enabled-events"));
            editorSounds.toggle(player, click.newState() == TriStateSelectionState.ENABLED);
        }
        if (action == TriStateSelectionActionType.PREVIOUS_PAGE
                || action == TriStateSelectionActionType.NEXT_PAGE
                || action == TriStateSelectionActionType.CLEAR_SEARCH
                || action == TriStateSelectionActionType.TOGGLE) {
            if (action != TriStateSelectionActionType.TOGGLE) {
                playTriStateNavigationSound(player, action);
            }
            openEventSelector(player, id, click.nextRequest());
        }
    }

    private void handleGlobalEventSelectionClick(Player player, BoosterTarget target, TriStateSelectionClick click) {
        TriStateSelectionActionType action = click.action();
        if (action == TriStateSelectionActionType.NONE) {
            return;
        }
        if (action == TriStateSelectionActionType.BACK) {
            eventSelectorTargets.remove(player.getUniqueId());
            editorSounds.back(player);
            openTargetMain(player, target);
            return;
        }
        if (action == TriStateSelectionActionType.TOGGLE) {
            saveGlobalEvents(player, click.nextRequest());
            editorSounds.toggle(player, click.newState() == TriStateSelectionState.ENABLED);
        }
        if (action == TriStateSelectionActionType.PREVIOUS_PAGE
                || action == TriStateSelectionActionType.NEXT_PAGE
                || action == TriStateSelectionActionType.CLEAR_SEARCH
                || action == TriStateSelectionActionType.TOGGLE) {
            if (action != TriStateSelectionActionType.TOGGLE) {
                playTriStateNavigationSound(player, action);
            }
            openGlobalEventSelector(player, target, click.nextRequest());
        }
    }

    private void playMaterialNavigationSound(Player player, MaterialChooserActionType action) {
        switch (action) {
            case PREVIOUS_PAGE -> editorSounds.previousPage(player);
            case NEXT_PAGE -> editorSounds.nextPage(player);
            case CLEAR_SEARCH -> editorSounds.clearSearch(player);
            default -> {
            }
        }
    }

    private void playTriStateNavigationSound(Player player, TriStateSelectionActionType action) {
        switch (action) {
            case PREVIOUS_PAGE -> editorSounds.previousPage(player);
            case NEXT_PAGE -> editorSounds.nextPage(player);
            case CLEAR_SEARCH -> editorSounds.clearSearch(player);
            default -> {
            }
        }
    }

    private void openWorldSearch(Player player, BoosterTarget target, TriStateSelectionRequest request) {
        openSearchPrompt(
                player,
                "Search Worlds",
                "world name",
                request.filter(),
                () -> openWorldSelector(player, target, request),
                input -> openWorldSelector(player, target, request.withFilter(input))
        );
    }

    private void openSearchPrompt(Player player, String title, String placeholder, String currentFilter, Runnable reopen, Consumer<String> consumer) {
        TextDialogRequest request = new TextDialogRequest(
                FoStyle.YELLOW + cleanTitle(title),
                List.of(FoStyle.MUTED + "Current filter: " + FoStyle.THEME + (currentFilter == null || currentFilter.isBlank() ? "none" : currentFilter)),
                FoStyle.WHITE + "Search",
                "",
                placeholder,
                DialogButton.search("Search", "", DIALOG_BUTTON_WIDTH),
                DialogButton.cancel("Cancel", "", DIALOG_BUTTON_WIDTH),
                DIALOG_BODY_WIDTH,
                DIALOG_INPUT_WIDTH,
                64,
                true,
                true,
                false
        );
        openEditorInput(player, request, consumer, reopen, "search");
    }

    private void saveDisabledWorlds(Player player, TriStateSelectionRequest request) {
        configManager.setConfigValue("disabled-worlds", TriStateSelections.disabledKeys(request));
        activeBoosterService.invalidateAll();
        messages.send(player, "messages.editor-saved", Map.of("setting", "disabled-worlds"));
    }

    private Map<String, TriStateSelectionState> disabledWorldStates(Set<String> disabledWorlds) {
        Map<String, TriStateSelectionState> states = new LinkedHashMap<>();
        for (String world : disabledWorlds) {
            if (world != null && !world.isBlank()) {
                states.put(world, TriStateSelectionState.DISABLED);
            }
        }
        return states;
    }

    private List<TriStateSelectionEntry> eventEntries(String descriptionPrefix) {
        List<TriStateSelectionEntry> entries = new ArrayList<>();
        for (EventFamily family : EventFamily.values()) {
            String label = pretty(family.configKey());
            entries.add(TriStateSelectionEntry.of(
                    family.configKey(),
                    label,
                    List.of(FoStyle.WHITE + descriptionPrefix + label + ".")
            ));
        }
        return entries;
    }

    private Map<String, TriStateSelectionState> eventStates(Set<EventFamily> enabledEvents) {
        Map<String, TriStateSelectionState> states = new LinkedHashMap<>();
        Set<EventFamily> enabled = enabledEvents == null ? Set.of() : enabledEvents;
        for (EventFamily family : EventFamily.values()) {
            states.put(family.configKey(), enabled.contains(family)
                    ? TriStateSelectionState.ENABLED
                    : TriStateSelectionState.DISABLED);
        }
        return states;
    }

    private Map<String, TriStateSelectionState> globalEventStates() {
        Map<String, TriStateSelectionState> states = new LinkedHashMap<>();
        for (EventFamily family : EventFamily.values()) {
            states.put(family.configKey(), configManager.isEventEnabled(family)
                    ? TriStateSelectionState.ENABLED
                    : TriStateSelectionState.DISABLED);
        }
        return states;
    }

    private List<String> eventKeys(TriStateSelectionRequest request) {
        Set<EventFamily> enabled = new LinkedHashSet<>();
        for (EventFamily family : EventFamily.values()) {
            if (request.state(family.configKey()) == TriStateSelectionState.ENABLED) {
                enabled.add(family);
            }
        }
        return configManager.eventKeys(enabled);
    }

    private void saveGlobalEvents(Player player, TriStateSelectionRequest request) {
        for (EventFamily family : EventFamily.values()) {
            configManager.setConfigValue("events." + family.configKey(), request.state(family.configKey()) == TriStateSelectionState.ENABLED);
        }
        activeBoosterService.invalidateAll();
        messages.send(player, "messages.editor-saved", Map.of("setting", "events"));
    }

    private void createBooster(Player player, BoosterTarget target, String input, int returnPage, String returnFilter) {
        String id = configManager.normalizeId(input);
        if (!id.matches("[a-z0-9_-]{2,32}")) {
            messages.send(player, "messages.invalid-id");
            editorSounds.error(player);
            openBoosters(player, target, returnPage, returnFilter);
            return;
        }
        if (configManager.hasBooster(id)) {
            messages.send(player, "messages.booster-exists");
            editorSounds.error(player);
            openBoosters(player, target, returnPage, returnFilter);
            return;
        }
        configManager.createBooster(id, target);
        messages.send(player, "messages.editor-created", Map.of("booster", id));
        editorSounds.add(player);
        openBooster(player, id);
    }

    private void deleteBooster(Player player, String id, String input) {
        if (!input.equalsIgnoreCase("delete")) {
            messages.send(player, "messages.editor-cancelled");
            openBooster(player, id);
            return;
        }
        if (configManager.boosterIds().size() <= 1) {
            messages.send(player, "messages.cannot-delete-last-booster");
            editorSounds.error(player);
            openBooster(player, id);
            return;
        }
        configManager.deleteBooster(id);
        messages.send(player, "messages.editor-deleted", Map.of("booster", id));
        editorSounds.delete(player);
        openMain(player, false);
    }

    private void prompt(Player player, String dialogId, String title, String format, String currentValue, Runnable reopen, Consumer<String> consumer) {
        TextDialogRequest request = dialogRequest(dialogId, title, format, currentValue);
        openEditorInput(player, request, consumer, () -> {
            messages.send(player, "messages.editor-cancelled");
            reopen.run();
        }, title);
    }

    private void openEditorInput(Player player, TextDialogRequest request, Consumer<String> onSubmit, Runnable onCancel, String type) {
        try {
            boolean openedNative = EditorDialogInputs.openTextFromInventory(
                    plugin,
                    core.inventoryCloseSuppressor(),
                    dialogService,
                    player,
                    request,
                    input -> onSubmit.accept(input == null ? "" : input.trim()),
                    () -> {
                        editorSounds.back(player);
                        onCancel.run();
                    }
            );
            if (openedNative) {
                editorSounds.open(player);
            } else {
                warnDialogFallback(player);
            }
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Editor " + type + " input failed: " + exception.getMessage());
            editorSounds.error(player);
            onCancel.run();
        }
    }

    private DialogService createDialogService(FoCoreContext core) {
        return core.createDialogService(new FallbackDialogService(
                core.nativeDialogs(),
                (player, request, onClose) -> run(onClose),
                (player, request, onConfirm, onCancel) -> run(onCancel),
                this::openChatPrompt
        ));
    }

    private void openChatPrompt(Player player, TextDialogRequest request, Consumer<String> onSubmit, Runnable onCancel) {
        player.closeInventory();
        pendingPrompts.put(player.getUniqueId(), new PendingPrompt(onSubmit, onCancel));
        messages.send(player, "messages.prompt", Map.of("format", request.placeholder()));
    }

    private void handleChatPrompt(Player player, String input) {
        PendingPrompt prompt = pendingPrompts.remove(player.getUniqueId());
        if (prompt == null) {
            return;
        }
        if (input.equalsIgnoreCase("cancel")) {
            run(prompt.onCancel());
            return;
        }
        if (prompt.onSubmit() != null) {
            prompt.onSubmit().accept(input);
        }
    }

    private void run(Runnable runnable) {
        if (runnable != null) {
            runnable.run();
        }
    }

    private void warnDialogFallback(Player player) {
        NativeDialogAvailability availability = core.nativeDialogs().availability();
        if (!availability.configEnabled() || availability.canUseNativeDialogs() || !core.nativeDialogs().warnOnFallback()) {
            return;
        }
        if (!player.hasPermission("fodropbooster.admin")) {
            return;
        }
        if (warnedDialogFallbackPlayers.add(player.getUniqueId())) {
            messages.send(player, "messages.native-dialogs-fallback");
        }
    }

    private TextDialogRequest dialogRequest(String dialogId, String title, String format, String currentValue) {
        return switch (dialogId) {
            case "editor-number-input" -> textRequest(
                    title,
                    "New value",
                    currentValue,
                    format,
                    DialogButton.save("Save", "", DIALOG_BUTTON_WIDTH),
                    NUMBER_MAX_LENGTH
            );
            case "editor-duration-input" -> textRequest(
                    title,
                    "New duration",
                    currentValue,
                    format,
                    DialogButton.save("Save", "", DIALOG_BUTTON_WIDTH),
                    DURATION_MAX_LENGTH
            );
            case "editor-confirm-input" -> new TextDialogRequest(
                    FoStyle.BAD + cleanTitle(title),
                    List.of(
                            FoStyle.MUTED + "Target: " + FoStyle.THEME + safe(currentValue),
                            FoStyle.MUTED + "Type " + FoStyle.BAD + safe(format) + FoStyle.MUTED + " to confirm."
                    ),
                    FoStyle.WHITE + "Confirmation",
                    "",
                    safe(format),
                    DialogButton.confirm("Confirm", "", DIALOG_BUTTON_WIDTH),
                    DialogButton.cancel("Cancel", "", DIALOG_BUTTON_WIDTH),
                    DIALOG_BODY_WIDTH,
                    DIALOG_INPUT_WIDTH,
                    CONFIRM_MAX_LENGTH,
                    true,
                    false,
                    false
            );
            default -> textRequest(
                    title,
                    "New value",
                    currentValue,
                    format,
                    DialogButton.save("Save", "", DIALOG_BUTTON_WIDTH),
                    TEXT_MAX_LENGTH
            );
        };
    }

    private TextDialogRequest textRequest(
            String title,
            String fieldLabel,
            String currentValue,
            String format,
            DialogButton submitButton,
            int maxLength
    ) {
        return new TextDialogRequest(
                FoStyle.THEME + cleanTitle(title),
                List.of(
                        FoStyle.MUTED + "Current value: " + FoStyle.THEME + safe(currentValue),
                        FoStyle.MUTED + "Expected input: " + FoStyle.WHITE + safe(format)
                ),
                FoStyle.WHITE + fieldLabel,
                safe(currentValue),
                safe(format),
                submitButton,
                DialogButton.cancel("Cancel", "", DIALOG_BUTTON_WIDTH),
                DIALOG_BODY_WIDTH,
                DIALOG_INPUT_WIDTH,
                maxLength,
                true,
                false,
                false
        );
    }

    private String cleanTitle(String title) {
        return title == null || title.isBlank() ? "Input" : title;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void toggleConfig(Player player, String path, String setting, Runnable reopen) {
        boolean newValue = !configManager.config().getBoolean(path, false);
        configManager.setConfigValue(path, newValue);
        activeBoosterService.invalidateAll();
        messages.send(player, "messages.editor-saved", Map.of("setting", setting));
        editorSounds.toggle(player, newValue);
        reopen.run();
    }

    private void toggleBooster(Player player, String id, String path, String setting, Runnable reopen) {
        YamlConfiguration booster = configManager.booster(id);
        boolean newValue = !booster.getBoolean(path, false);
        configManager.setBoosterValue(id, path, newValue);
        messages.send(player, "messages.editor-saved", Map.of("setting", setting));
        editorSounds.toggle(player, newValue);
        reopen.run();
    }

    private void saveBooster(Player player, String id, String path, Object value, String setting) {
        configManager.setBoosterValue(id, path, value);
        messages.send(player, "messages.editor-saved", Map.of("setting", setting));
        editorSounds.save(player);
    }

    private ItemStack teamMultiplierItem() {
        boolean enabled = configManager.teamMultiplierEnabled();
        String color = enabled ? "#3ecf8e" : "#ff5d73";
        String status = enabled ? "Enabled" : "Disabled";
        return item(Material.PLAYER_HEAD, color + "Team Multiplier", List.of(
                "#ffffffHidden passive multiplier from additional online teammates.",
                "#ffffffPer extra member: #03fc88" + activeBoosterService.formatMultiplier(configManager.teamMultiplierPerOnlineMember()) + "x",
                "#ffffffCap: #03fc88" + activeBoosterService.formatMultiplier(configManager.teamMultiplierCap()) + "x",
                "",
                "#a7b8b0Requires FoTeams.",
                "#a7b8b0Disabled automatically when FoTeams is not installed.",
                "",
                color + status,
                "#a7b8b0Click to toggle."
        ));
    }

    private ItemStack toggleItem(String name, boolean enabled, String description) {
        Material material = enabled ? Material.LIME_DYE : Material.RED_DYE;
        String color = enabled ? "#3ecf8e" : "#ff5d73";
        String status = enabled ? "Enabled" : "Disabled";
        return item(material, color + name, List.of("#ffffff" + description, "", color + status));
    }

    private void fill(Inventory inventory) {
        ItemStack filler = EditorItemFactory.filler();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        return EditorItemFactory.item(material, name, lore);
    }

    private ItemStack boosterEntryItem(String id, BoosterProfile profile) {
        return item(profile.material(), "#03fc88" + id, List.of(
                "#ffffffTarget: #03fc88" + profile.target().display(),
                "#ffffffType: #03fc88" + profile.type().display(),
                "#ffffffMultiplier: #03fc88" + activeBoosterService.formatMultiplier(profile.multiplier()) + "x",
                "#ffffffDuration: #03fc88" + FormatUtil.duration(profile.durationSeconds()),
                "",
                "#a7b8b0Click to edit."
        ));
    }

    private boolean matchesBoosterFilter(String id, BoosterProfile profile, String filter) {
        String query = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        return query.isBlank()
                || id.toLowerCase(Locale.ROOT).contains(query)
                || profile.name().toLowerCase(Locale.ROOT).contains(query)
                || profile.type().display().toLowerCase(Locale.ROOT).contains(query);
    }

    private ItemStack activeBoosterEntryItem(ActiveBooster booster) {
        String scope = switch (booster.type()) {
            case PERSONAL -> booster.ownerName();
            case TEAM -> booster.teamName();
            case WORLD -> booster.worldName();
            case GLOBAL -> "server";
        };
        return item(Material.BEACON, "#03fc88" + booster.id().substring(0, Math.min(8, booster.id().length())), List.of(
                "#ffffffSystem: #03fc88" + booster.target().display(),
                "#ffffffType: #03fc88" + booster.type().display(),
                "#ffffffScope: #03fc88" + scope,
                "#ffffffMultiplier: #03fc88" + activeBoosterService.formatMultiplier(booster.multiplier()) + "x",
                "#ffffffRemaining: #03fc88" + FormatUtil.duration(booster.remainingMillis(System.currentTimeMillis()) / 1000L),
                "",
                "#ff5d73Click to remove."
        ));
    }

    private boolean matchesActiveFilter(ActiveBooster booster, String filter) {
        String query = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        String scope = switch (booster.type()) {
            case PERSONAL -> booster.ownerName();
            case TEAM -> booster.teamName();
            case WORLD -> booster.worldName();
            case GLOBAL -> "server";
        };
        return query.isBlank()
                || booster.id().toLowerCase(Locale.ROOT).contains(query)
                || booster.type().display().toLowerCase(Locale.ROOT).contains(query)
                || scope.toLowerCase(Locale.ROOT).contains(query);
    }

    private String title(String key, String fallback) {
        return GuiTitles.format(fallback);
    }

    private BoosterTarget target(String value) {
        return BoosterTarget.from(value);
    }

    private boolean foLevelsAvailable() {
        return plugin.getServer().getPluginManager().isPluginEnabled("FoLevels");
    }

    private String eventList(Set<EventFamily> families) {
        if (families.isEmpty()) {
            return "none";
        }
        if (families.size() == EventFamily.values().length) {
            return "all";
        }
        return String.join(", ", families.stream().map(EventFamily::configKey).toList());
    }

    private Set<EventFamily> globalEnabledEvents() {
        Set<EventFamily> enabled = new LinkedHashSet<>();
        for (EventFamily family : EventFamily.values()) {
            if (configManager.isEventEnabled(family)) {
                enabled.add(family);
            }
        }
        return enabled;
    }

    private String blankAsCurrent(String value) {
        return value == null || value.isBlank() ? "current world on use" : value;
    }

    private Set<String> disabledWorlds() {
        return new LinkedHashSet<>(configManager.config().getStringList("disabled-worlds"));
    }

    private String disabledWorldsDisplay() {
        Set<String> disabled = disabledWorlds();
        return disabled.isEmpty() ? "none" : String.join(", ", disabled);
    }

    private boolean containsIgnoreCase(Set<String> values, String target) {
        for (String value : values) {
            if (value.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    private String pretty(String input) {
        String[] parts = input.split("-");
        List<String> words = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            words.add(part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1));
        }
        return String.join(" ", words);
    }

    private enum MenuType {
        SELECTOR,
        MAIN,
        BOOSTER
    }

    private record BoosterBrowserContext(BoosterTarget target) {
    }

    private record ActiveBoosterBrowserContext(BoosterTarget target) {
    }

    private static final class MenuHolder implements InventoryHolder {
        private final MenuType type;
        private final String value;
        private Inventory inventory;

        private MenuHolder(MenuType type, String value) {
            this.type = type;
            this.value = value;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private record PendingPrompt(Consumer<String> onSubmit, Runnable onCancel) {
    }

}
