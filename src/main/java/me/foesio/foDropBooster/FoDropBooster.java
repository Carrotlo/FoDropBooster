package me.foesio.foDropBooster;

import me.foesio.core.FoCoreContext;
import me.foesio.core.FoPluginCore;
import me.foesio.core.message.FoMessageMigrations;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.placeholder.FoPlaceholders;
import me.foesio.core.reload.FoReloadRegistry;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.core.sound.FoAdminSounds;
import me.foesio.core.sound.FoEditorSounds;
import me.foesio.core.sound.FoSound;
import me.foesio.core.sound.FoSoundMigrations;
import me.foesio.core.sound.FoSoundService;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.foDropBooster.api.DefaultFoDropBoosterApi;
import me.foesio.foDropBooster.api.FoDropBoosterApi;
import me.foesio.foDropBooster.booster.ActiveBoosterService;
import me.foesio.foDropBooster.booster.BoosterItemService;
import me.foesio.foDropBooster.booster.BoosterTarget;
import me.foesio.foDropBooster.bossbar.BossbarService;
import me.foesio.foDropBooster.command.DropBoosterBossbarCommand;
import me.foesio.foDropBooster.command.FoDropBoosterAdminCommand;
import me.foesio.foDropBooster.config.ConfigManager;
import me.foesio.foDropBooster.gui.EditorManager;
import me.foesio.foDropBooster.hook.FoDropsRewardBridge;
import me.foesio.foDropBooster.hook.FoLevelsXpBridge;
import me.foesio.foDropBooster.hook.FoTeamsHook;
import me.foesio.foDropBooster.listener.BoosterCacheListener;
import me.foesio.foDropBooster.listener.BoosterItemListener;
import me.foesio.foDropBooster.listener.DropBoostListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.SoundCategory;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.ToDoubleFunction;

public final class FoDropBooster extends JavaPlugin {
    private static final String MODRINTH_PROJECT_ID = "fodropbooster";
    private static final int BSTATS_PLUGIN_ID = 33038;

    private FoCoreContext core;
    private ConfigManager configManager;
    private FoSoundService sounds;
    private FoAdminSounds adminSounds;
    private FoEditorSounds editorSounds;
    private FoMessageService messages;
    private BoosterItemService boosterItemService;
    private ActiveBoosterService activeBoosterService;
    private BossbarService bossbarService;
    private EditorManager editorManager;
    private UpdateNoticeService updateNotices;
    private FoTeamsHook foTeamsHook;
    private FoDropBoosterApi api;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.load();
        reloadCoreContext();
        sounds = core.createSounds(soundMigrations());
        adminSounds = FoAdminSounds.create(sounds);
        editorSounds = FoEditorSounds.create(sounds);
        messages = FoMessageService.load(this, messageMigrations());
        boosterItemService = new BoosterItemService(this, configManager);
        foTeamsHook = new FoTeamsHook(this);
        activeBoosterService = new ActiveBoosterService(this, core.scheduler(), configManager, messages, sounds, foTeamsHook);
        activeBoosterService.start();
        api = new DefaultFoDropBoosterApi(activeBoosterService);
        getServer().getServicesManager().register(FoDropBoosterApi.class, api, this, ServicePriority.Normal);
        bossbarService = new BossbarService(core.scheduler(), configManager, activeBoosterService);
        bossbarService.start();
        updateNotices = core.createUpdateNotices(messages, MODRINTH_PROJECT_ID, adminSounds).start();

        DropBoostListener dropBoostListener = new DropBoostListener(core.scheduler(), configManager, activeBoosterService);
        editorManager = new EditorManager(this, core, editorSounds, configManager, messages, boosterItemService, activeBoosterService);
        getServer().getPluginManager().registerEvents(editorManager, this);
        getServer().getPluginManager().registerEvents(new BoosterCacheListener(activeBoosterService), this);
        getServer().getPluginManager().registerEvents(new BoosterItemListener(configManager, messages, sounds, boosterItemService, activeBoosterService, bossbarService), this);
        getServer().getPluginManager().registerEvents(dropBoostListener, this);
        new FoDropsRewardBridge(this, dropBoostListener).register();
        new FoLevelsXpBridge(this, activeBoosterService).register();
        if (getServer().getPluginManager().getPlugin("FoAutoCollect") != null) {
            getLogger().info("Hooked into FoAutoCollect item collection flow.");
        }
        if (foTeamsHook.available()) {
            getLogger().info("Hooked into FoTeams teams.");
        }

        FoDropBoosterAdminCommand adminCommand = new FoDropBoosterAdminCommand(this, configManager, messages, adminSounds, boosterItemService, activeBoosterService, editorManager, updateNotices, core.inventoryDeposits());
        PluginCommand command = getCommand("fodropboosteradmin");
        if (command != null) {
            command.setExecutor(adminCommand);
            command.setTabCompleter(adminCommand);
        } else {
            getLogger().severe("Command 'fodropboosteradmin' is missing from plugin.yml.");
        }
        DropBoosterBossbarCommand bossbarCommand = new DropBoosterBossbarCommand(messages, bossbarService, adminSounds);
        PluginCommand bossbarToggleCommand = getCommand("dropboosterbossbar");
        if (bossbarToggleCommand != null) {
            bossbarToggleCommand.setExecutor(bossbarCommand);
            bossbarToggleCommand.setTabCompleter(bossbarCommand);
        } else {
            getLogger().severe("Command 'dropboosterbossbar' is missing from plugin.yml.");
        }

        registerPlaceholderHook();
        getLogger().info("Enabled FoDropBooster.");
    }

    @Override
    public void onDisable() {
        if (api != null) {
            getServer().getServicesManager().unregister(FoDropBoosterApi.class, api);
            api = null;
        }
        if (editorManager != null) {
            editorManager.close();
        }
        if (bossbarService != null) {
            bossbarService.shutdown();
        }
        if (activeBoosterService != null) {
            activeBoosterService.shutdown();
        }
        updateNotices = null;
        if (core != null) {
            core.close();
            core = null;
        }
        getLogger().info("Disabled FoDropBooster.");
    }

    public void reloadFiles() {
        FoReloadResult result = FoReloadRegistry.create()
                .add("config", configManager::load)
                .add("core", this::reloadCoreContext)
                .add("sounds", sounds::reload)
                .addMessages(messages)
                .add("editor", () -> editorManager.reload(core, editorSounds))
                .add("active boosters", activeBoosterService::reload)
                .add("bossbar", bossbarService::restart)
                .reload();
        if (!result.successful()) {
            throw new IllegalStateException("Reload failed at " + result.failedStep() + ": " + result.errorMessage(), result.error());
        }
    }

    public FoDropBoosterApi getApi() {
        return api;
    }

    private void registerPlaceholderHook() {
        FoPlaceholders placeholders = core.placeholders("fodropbooster").logRegistration(false);
        registerMultiplierPlaceholder(placeholders, "active", player -> activeBoosterService.displayBoost(player).multiplier());
        registerMultiplierPlaceholder(placeholders, "dropactive", player -> activeBoosterService.effective(player, BoosterTarget.DROPS, null).multiplier());
        registerMultiplierPlaceholder(placeholders, "dropsactive", player -> activeBoosterService.effective(player, BoosterTarget.DROPS, null).multiplier());
        registerMultiplierPlaceholder(placeholders, "xpactive", player -> activeBoosterService.effective(player, BoosterTarget.FOLEVELS_XP, null).multiplier());
        registerMultiplierPlaceholder(placeholders, "levelxpactive", player -> activeBoosterService.effective(player, BoosterTarget.FOLEVELS_XP, null).multiplier());
        registerMultiplierPlaceholder(placeholders, "folevelsactive", player -> activeBoosterService.effective(player, BoosterTarget.FOLEVELS_XP, null).multiplier());
        registerMultiplierPlaceholder(placeholders, "teammultiplier", player -> activeBoosterService.displayBoost(player).teamMultiplier());
        placeholders.registerIfAvailable();
    }

    private void registerMultiplierPlaceholder(FoPlaceholders placeholders, String key, ToDoubleFunction<Player> valueProvider) {
        placeholders.player(key, player -> placeholderMultiplier(valueProvider.applyAsDouble(player), false));
        placeholders.player(key + "_raw", player -> placeholderMultiplier(valueProvider.applyAsDouble(player), true));
    }

    private String placeholderMultiplier(double value, boolean raw) {
        String formatted = activeBoosterService.formatMultiplier(value);
        return raw ? formatted : formatted + "x";
    }

    private void reloadCoreContext() {
        if (core != null) {
            core.close();
        }
        core = FoPluginCore.create(this);
        core.warnIfNativeDialogsUnavailable();
        core.metrics(BSTATS_PLUGIN_ID);
    }

    private FoMessageMigrations messageMigrations() {
        return FoMessageMigrations.create()
                .remove("messages.update")
                .removeIfMemorySectionString("general")
                .removeIfMemorySectionString("admin")
                .removeIfMemorySectionString("native-dialogs")
                .removeIfMemorySectionString("editor")
                .build();
    }

    private FoSoundMigrations soundMigrations() {
        return FoSoundMigrations.create()
                .move("editor.next-page", "editor.page-next")
                .move("editor.previous-page", "editor.page-previous")
                .replaceIfMatches("editor.open",
                        sound("BLOCK_NOTE_BLOCK_PLING", SoundCategory.PLAYERS, 0.45f, 1.35f),
                        sound("BLOCK_VAULT_OPEN_SHUTTER", SoundCategory.MASTER, 0.55f, 1.0f))
                .replaceIfMatches("editor.back",
                        sound("UI_BUTTON_CLICK", SoundCategory.PLAYERS, 0.35f, 0.85f),
                        sound("BLOCK_VAULT_CLOSE_SHUTTER", SoundCategory.MASTER, 0.55f, 0.95f))
                .replaceIfMatches("editor.page-next",
                        sound("UI_BUTTON_CLICK", SoundCategory.PLAYERS, 0.35f, 1.2f),
                        sound("BLOCK_BAMBOO_WOOD_BUTTON_CLICK_ON", SoundCategory.MASTER, 0.55f, 1.1f))
                .replaceIfMatches("editor.page-previous",
                        sound("UI_BUTTON_CLICK", SoundCategory.PLAYERS, 0.35f, 0.95f),
                        sound("BLOCK_CHERRY_WOOD_BUTTON_CLICK_OFF", SoundCategory.MASTER, 0.55f, 0.95f))
                .replaceIfMatches("editor.search",
                        sound("BLOCK_NOTE_BLOCK_HAT", SoundCategory.PLAYERS, 0.35f, 1.25f),
                        sound("BLOCK_CRAFTER_CRAFT", SoundCategory.MASTER, 0.55f, 1.0f))
                .replaceIfMatches("editor.clear-search",
                        sound("BLOCK_NOTE_BLOCK_HAT", SoundCategory.PLAYERS, 0.35f, 0.85f),
                        sound("BLOCK_COPPER_BULB_TURN_OFF", SoundCategory.MASTER, 0.55f, 1.0f))
                .replaceIfMatches("editor.toggle-on",
                        sound("BLOCK_LEVER_CLICK", SoundCategory.PLAYERS, 0.4f, 1.25f),
                        sound("BLOCK_VAULT_INSERT_ITEM", SoundCategory.MASTER, 1.0f, 1.5f))
                .replaceIfMatches("editor.toggle-off",
                        sound("BLOCK_LEVER_CLICK", SoundCategory.PLAYERS, 0.4f, 0.75f),
                        sound("BLOCK_VAULT_INSERT_ITEM_FAIL", SoundCategory.MASTER, 1.0f, 1.5f))
                .replaceIfMatches("editor.add",
                        sound("ENTITY_ITEM_PICKUP", SoundCategory.PLAYERS, 0.45f, 1.2f),
                        sound("BLOCK_VAULT_INSERT_ITEM", SoundCategory.MASTER, 0.55f, 1.1f))
                .replaceIfMatches("editor.delete",
                        sound("ENTITY_ITEM_BREAK", SoundCategory.PLAYERS, 0.45f, 0.9f),
                        sound("BLOCK_VAULT_EJECT_ITEM", SoundCategory.MASTER, 0.6f, 0.9f))
                .replaceIfMatches("editor.cycle",
                        sound("UI_BUTTON_CLICK", SoundCategory.PLAYERS, 0.35f, 1.4f),
                        sound("BLOCK_COPPER_BULB_TURN_ON", SoundCategory.MASTER, 1.0f, 0.5f))
                .replaceIfMatches("editor.save",
                        sound("ENTITY_EXPERIENCE_ORB_PICKUP", SoundCategory.PLAYERS, 0.4f, 1.4f),
                        sound("BLOCK_VAULT_ACTIVATE", SoundCategory.MASTER, 0.65f, 1.0f))
                .replaceIfMatches("editor.error",
                        sound("ENTITY_VILLAGER_NO", SoundCategory.PLAYERS, 0.45f, 0.85f),
                        sound("BLOCK_VAULT_INSERT_ITEM_FAIL", SoundCategory.MASTER, 0.75f, 1.0f))
                .replaceIfMatches("admin.reload",
                        sound("BLOCK_NOTE_BLOCK_PLING", SoundCategory.PLAYERS, 0.5f, 1.25f),
                        sound("BLOCK_BEACON_ACTIVATE", SoundCategory.MASTER, 0.8f, 1.2f))
                .replaceIfMatches("admin.reload-error",
                        sound("ENTITY_VILLAGER_NO", SoundCategory.PLAYERS, 0.5f, 0.75f),
                        sound("BLOCK_TRIAL_SPAWNER_OMINOUS_ACTIVATE", SoundCategory.MASTER, 0.8f, 0.9f))
                .replaceIfMatches("admin.update-latest",
                        sound("BLOCK_NOTE_BLOCK_PLING", SoundCategory.PLAYERS, 0.45f, 1.4f),
                        sound("BLOCK_TRIAL_SPAWNER_EJECT_ITEM", SoundCategory.MASTER, 0.8f, 1.0f))
                .replaceIfMatches("admin.update-available",
                        sound("ENTITY_PLAYER_LEVELUP", SoundCategory.PLAYERS, 0.55f, 1.1f),
                        sound("BLOCK_TRIAL_SPAWNER_OPEN_SHUTTER", SoundCategory.MASTER, 0.7f, 0.9f))
                .replaceIfMatches("admin.update-error",
                        sound("ENTITY_VILLAGER_NO", SoundCategory.PLAYERS, 0.45f, 0.8f),
                        sound("BLOCK_TRIAL_SPAWNER_DETECT_PLAYER", SoundCategory.MASTER, 0.8f, 1.0f))
                .replaceIfMatches("booster.activate-error",
                        sound("ENTITY_VILLAGER_NO", SoundCategory.PLAYERS, 0.45f, 0.8f),
                        sound("BLOCK_VAULT_INSERT_ITEM_FAIL", SoundCategory.PLAYERS, 0.75f, 1.0f))
                .remove("editor.add-item")
                .remove("editor.add-item-error")
                .build();
    }

    private static FoSound sound(String key, SoundCategory category, float volume, float pitch) {
        return new FoSound(key, category, volume, pitch, true);
    }
}
