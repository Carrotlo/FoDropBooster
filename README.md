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

Compile against the matching jar (`provided` in Maven, `compileOnly` in Gradle); install it locally if needed. Declare `FoDropBooster` in your plugin metadata (`softdepend` for an optional integration).

```xml
<dependency>
  <groupId>me.foesio</groupId>
  <artifactId>FoDropBooster</artifactId>
  <version>2.3</version>
  <scope>provided</scope>
</dependency>
```

```java
import me.foesio.foDropBooster.api.FoDropBoosterApi;
import me.foesio.foDropBooster.api.event.FoDropBoosterActivatedEvent;

FoDropBoosterApi api = Bukkit.getServicesManager().load(FoDropBoosterApi.class);
if (api != null) api.activeTimedBoosters();

@EventHandler
public void onFoDropBoosterActivatedEvent(FoDropBoosterActivatedEvent event) {
    Bukkit.getLogger().info(event.getClass().getSimpleName());
}
```

**API methods** (overloads grouped):

- `FoDropBoosterApi`: `setPlayerDropBoost`, `setPlayerBoost`, `setTeamDropBoost`, `setTeamBoost`, `setGlobalDropBoost`, `setGlobalBoost`, `setWorldDropBoost`, `setWorldBoost`, `setBoost`, `removeTeamBoost`, `removeBoost`, `removeTeamBoosts`, `removeBoosts`, `teamDropBoostBonus`, `teamBoostBonus`, `effectiveDropMultiplier`, `effectiveFoLevelsXpMultiplier`, `activatePreset`, `activeTimedBoosters`.

**Other API types:** `FoDropBoosterActivationResult`, `FoDropBoosterActivationSource`, `FoDropBoosterActivationStatus`, `FoDropBoosterDropCause`, `FoDropBoosterEndReason`, `FoDropBoosterScope`, `FoDropBoosterSnapshot`, `FoDropBoosterTarget`.

**Events:** `FoDropBoosterActivatedEvent`, `FoDropBoosterEndedEvent`, `FoDropBoosterPreActivateEvent`.

[Browse API source](src/main/java/me/foesio/foDropBooster/api) for signatures and event details.

</details>
