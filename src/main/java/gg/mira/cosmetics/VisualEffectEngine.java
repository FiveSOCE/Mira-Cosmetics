package gg.mira.cosmetics;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Locale;

public final class VisualEffectEngine {
    private final MiraCosmeticsPlugin plugin;

    public VisualEffectEngine(MiraCosmeticsPlugin plugin) {
        this.plugin = plugin;
    }

    public void play(Player player, String eventId, Location location) {
        if (player == null || eventId == null || location == null || location.getWorld() == null) return;
        String id = eventId.toLowerCase(Locale.ROOT);
        if (!plugin.visualsEnabled(player)) return;
        if (!plugin.getConfig().getBoolean("events." + id + ".visual.enabled", true)) return;

        switch (id) {
            case "teleport_warmup" -> teleportWarmup(player);
            case "faction_claim" -> ring(location, Color.fromRGB(80, 220, 120), 1.3, 32);
            case "faction_unclaim" -> ring(location, Color.fromRGB(180, 180, 180), 1.3, 32);
            case "safezone_enter" -> doubleRing(location, Color.fromRGB(70, 150, 255), Color.WHITE);
            case "warzone_enter" -> doubleRing(location, Color.fromRGB(255, 60, 60), Color.fromRGB(255, 150, 40));
            case "faction_create" -> burst(location.clone().add(0, 1, 0), Color.fromRGB(255, 215, 70), 42, 0.8);
            case "faction_upgrade" -> {
                burst(location.clone().add(0, 1, 0), Color.fromRGB(190, 90, 255), 28, 0.55);
                burst(location.clone().add(0, 1.2, 0), Color.fromRGB(255, 215, 70), 18, 0.4);
            }
            case "faction_disband" -> burst(location.clone().add(0, 1, 0), Color.fromRGB(120, 120, 120), 48, 0.9);
            case "outpost_captured" -> {
                burst(location.clone().add(0, 1, 0), Color.fromRGB(255, 200, 40), 65, 1.2);
                ring(location.clone().add(0, 0.2, 0), Color.WHITE, 1.8, 44);
            }
            case "crate_open" -> crateOpen(location);
            case "crate_reward_common" -> burst(location.clone().add(0, 1, 0), Color.WHITE, 24, 0.45);
            case "crate_reward_rare" -> burst(location.clone().add(0, 1, 0), Color.fromRGB(80, 190, 255), 38, 0.65);
            case "crate_reward_legendary" -> {
                burst(location.clone().add(0, 1, 0), Color.fromRGB(255, 190, 40), 65, 0.9);
                location.getWorld().spawnParticle(Particle.FIREWORK, location.clone().add(0, 1, 0), 30,
                        0.55, 0.7, 0.55, 0.04);
            }
            case "pinata_spawn" -> {
                burst(location.clone().add(0, 1, 0), Color.fromRGB(255, 70, 180), 30, 0.65);
                burst(location.clone().add(0, 1, 0), Color.fromRGB(70, 190, 255), 30, 0.65);
            }
            case "pinata_hit" -> burst(location.clone().add(0, 1, 0), Color.WHITE, 8, 0.25);
            case "pinata_low_health" -> doubleRing(location, Color.fromRGB(255, 40, 40), Color.WHITE);
            case "pinata_death" -> {
                burst(location.clone().add(0, 1, 0), Color.fromRGB(255, 70, 180), 45, 1.0);
                burst(location.clone().add(0, 1, 0), Color.fromRGB(70, 190, 255), 45, 1.0);
                burst(location.clone().add(0, 1, 0), Color.fromRGB(255, 220, 60), 45, 1.0);
            }
            case "kit_claim" -> {
                ring(location, Color.fromRGB(90, 230, 120), 1.0, 28);
                burst(location.clone().add(0, 0.8, 0), Color.WHITE, 18, 0.35);
            }
            case "kit_temp_claim" -> {
                ring(location, Color.fromRGB(190, 90, 255), 1.0, 28);
                burst(location.clone().add(0, 0.8, 0), Color.WHITE, 22, 0.4);
            }
            default -> { }
        }
    }

    private void teleportWarmup(Player player) {
        int ticks = Math.max(20, plugin.getConfig().getInt("events.teleport_warmup.visual.duration-ticks", 80));
        new BukkitRunnable() {
            int age;
            @Override
            public void run() {
                if (!player.isOnline() || !plugin.visualsEnabled(player) || age >= ticks) {
                    cancel();
                    return;
                }
                Location base = player.getLocation();
                double angle = age * 0.24D;
                double radius = 0.95D;
                double y = 0.15D + (age % 30) / 30.0D * 1.7D;
                Color color = ((age / 4) % 2 == 0) ? Color.fromRGB(70, 150, 255) : Color.WHITE;
                dust(base.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius), color, 1);
                age += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void crateOpen(Location center) {
        new BukkitRunnable() {
            int step;
            @Override
            public void run() {
                if (step >= 30 || center.getWorld() == null) {
                    cancel();
                    return;
                }
                double angle = step * 0.55D;
                double y = 0.1D + step * 0.055D;
                dust(center.clone().add(Math.cos(angle) * 0.8D, y, Math.sin(angle) * 0.8D),
                        step % 2 == 0 ? Color.fromRGB(80, 190, 255) : Color.WHITE, 2);
                step++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void doubleRing(Location center, Color lower, Color upper) {
        ring(center.clone().add(0, 0.2, 0), lower, 1.1, 30);
        ring(center.clone().add(0, 0.75, 0), upper, 1.1, 30);
    }

    private void ring(Location center, Color color, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2D * i / points;
            dust(center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius), color, 1);
        }
    }

    private void burst(Location center, Color color, int count, double spread) {
        if (center.getWorld() == null) return;
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.0F);
        center.getWorld().spawnParticle(Particle.DUST, center, count, spread, spread, spread, 0.02, dust);
    }

    private void dust(Location location, Color color, int count) {
        if (location.getWorld() == null) return;
        location.getWorld().spawnParticle(Particle.DUST, location, count, 0D, 0D, 0D, 0D,
                new Particle.DustOptions(color, 0.9F));
    }
}
