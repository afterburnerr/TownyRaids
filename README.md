# TownyRaids

[![Build](https://github.com/afterburnerr/TownyRaids/actions/workflows/build.yml/badge.svg)](https://github.com/afterburnerr/TownyRaids/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Paper](https://img.shields.io/badge/Paper-1.21.4-yellow.svg)](https://papermc.io)
[![Towny](https://img.shields.io/badge/Towny-0.102.0.14-orange.svg)](https://github.com/TownyAdvanced/Towny)

A Paper plugin that adds a full raid system to [Towny Advanced](https://github.com/TownyAdvanced/Towny). Players can declare raids between towns, go through preparation and battle phases, pay or demand tribute, and admins get a complete toolbox through `/ta raids`.

Tested against Paper `1.21.4` and Towny `0.102.0.14`.

## Features

- Declare raids with `/t raid <town>`, confirmed through Towny's native confirmation prompt.
- Two-phase raid lifecycle (`PENDING` preparation → `BATTLE` → cooldown + protection).
- Configurable preparation, battle, attacker-cooldown and defender-protection windows.
- Boss bars on both towns for each phase; titles for every transition.
- Tribute system (`/t tribute request|accept|deny`) that transfers money between town banks through Towny's economy handler.
- Admin tools (`/ta raids reload|start|end|phase`) with per-command permissions.
- Persistence via HikariCP (SQLite by default, MySQL optional). Raids and cooldowns survive restarts.
- Fully translated: English, Russian, Spanish, Turkish (via `lang/<code>.yml`); all messages use MiniMessage.

## Installation

1. Install [Towny](https://modrinth.com/plugin/towny) and a Vault-compatible economy.
2. Drop `TownyRaids-<version>.jar` (from `build/libs`) into `plugins/`.
3. Start the server once to generate `config.yml` and the `lang/` directory.
4. Adjust `language: en|ru|es|tr` in `config.yml` and tune timings/flags as needed.

## Configuration

Every number is in seconds unless stated otherwise. Commented reference in the shipped `config.yml`.

```yaml
language: en

timings:
  preparation-seconds: 300
  battle-seconds: 900
  attacker-cooldown-seconds: 86400
  defender-protection-seconds: 172800
  tribute-request-seconds: 120
  confirmation-seconds: 30

requirements:
  defender-min-residents: 3
  attacker-min-residents: 3
  require-different-nations: true
  block-declaring-while-under-raid: true

battle-flags:
  pvp: true
  explosion: true
  fire: true
  mobs: true

tribute:
  enabled: true
  min-amount: 100.0
  max-amount: 0.0

storage:
  type: sqlite
  sqlite-file: "storage.db"
```

Switch `storage.type` to `mysql` and fill in the `mysql:` section for shared-state clusters.

## Commands

| Command | Description |
|---|---|
| `/t raid <town>` | Opens a Towny confirmation and starts the preparation phase on accept. |
| `/t raid cancel` | Cancels your own raid during the preparation phase. |
| `/t tribute request <amount>` | Attacker offers to cancel the raid in exchange for a tribute. |
| `/t tribute accept` | Defender pays the demanded tribute from the town bank. |
| `/t tribute deny` | Defender rejects the tribute offer. |
| `/ta raids reload` | Reloads `config.yml` and the active language file. |
| `/ta raids start <attacker> <defender>` | Forces a raid without confirmation. |
| `/ta raids end <town>` | Force-ends the raid involving a town. |
| `/ta raids phase <town> <pending\|battle>` | Jumps an active raid between phases. |

## Permissions

| Node | Default | Purpose |
|---|---|---|
| `townyraids.raid.declare` | op | Required for `/t raid <town>`. |
| `townyraids.raid.cancel` | op | Required for `/t raid cancel`. |
| `townyraids.tribute.request` | op | Required for `/t tribute request`. |
| `townyraids.tribute.accept` | op | Required for `/t tribute accept|deny`. |
| `townyraids.admin` | op | Root access to the admin command. |
| `townyraids.admin.reload` | op | `/ta raids reload`. |
| `townyraids.admin.force-start` | op | `/ta raids start`. |
| `townyraids.admin.force-end` | op | `/ta raids end`. |
| `townyraids.admin.force-phase` | op | `/ta raids phase`. |

Player commands additionally require the player to be the mayor or an assistant of the town.

## Languages

Bundled language files live in `src/main/resources/lang/`:

- `en.yml` — English
- `ru.yml` — Русский
- `es.yml` — Español
- `tr.yml` — Türkçe

All files share the same structure; edit any file on disk to override the shipped defaults. Missing keys fall back to `en.yml`. Every value is parsed as [MiniMessage](https://docs.advntr.dev/minimessage/).

## Architecture

Package overview (`src/main/java/gg/afterburner/townyRaids/`):

```
bootstrap/        PluginBootstrap, PluginShutdown, PluginRuntime
config/           ConfigManager, MessagesManager, RaidSettings
command/          RaidCommand, TributeCommand, admin/AdminRaidsCommand
cooldown/         CooldownStore
database/         DatabaseManager, RaidRepository, CooldownRepository
hud/              RaidBossBarManager, RaidTitleManager
listener/         PlayerConnectionListener, TownyTownLifecycleListener
raid/
  RaidManager                 thin facade
  model/                      Raid, RaidPhase, RaidFlagsSnapshot
  lifecycle/                  RaidRegistry, RaidDeclarationValidator,
                              RaidPhaseTransitionService, RaidStateRestorer
  schedule/                   RaidScheduler
  world/                      RaidFlagApplier
  notification/               RaidBroadcaster
  result/                     sealed RaidDeclarationResult, TributeResult,
                              RaidCancellationResult
tribute/          TributeManager, TributeRequest
util/             Permissions, TownyUtil, DurationFormatter
```

Each package has a clear single responsibility. `RaidManager` is the only public entry point for the rest of the plugin; it delegates validation, persistence, scheduling, world changes and notifications to dedicated services.

## Building

```
./gradlew shadowJar
```

The relocated shaded jar ends up in `build/libs/TownyRaids-<version>.jar`. HikariCP, SQLite and SLF4J are relocated into `gg.afterburner.townyRaids.libs.*` to avoid clashes with other plugins.

## License

MIT.
