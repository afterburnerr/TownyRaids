> Каждый блок ниже подписан названием поля в форме ресурса. Скопируй содержимое блока (всё, что **после** строки `===`) в одноимённое поле на платформе.

---

## Tag Line
=== скопируй ниже ===

Full-featured raids between Towny towns: preparation, battle, tribute and a complete admin toolbox.

---

## Title / Name
=== скопируй ниже ===

TownyRaids

---

## Version
=== скопируй ниже ===

1.0.0

---

## Native Minecraft Version
=== скопируй ниже ===

1.21.4

---

## Tested Minecraft Versions
=== скопируй ниже ===

1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4

---

## Description
=== скопируй ниже ===

# TownyRaids

A Paper addon for [Towny Advanced](https://github.com/TownyAdvanced/Towny) that adds a complete town-vs-town raid system. Declare a raid on a rival town, watch the preparation timer count down, fight through the battle phase with custom flags, or settle the conflict with a tribute payment between town banks.

## Highlights

- **Two-phase lifecycle.** A `PENDING` preparation window gives the defender time to react, then `BATTLE` flips the configured flags (PvP, explosions, fire, mob spawning) on the defender's town until the timer runs out.
- **Boss bars + titles.** Every raid shows a synchronized boss bar with the live phase timer to both attacker and defender residents, plus titles on every transition.
- **Tribute system.** The attacker can demand a tribute (`/t tribute request <amount>`); the defender accepts or denies (`/t tribute accept|deny`) — money moves between town banks through Towny's economy and the raid is cancelled.
- **Persistent state.** All active raids and cooldowns are stored in SQLite (default) or MySQL via HikariCP. The plugin survives restarts and resumes scheduled phase transitions on startup.
- **Admin toolbox.** Full `/ta raids` command for reloads and forced state changes.
- **Cooldowns + protections.** After a raid ends the attacker enters a cooldown and the defender enters a protection window — both configurable.
- **Confirmation flow.** Declarations go through Towny's native confirmation prompt so misclicks are safe.
- **MiniMessage everywhere.** Every visible string is in `lang/<code>.yml`, parsed with MiniMessage. Bundled translations: English, Russian, Spanish, Turkish.

## Requirements

- Paper **1.21.4** (or compatible)
- Towny **0.102.0.14** (or newer)
- Java **21**
- A Vault-compatible economy plugin

## Quick start

1. Install Towny and a Vault-compatible economy.
2. Drop `TownyRaids.jar` into `plugins/`.
3. Start the server once to generate `config.yml` and the `lang/` folder.
4. Adjust `language: en|ru|es|tr` and tune timings/flags as needed.
5. Players can now use `/t raid <town>` to declare.

Source code & issues: https://github.com/afterburnerr/TownyRaids

---

## Documentation
=== скопируй ниже ===

# Commands

| Command | Description |
|---|---|
| `/t raid <town>` | Open a Towny confirmation, then start the preparation phase. |
| `/t raid cancel` | Cancel your own raid during preparation. |
| `/t tribute request <amount>` | Attacker offers to cancel the raid in exchange for a tribute. |
| `/t tribute accept` | Defender accepts the tribute and the raid is cancelled. |
| `/t tribute deny` | Defender rejects the tribute offer. |
| `/ta raids reload` | Hot-reload `config.yml` and the active language file. |
| `/ta raids start <attacker> <defender>` | Force a raid without confirmation. |
| `/ta raids end <town>` | Force-end the raid involving a town. |
| `/ta raids phase <town> <pending\|battle>` | Move an active raid between phases. |

# Permissions

| Node | Description |
|---|---|
| `townyraids.raid.declare` | Required for `/t raid <town>`. |
| `townyraids.raid.cancel` | Required for `/t raid cancel`. |
| `townyraids.tribute.request` | Required for `/t tribute request`. |
| `townyraids.tribute.accept` | Required for `/t tribute accept` / `deny`. |
| `townyraids.admin` | Root access to the admin command. |
| `townyraids.admin.reload` | `/ta raids reload`. |
| `townyraids.admin.force-start` | `/ta raids start`. |
| `townyraids.admin.force-end` | `/ta raids end`. |
| `townyraids.admin.force-phase` | `/ta raids phase`. |

Player commands additionally require the player to be the mayor or an assistant of the town.

# Configuration (`config.yml`)

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
  sqlite-file: storage.db
  mysql:
    host: localhost
    port: 3306
    database: townyraids
    username: root
    password: ""
```

All durations are in seconds. Set `tribute.max-amount: 0.0` to remove the upper bound. Switch `storage.type` to `mysql` for shared-state clusters.

# Languages

Bundled language files live in `plugins/TownyRaids/lang/`:

- `en.yml` — English
- `ru.yml` — Русский
- `es.yml` — Español
- `tr.yml` — Türkçe

Every value is parsed as [MiniMessage](https://docs.advntr.dev/minimessage/). Missing keys fall back to English. Set `language` in `config.yml` to switch the active file or drop your own translation in.

# Lifecycle

1. **Declaration.** Attacker mayor runs `/t raid <town>`, gets a Towny confirmation prompt.
2. **Preparation (`PENDING`).** Configurable countdown, defender can call players, prepare defenses or pay tribute. Attacker can still cancel with `/t raid cancel`.
3. **Battle (`BATTLE`).** Defender's flags switch to the configured `battle-flags` (PvP, explosions, fire, mobs). Boss bar + title notify everyone.
4. **End.** When the battle timer runs out (or an admin forces it), original flags are restored, attacker enters cooldown, defender enters protection.

---

## Source Code
=== скопируй ниже ===

https://github.com/afterburnerr/TownyRaids

---

## Donation Link
=== скопируй ниже ===

(оставь пустым или вставь свою ссылку)

---

## External Resource Links / Issues
=== скопируй ниже ===

https://github.com/afterburnerr/TownyRaids/issues
