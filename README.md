# MiraCosmetics

MiraCosmetics is the cosmetic unlock and effect framework for the Mira Paper server suite. It manages persistent cosmetic ownership and equipped slots for visual effects such as trails, join effects and kill effects.

## Download

[**Download MiraCosmetics v0.1.0**](https://github.com/FiveSOCE/Mira-Cosmetics/releases/download/v0.1.0/MiraCosmetics-0.1.0.jar)

## Requirements / Dependencies

- Paper 1.21.11
- Java 21

## How MiraCosmetics Works

Cosmetics are registered under unique IDs and assigned to cosmetic slots such as trail, join and kill effects. Player unlocks and equipped choices persist across restarts. Built-in examples include flame/heart trails, a totem join effect and a soul-fire kill effect.

The public Bukkit ServicesManager API lets other Mira systems such as Crates, Daily and Seasons grant cosmetic unlocks without directly editing MiraCosmetics data.

## Commands

| Command | Permission | What it does |
| --- | --- | --- |
| `/cosmetics list` | `miracosmetics.use` | Lists available and/or owned cosmetics. |
| `/cosmetics equip <id>` | `miracosmetics.use` | Equips an unlocked cosmetic in its appropriate slot. |
| `/cosmetics clear <trail|join|kill>` | `miracosmetics.use` | Clears the equipped cosmetic from a slot. |
| `/cosmetics grant <player> <id>` | `miracosmetics.admin` | Administratively grants a cosmetic unlock to a player. |

Alias: `/cosmetic`

## Permissions

| Permission | Default | What it does |
| --- | --- | --- |
| `miracosmetics.use` | Everyone | Allows normal cosmetic viewing, equipping and clearing. |
| `miracosmetics.admin` | OP | Allows administrative cosmetic grants. |
