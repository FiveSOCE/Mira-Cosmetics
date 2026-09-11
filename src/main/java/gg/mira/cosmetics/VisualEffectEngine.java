package gg.mira.cosmetics;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class VisualEffectEngine implements Listener {
    private enum WarmupSource { GENERIC, ESSENTIALS, FACTIONS, RTP }

    private final MiraCosmeticsPlugin plugin;
    private final Map<UUID, BukkitTask> teleportWarmups = new HashMap<>();
    private final Map<UUID, WarmupSource> teleportSources = new HashMap<>();

    public VisualEffectEngine(MiraCosmeticsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        registerExternalEvent("net.ess3.api.events.teleport.TeleportWarmupEvent", this::onEssentialsWarmup);
        registerExternalEvent("net.essentialsx.api.v2.events.TeleportWarmupCancelledEvent", this::onEssentialsWarmupCancelled);
    }

    public void play(Player player, String eventId, Location location) {
        if (player == null || eventId == null || location == null || location.getWorld() == null) return;
        String id = eventId.toLowerCase(Locale.ROOT);

        // Teleport warmups are a lifecycle, not a one-shot particle event. They remain active
        // until the real teleport occurs or the source explicitly cancels the queue.
        if (id.equals("teleport_cancel") || id.equals("teleport_complete")) {
            stopTeleportWarmup(player.getUniqueId());
            return;
        }
        if (id.equals("teleport_warmup")) {
            startTeleportWarmup(player, WarmupSource.GENERIC, configuredMaximumTicks());
            return;
        }

        if (!plugin.visualsEnabled(player)) return;
        if (!plugin.getConfig().getBoolean("events." + id + ".visual.enabled", true)) return;

        switch (id) {
            case "faction_claim" -> ring(player, location, Color.fromRGB(80, 220, 120), 1.3, 32);
            case "faction_unclaim" -> ring(player, location, Color.fromRGB(180, 180, 180), 1.3, 32);
            case "safezone_enter" -> doubleRing(player, location, Color.fromRGB(70, 150, 255), Color.WHITE);
            case "warzone_enter" -> doubleRing(player, location, Color.fromRGB(255, 60, 60), Color.fromRGB(255, 150, 40));
            case "faction_create" -> burst(player, location.clone().add(0, 1, 0), Color.fromRGB(255, 215, 70), 42, 0.8);
            case "faction_upgrade" -> {
                burst(player, location.clone().add(0, 1, 0), Color.fromRGB(190, 90, 255), 28, 0.55);
                burst(player, location.clone().add(0, 1.2, 0), Color.fromRGB(255, 215, 70), 18, 0.4);
            }
            case "faction_disband" -> burst(player, location.clone().add(0, 1, 0), Color.fromRGB(120, 120, 120), 48, 0.9);
            case "outpost_captured" -> {
                burst(player, location.clone().add(0, 1, 0), Color.fromRGB(255, 200, 40), 65, 1.2);
                ring(player, location.clone().add(0, 0.2, 0), Color.WHITE, 1.8, 44);
            }
            case "crate_open" -> crateOpen(player, location);
            case "crate_reward_common" -> burst(player, location.clone().add(0, 1, 0), Color.WHITE, 24, 0.45);
            case "crate_reward_rare" -> burst(player, location.clone().add(0, 1, 0), Color.fromRGB(80, 190, 255), 38, 0.65);
            case "crate_reward_legendary" -> {
                burst(player, location.clone().add(0, 1, 0), Color.fromRGB(255, 190, 40), 65, 0.9);
                player.spawnParticle(Particle.FIREWORK, location.clone().add(0, 1, 0), 30,
                        0.55, 0.7, 0.55, 0.04);
            }
            case "pinata_spawn" -> {
                burst(player, location.clone().add(0, 1, 0), Color.fromRGB(255, 70, 180), 30, 0.65);
                burst(player, location.clone().add(0, 1, 0), Color.fromRGB(70, 190, 255), 30, 0.65);
            }
            case "pinata_hit" -> burst(player, location.clone().add(0, 1, 0), Color.WHITE, 8, 0.25);
            case "pinata_low_health" -> doubleRing(player, location, Color.fromRGB(255, 40, 40), Color.WHITE);
            case "pinata_death" -> {
                burst(player, location.clone().add(0, 1, 0), Color.fromRGB(255, 70, 180), 45, 1.0);
                burst(player, location.clone().add(0, 1, 0), Color.fromRGB(70, 190, 255), 45, 1.0);
                burst(player, location.clone().add(0, 1, 0), Color.fromRGB(255, 220, 60), 45, 1.0);
            }
            case "kit_claim" -> {
                ring(player, location, Color.fromRGB(90, 230, 120), 1.0, 28);
                burst(player, location.clone().add(0, 0.8, 0), Color.WHITE, 18, 0.35);
            }
            case "kit_temp_claim" -> {
                ring(player, location, Color.fromRGB(190, 90, 255), 1.0, 28);
                burst(player, location.clone().add(0, 0.8, 0), Color.WHITE, 22, 0.4);
            }
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        stopTeleportWarmup(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        stopTeleportWarmup(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (teleportSources.get(event.getPlayer().getUniqueId()) != WarmupSource.FACTIONS || event.getTo() == null) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getWorld() != to.getWorld()
                || from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()) {
            plugin.playEvent(event.getPlayer(), "teleport_cancel", event.getPlayer().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQueuedTeleportCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage();
        if (raw == null || raw.length() < 2) return;
        String[] parts = raw.substring(1).trim().split("\\s+");
        if (parts.length == 0) return;
        String label = deNamespace(parts[0]).toLowerCase(Locale.ROOT);

        if ((label.equals("f") || label.equals("faction") || label.equals("factions")) && parts.length >= 2) {
            String sub = parts[1].toLowerCase(Locale.ROOT);
            if (!sub.equals("home") && !sub.equals("warp")) return;
            // Run one tick later so MiraFactions has first accepted the command. Actual movement
            // cancellation and the PlayerTeleportEvent terminate the visual lifecycle.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (event.getPlayer().isOnline()) {
                    plugin.playAudioEvent(event.getPlayer(), "teleport_warmup", event.getPlayer().getLocation());
                    startTeleportWarmup(event.getPlayer(), WarmupSource.FACTIONS, 20 * 15);
                }
            });
            return;
        }

        if (label.equals("rtp") && (parts.length == 1 || (!parts[1].equalsIgnoreCase("status") && !parts[1].equalsIgnoreCase("reload")))) {
            // MiraRTP has an async location-search queue rather than a fixed timer. Check its
            // registered API after command execution so denied/cooldown requests do not animate.
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = event.getPlayer();
                if (player.isOnline() && rtpSearching(player.getUniqueId())) {
                    plugin.playAudioEvent(player, "teleport_warmup", player.getLocation());
                    startTeleportWarmup(player, WarmupSource.RTP, configuredMaximumTicks());
                }
            });
        }
    }

    private void startTeleportWarmup(Player player, WarmupSource source, int maxTicks) {
        if (player == null || !player.isOnline()) return;
        if (!plugin.visualsEnabled(player)) return;
        if (!plugin.getConfig().getBoolean("effects.teleport.enabled", true)) return;
        if (!plugin.getConfig().getBoolean("events.teleport_warmup.visual.enabled", true)) return;

        stopTeleportWarmup(player.getUniqueId());
        teleportSources.put(player.getUniqueId(), source);
        int safetyTicks = Math.max(20, maxTicks);

        BukkitRunnable runnable = new BukkitRunnable() {
            int age;
            @Override
            public void run() {
                if (!player.isOnline() || !plugin.visualsEnabled(player) || age >= safetyTicks) {
                    stopTeleportWarmup(player.getUniqueId());
                    return;
                }
                if (source == WarmupSource.RTP && age >= 4 && !rtpSearching(player.getUniqueId())) {
                    stopTeleportWarmup(player.getUniqueId());
                    return;
                }

                Location base = player.getLocation();
                double angle = age * 0.24D;
                double radius = 0.95D;
                double y = 0.15D + (age % 30) / 30.0D * 1.7D;
                Color color = ((age / 4) % 2 == 0) ? Color.fromRGB(70, 150, 255) : Color.WHITE;
                dust(player, base.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius), color, 1);
                age += 2;
            }
        };
        BukkitTask task = runnable.runTaskTimer(plugin, 0L, 2L);
        teleportWarmups.put(player.getUniqueId(), task);
    }

    private void stopTeleportWarmup(UUID playerId) {
        BukkitTask task = teleportWarmups.remove(playerId);
        if (task != null) task.cancel();
        teleportSources.remove(playerId);
    }

    private int configuredMaximumTicks() {
        return Math.max(100, plugin.getConfig().getInt("events.teleport_warmup.visual.max-duration-ticks", 2400));
    }

    private boolean rtpSearching(UUID playerId) {
        Plugin rtp = Bukkit.getPluginManager().getPlugin("MiraRTP");
        if (rtp == null || !rtp.isEnabled()) return false;
        for (RegisteredServiceProvider<?> registration : Bukkit.getServicesManager().getRegistrations(rtp)) {
            if (!registration.getService().getName().endsWith("RtpApi")) continue;
            try {
                Object value = registration.getProvider().getClass()
                        .getMethod("searching", UUID.class)
                        .invoke(registration.getProvider(), playerId);
                return value instanceof Boolean result && result;
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private void registerExternalEvent(String className, EventExecutor executor) {
        try {
            Class<?> raw = Class.forName(className);
            if (!Event.class.isAssignableFrom(raw)) return;
            plugin.getServer().getPluginManager().registerEvent(
                    (Class<? extends Event>) raw,
                    this,
                    EventPriority.MONITOR,
                    executor,
                    plugin,
                    true
            );
        } catch (ClassNotFoundException ignored) {
            // Optional dependency is not installed.
        } catch (Throwable ex) {
            plugin.getLogger().warning("Could not hook teleport event " + className + ": " + ex.getMessage());
        }
    }

    private void onEssentialsWarmup(Listener ignored, Event event) {
        try {
            Method getDelay = event.getClass().getMethod("getDelay");
            Object delayRaw = getDelay.invoke(event);
            double delay = delayRaw instanceof Number number ? number.doubleValue() : 0D;
            if (delay <= 0D) return;

            Object user = event.getClass().getMethod("getTeleportee").invoke(event);
            if (user == null) return;
            Object base = user.getClass().getMethod("getBase").invoke(user);
            if (!(base instanceof Player player)) return;

            plugin.playAudioEvent(player, "teleport_warmup", player.getLocation());
            startTeleportWarmup(player, WarmupSource.ESSENTIALS,
                    Math.max(40, (int) Math.ceil((delay + 3D) * 20D)));
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().fine("Could not read Essentials teleport warmup: " + ex.getMessage());
        }
    }

    private void onEssentialsWarmupCancelled(Listener ignored, Event event) {
        try {
            Object value = event.getClass().getMethod("getPlayer").invoke(event);
            if (value instanceof Player player) {
                plugin.playEvent(player, "teleport_cancel", player.getLocation());
            }
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().fine("Could not read Essentials teleport cancellation: " + ex.getMessage());
        }
    }

    private String deNamespace(String command) {
        int colon = command.indexOf(':');
        return colon >= 0 && colon + 1 < command.length() ? command.substring(colon + 1) : command;
    }

    private void crateOpen(Player player, Location center) {
        new BukkitRunnable() {
            int step;
            @Override
            public void run() {
                if (!player.isOnline() || !plugin.visualsEnabled(player) || step >= 30) {
                    cancel();
                    return;
                }
                double angle = step * 0.55D;
                double y = 0.1D + step * 0.055D;
                dust(player, center.clone().add(Math.cos(angle) * 0.8D, y, Math.sin(angle) * 0.8D),
                        step % 2 == 0 ? Color.fromRGB(80, 190, 255) : Color.WHITE, 2);
                step++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void doubleRing(Player player, Location center, Color lower, Color upper) {
        ring(player, center.clone().add(0, 0.2, 0), lower, 1.1, 30);
        ring(player, center.clone().add(0, 0.75, 0), upper, 1.1, 30);
    }

    private void ring(Player player, Location center, Color color, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2D * i / points;
            dust(player, center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius), color, 1);
        }
    }

    private void burst(Player player, Location center, Color color, int count, double spread) {
        player.spawnParticle(Particle.DUST, center, count, spread, spread, spread, 0.02,
                new Particle.DustOptions(color, 1.0F));
    }

    private void dust(Player player, Location location, Color color, int count) {
        player.spawnParticle(Particle.DUST, location, count, 0D, 0D, 0D, 0D,
                new Particle.DustOptions(color, 0.9F));
    }
}
