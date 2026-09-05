# MiraCosmetics

MiraCosmetics is the cosmetic unlock and centralized visual-effects framework for the Mira Paper server suite. It manages persistent cosmetic ownership/equipment and is the suite authority for reusable particle effects such as trails, joins, kills, teleports and flight.

## Download

[**Download MiraCosmetics v0.1.7**](https://github.com/FiveSOCE/Mira-Cosmetics/releases/download/v0.1.7/MiraCosmetics-0.1.7.jar)

[View All Releases](https://github.com/FiveSOCE/Mira-Cosmetics/releases)

## Requirements / Dependencies

- Paper 1.21.11
- Java 21
- MiraCore 0.2.0 or newer
- MiraFly optional consumer
- MiraHomes optional consumer
- MiraWarps optional consumer
- MiraRTP optional consumer

## How MiraCosmetics Works

Cosmetics are registered under stable IDs and belong to effect channels. Player unlocks and equipped choices persist across restarts.

Built-in channels are:

- `TRAIL`
- `JOIN`
- `KILL`
- `TELEPORT`
- `FLY`

Built-in examples include flame/heart trails, a totem join effect and a soul-fire kill effect. TELEPORT and FLY are now canonical Mira signatures rather than selectable particle variants.

v0.1.4 makes MiraCosmetics the first-party visual-effects authority for Mira teleports and flight. A global `PlayerTeleportEvent` listener applies the selected/default TELEPORT effect at both the origin and destination, so Essentials `/spawn`, Essentials teleports, MiraHomes, MiraWarps, MiraRTP and other proper Bukkit/Paper teleports automatically receive the same effect pipeline without duplicating particle logic in every plugin.

MiraFly can use the public `CosmeticsApi.playFly(Player)` method for continuous flight effects. MiraCosmetics owns throttling and effect selection, so MiraFly does not need to know which particle a player selected.

## Default Visual Effects

### Teleport

- `teleport_portal` - default Portal effect
- `teleport_endrod` - End Rod effect
- `teleport_firework` - Firework effect

### Flight

- `fly_cloud` - default Cloud effect
- `fly_flame` - Flame effect
- `fly_endrod` - End Rod effect

Default TELEPORT/FLY cosmetics remain usable without an explicit unlock. Alternate cosmetics can be granted and equipped normally.

## Commands

| Command | Permission | What it does |
| --- | --- | --- |
| `/cosmetics list` | `miracosmetics.use` | Lists registered cosmetics and ownership/default state. |
| `/cosmetics equip <id>` | `miracosmetics.use` | Equips an unlocked/default cosmetic in its channel. |
| `/cosmetics clear <trail|join|kill|teleport|fly>` | `miracosmetics.use` | Clears that slot and returns TELEPORT/FLY to their configured default where applicable. |
| `/cosmetics status` | `miracosmetics.use` | Shows the effective cosmetic for every channel. |
| `/cosmetics grant <player> <id>` | `miracosmetics.admin` | Grants a cosmetic unlock. |
| `/cosmetics revoke <player> <id>` | `miracosmetics.admin` | Revokes an unlock and safely unequips it if selected. |

Alias: `/cosmetic`

## Permissions

| Permission | Default | What it does |
| --- | --- | --- |
| `miracosmetics.use` | Everyone | Allows cosmetic viewing, equipping, clearing and status. |
| `miracosmetics.admin` | OP | Allows administrative grants/revokes. |

## Configuration

Important effect controls are in `config.yml`:

- `effects.teleport.enabled`
- `effects.teleport.default-cosmetic`
- `effects.teleport.count-origin`
- `effects.teleport.count-destination`
- `effects.fly.enabled`
- `effects.fly.default-cosmetic`
- `effects.fly.throttle-millis`
- `effects.fly.count`
- `effects.trail.throttle-millis`

Particle counts/throttles are centrally configurable so high-frequency effects can be performance-tuned without editing MiraFly/Homes/Warps/RTP.

## API / Integration

`CosmeticsApi` is registered through Bukkit ServicesManager and MiraCore. It supports:

- cosmetic registration and lookup
- ownership grant/revoke/query
- channel equip/effective selection
- `playTeleport(Player, origin, destination)`
- `playFly(Player)`

Other Mira plugins should call these services rather than implementing their own particle-selection logic.

## Persistence

Player ownership and equipped selections are stored in `plugins/MiraCosmetics/cosmetics.yml`.

## Building

```bash
gradle clean build
```

The output JAR is created in `build/libs/`.


## Canonical Teleport Effect

Every successful Bukkit/Paper teleport handled by MiraCosmetics renders three horizontal rings around the player at both the origin and destination:

1. blue
2. white
3. blue

The rings use DUST particles and are configurable through ring point count, radius, vertical spacing and base Y offset. Other Mira plugins must not render their own teleport particles.

## Canonical Flight Effect

While MiraFly reports a player as actively flying, MiraCosmetics renders a white DUST particle trail directly under the player's feet.

The trail throttle, particle count, Y offset and dust size are configurable in MiraCosmetics. MiraFly remains the sole authority for flight state; MiraCosmetics only renders the visual.


## Player Effect Toggles

Running `/cosmetics` with no arguments now opens the MiraCosmetics GUI.

The GUI includes two persistent per-player controls:

- **Visual Effects** - enables/disables Mira cosmetic particle rendering.
- **Audio Effects** - enables/disables Mira cosmetic sounds.

Both settings are stored in `cosmetics.yml` and survive restarts.

## Teleport Package

Teleport effects now include both warmup and completion phases.

**Warmup**
- blue/white rising spiral around the player
- beacon-style warmup sound
- triggered by consumers such as MiraWarps when their teleport warmup begins

**Completion**
- blue-white-blue horizontal rings at the origin and destination
- teleport completion sound
- only fires from the real successful Bukkit/Paper teleport event

Visual and audio channels respect the player's GUI toggles independently.


## MiraCosmetics Integration (0.1.4)

Adds independent persistent Visual/Audio GUI toggles, a centralized viewer-specific visual event engine, approved Factions/Outposts/Crates/Pinata/Kits visual channels and audio configuration placeholders with no sounds selected yet.

## MiraCosmetics Audio Integration (0.1.7)

Adds the first active Mira audio-cosmetics pass with persistent `/cosmetics` Audio control, independent per-event audio settings, configurable sound/volume/pitch, teleport and crate pitch sequences, nearby opted-in teleport completion audio, cancellation-safe teleport warmup sequences, and shared event channels for combat/bounties, Outposts, Crates, Kits, Tags and economy systems.
