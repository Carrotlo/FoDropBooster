# FoDropBooster

[![Modrinth](https://img.shields.io/badge/Download-Modrinth-1bd96a?logo=modrinth&logoColor=white)](https://modrinth.com/plugin/fodropbooster) [![Versions](https://img.shields.io/badge/Supported_versions-Modrinth-1bd96a)](https://modrinth.com/plugin/fodropbooster/versions) [![Discord](https://img.shields.io/badge/Support-Discord-5865f2?logo=discord&logoColor=white)](https://discord.gg/kEF77QCfu7)

Timed drop and FoLevels XP boosters across personal and shared scopes.

*Developed by Carrotio.*

## Features

- Runs timed personal, team, world, and global boosters.
- Supports item-drop and FoLevels XP targets.
- Shows active boost information through bossbars and placeholders.
- Provides configurable booster items and an in-game editor.
- Exposes a Bukkit service API for external boost ownership and cleanup.

## Download and dependencies

Download the latest compatible version from [Modrinth](https://modrinth.com/plugin/fodropbooster). Check the [versions page](https://modrinth.com/plugin/fodropbooster/versions) for supported Minecraft versions and server platforms.

- **Required:** None
- **Optional integrations:** `FoDrops`, `FoAutoCollect`, `FoTeams`, `FoLevels`, `PlaceholderAPI`

## Support

For help or issue reports, join the [Carrotio Discord server](https://discord.gg/kEF77QCfu7).

<details>
<summary><strong>Commands and permissions</strong></summary>

### Commands

- `/fodropboosteradmin <version|reload|editor|give|removeactive>` — FoDropBooster admin command.
- `/dropboosterbossbar [on|off]` — Toggle your FoDropBooster bossbar.

### Permissions

- `fodropbooster.admin` — Allows managing FoDropBooster. Default: `op`.
- `fodropbooster.togglebossbar` — Allows toggling the personal FoDropBooster bossbar. Default: `true`.

</details>

<details>
<summary><strong>PlaceholderAPI</strong></summary>

Requires PlaceholderAPI.

- `%fodropbooster_active% / %fodropbooster_active_raw%` — Displayed active multiplier.
- `%fodropbooster_dropactive% / %fodropbooster_dropactive_raw%` — Effective drop multiplier.
- `%fodropbooster_dropsactive% / %fodropbooster_dropsactive_raw%` — Alias for the effective drop multiplier.
- `%fodropbooster_xpactive% / %fodropbooster_xpactive_raw%` — Effective FoLevels XP multiplier.
- `%fodropbooster_levelxpactive% / %fodropbooster_levelxpactive_raw%` — Alias for the XP multiplier.
- `%fodropbooster_folevelsactive% / %fodropbooster_folevelsactive_raw%` — Alias for the XP multiplier.
- `%fodropbooster_teammultiplier% / %fodropbooster_teammultiplier_raw%` — Displayed team multiplier.

</details>

<details>
<summary><strong>Configuration</strong></summary>

- `config.yml` — Main behavior and feature settings.
- `messages.yml` — Player-facing messages and interface text.
- `sounds.yml` — Configurable interaction sounds.
- `boosters/*.yml` — Booster item definitions.

Main config areas: `settings`, `native-dialogs`, `events`, `disabled-worlds`, `bossbar`, `custom-item`, `broadcast`.

</details>

<details>
<summary><strong>Developer API</strong></summary>

`FoDropBoosterApi` is registered with Bukkit's services manager after boosters and the bossbar initialize, and unregistered on disable. Compile against the matching FoDropBooster jar as `provided` or `compileOnly`; do not shade it. The Maven coordinates below are for a **locally installed** 2.3 jar, not a claim that an artifact is published:

~~~sh
mvn install:install-file -Dfile=target/FoDropBooster-2.3.jar -DgroupId=me.foesio -DartifactId=FoDropBooster -Dversion=2.3 -Dpackaging=jar
~~~

~~~xml
<dependency>
    <groupId>me.foesio</groupId>
    <artifactId>FoDropBooster</artifactId>
    <version>2.3</version>
    <scope>provided</scope>
</dependency>
~~~

In a `plugin.yml` consumer, add `softdepend: [FoDropBooster]` (or `depend` if required). Keep direct FoDropBooster class references in a separate integration class, loaded only after the provider is enabled. Reacquire the Bukkit service after a provider restart. For Paper plugin metadata, declare a server dependency on FoDropBooster with `join-classpath: true` if directly linking its classes.

~~~java
FoDropBoosterApi api = Bukkit.getServicesManager().load(FoDropBoosterApi.class);
if (api != null) {
    api.setGlobalDropBoost(this, "event", 0.5); // additive +0.5x, until removed or this plugin disables
    double miningMultiplier = api.effectiveDropMultiplier(player, FoDropBoosterDropCause.BLOCK_BREAK);
    FoDropBoosterActivationResult result = api.activatePreset(this, player, "basic");
    if (result.success()) {
        String timedBoosterId = result.booster().id();
    }
}
~~~

The existing `setBoost` overloads support personal, FoTeams team, world, and global scopes for drops or FoLevels XP. External boosts stack additively, remain in memory only, and are removed when their owning plugin disables. `teamBoostBonus` returns only the external additive team bonus. The effective multiplier queries include timed, passive team, and external bonuses and return `1.0` when none applies or the target/cause is disabled. They report the multiplier before item caps and protected-item exclusions.

`activatePreset(caller, player, presetId)` requires an enabled caller plugin, an online player, and an exact configured preset. It returns a status and an immutable snapshot on success, or a status with no snapshot on failure. It follows the same world, FoTeams, FoLevels, conflict, and persistence rules as item activation. It consumes no item and sends no messages, sounds, or broadcasts; the caller handles those. It refreshes the bossbar. `activeTimedBoosters()` returns an immutable list of immutable stored-booster snapshots, including boosters currently ineffective in a disabled world. It omits expired entries without changing stored state. External bonuses are not included. Snapshot owner-player fields are populated only for personal and team boosters.

Listen to `FoDropBoosterPreActivateEvent` to cancel a validated timed activation before the booster is stored or an item is consumed. `FoDropBoosterActivatedEvent` fires once after storage and, for an item, after consumption. Both expose a snapshot and `ITEM` or `PRESET_API` source; the caller plugin is null for item use. `FoDropBoosterEndedEvent` fires after expiry or admin/editor removal, with `EXPIRED` or `ADMIN_REMOVED`. Startup restoration, reload replacement, plugin disable, rejected requests, and external bonus changes do not fire these timed-booster events. Events reflect completed **in-memory** changes; a failed `active.yml` write is logged but does not change the result.

Call player queries and preset activation on the player's owning server thread. Call the remaining API methods from server event/scheduler context, never an arbitrary async worker. The event async flag reflects the dispatch thread; expiry can be dispatched by the global scheduler, so an ended-event listener must schedule any player/world work appropriately. Regionized/Folia execution has not been verified for this plugin.

### Event listener example

Inside a Bukkit `Listener` registered by your plugin (import this event class, `org.bukkit.event.EventHandler`, and `org.bukkit.Bukkit` where used):

```java
@EventHandler
public void onFoDropBoosterActivated(FoDropBoosterActivatedEvent event) {
    Bukkit.getLogger().info("Booster activated via " + event.getSource());
}
```

### Complete public API method index

Signatures use short type names; import Fo types from this plugin's `api` package and Bukkit types from Bukkit.

#### `FoDropBoosterApi`

```java
boolean setPlayerDropBoost(Plugin, String, UUID, double);
boolean setPlayerBoost(Plugin, String, UUID, FoDropBoosterTarget, double);
boolean setTeamDropBoost(Plugin, String, String, double);
boolean setTeamBoost(Plugin, String, String, FoDropBoosterTarget, double);
boolean setGlobalDropBoost(Plugin, String, double);
boolean setGlobalBoost(Plugin, String, FoDropBoosterTarget, double);
boolean setWorldDropBoost(Plugin, String, String, double);
boolean setWorldBoost(Plugin, String, String, FoDropBoosterTarget, double);
boolean setBoost(Plugin, String, FoDropBoosterTarget, FoDropBoosterScope, String, double);
boolean removeTeamBoost(Plugin, String);
boolean removeBoost(Plugin, String);
int removeTeamBoosts(Plugin);
int removeBoosts(Plugin);
double teamDropBoostBonus(String);
double teamBoostBonus(String, FoDropBoosterTarget);
double effectiveDropMultiplier(Player, FoDropBoosterDropCause);
double effectiveFoLevelsXpMultiplier(Player);
FoDropBoosterActivationResult activatePreset(Plugin, Player, String);
List<FoDropBoosterSnapshot> activeTimedBoosters();
```

**Other public contract types:** `FoDropBoosterActivationResult`, `FoDropBoosterActivationSource`, `FoDropBoosterActivationStatus`, `FoDropBoosterDropCause`, `FoDropBoosterEndReason`, `FoDropBoosterScope`, `FoDropBoosterSnapshot`, `FoDropBoosterTarget`.

### Complete Bukkit event index

- `FoDropBoosterActivatedEvent`.
- `FoDropBoosterEndedEvent`.
- `FoDropBoosterPreActivateEvent`.

</details>
