# TotalXPRewards v1.6.0 - Personal reward scoreboard

* Add an optional right-side scoreboard with current rank and next item reward.
* Find the next rank with visible rewards, skipping rank-ups without items.
* Derive item names and quantities from vanilla give commands, with optional custom display lines in ranks.yml.
* Make title, lines, permanent/dynamic display and timeout configurable.

# TotalXPRewards v1.5.0 - Reliability and admin tools

* Save periodic player snapshots off the server tick, cache reward history at login and flush queued saves before shutdown.
* Reject failed XP and reward-history reads instead of treating database errors as zero XP or missing rewards.
* Validate LuckPerms groups and BossBar settings before applying a configuration reload.
* Add `/txp doctor` for database, LuckPerms rank and PlaceholderAPI checks.
* Deliver configured vanilla `give` rewards directly and drop inventory overflow at the player.
* Replace the README with an admin-focused guide.

# TotalXPRewards v1.4.0 - PlaceholderAPI expansion

* Add built-in `%totalxprewards_*%` placeholders for rank XP, position, names, groups and thresholds.
* Read online player XP from the live cache, so TAB can show rank changes immediately.
* Support saved offline player XP and document use in TAB and other PlaceholderAPI consumers.

# TotalXPRewards v1.3.1 - Compact suffix and JAR naming

* Remove the leading space before LuckPerms rank suffix text.
* Name plugin JARs with plugin name, plugin version and Minecraft version.

# TotalXPRewards v1.3.0 MiniMessage suffix v4

* Render `ranks.yml` MiniMessage rank names as compact LuckPerms suffix colors for EssentialsX Chat.
* Remove the hardcoded yellow color and surrounding parentheses.
* Preserve plain display names and refresh group metadata on startup.

# TotalXPRewards v1.1.1 - Configurable rank-local BossBar

* Add rank number/count and local earned/required/remaining XP placeholders.
* Default title shows current rank, rank position, next rank and local XP; XP resets at rank-up.
* Share the calculation between placeholders and BossBar fill; retain cumulative XP placeholders.
* Make maximum-rank and empty-rank titles configurable, preserving custom titles on upgrade.
* Test screenshot values, rank boundaries, maximum/empty ranks and custom-title reloads.

# TotalXPRewards v1.1.0 - Rank progression protection

* Keep vanilla XP unchanged; limit rank XP to a persisted 1,000 XP budget refilling at 500 XP per active hour.
* Track real actions and movement input, excluding idle/offline time, teleports, passive movement, vehicles and flight.
* Reduce local repeated mob kills to 10% at 30 kills in five minutes within 24 blocks; exclude bosses and player deaths.
* Persist farm factors on XP orbs and propagate through merging and spawn-time stacking.
* Retain fractional credits, discard surplus and checkpoint XP/progression together every 60 seconds, on quit and shutdown.
* Preserve existing XP, rewards, ranks and custom config; add validated progression settings and an additive SQLite table.
* Add `/totalxp status [player]`, localized limiting notices, explicit admin set/reset behavior and regression tests.

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
# TotalXPRewards v1.2.0 - SelfUnlock and LuckPerms rank integration

* Require a direct, global `spieler` parent before recording rank XP or issuing rewards; vanilla XP remains unchanged.
* Map each of the 100 thresholds to an explicit, existing LuckPerms group, including `xp_abenteurer` and `xp_legende`.
* Reconcile the XP parent after unlock, on login and after admin XP changes, preserving `spieler` and independent groups.
* Set rank group display names, prefixes and weights below moderator/admin through the LuckPerms API.
* Report old XP group users without direct `spieler`; never infer an unlock or replay item rewards.
* Add guest, rank transition and independent group tests.
# TotalXPRewards v1.3.0 - Separate rank configuration

* Move the 100 rank definitions and rewards from `config.yml` into `ranks.yml`.
* Migrate legacy `config.yml` rewards on startup after backing up the existing file; preserve their values and reward history.
* Reject simultaneous legacy rewards and a separate rank file for manual review.
* Reload both files together and reject invalid rank YAML before replacing active ranks.
# TotalXPRewards v1.4.0 - PlaceholderAPI expansion

* Add built-in `%totalxprewards_*%` placeholders for rank XP, position, names, groups and thresholds.
* Read online player XP from the live cache, so TAB can show rank changes immediately.
* Support saved offline player XP and document use in TAB and other PlaceholderAPI consumers.
# TotalXPRewards v1.5.0 - Reliability and admin tools

* Save periodic player snapshots off the server tick and flush queued saves before shutdown.
* Reject unsafe reward-history lookups instead of treating database errors as missing rewards.
* Validate LuckPerms groups and BossBar settings before applying a configuration reload.
* Add `/txp doctor` for database, LuckPerms rank and PlaceholderAPI checks.
* Deliver configured vanilla `give` rewards directly and drop inventory overflow at the player.
* Replace the README with an admin-focused guide.
