# MiraCosmetics

Centralized visual and audio effects framework for the Mira Paper server suite.

MiraCosmetics owns persistent cosmetic unlocks/equipment plus the shared presentation channels used by Mira teleports, flight, crates, kits, outposts, combat, bounties, tags and other suite events.

## Current Release

**v0.1.13** — compatible with Paper/Minecraft **1.21.11 through 26.2** using Java 21 bytecode.

[View releases](https://github.com/FiveSOCE/Mira-Cosmetics/releases)

## Requirements / Integrations

- Paper 1.21.11 through 26.2
- Java 21
- MiraCore
- optional consumers include MiraFly, MiraHomes, MiraWarps, MiraRTP, MiraCrates, MiraKits, MiraOutposts and other Mira modules

## Player Controls

Running `/cosmetics` opens the player GUI.

Each player has two persistent independent toggles:

- **Visual Effects** — particles and other visual presentation
- **Audio Effects** — Mira sound presentation

Both are stored in `cosmetics.yml` and survive restarts.

## Cosmetic Channels

Built-in persistent cosmetic channels include:

```text
TRAIL
JOIN
KILL
TELEPORT
FLY
```

MiraCosmetics also exposes shared event presentation for suite-owned systems such as:

- teleport warmup/completion/cancellation
- combat/bounty events
- outpost states and captures
- crate opening/reward rarity
- kit claims
- tags
- economy events
- Pinata events

Visual and audio delivery can be controlled independently.

## Teleport Presentation

MiraCosmetics is the suite authority for teleport visuals/audio.

Successful Bukkit/Paper teleports can render the canonical origin/destination effect. Warmup consumers can explicitly start a warmup package, and cancellation can terminate that package cleanly.

The standard teleport package includes:

- rising warmup visual/audio
- completion effect at the real successful teleport
- cancellation presentation when a queued teleport is cancelled

Other Mira plugins should report teleport lifecycle state rather than drawing competing particles themselves.

## Real Warmup Lifecycle Tracking — v0.1.13

Teleport cosmetics now follow the **real teleport queue lifecycle** rather than approximating it.

Supported integrations include:

- EssentialsX warmups/cancellations
- MiraWarps spawn queues
- MiraFactions home/warp queues
- MiraRTP searches

The animation starts when the underlying queue/search starts and terminates when the teleport completes or is cancelled.

This prevents cosmetic warmups from continuing after a real teleport has already failed/cancelled.

## Flight Presentation

MiraFly remains the authority for whether a player is flying. MiraCosmetics owns the visual trail and throttling.

Consumers can call the public flight presentation API without needing to know which particle/effect the player has selected.

## Audio Delivery

MiraCosmetics supports different audiences depending on the event:

- actioning player only
- nearby opted-in players
- faction audience where the event owner supplies one
- server-wide opted-in listeners
- visual-only presentation

Global audio is played at each listener's location so cross-world/global events remain audible where intended.

Configured Bukkit sound constants are resolved directly before registry-key fallback, avoiding underscore-to-dot conversion failures.

## Crate Audio

MiraCrates can use:

- synchronized spin ticks
- Common reward audio
- Rare reward audio
- Legendary/Mythic celebration audio

The current crate spin default uses a clear UI click instead of the previous near-inaudible note-block hat configuration.

## Commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/cosmetics` | `miracosmetics.use` | Opens the cosmetics GUI. |
| `/cosmetics list` | `miracosmetics.use` | Lists registered cosmetics and ownership/default state. |
| `/cosmetics equip <id>` | `miracosmetics.use` | Equips an unlocked/default cosmetic. |
| `/cosmetics clear <channel>` | `miracosmetics.use` | Clears the equipped cosmetic for a channel. |
| `/cosmetics status` | `miracosmetics.use` | Shows effective cosmetics/settings. |
| `/cosmetics grant <player> <id>` | `miracosmetics.admin` | Grants an unlock. |
| `/cosmetics revoke <player> <id>` | `miracosmetics.admin` | Revokes an unlock safely. |

Alias: `/cosmetic`

## Permissions

| Permission | Default | Purpose |
| --- | --- | --- |
| `miracosmetics.use` | Everyone | Player cosmetic GUI/settings/equipment. |
| `miracosmetics.admin` | OP | Administrative grants/revokes. |

## API

`CosmeticsApi` is exposed through Bukkit ServicesManager/MiraCore and supports:

- cosmetic registration/lookup
- ownership grant/revoke/query
- channel equip/effective selection
- teleport presentation
- flight presentation
- shared visual/audio event presentation
- warmup lifecycle integration

Other Mira plugins should use this API instead of maintaining separate particle/sound implementations.

## Persistence

Player ownership, equipment and toggle state are stored under:

```text
plugins/MiraCosmetics/
```

## Building

```bash
gradle clean build
```

The output JAR is created in `build/libs/`.
