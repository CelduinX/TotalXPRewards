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