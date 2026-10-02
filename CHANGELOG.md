# TotalXPRewards v1.0.3 - Paper 26.2

* Build against Paper 26.2 build 129 and its Adventure 5 API, using Java 25 and Gradle 9.5.
* Update the plugin API version and PlaceholderAPI compile dependency to 2.12.3.
* Save cached player XP on disable and close SQLite after saving; finish quit and admin saves before reconnect/shutdown.
* Load join fallback data synchronously and initialize rewards before calculating cached ranks.
* Calculate XP command changes from level/progress rather than the separate total-experience counter.
* Recognize namespaced vanilla XP commands and RCON commands; coalesce checks in the same tick and exclude already counted natural gains.
* Use the loaded cache for the join BossBar and update the cached rank on reset.
* Synchronize SQLite reward reads, resets, and closing with other database access.
* Keep empty/invalid selectors from falling back to offline player names.
* Preserve the existing configuration, language file, and database schema.
* Add XP/persistence regression tests and a local Paper RCON smoke test.

# TotalXPRewards v1.0.2 - Optimization Update

## 🚀 Performance Optimizations
*   **Async Caching System**: 
    *   Player XP data is now loaded asynchronously when a player joins, preventing main-thread lag.
    *   XP updates during gameplay are handled instantly in-memory.
    *   Data is saved asynchronously on player quit or server shutdown.
*   **Thread Safety**: Database connections and write operations are now synchronized to ensure data integrity in asynchronous environments.

## 🎨 MiniMessage Support
*   **Rich Text Formatting**: Added support for **MiniMessage** formatting.
    *   Use RGB colors: `<#ff0000>Red`
    *   Use Gradients: `<gradient:red:blue>Rainbow Text</gradient>`
    *   Use Click events and Hover text in broadcasts.
*   **Backwards Compatibility**: Standard legacy color codes (e.g., `&a`, `&l`) are still fully supported. You can mix both in your configuration!

### 💾 Database Improvements
*   **External Access Support**: Added `username` and `current_rank` columns to the SQLite database.
*   **Automatic Migration**: Existing databases are automatically updated on startup. This allows external apps (typ. Node.js) to easily query player ranks.

### ⚙️ Configuration Safety
*   **Config Versioning**: Added `config-version` tracking. The plugin now automatically detects old configurations and safely applies necessary updates.
*   **Hybrid Color Support**: Text formatting now supports both legacy color codes (`&`) and modern MiniMessage tags simultaneously (e.g., `&bRank: <gradient:red:blue>...`).
*   **Configurable Max Rank**: The "Max Rank Reached" text is now fully customizable in `lang.yml` via the `max-rank` key.

## 🔧 Refactoring
*   **PlayerData Manager**: Centralized data handling into a new [PlayerDataManager] class for cleaner and more maintainable code.
*   **Cache-First Logic**: Commands (`/totalxp set/get`) now utilize the cache for online players, reducing unnecessary database queries.
