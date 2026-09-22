# Hypixel Scout

Bedwars opponent stats inside Minecraft 26.2 (Fabric). Stars, FKDR, winstreaks, nicks and full
profiles, read from the official Hypixel API with your own key — in a real in-game interface.

## What it does

- **The Scout screen** (`K`, or `/scout`) — four tabs under vanilla's own tab bar:
  - **Game**: everybody in your match, with faces, team colours, rank, threat level and measured
    columns. Click a column heading to sort by it, click a player for their profile.
  - **Teams**: one card per team, the most dangerous first — members, combined stars/FKDR/WLR,
    anybody on a long winstreak — and buttons that report the enemy teams to **team chat** or
    **party chat**: one line per team with its threat level and every player's star and FKDR,
    e.g. `Yellow EXTREME: Sundial 1502* 13.8 WS104, Orchard 305* 1.6, Glimmer NICK`.
  - **Lookup**: any player at all, in your game or not, with your recent lookups as clickable faces.
  - **Queue**: the nine quick-queue slots, each bound to a key, set and played from here.
- **Profile screen**: head, rank, level, karma, account age, last login, threat, and cards for
  combat, games and beds, plus the per-game rates and linked socials. **Team chat** and **Party
  chat** buttons send the player's stats in one line, e.g.
  `Sundial [MVP+] 1502* EXTREME | FKDR 13.83 | WLR 5.27 | WS 104 | Beds/g 1.7 | Kills/g 8.5`.
  Two bindings do the same for whoever you are aiming at, without opening anything.
- **Peek** (hold `R`, rebindable under **Settings → Keys**): while the key is down, the full profile of whoever you aim at — or, aiming
  at nobody, the whole game's table — drawn over the game. Let go and it is gone. Nothing pauses and
  the mouse stays with the camera, so you can keep moving while you look.
- **Beds and kills per game** (B/G, K/G) for every player, coloured from harmless to dangerous:
  who rushes beds and who wins fights.
- **Stats table** on the HUD (`Y`): a card you open and close, placed anywhere with the built-in
  drag editor. It can toggle, need the key held, open itself at game start, or stay up.
- **Look tooltip**: the stats of whoever you aim at — across the map, but *not* through walls.
- **Chat hover**: hovering a player's name in chat shows their stats, clicking opens the profile.
- **Tab list** with stats, faces and team colours (off by default).
- **Nametags** with the star in front and the FKDR after (off by default).
- **Nick and winstreak alerts** in your own chat.
- **Settings screen** with everything in four tabs, also reachable from Mod Menu. English and German.

## Requirements

- Minecraft 26.2 with Fabric Loader 0.19.3+ and Fabric API
- [Hypixel Mod API](https://modrinth.com/mod/hypixel-mod-api) — required. It is what tells the mod a
  Bedwars game has started.
- A Hypixel API key from [developer.hypixel.net](https://developer.hypixel.net/dashboard)

## Setup

1. Put `hypixelscout-mc26.2-<version>.jar`, Fabric API and the Hypixel Mod API into `mods/` — or
   import `hypixel-scout-<version>-mc26.2.mrpack`, which brings all three.
2. Start the game, press `K`, **Set the API key…**, paste it, **Test key**.

Any key works — development, personal or production. The mod reads the budget Hypixel reports with
every answer (`RateLimit-*` headers), so a production key's larger limit is used and requests made
by other programs on the same key are counted. Development keys expire after three days.

The key is stored in `config/hypixelscout.json`. It only reads public statistics and can be revoked
at any time, but the file is still not one to show on stream — the settings screen masks the key.

## Hotkeys

Everything has a binding under **Options → Controls → Hypixel Scout**. Only the Scout screen (`K`),
the table (`Y`), peek (`R`, hold) and the queue slots (number pad, `0` for a random slot) come bound; the rest is left
free on purpose.

## Commands

| Command | What it does |
| --- | --- |
| `/scout` | Opens the Scout screen |
| `/scout game \| teams \| lookup \| queue` | Opens it on that tab |
| `/scout <player>` | Opens that player's profile |
| `/scout <player> team \| party` | Sends that player's stats to team or party chat |
| `/scout settings` | Opens the settings |
| `/scout move` | Opens the table editor |
| `/scout key <key>` / `/scout testkey` | Stores / checks your API key |
| `/scout team` | Sends the enemy threat report to team chat (not in Solo) |
| `/scout party` | Sends the enemy threat report to party chat |
| `/scout table` | Toggles the table |
| `/scout refresh` | Looks everybody up again |
| `/scout status` | Key, request budget, Mod API and game state |

## When players are looked up

Not in the waiting lobby before a match: it fills and empties for minutes, and every player who
leaves before the start would be a request spent on nothing. The players are listed there, and
looked up the moment the match begins — seen from the scoreboard putting everybody into teams, or
from the game's opening line in chat. **Look up now** in the Game tab starts it early, and
Settings → General → *Look up in lobby* switches the wait off.

## Threat levels

Measured against **you and your team** by default: an enemy is LOW below half of that reference,
MED up to 1.5 times it (an even match), HIGH up to 4 times, EXTREME beyond. The reference is the
geometric mean of your own index (stars × FKDR²) and your teammates' average, so a strong teammate
raises the bar and a weak one lowers it. Settings → General → *Threat vs* switches to measuring
against **you** alone, or back to the fixed bands the same for everybody. Teammates are shown as
allies, not rated. The team and party reports use the same scale.

## Colours

- **Stars** are Hypixel's own prestige colours: one colour per hundred up to 999, the rainbow only at
  1000–1099, then the "prime" prestiges (grey brackets) from 1100, and a scheme of its own for every
  hundred after that, with the star symbol changing at 1100 (✪), 2100 (⚝), 3100 (✥) and 4100 (✭).
- **Ranks** in Hypixel's chat colours: VIP green, MVP aqua, MVP++ gold, YouTube and admins red.
- **FKDR and WLR** by how much trouble they mean: green under 1, yellow under 3, gold under 5, red
  under 10, dark red above. These bands are the mod's, not Hypixel's.
- **Threat** from green (LOW) to dark red (EXTREME), measured as described above.
- **Team bars** are the team's scoreboard colour; the **accent** (gold by default) is yours to pick.

## What it deliberately does not do

- **No ban history.** The Hypixel API exposes no punishments per player.
- **No automatic chat.** The team and party reports and `/play` are only sent when you press for
  them — one line at a time, slower for players without a rank, whom Hypixel lets chat only every
  few seconds.
- **No wallhack.** The look tooltip needs line of sight and only sees players the server sent you.
- **Nicked players stay unknown.** There is no profile behind a nick; the mod says so.

## Building

```sh
./gradlew build                              # core + 26.2 module, all unit tests
./gradlew -p versions/26.2 runClientGameTest # real client, staged game, screenshots of every screen
./gradlew -p versions/26.2 mrpack            # importable modpack
./gradlew -p versions/26.2 runClient         # dev client
```

The Minecraft 1.8.9 Forge version lives on the `1.8.9-support` branch (tag `legacy-1_8_9-support`).
