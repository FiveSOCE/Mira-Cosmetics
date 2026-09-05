package gg.mira.cosmetics;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AudioEffectEngine {
    private final MiraCosmeticsPlugin plugin;
    private final Map<UUID, Map<String, Long>> lastPlayed = new HashMap<>();
    private final Map<UUID, Map<String, BukkitTask>> sequences = new HashMap<>();

    public AudioEffectEngine(MiraCosmeticsPlugin plugin) {
        this.plugin = plugin;
    }

    public void play(Player player, String eventId, Location location) {
        if (player == null || eventId == null || eventId.isBlank() || !player.isOnline()) return;
        String id = eventId.toLowerCase(Locale.ROOT);
        if (!plugin.audioEnabled(player)) return;
        if (id.equals("teleport_cancel")) cancelSequence(player.getUniqueId(), "teleport_warmup");
        String base = "events." + id + ".audio.";
        if (!plugin.getConfig().getBoolean(base + "enabled", false)) return;

        long cooldown = Math.max(0L, plugin.getConfig().getLong(base + "cooldown-millis", 0L));
        long now = System.currentTimeMillis();
        Map<String, Long> playerTimes = lastPlayed.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        if (cooldown > 0L && now - playerTimes.getOrDefault(id, 0L) < cooldown) return;
        playerTimes.put(id, now);

        Location at = location == null ? player.getLocation() : location;
        playConfigured(player, at, base, "sound", "volume", "pitch");

        if (id.equals("teleport_warmup")) playPitchSequence(player, id, at, base + "progress.");
        if (id.equals("crate_open")) playPitchSequence(player, id, at, base + "progress.");
        if (id.equals("crate_reward_legendary")) {
            playConfigured(player, at, base + "secondary.", "sound", "volume", "pitch");
        }
    }

    private void playPitchSequence(Player player, String eventId, Location location, String base) {
        if (!plugin.getConfig().getBoolean(base + "enabled", true)) return;
        Sound sound = resolve(plugin.getConfig().getString(base + "sound", ""));
        if (sound == null) return;

        List<Double> pitches = plugin.getConfig().getDoubleList(base + "pitches");
        if (pitches.isEmpty()) return;
        float volume = positive(plugin.getConfig().getDouble(base + "volume", 0.35D), 0.35F);
        long interval = Math.max(1L, plugin.getConfig().getLong(base + "interval-ticks", 12L));

        cancelSequence(player.getUniqueId(), eventId);
        BukkitRunnable runnable = new BukkitRunnable() {
            int index;
            @Override
            public void run() {
                if (!player.isOnline() || !plugin.audioEnabled(player) || index >= pitches.size()) {
                    cancel();
                    removeSequence(player.getUniqueId(), eventId);
                    return;
                }
                float pitch = positive(pitches.get(index), 1.0F);
                player.playSound(location, sound, volume, pitch);
                index++;
            }
        };
        BukkitTask task = runnable.runTaskTimer(plugin, interval, interval);
        sequences.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>()).put(eventId, task);
    }

    private void cancelSequence(UUID playerId, String eventId) {
        Map<String, BukkitTask> playerTasks = sequences.get(playerId);
        if (playerTasks == null) return;
        BukkitTask task = playerTasks.remove(eventId);
        if (task != null) task.cancel();
        if (playerTasks.isEmpty()) sequences.remove(playerId);
    }

    private void removeSequence(UUID playerId, String eventId) {
        Map<String, BukkitTask> playerTasks = sequences.get(playerId);
        if (playerTasks == null) return;
        playerTasks.remove(eventId);
        if (playerTasks.isEmpty()) sequences.remove(playerId);
    }

    private void playConfigured(Player player, Location location, String base,
                                String soundKey, String volumeKey, String pitchKey) {
        Sound sound = resolve(plugin.getConfig().getString(base + soundKey, ""));
        if (sound == null) return;
        float volume = positive(plugin.getConfig().getDouble(base + volumeKey, 1.0D), 1.0F);
        float pitch = positive(plugin.getConfig().getDouble(base + pitchKey, 1.0D), 1.0F);
        player.playSound(location, sound, volume, pitch);
    }

    private Sound resolve(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String key = raw.trim().toLowerCase(Locale.ROOT);
        if (!key.contains(":")) key = "minecraft:" + key.replace('_', '.');
        NamespacedKey namespaced = NamespacedKey.fromString(key);
        if (namespaced == null) return null;
        Sound sound = Registry.SOUNDS.get(namespaced);
        if (sound == null) plugin.getLogger().warning("Unknown configured sound: " + raw);
        return sound;
    }

    private static float positive(double value, float fallback) {
        if (!Double.isFinite(value) || value <= 0D) return fallback;
        return (float) value;
    }
}
