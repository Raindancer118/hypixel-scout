# Hypixel Scout

Bedwars opponent stats for Minecraft 1.8.9 (Forge). Stars, FKDR, winstreaks, nicks and full
profiles, read from the official Hypixel API with your own key.

## What it does

- **Table** of everybody in the game, sorted by stars, FKDR, W/L or name.
- **Tab list** replaced by one with heads, team colours and stats (off by default).
- **Nametags** carrying the star and FKDR above each player (off by default).
- **Look tooltip** showing the stats of whoever you are aiming at — across the map, but *not*
  through walls.
- **Chat hover**: hovering a player's name shows their stats, clicking opens their profile.
- **Lobby screen** (`L`, or `/scout lobby`) listing everybody in the game with a one-line summary;
  clicking a row opens that player's full profile, escape goes back to the list.
- **Profile screen** with the full record and a search box for any player, in or out of your game.
- **Nick alert**: a chat line the moment somebody in your game turns out to be nicked.
- **Winstreak alert** above a threshold you choose.
- **Party report** (`/scout party`): each enemy team's combined stars, FKDR and W/L into party chat.
- **Quick queue**: nine hotkeys for nine modes, plus one that picks at random.

## Requirements

- Minecraft 1.8.9 with Forge
- [Hypixel Mod API](https://modrinth.com/mod/hypixel-mod-api) — required, not optional. It is what
  tells the mod a Bedwars game has started.
- A Hypixel API key from [developer.hypixel.net](https://developer.hypixel.net)

## Setup

1. Drop both jars into `mods/`.
2. Start the game, join Hypixel.
3. `/scout key <your-key>` — or paste it into the field the lobby screen shows while no key is
   set, or into `general.apiKey` in the config file.

Everything else is under `/scout config`, **Mods → Hypixel Scout → Config**, the Settings button on
the lobby screen, or in `config/hypixelscout.cfg`.

## Commands

| Command | What it does |
| --- | --- |
| `/scout` | Opens the profile screen with a search box |
| `/scout lobby` | Lists everybody in the game; click a row for the details |
| `/scout <player>` | Opens that player's profile |
| `/scout config` | Opens the settings screen |
| `/scout key <key>` | Stores your API key |
| `/scout party` | Sends the enemy team report to party chat |
| `/scout table` | Toggles the table |
| `/scout status` | Key, request budget, Mod API and game state |
| `/scout reload` | Re-reads the settings and clears the cache |

## What it deliberately does not do

- **No ban history.** The Hypixel API does not expose punishments for a player, and anything
  claiming otherwise is guessing. What it does show is account age, last login and level, which is
  as close as honest data gets.
- **No automatic party messages.** The report is sent when you ask for it. Sending it by itself
  would be a chat macro, and Hypixel bans people for those.
- **No wallhack.** The look tooltip needs line of sight. It also cannot see players the server has
  not told your client about, which is a limit of the game and not of the mod.
- **Nicked players stay unknown.** There is no profile behind a nick; the mod says so instead of
  inventing numbers.

## Building

```sh
./gradlew build      # jar in build/libs
./gradlew test       # the core logic, no game needed
./gradlew runClient  # dev client, Java 8 fetched automatically
```
