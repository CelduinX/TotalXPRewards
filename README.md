# Total XP Rewards

A Paper plugin that tracks each player's **rank XP** and executes **custom rewards** when milestones are reached. Vanilla XP remains unchanged.
Fully configurable, translation-ready, and built for Paper 26.2 (Java 25).

## Building and updating

With JDK 25 installed, run `./gradlew build` (`gradlew.bat build` on Windows).
The plugin JAR is written to `build/libs/TotalXPRewards-1.4.0-mc26.2.jar`.
The build runs regression tests for budgets, activity, farms, commands, rewards and SQLite persistence.

Stop the server before replacing the old TotalXPRewards JAR. Keep the existing
`plugins/TotalXPRewards` folder, including `config.yml`, `ranks.yml`, `lang.yml`, and
`totalxp.db`. Version 1.1.0 adds a `progression` config section and a
`player_progression` SQLite table. Existing XP, reward history and custom ranks are retained.

Version 1.2.0 requires LuckPerms and an explicit `group` for every reward threshold.
The bundled configuration maps all 100 thresholds to existing server groups;
create those groups in LuckPerms before using this configuration elsewhere.
Existing installations must add their own explicit mappings to `ranks.yml`.
Missing groups disable rank progression. Keep `totalxp.db` and its reward history.

Version 1.3.0 moves every rank and reward definition into `ranks.yml`.
`config.yml` retains general settings. On upgrade from an older configuration,
the plugin backs up `config.yml`, writes its `rewards` section to `ranks.yml`,
then removes that section from `config.yml`. If both files already define ranks,
startup stops for manual review rather than choosing one silently. Back up both
files before upgrading; the server's existing values take priority over the
bundled defaults.

## MiniMessage rank suffix (1.3.1)

Rank names in `ranks.yml` support MiniMessage, including gradients. On startup,
the plugin writes the plain text name as the LuckPerms display name and a
compact legacy/hex-color suffix at priority 40. EssentialsX Chat can render
that suffix. The suffix contains the colored rank name without added brackets
or a forced yellow color or leading space. Existing rank group metadata is refreshed on startup.
The compact color format keeps the LuckPerms H2 permission key within its
200-character limit for the bundled ranks.

## SelfUnlock and LuckPerms ranks (1.2.0)

Only a direct, global, permanent LuckPerms `spieler` parent unlocks rank XP and
rank rewards. Effective inheritance and permissions do not count. Vanilla XP is
unchanged. SelfUnlock should grant `spieler` with `lp user {uuid} parent add spieler`
and use a private, strong password. TotalXPRewards never grants `spieler`.

Each reward threshold in `ranks.yml` has an explicit LuckPerms group, for example:

```yaml
rewards:
  '3260':
    group: xp_abenteurer
    name: '&aAbenteurer'
  '30000':
    group: xp_legende
    name: '&6Legende'
```

Rank changes remove only previous XP rank parents and add the matching new
parent through the LuckPerms API. Independent and team parents are retained.
The plugin reconciles ranks at login and after the LuckPerms unlock mutation.
Reconciliation does not run item rewards again. Rank groups receive a display
name, suffix at priority 40 and weight 40; moderator/admin should have higher
weights so they remain visible with `primary-group-calculation: parents-by-weight`.
Inspect users with old XP groups but without direct `spieler` individually;
old rank membership is not proof of SelfUnlock completion.

For a running local server with RCON already enabled, run
`python tools/paper_smoke_test.py --server ../../server --stop` to check plugin
startup, console commands, reload, and config preservation, then stop gracefully.
This smoke test does not exercise a real player's in-game interactions.

## Rank progression protection (1.1.0)

The default budget starts at 1,000 rank XP and refills at 500 XP per active hour,
up to 1,000. At a 30,000 XP final rank, new players need at least 58 active hours.
Mining, placing blocks, dealing damage, shooting and inventory changes renew a
120-second activity window. Moving at least two blocks with directional/jump input
also renews it; passive water movement, vehicles, flight, teleports, looking,
chat and XP pickup do not. Offline, idle, Creative and Spectator time never refills it.
Activity checks cannot reliably identify macros. A main-thread stall over five
seconds is not credited. Relogging and reloading do not reset the budget.

After 30 player-attributed mob kills within 300 seconds and a 24-block radius of
the latest kill, that kill's XP counts at 10% before applying the budget. Types
and spawn reasons are combined. Player deaths, Ender Dragon, Wither and Warden
are excluded from this extra reduction, but their XP still uses the budget.
Farm factors follow the orbs, including transfer between players and merges;
mixed orbs use the lowest factor. To cover Paper's spawn-time stacking, existing
orbs within 1.5 blocks of a reduced death are conservatively tagged as well.
Unknown sources receive only the budget limit.

Only accepted XP consumes budget. Fractions persist, and surplus is discarded
for rank progression; vanilla XP, mending and enchanting are untouched.
`%xp%`, BossBars and existing rank thresholds show accepted rank XP.
`/totalxp status [player]` shows budget, activity and the last limiting reason;
only admins can inspect others. Actionbar notices are limited to once a minute.
`/totalxp set` is an admin correction without a budget refill or retroactive rewards.
`/totalxp reset` also clears protection history and restores the start budget.
There is no automatic OP exemption.

All settings below are configurable. Invalid reloads retain the active settings;
invalid startup configurations prevent the plugin from enabling.

```yaml
progression:
  enabled: true
  budget-capacity: 1000
  refill-per-active-hour: 500
  activity-timeout-seconds: 120
  farm:
    enabled: true
    window-seconds: 300
    radius: 24
    kill-threshold: 30
    factor: 0.10
```

XP, budget, fractions, active time and recent kills are checkpointed atomically
every 60 seconds, on logout and on clean shutdown. A process crash can lose the
last checkpoint interval. The existing language file is retained; new message
keys use bundled defaults when absent. Custom BossBar/messages can be labelled
"Rang-XP" to distinguish progression from vanilla experience.

## Configurable BossBar rank progress (1.1.1)

The default title displays `Wanderer · Rang 4/100 → Sammler · 16/180 XP`
at 626 cumulative rank XP when Wanderer starts at 610 and Sammler at 790.
Both the title and bar fill use progress within the current rank. The XP display
resets to zero exactly at rank-up; cumulative XP remains stored.

```yaml
bossbar:
  title: "&e%current_rank%&r &7· Rang %rank_number%/%rank_count% &7→ &e%next_rank%&r &7· &a%rank_xp%&7/&e%rank_required_xp% &7XP"
  max-rank-title: "&e%current_rank%&r &7· Rang %rank_number%/%rank_count% &7· Höchster Rang erreicht"
  no-ranks-title: "&7Keine Ränge konfiguriert"
```

All titles support legacy colors, MiniMessage and placeholders. Rank numbers are
derived from the configured XP thresholds, not LuckPerms group names: rank 0
before the first threshold, then ranks 1 through the actual configured count.
The highest rank displays the configurable maximum title and a full bar; an
empty rank list displays the empty-list title and an empty bar. At maximum rank,
the local earned/required/remaining XP placeholders are zero. Existing custom
titles remain unchanged on upgrade; the new defaults can be copied manually.

---

## ✨ Features

- **Global Total XP Tracking** 📈
  - Tracks XP from killing mobs, mining, **and** vanilla commands (`/xp`, `/experience`).
  - Never resets, even after death.
  - **Caching**: Data is preloaded asynchronously and gameplay gains are processed in memory.
- **BossBar Progress System** 📊
  - Displays a customizable BossBar showing progress to the next rank.
  - **Dynamic Mode**: Auto-hides the bar when not gaining XP.
  - **Rich Text support**: Supports **MiniMessage** (Gradients, RGB) AND Legacy Color Codes (`&a`) simultaneously!
- **Reward System** 🎁
  - Execute multiple commands when reaching a threshold.
  - Send custom broadcast messages.
  - Supports **Minecraft Target Selectors** in commands (e.g., `@a`, `@p`).
- **Full Customization** 🛠️
  - **PlaceholderAPI** support.
  - Complete language control via `lang.yml` (including "Max Rank" text).
  - **SQLite** storage with automatic schema migration (external apps can read `current_rank`).

---

## 📥 Installation

1. Download the latest release from the **Releases** page.
2. Drop the `.jar` file into your server's `plugins` folder.
3. Start the server to generate config files.
4. Adjust `config.yml` and `lang.yml` to your liking.
5. Restart or run:
   ```
   /txp reload
   ```

---

## ⚙️ Configuration

### `config.yml` (settings example)

```yaml
bossbar:
  enabled: true
  # Hybrid Support: Mix Legacy (&) and MiniMessage (<gradient>)!
  title: "&bCurrent Rank: &e%current_rank% &7| <gradient:blue:aqua>Next: %next_rank%</gradient> &7(&a%xp%&7/&c%required_xp%&7)"
  color: BLUE
  style: SOLID
  dynamic-mode: true # Bar appears on XP gain and hides after timeout
  timeout: 10
```

### `ranks.yml` (rank and reward example)

```yaml
rewards:
  "1000":
    group: xp_novice
    name: "<gradient:#2486B5:#3A816A>Novice</gradient>"
    commands:
      - "give %player% diamond 1"
      - "eco give %player% 250"
    broadcast: "&a%player% reached %threshold% XP (Novice)!"

  "50000":
    group: xp_master
    name: "Master"
    commands:
      - "give %player% netherite_ingot 1"
    broadcast: "&6%player% is now a Master!"
```

---

## 🧩 Placeholders

### PlaceholderAPI in TAB und anderen Plugins (ab 1.4.0)

TotalXPRewards registriert beim Serverstart automatisch eine interne
PlaceholderAPI-Erweiterung, wenn PlaceholderAPI installiert und aktiviert ist.
Ein separater `/papi ecloud download` ist nicht nötig. Alle Werte beziehen sich
auf den Spieler, für den das andere Plugin den Platzhalter auswertet.

| PlaceholderAPI-Platzhalter | Wert |
| :--- | :--- |
| `%totalxprewards_xp%` | Gesammelte, akzeptierte Rang-XP insgesamt |
| `%totalxprewards_rank_number%` | Rangnummer; 0 vor dem ersten Rang |
| `%totalxprewards_rank_count%` | Anzahl der konfigurierten Ränge |
| `%totalxprewards_rank_xp%` | XP seit Beginn des aktuellen Rangs |
| `%totalxprewards_rank_required_xp%` | XP-Abstand vom aktuellen zum nächsten Rang |
| `%totalxprewards_rank_remaining_xp%` | Bis zum nächsten Rang fehlende XP |
| `%totalxprewards_rank_start_xp%` | Gesamte XP an der aktuellen Rangschwelle; vor dem ersten Rang 0 |
| `%totalxprewards_next_rank_xp%` | Gesamte XP an der nächsten Rangschwelle; beim Höchstrang 0 |
| `%totalxprewards_required_xp%` | Alias für `next_rank_xp` |
| `%totalxprewards_current_rank%` | Formatierter aktueller Rangname aus `ranks.yml`; vor dem ersten Rang `None` |
| `%totalxprewards_current_rank_plain%` | Aktueller Rangname ohne Farben |
| `%totalxprewards_current_rank_group%` | LuckPerms-Gruppe des aktuellen Rangs; vorher leer |
| `%totalxprewards_next_rank%` | Formatierter nächster Rangname; beim Höchstrang `lang.yml`-Wert `max-rank` |
| `%totalxprewards_next_rank_plain%` | Nächster Rangname ohne Farben |
| `%totalxprewards_next_rank_group%` | LuckPerms-Gruppe des nächsten Rangs; beim Höchstrang leer |

`current_rank` und `next_rank` enthalten kompakte `&`-Farbcodes (bei Gradienten
auch `&#RRGGBB`) statt roher MiniMessage-Tags. Wenn ein Zielplugin diese Codes
nicht auswertet, verwende die jeweilige `_plain`-Variante. Die Rangnummer wird
aus der Reihenfolge der XP-Schwellen berechnet, unabhängig vom Gruppennamen.
Beim Höchstrang sind lokale Rang-XP und die verbleibenden XP 0.

Beispiel für `plugins/TAB/groups.yml` im Abschnitt `_DEFAULT_`, wenn der
Rangname wie bisher aus dem LuckPerms-Suffix kommt:

```yaml
_DEFAULT_:
  tabsuffix: ' &7[Rang %totalxprewards_rank_number%/%totalxprewards_rank_count%] &r%luckperms-suffix%'
```

Falls dein TAB-Setup den Suffix in `config.yml` statt `groups.yml` definiert,
setze dieselbe Zeichenfolge dort ein. Prüfe zuerst mit
`/papi parse me %totalxprewards_rank_number%`, danach mit `/tab reload` in TAB.
Bei TAB auf einem Proxy ist TAB Bridge auf dem Backend für PlaceholderAPI-Werte
erforderlich. Die Online-Werte stammen aus dem aktuellen Plugin-Cache; für
Offline-Abfragen wird der zuletzt gespeicherte XP-Stand verwendet.

### Interne Platzhalter

Diese kurzen Platzhalter gelten nur in TotalXPRewards-Texten (Befehle,
Broadcasts und BossBar). In TAB und anderen Plugins nutze die obigen
`%totalxprewards_*%`-Formen.

| Placeholder | Description |
| :--- | :--- |
| `%player%` | Player's name |
| `%xp%` | Player's accumulated accepted rank XP |
| `%rank_number%` | Current rank number, 0 before the first threshold |
| `%rank_count%` | Number of configured ranks |
| `%rank_xp%` | XP earned within the current rank |
| `%rank_required_xp%` | XP gap from this rank to the next |
| `%rank_remaining_xp%` | XP still missing until the next rank |
| `%current_rank%` | Name of the current rank (e.g. "Novice") |
| `%next_rank%` | Name of the next rank (e.g. "Master") |
| `%required_xp%` | XP required for the next rank |
| `%threshold%` | The specific threshold reached (Rewards only) |

---

## 🔧 Commands

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/txp get <player>` | View a player’s total XP | `totalxp.view` |
| `/txp status [player]` | Budget, activity and rank XP | `totalxp.use` (others: `totalxp.admin`) |
| `/txp show` | Show your BossBar | `totalxp.use` |
| `/txp hide` | Hide your BossBar | `totalxp.use` |
| `/txp set <player> <amount>` | Set a player’s XP | `totalxp.admin` |
| `/txp reset <player>` | Reset player XP & history | `totalxp.admin` |
| `/txp reload` | Reload config & language | `totalxp.admin` |

---

## 💾 Storage

XP and reward history are stored via **SQLite**, located in:
`plugins/TotalXPRewards/totalxp.db`

**External Access**:
The database now includes a `current_rank` and `username` column, making it easy to integrate with web leaderboards (e.g. Node.js apps).

---

## 🧩 Plugin Support

- **LuckPerms** (for rank rewards)
- **Vault** (for economy)
- **PlaceholderAPI** (for TotalXPRewards placeholders and placeholders from other plugins)

LuckPerms is required. PlaceholderAPI is optional and detected automatically.

---

## 🛡️ Disclaimer

This plugin was developed with the assistance of **Google DeepMind's AI**.
All code and design decisions were reviewed and finalized manually.

---

## 📄 License

This project is licensed under the **MIT License**.
You are free to use, modify, and contribute.

---

## 🤝 Contributing

Pull requests and feature suggestions are welcome!
Feel free to open an issue if you encounter bugs or have ideas.

---

## ⭐ Support the Project

If you enjoy this plugin, consider leaving a star on GitHub — it helps a lot!
