# Total XP Rewards

A powerful and lightweight Paper plugin that tracks each player's **lifetime XP** and executes **custom rewards** when XP milestones are reached.  
Fully configurable, translation-ready, and built for modern Paper servers (1.21+).

---

## ✨ Features

- Tracks **global total XP** per player  
  → never resets, even after death  
- Executes **one or multiple commands** when reaching a defined XP threshold  
- Custom **broadcast messages** for each reward  
- Full **PlaceholderAPI support** (optional)  
- **SQLite storage** (no setup required)  
- `/txp` admin commands for managing XP  
- Complete language customization via `lang.yml`  
- Designed for **Paper 1.21.10**  
- Clean architecture and high performance

---

## 📥 Installation

1. Download the latest release from the **Releases** page.
2. Drop the `.jar` file into your server's `plugins` folder.
3. Start the server once to generate config files.
4. Adjust `config.yml` and `lang.yml` to your liking.
5. Restart or run:  
   ```
   /txp reload
   ```

---

## ⚙️ Configuration

### `config.yml` (example)

```yaml
rewards:
  "1000":
    commands:
      - "give %player% diamond 1"
      - "eco give %player% 250"
    broadcast: "&a%player% reached %threshold% XP (&e%xp%&a)!"

  "5000":
    commands:
      - "lp user %player% parent add vip"
    broadcast: "&6%player% &ais now VIP!"

  "10000":
    commands:
      - "eco give %player% 10000"
    broadcast: "&b%player% reached %threshold% XP and received &e10000$&a!"
```

---

## 🗣️ Localization

All messages are stored in **`lang.yml`** and are fully editable.  
Example:

```yaml
prefix: "&7[&aTotalXP&7] "
xp-view: "&a%player% has &e%xp% XP."
no-permission: "&cYou do not have permission."
```

---

## 🔧 Commands

| Command                          | Description                          | Permission                  |
|----------------------------------|--------------------------------------|-----------------------------|
| `/txp get <player>`              | View a player’s total XP             | totalxprewards.view         |
| `/txp set <player> <amount>`     | Set a player’s XP                    | totalxprewards.admin        |
| `/txp reset <player>`            | Reset player XP & reward history     | totalxprewards.admin        |
| `/txp reload`                    | Reload config + language files       | totalxprewards.admin        |

---

## 🛠️ Permissions

- `totalxprewards.use` — basic usage (default: true)
- `totalxprewards.view` — view XP of other players
- `totalxprewards.admin` — set/reset/reload

---

## 💾 Storage

XP and reward history are stored via **SQLite**, located in:

```
plugins/TotalXPRewards/database.db
```

This requires **no external database setup**.

---

## 🧱 API Compatibility

- Paper 1.21.10+  
- Java 17 or Java 21 (recommended)

---

## 🧩 Plugin Support

- **LuckPerms** (for rank rewards)
- **Vault** (for economy)
- **PlaceholderAPI**

The plugin does not depend on them but integrates automatically if installed.

---

## 🛡️ Disclaimer

This plugin was developed with the assistance of *ChatGPT* (OpenAI).  
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
