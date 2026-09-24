# Hypixel Scout

Bedwars opponent stats inside Minecraft 26.2 (Fabric). Stars, FKDR, winstreaks, nicks and full
profiles, read from the official Hypixel API with your own key — in a real in-game interface.

## What it does

- **The Scout screen** (`K`, or `/scout`) — four tabs under vanilla's own tab bar:
  - **Game**: everybody in your match, with faces, team colours, rank, threat level and measured
    columns. Click a column heading to sort by it, click a player for their profile.
  - **Teams**: one card per team, the most dangerous first — members, combined stars/FKDR/WLR,
    anybody on a long winstreak — and buttons that report the enemy teams to **team chat** or
    **party chat**: one short line per enemy worth a warning, most dangerous first — harmless
    players are left out, nicks named, at most six lines:
    `YELLOW Sundial 1502* - INSANE - 13.8 FKDR - 104 WS`, `BLUE Glimmer is nicked`.
    **List → team** / **List → party** send the whole enemy list instead: every player, one line
    each, the harmless ones too, with a closing count of anybody not looked up yet.
  - **Lookup**: any player at all, in your game or not, with your recent lookups as clickable faces.
  - **Queue**: the nine quick-queue slots, each bound to a key, set and played from here.
- **Profile screen**: head, rank, level, karma, account age, last login, threat, and cards for
  combat, games and beds, plus the per-game rates and linked socials. **Team chat** and **Party
  chat** buttons send the player's stats in two lines:
  `Sundial [MVP+] 1502* is INSANE` and `13.8 FKDR, 5.3 WLR, 104 winstreak, 1.7 beds and 8.5 kills a game`.
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
- **Auto requeue** (Settings → Alerts, off by default): joins the next game of the same mode once
  you are *finally* out — bed gone and dead, i.e. a `FINAL KILL` on you; an ordinary death with the
  bed standing does nothing. *when party is out* waits until you and every party member in the game
  are out (party from Hypixel's Mod API; without a party it is the same as *when I'm out*). A
  countdown (0–15 s, default 3) with a clickable **[Cancel]** comes first (`/scout requeue cancel`).
  Only the party leader can move a party; a member gets a note instead. As leader, *when I'm out*
  pulls the whole party along, living teammates included.
- **Proximity popup**: an enemy walking within 12 blocks (Settings → Overlays → *Popup radius*,
  2–48) gets a small card at the top of the screen for 4 seconds — threat level, stars, FKDR,
  winstreak. Once per approach: they have to walk a few blocks beyond the radius and 15 seconds
  must pass before it shows again. *Popup from* skips players below a threat level; nicks and
  players not looked up yet always show. Only players the scoreboard puts on another team than
  yours get one — no teammates, and nobody while the teams are not readable yet (waiting lobby).
- **Flight paths** (Settings → Missiles): every arrow and fireball in the air gets its predicted
  path drawn into the world, up to the block it hits — white for arrows, orange for fireballs, red
  when it is headed at you. Vanilla's own flight rules (arrow drag and gravity, fireball
  acceleration), occluded by blocks like everything else.
- **Incoming warning**: while an arrow or fireball is about to hit you — directly, or a fireball
  landing within 2.5 blocks — a pulsing card above the crosshair says what, from which side (↑ ahead,
  ↓ behind, and the six between) and in how many seconds. For a fireball a **missile-launch warning**
  loops as long as its path or blast has you in it and stops the moment you are out of the way: the
  F/A-18's AN/ALR-67 tone, a steady warble between 455 and 555 Hz every 0.1 s. An arrow gets a short
  ping. (Synthesised from those numbers by `tools/missile_tone.py`, no third-party audio.) Not for your own, and
  not for one first seen within 3 blocks of your eyes inside your view: thrown in your face, you see
  it anyway. That is judged once, when it appears; one that flew in from afar still counts up close.
- **Fireball aim line**: while you hold a fire charge, the line your fireball would fly if you
  threw it now, and the spot it would hit. Just a line — nothing aims for you.
- **Callouts** (Settings → Callouts): six messages on keys of your choice, about whoever is under
  your crosshair. `{team} inc` while aiming at a red player sends `RED inc` to team chat (or party
  chat, one switch). Placeholders: `{team}` `{name}` `{stars}` `{threat}` `{fkdr}` `{wlr}` `{bblr}`
  `{ws}` `{distance}`; stats not looked up yet read `?`. A message without placeholders needs nobody
  aimed at. One press, one message; a second press while it is still waiting sends nothing twice.
  Team chat is refused in Solo, where everybody would read it.
- **Nick and winstreak alerts** in your own chat.
- **Settings screen** with everything in seven tabs, also reachable from Mod Menu. English and German.

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
| `/scout requeue cancel` | Stops an auto requeue that is counting down |
| `/scout list team` / `/scout list party` | Sends every enemy, one line each, to team or party chat |
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

Two kinds of danger, each with its own index, both built so that an ordinary player comes out at the
community's stars × FKDR²:

- **Fights** — stars × FKDR^1.4 × (1.5 KDR)^0.35 × (2 WLR)^0.25, times finals a game and winstreak.
- **Beds** — stars × (1.25 BBLR)^1.4 × (2 WLR)^0.6, times beds a game and winstreak. Finds the rushers.

Finals and beds a game count at their fourth root and at most double or halve an index; a winstreak
adds 1 % per win, up to double. Settings → General → *Threat in* picks **fights**, **beds** or
**both** (default): with both, each is rated on its own, the worse one is shown and marked
"at beds" when that is the beds; profiles and tooltips show both side by side.

Seven levels: NONE, LOW, MED, HIGH, V.HIGH, EXTREME, INSANE (plus `?` for not looked up yet and
NICK). Measured against **you and your team** by default: an enemy is NONE below a fifth of that
reference, LOW below half, MED up to 1.5 times it (an even match), HIGH up to 2.5 times, V.HIGH up
to 4 times, EXTREME up to 10 times, INSANE beyond. The reference is the geometric mean of your own
index and your teammates' average — fights against your fights, beds against your beds —, so a strong teammate raises the bar and a weak
one lowers it. Settings → General → *Threat vs* switches to measuring against **you** alone, or to
the fixed bands the same for everybody (index under 100 NONE, 500 LOW, 3 000 MED, 10 000 HIGH,
30 000 V.HIGH, 150 000 EXTREME, above that INSANE). Teammates are shown as allies, not rated.

**Threat sensitivity** (Settings → General, 25–400 %) rates every enemy as if they were that much
stronger or weaker: at 200 % the levels come twice as early, at 50 % only the worst stand out.
**Report from** picks the lowest level the team and party reports name (MED by default); anybody
below it is only counted, unless they are on a winstreak above the alert threshold. The table, the
cards, the tooltip and both reports all use the same scale.

## Colours

- **Stars** are Hypixel's own prestige colours: one colour per hundred up to 999, the rainbow only at
  1000–1099, then the "prime" prestiges (grey brackets) from 1100, and a scheme of its own for every
  hundred after that, with the star symbol changing at 1100 (✪), 2100 (⚝), 3100 (✥) and 4100 (✭).
- **Ranks** in Hypixel's chat colours: VIP green, MVP aqua, MVP++ gold, YouTube and admins red.
- **FKDR and WLR** by how much trouble they mean: green under 1, yellow under 3, gold under 5, red
  under 10, dark red above. These bands are the mod's, not Hypixel's.
- **Threat** from grey (NONE) over green (LOW), yellow, gold, red and dark red to purple (INSANE), measured as described above.
- **Team bars** are the team's scoreboard colour; the **accent** (gold by default) is yours to pick.

## What it deliberately does not do

- **No ban history.** The Hypixel API exposes no punishments per player.
- **No automatic chat.** The team and party reports are only sent when you press for them — one
  line at a time, 0.5 s apart by default (Settings → Alerts → *Chat interval*), but never faster
  than every 3.2 s for players without a rank, whom Hypixel lets chat only every three seconds.
  `/play` goes out only on a key press, or through the auto requeue if you switched it on.
- **No wallhack.** The look tooltip needs line of sight and only sees players the server sent you;
  flight paths are hidden behind blocks like anything else.
- **No aim assist that aims.** The fireball line shows where a throw would go; moving the crosshair
  stays yours.
- **Nicked players stay unknown.** There is no profile behind a nick; the mod says so.

## Building

```sh
./gradlew build                              # core + 26.2 module, all unit tests
./gradlew -p versions/26.2 runClientGameTest # real client, staged game, screenshots of every screen
./gradlew -p versions/26.2 mrpack            # importable modpack
./gradlew -p versions/26.2 runClient         # dev client
```

The Minecraft 1.8.9 Forge version lives on the `1.8.9-support` branch (tag `legacy-1_8_9-support`).
