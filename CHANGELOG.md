# TotalXPRewards Changelog

## 🚀 New Features

### BossBar System
*   **Progress Tracking**: A BossBar now displays the player's progress towards the next rank.
*   **Dynamic Mode**: 
    *   Enabled via `bossbar.dynamic-mode: true` in config.
    *   When enabled, the BossBar only appears when a player gains XP and automatically hides after a configurable timeout (default 10s).
*   **Toggle Commands**: Players can manually control their BossBar visibility using:
    *   `/totalxp show`
    *   `/totalxp hide`
*   **Full Customization**: Title, Color, and Style are fully configurable.

### Enhanced Placeholders
New placeholders are now available globally (in Chat, Broadcasts, and BossBar titles):
*   `%current_rank%` - Displays the name of the player's current rank (e.g., "Novice").
*   `%next_rank%` - Displays the name of the next achievable rank.
*   `%required_xp%` - Shows the total XP required to reach the next rank.
*   `%xp%` - Shows the player's current total XP.
*   `%player%` - Shows the player's name.

### Rank Names
*   **Custom Names**: Rewards in `config.yml` now support a `name:` field (e.g., "Novice", "Master").
*   **Auto-Migration**: Existing configurations are automatically updated to include default rank names if missing.

### Vanilla Integration
*   **Target Selectors**: Commands now support Minecraft target selectors (e.g., `/totalxp get @a`, `/totalxp set @p 100`).
*   **Command Tracking**: XP gained via vanilla commands (`/xp` or `/experience`) is now correctly tracked and added to the player's Total XP.

---

## 🛠️ Improvements & Refactoring

*   **Configuration Template**: 
    *   The default `config.yml` has been completely rewritten in English.
    *   Improved layout, ASCII header, and detailed comments explaining every setting.
    *   added list of all available placeholders directly in the file.
*   **Language File**: 
    *   Refactored `lang.yml` for better readability and added missing messages for new commands.
*   **Metrics**: Integrated bStats metrics (ID 28208) for anonymous usage tracking.
*   **Code Structure**: Unified placeholder logic into the main plugin class to ensure consistency across all features.

---

## 🐛 Bug Fixes

*   **Config Migration**: Fixed a critical bug where numeric rank names (e.g. "1") were incorrectly interpreted and overwritten during configuration updates.
*   **BossBar Reload**: Fixed an issue where `reload` did not immediately update the BossBar for online players.
*   **Initial Setup**: Fixed a warning regarding `lang.yml` creation on fresh installs.
