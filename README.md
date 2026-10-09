# TotalXPRewards

Ränge durch gesammelte Erfahrung für Paper-Server. Spieler verdienen Rang-XP beim Spielen, steigen an festgelegten XP-Schwellen auf und erhalten die von dir konfigurierten Belohnungen. TotalXPRewards verwaltet die zugehörigen LuckPerms-Gruppen und stellt Werte für TAB und andere Plugins über PlaceholderAPI bereit.

**Version:** 1.6.1 · **Minecraft/Paper:** 26.2 · **Benötigt:** LuckPerms · **Optional:** PlaceholderAPI

[Neueste Version herunterladen](https://github.com/CelduinX/TotalXPRewards/releases/latest)

## Was das Plugin bietet

- Frei definierbare Ränge, Namen und Belohnungen in `ranks.yml`
- Automatischer Wechsel der zugeordneten LuckPerms-Ranggruppe
- Farbige Rangnamen mit `&`-Farbcodes oder MiniMessage, einschließlich Gradienten
- BossBar mit Rangfortschritt; dauerhaft oder nur nach XP-Gewinn sichtbar
- Persönliches Scoreboard mit aktuellem Rang und nächster Item-Belohnung; dauerhaft oder kurz nach XP-Gewinn sichtbar
- Konfigurierbare Begrenzung für Rang-XP gegen AFK-Spiel und Mob-Farmen; normale Minecraft-XP bleiben erhalten
- Konsolenbefehle und Nachrichten als Rangbelohnung
- Item-Belohnungen, die bei vollem Inventar vor dem Spieler liegen bleiben
- PlaceholderAPI-Werte für TAB, Scoreboards, Chat und weitere Plugins
- `/txp doctor` zur schnellen Prüfung der Einrichtung

## Installation und Update

1. Server stoppen. Vor einem Update `plugins/TotalXPRewards/` und die LuckPerms-Daten sichern.
2. Die JAR `TotalXPRewards-1.6.1-mc26.2.jar` in den Ordner `plugins/` legen. Eine ältere TotalXPRewards-JAR aus diesem Ordner entfernen.
3. Server starten. Beim ersten Start werden `config.yml`, `ranks.yml` und `lang.yml` unter `plugins/TotalXPRewards/` angelegt.
4. Die Gruppen aus `ranks.yml` in LuckPerms anlegen. Jede Rangdefinition braucht eine vorhandene, eigene Gruppe.
5. Server neu starten und `/txp doctor` ausführen.

**Beim Update vorhandene Daten behalten:** `ranks.yml`, `config.yml`, `lang.yml` und `totalxp.db` enthalten deine Einstellungen beziehungsweise Spielerfortschritte. Ersetze bei einem normalen Update nur die Plugin-JAR. Ältere Konfigurationen mit Rängen in `config.yml` werden beim Start mit Sicherung nach `ranks.yml` migriert. Falls sowohl alte Ränge in `config.yml` als auch `ranks.yml` vorhanden sind, bricht das Plugin ab, damit du den Konflikt prüfen kannst.

### Meldung bei begrenzten Rang-XP

Die Meldung erscheint in der ActionBar. Ab Version 1.6.1 kannst du ihre Anzeigedauer und den Text in `config.yml` einstellen:

```yaml
progression:
  notice:
    duration-seconds: 10
    message: '&eRang-XP: %reason% &7| %budget%/%capacity% &7| +1 in ~%time_to_next_xp% &7| voll in ~%time_to_full% &7(aktiv)'
```

Die Dauer darf 1 bis 60 Sekunden betragen; währenddessen wird die Meldung einmal pro Sekunde aktualisiert. `%reason%` nennt Rang-XP-Budget, Mob-Farm oder beides. `%budget%` und `%capacity%` zeigen das verfügbare und maximale Rang-XP-Budget. `%time_to_next_xp%` schätzt die Zeit bis zu einem Budgetpunkt, `%time_to_full%` bis zur vollen Aufladung. Diese Zeiten gelten **nur bei weiterer aktiver Spielzeit**; offline und im Leerlauf füllt sich das Budget nicht. Die Mob-Farm-Drosselung hat keinen festen globalen Ablaufzeitpunkt. Normale Minecraft-XP bleiben erhalten. Eine bisher selbst angepasste `progression-limited`-Nachricht aus `lang.yml` wird beim Update als neue Textvorlage übernommen.

## Ränge einrichten

In `plugins/TotalXPRewards/ranks.yml` ist jede Zahl eine **gesamte Rang-XP-Schwelle**. Die Namen der LuckPerms-Gruppen müssen bereits existieren.

```yaml
rewards:
  '160':
    group: initiat
    name: '&7Initiat'
    commands: []
    broadcast: '&a%player% hat %current_rank% erreicht!'

  '280':
    group: neuling
    name: '<gradient:#21b056:#8aedac>Neuling</gradient>'
    commands:
      - 'give %player% iron_ingot 8'
    broadcast: '&a%player% ist jetzt %current_rank%!'
```

`commands` laufen als Konsole. Für einfache und mit Minecraft-Datenkomponenten versehene `give %player% …`-Belohnungen legt TotalXPRewards Gegenstände zunächst ins Inventar. Was nicht hineinpasst, wird am Standort des Spielers gedroppt. Befehle anderer Plugins werden normal ausgeführt; deren Verhalten bei vollem Inventar bestimmt das jeweilige Plugin.

Ein Spieler sammelt Rang-XP und erhält Rangbelohnungen erst, wenn er die LuckPerms-Gruppe `spieler` **direkt und dauerhaft** besitzt. Eine nur geerbte oder zeitlich begrenzte Gruppenzugehörigkeit reicht dafür nicht. Die Ränge aus `ranks.yml` werden vom Plugin verwaltet; unabhängige Gruppen wie Team- oder Adminränge bleiben bestehen.

Beim Serverstart werden die konfigurierten Rangnamen als LuckPerms-Suffix aktualisiert. Ändere den Suffix dieser Ranggruppen daher in `ranks.yml` über `name`, nicht manuell in LuckPerms.

## Persönliches Scoreboard

Das Scoreboard ist zunächst ausgeschaltet. Ergänze in `plugins/TotalXPRewards/config.yml`:

```yaml
scoreboard:
  enabled: true
  dynamic-mode: false
  timeout: 10
  title: '&aTotal XP'
  lines:
    - '&7Aktueller Rang:'
    - '%current_rank% &7(%rank_number%/%rank_count%)'
    - ''
    - '&7Nächste Belohnung:'
    - '%next_reward_rank% &7(Rang %next_reward_number%)'
    - '%reward_items%'
  max-lines:
    - '&7Aktueller Rang:'
    - '%current_rank% &7(%rank_number%/%rank_count%)'
    - ''
    - '&aAlle Item-Belohnungen erreicht!'
```

Bei `dynamic-mode: true` erscheint die Seitenleiste nach einem XP-Gewinn für `timeout` Sekunden. `false` zeigt sie dauerhaft. Beim Beitritt erscheint sie ebenfalls zunächst; im dynamischen Modus verschwindet sie nach dem Timeout. Änderungen werden mit `/txp reload` geladen. Titel und Zeilen verstehen `&`-Farben und MiniMessage sowie die üblichen TotalXPRewards-Platzhalter. Für die nächste Belohnung gibt es `%next_reward_rank%`, `%next_reward_number%`, `%next_reward_xp%`, `%next_reward_remaining_xp%` und `%reward_items%`. Letzterer steht allein in einer Zeile und erzeugt eine Zeile pro Item. Insgesamt zeigt Minecraft höchstens 15 Scoreboard-Zeilen.

Das Plugin sucht die nächste Rangstufe mit einem `give %player% …`-Befehl und zeigt deren Gegenstände automatisch an. Andere Belohnungsbefehle lassen sich in `ranks.yml` für die Anzeige beschriften:

```yaml
  '4540':
    group: entdecker_i
    name: '<green>Entdecker I</green>'
    commands:
      - 'give %player% diamond 4'
      - 'give %player% golden_carrot 16'
    scoreboard-rewards:
      - '&7- &f4 Diamanten'
      - '&7- &f16 goldene Karotten'
```

`scoreboard-rewards` ersetzt die automatische Itemliste dieser Rangstufe und eignet sich auch für Belohnungen aus anderen Plugins. Beim höchsten beziehungsweise letzten Rang mit anzeigbarer Belohnung gelten die `max-lines`. Wenn TAB ebenfalls ein Scoreboard anzeigen soll, muss dessen Scoreboard-Funktion ausgeschaltet sein; seine Tab-Liste und Teams können weiterlaufen.

## Anzeige und Platzhalter

### TAB-Beispiel

Wenn dein TAB-Setup den Rangnamen bereits über den LuckPerms-Suffix zeigt, kannst du die Rangnummer ergänzen:

```yaml
_DEFAULT_:
  tabsuffix: ' &7[Rang %totalxprewards_rank_number%/%totalxprewards_rank_count%] &r%luckperms-suffix%'
```

Setze die Zeile an der Stelle deiner TAB-Konfiguration ein, an der du bisher den Suffix verwendest. Prüfe einen Platzhalter mit `/papi parse me %totalxprewards_rank_number%` und lade danach TAB mit `/tab reload` neu. Die PlaceholderAPI-Erweiterung ist in der TotalXPRewards-JAR enthalten; ein zusätzlicher Download über PAPI ist nicht nötig. Läuft TAB auf einem Proxy, wird TAB Bridge für Platzhalter vom Backend benötigt.

### PlaceholderAPI

| Platzhalter | Bedeutung |
| --- | --- |
| `%totalxprewards_xp%` | Gesammelte Rang-XP |
| `%totalxprewards_rank_number%` | Aktuelle Rangnummer; vor dem ersten Rang `0` |
| `%totalxprewards_rank_count%` | Anzahl der Ränge |
| `%totalxprewards_rank_xp%` | XP innerhalb des aktuellen Rangs |
| `%totalxprewards_rank_required_xp%` | XP-Abstand bis zum nächsten Rang |
| `%totalxprewards_rank_remaining_xp%` | Noch fehlende XP bis zum nächsten Rang |
| `%totalxprewards_rank_start_xp%` | Gesamte XP am Beginn des aktuellen Rangs |
| `%totalxprewards_next_rank_xp%` | Gesamte XP für den nächsten Rang |
| `%totalxprewards_required_xp%` | Gleicher Wert wie `next_rank_xp` |
| `%totalxprewards_current_rank%` | Farbiger Name des aktuellen Rangs |
| `%totalxprewards_current_rank_plain%` | Aktueller Rangname ohne Farbe |
| `%totalxprewards_current_rank_group%` | Aktuelle LuckPerms-Ranggruppe |
| `%totalxprewards_next_rank%` | Farbiger Name des nächsten Rangs |
| `%totalxprewards_next_rank_plain%` | Nächster Rangname ohne Farbe |
| `%totalxprewards_next_rank_group%` | Nächste LuckPerms-Ranggruppe |

Die farbigen Namen verwenden `&`- beziehungsweise `&#RRGGBB`-Farbcodes. Für Plugins ohne Farbauswertung eignen sich die `_plain`-Varianten. Beim höchsten Rang sind die Werte für den nächsten Rang `0` beziehungsweise leer; als nächster Rangname erscheint der `max-rank`-Text aus `lang.yml`.

In Nachrichten und BossBar-Texten von **TotalXPRewards selbst** kannst du außerdem die kurzen Formen wie `%xp%`, `%current_rank%`, `%next_rank%`, `%rank_number%`, `%rank_count%`, `%rank_xp%`, `%rank_required_xp%` und `%rank_remaining_xp%` verwenden. `%player%` ist der Spielername; `%threshold%` steht nur bei Rangbelohnungen zur Verfügung.

## Befehle

`/totalxp` und `/txp` sind gleichwertig.

| Befehl | Zweck | Berechtigung |
| --- | --- | --- |
| `/txp get <Spieler>` | Rang-XP anzeigen | `totalxp.view` |
| `/txp status [Spieler]` | XP, Aktivität und XP-Budget anzeigen | `totalxp.use`; andere Spieler: `totalxp.admin` |
| `/txp show` / `/txp hide` | Eigene BossBar ein- oder ausblenden | `totalxp.use` |
| `/txp set <Spieler> <XP>` | Rang-XP setzen | `totalxp.admin` |
| `/txp reset <Spieler>` | Rang-XP und Belohnungshistorie zurücksetzen | `totalxp.admin` |
| `/txp reload` | Konfiguration neu laden | `totalxp.admin` |
| `/txp doctor` | Einrichtung und Verbindungen prüfen | `totalxp.admin` |

## Häufige Fragen

**Der Spieler bekommt keine Rang-XP.** Prüfe mit LuckPerms, ob er `spieler` direkt und dauerhaft als Gruppe hat. `/txp status` zeigt außerdem das aktuelle XP-Budget und mögliche Begrenzungen.

**Ein Rang wird nicht gesetzt.** Prüfe mit `/txp doctor`, ob alle Gruppen aus `ranks.yml` in LuckPerms vorhanden sind. Der Gruppenname unter `group:` muss eindeutig sein.

**Ein Platzhalter erscheint als Text.** Prüfe ihn zuerst mit `/papi parse me <Platzhalter>`. Stelle sicher, dass PlaceholderAPI installiert ist und TAB bei einer Proxy-Installation Zugriff auf Backend-Platzhalter hat.

**Ein Reload schlägt fehl.** Die bisherige aktive Konfiguration bleibt erhalten. Die Fehlermeldung und `/txp doctor` zeigen, welche Einstellung oder LuckPerms-Gruppe korrigiert werden muss.

**Ein Item liegt vor dem Spieler.** Das Inventar war für die Belohnung voll. Gedroppte Items können wie andere Items in der Welt verschwinden; sorge bei Bedarf für eine sichere Stelle zum Einsammeln.

## Lizenz

Das Projekt steht unter der [Lizenz des Repositories](LICENSE.md).
