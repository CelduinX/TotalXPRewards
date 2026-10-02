# Total XP Rewards

A powerful and lightweight Paper plugin that tracks each player's **lifetime XP** and executes **custom rewards** when XP milestones are reached.
Fully configurable, translation-ready, and built for Paper 26.2 (Java 25).

## Building and updating

With JDK 25 installed, run `./gradlew build` (`gradlew.bat build` on Windows).
The plugin JAR is written to `build/libs/TotalXPRewards-1.0.3.jar`.
The build runs regression tests for XP commands and SQLite persistence.

Stop the server before replacing the old TotalXPRewards JAR. Keep the existing
`plugins/TotalXPRewards` folder, including `config.yml`, `lang.yml`, and
`totalxp.db`. Version 1.0.3 uses the same configuration and database schema.

For a running local server with RCON already enabled, run
`python tools/paper_smoke_test.py --server ../../server --stop` to check plugin
startup, console commands, reload, and config preservation, then stop gracefully.
This smoke test does not exercise a real player's in-game interactions.

---

## ✨ Features

- **Global Total XP Tracking** 📈
  - Tracks XP from killing mobs, mining, **and** vanilla commands (`/xp`, `/experience`).
  - Never resets, even after death.
  - **Async Caching**: High-performance data handling prevents server lag.
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

### `config.yml` (example)

```yaml
bossbar:
  enabled: true
  # Hybrid Support: Mix Legacy (&) and MiniMessage (<gradient>)!
  title: "&bCurrent Rank: &e%current_rank% &7| <gradient:blue:aqua>Next: %next_rank%</gradient> &7(&a%xp%&7/&c%required_xp%&7)"
  color: BLUE
  style: SOLID
  dynamic-mode: true # Bar appears on XP gain and hides after timeout
  timeout: 10

rewards:
  "1000":
    name: "<gradient:#2486B5:#3A816A>Novice</gradient>"
    commands:
      - "give %player% diamond 1"
      - "eco give %player% 250"
    broadcast: "&a%player% reached %threshold% XP (Novice)!"

  "50000":
    name: "Master"
    commands:
      - "give %player% netherite_ingot 1"
    broadcast: "&6%player% is now a Master!"
```

---

## 🧩 Placeholders

Available for use in **Chat**, **Broadcasts**, and **BossBar**:

| Placeholder | Description |
| :--- | :--- |
| `%player%` | Player's name |
| `%xp%` | Player's total lifetime XP |
| `%current_rank%` | Name of the current rank (e.g. "Novice") |
| `%next_rank%` | Name of the next rank (e.g. "Master") |
| `%required_xp%` | XP required for the next rank |
| `%threshold%` | The specific threshold reached (Rewards only) |

---

## 🔧 Commands

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/txp get <player>` | View a player’s total XP | `totalxp.view` |
| `/txp show` | Show your BossBar | `totalxp.use` |
| `/txp hide` | Hide your BossBar | `totalxp.use` |
| `/txp set <player> <amount>` | Set a player’s XP | `totalxp.admin` |
| `/txp reset <player>` | Reset player XP & history | `totalxp.admin` |
| `/txp reload` | Reload config & language | `totalxp.admin` |

---

## 💾 Storage

XP and reward history are stored via **SQLite**, located in:
`plugins/TotalXPRewards/database.db`

**External Access**:
The database now includes a `current_rank` and `username` column, making it easy to integrate with web leaderboards (e.g. Node.js apps).

---

## 🧩 Plugin Support

- **LuckPerms** (for rank rewards)
- **Vault** (for economy)
- **PlaceholderAPI** (for extra placeholders)

The plugin does not depend on them but integrates automatically if installed.

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
