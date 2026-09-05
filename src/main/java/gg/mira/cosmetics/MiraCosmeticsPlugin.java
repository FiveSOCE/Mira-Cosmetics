package gg.mira.cosmetics;

import com.mira.core.api.MiraCore;
import com.mira.core.api.MiraCoreProvider;
import com.mira.core.api.ModuleHealth;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class MiraCosmeticsPlugin extends JavaPlugin implements Listener, TabExecutor {
    private MiraCore core;
    private CosmeticService service;
    private CosmeticsGuiService gui;
    private VisualEffectEngine visualEngine;
    private AudioEffectEngine audioEngine;
    private final Map<UUID, Long> trailThrottle = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Existing installations may predate newer event/audio keys. Merge only missing
        // bundled defaults so new sound channels become live without overwriting admin edits.
        getConfig().options().copyDefaults(true);
        saveConfig();

        core = MiraCoreProvider.require();
        service = new CosmeticService(this);
        registerDefaults();
        gui = new CosmeticsGuiService(this, service);
        visualEngine = new VisualEffectEngine(this);
        audioEngine = new AudioEffectEngine(this);

        getServer().getServicesManager().register(CosmeticsApi.class, service, this, ServicePriority.Normal);
        core.services().register(CosmeticsApi.class, service);
        core.modules().register(this, "MiraCosmetics");
        core.modules().setHealth(this, ModuleHealth.HEALTHY,
                "Player cosmetics plus centralized visual/audio event effects ready");

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(gui, this);
        var command = getCommand("cosmetics");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }

        getLogger().info("MiraCosmetics v" + getPluginMeta().getVersion() + " enabled with "
                + service.cosmetics().size() + " registered cosmetic(s).");
    }

    @Override
    public void onDisable() {
        if (service != null) service.save();
        getServer().getServicesManager().unregisterAll(this);
        if (core != null) {
            if (service != null) core.services().unregister(CosmeticsApi.class, service);
            core.modules().unregister(this);
        }
    }

    private void registerDefaults() {
        service.register(new Cosmetic("trail_flame", CosmeticType.TRAIL, "Flame Trail", Particle.FLAME));
        service.register(new Cosmetic("trail_hearts", CosmeticType.TRAIL, "Heart Trail", Particle.HEART));
        service.register(new Cosmetic("join_totem", CosmeticType.JOIN, "Totem Arrival", Particle.TOTEM_OF_UNDYING));
        service.register(new Cosmetic("kill_soul", CosmeticType.KILL, "Soul Kill", Particle.SOUL_FIRE_FLAME));

        service.register(new Cosmetic("teleport_mira", CosmeticType.TELEPORT, "Mira Teleport Rings", Particle.DUST));
        service.register(new Cosmetic("fly_white", CosmeticType.FLY, "White Flight Trail", Particle.DUST));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String action = args.length == 0 ? "gui" : args[0].toLowerCase(Locale.ROOT);

        if (action.equals("grant") || action.equals("revoke")) {
            if (!sender.hasPermission("miracosmetics.admin")) {
                msg(sender, "&cYou do not have permission.");
                return true;
            }
            if (args.length < 3) {
                msg(sender, "&eUsage: /cosmetics " + action + " <player> <id>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            Cosmetic cosmetic = service.cosmetic(args[2]).orElse(null);
            if (target == null || cosmetic == null) {
                msg(sender, "&cPlayer or cosmetic not found.");
                return true;
            }
            boolean changed = action.equals("grant")
                    ? service.grant(target.getUniqueId(), cosmetic.id())
                    : service.revoke(target.getUniqueId(), cosmetic.id());
            if (changed) {
                core.audit().record("MiraCosmetics", action.equals("grant") ? "COSMETIC_GRANTED" : "COSMETIC_REVOKED",
                        sender instanceof Player player ? player.getUniqueId() : null, sender.getName(),
                        target.getUniqueId().toString(), action,
                        Map.of("cosmetic", cosmetic.id(), "targetName", target.getName()));
                msg(sender, "&aCosmetic " + action + " updated for &f" + target.getName() + "&a.");
                msg(target, action.equals("grant")
                        ? "&aUnlocked cosmetic: &f" + cosmetic.displayName()
                        : "&eCosmetic removed: &f" + cosmetic.displayName());
            } else {
                msg(sender, "&eNo cosmetic ownership change was required.");
            }
            return true;
        }

        if (!(sender instanceof Player player)) {
            msg(sender, "&cPlayers only for this cosmetics action.");
            return true;
        }
        if (!sender.hasPermission("miracosmetics.use")) {
            msg(sender, "&cYou do not have permission.");
            return true;
        }

        switch (action) {
            case "gui" -> gui.open(player);
            case "list" -> {
                msg(sender, "&6Mira Cosmetics");
                for (Cosmetic cosmetic : service.cosmetics()) {
                    boolean defaultEffect = service.isDefault(cosmetic);
                    boolean owned = service.owns(player.getUniqueId(), cosmetic.id());
                    String state = owned ? "&a" : defaultEffect ? "&e" : "&7";
                    msg(sender, state + cosmetic.id() + " &8- &f" + cosmetic.displayName()
                            + " &7(" + cosmetic.type() + ")" + (defaultEffect ? " &8[DEFAULT]" : ""));
                }
            }
            case "equip" -> {
                if (args.length < 2) {
                    msg(sender, "&eUsage: /cosmetics equip <id>");
                    return true;
                }
                Cosmetic cosmetic = service.cosmetic(args[1]).orElse(null);
                if (cosmetic == null) {
                    msg(sender, "&cUnknown cosmetic.");
                    return true;
                }
                if (!service.owns(player.getUniqueId(), cosmetic.id()) && !service.isDefault(cosmetic)) {
                    msg(sender, "&cYou have not unlocked that cosmetic.");
                    return true;
                }
                service.equip(player.getUniqueId(), cosmetic.type(), cosmetic.id());
                msg(sender, "&aEquipped &f" + cosmetic.displayName() + "&a for &f"
                        + cosmetic.type().name().toLowerCase(Locale.ROOT) + "&a.");
            }
            case "clear" -> {
                if (args.length < 2) {
                    msg(sender, "&eUsage: /cosmetics clear <trail|join|kill|teleport|fly>");
                    return true;
                }
                try {
                    CosmeticType type = CosmeticType.valueOf(args[1].toUpperCase(Locale.ROOT));
                    service.equip(player.getUniqueId(), type, null);
                    msg(sender, "&aCosmetic slot cleared. &7Default effect will be used when configured.");
                } catch (IllegalArgumentException ex) {
                    msg(sender, "&cUnknown cosmetic type.");
                }
            }
            case "status" -> {
                msg(sender, "&6Cosmetic Status");
                for (CosmeticType type : CosmeticType.values()) {
                    Cosmetic cosmetic = service.effective(player.getUniqueId(), type).orElse(null);
                    msg(sender, "&7" + type + ": &f" + (cosmetic == null ? "None" : cosmetic.displayName()));
                }
            }
            default -> msg(sender, "&7/cosmetics <list|equip|clear|status>");
        }
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) return;
        Location from = event.getFrom().clone();
        Location to = event.getTo().clone();
        if (from.getWorld().equals(to.getWorld()) && from.distanceSquared(to) < 0.01D) return;
        service.playTeleport(event.getPlayer(), from, to);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!getConfig().getBoolean("effects.trail.enabled", true)) return;
        Player player = event.getPlayer();
        if (event.getTo() == null || event.getFrom().distanceSquared(event.getTo()) == 0) return;
        long now = System.currentTimeMillis();
        long throttle = Math.max(50L, getConfig().getLong("effects.trail.throttle-millis", 250L));
        if (now - trailThrottle.getOrDefault(player.getUniqueId(), 0L) < throttle) return;
        trailThrottle.put(player.getUniqueId(), now);
        service.equipped(player.getUniqueId(), CosmeticType.TRAIL).ifPresent(cosmetic ->
                player.getWorld().spawnParticle(cosmetic.particle(), player.getLocation().add(0, 0.1, 0),
                        2, 0.15, 0.05, 0.15, 0));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!getConfig().getBoolean("effects.join.enabled", true)) return;
        service.equipped(event.getPlayer().getUniqueId(), CosmeticType.JOIN).ifPresent(cosmetic ->
                event.getPlayer().getWorld().spawnParticle(cosmetic.particle(),
                        event.getPlayer().getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.05));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!getConfig().getBoolean("effects.kill.enabled", true)) return;
        Player killer = event.getPlayer().getKiller();
        if (killer == null) return;
        service.equipped(killer.getUniqueId(), CosmeticType.KILL).ifPresent(cosmetic ->
                event.getPlayer().getWorld().spawnParticle(cosmetic.particle(),
                        event.getPlayer().getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.03));
        playAudioEvent(killer, "player_kill", killer.getLocation());
    }

    public boolean visualsEnabled(Player player) {
        return player != null && service != null && service.visualEnabled(player.getUniqueId());
    }

    public boolean audioEnabled(Player player) {
        return player != null && service != null && service.audioEnabled(player.getUniqueId());
    }

    public void setVisualsEnabled(Player player, boolean enabled) {
        if (player != null && service != null) service.setVisualEnabled(player.getUniqueId(), enabled);
    }

    public void setAudioEnabled(Player player, boolean enabled) {
        if (player != null && service != null) service.setAudioEnabled(player.getUniqueId(), enabled);
    }

    public void playEvent(Player player, String eventId, Location location) {
        if (visualEngine != null) visualEngine.play(player, eventId, location);
        if (audioEngine != null) audioEngine.play(player, eventId, location);
    }

    /**
     * Backwards-compatible event entry point used by existing Mira bridges.
     * It now renders both independently-configurable visual and audio channels.
     */
    public void playVisualEvent(Player player, String eventId, Location location) {
        playEvent(player, eventId, location);
    }

    public void playAudioEvent(Player player, String eventId, Location location) {
        if (audioEngine != null) audioEngine.play(player, eventId, location);
    }

    public void playAudioEventNearby(Location location, String eventId, double radius) {
        if (audioEngine == null || location == null || location.getWorld() == null) return;
        double radiusSquared = Math.max(0D, radius) * Math.max(0D, radius);
        for (Player viewer : location.getWorld().getPlayers()) {
            if (viewer.getLocation().distanceSquared(location) <= radiusSquared) {
                audioEngine.play(viewer, eventId, location);
            }
        }
    }

    public void playAudioEventGlobal(String eventId, Location source) {
        if (audioEngine == null || eventId == null || eventId.isBlank()) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Location at = source == null ? viewer.getLocation() : source;
            audioEngine.play(viewer, eventId, at);
        }
    }

    private void msg(CommandSender sender, String raw) { core.messages().send(sender, raw); }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>(List.of("list", "equip", "clear", "status"));
            if (sender.hasPermission("miracosmetics.admin")) values.addAll(List.of("grant", "revoke"));
            return complete(args[0], values);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("equip")) {
            return complete(args[1], service.cosmetics().stream().map(Cosmetic::id).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("clear")) {
            return complete(args[1], Arrays.stream(CosmeticType.values())
                    .map(type -> type.name().toLowerCase(Locale.ROOT)).toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("grant") || args[0].equalsIgnoreCase("revoke"))) {
            return complete(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("grant") || args[0].equalsIgnoreCase("revoke"))) {
            return complete(args[2], service.cosmetics().stream().map(Cosmetic::id).toList());
        }
        return List.of();
    }

    private static List<String> complete(String prefix, Collection<String> values) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .distinct().sorted().toList();
    }

    public enum CosmeticType { TRAIL, JOIN, KILL, TELEPORT, FLY }

    public record Cosmetic(String id, CosmeticType type, String displayName, Particle particle) { }

    public interface CosmeticsApi {
        void register(Cosmetic cosmetic);
        Optional<Cosmetic> cosmetic(String id);
        boolean grant(UUID player, String cosmeticId);
        boolean revoke(UUID player, String cosmeticId);
        boolean owns(UUID player, String cosmeticId);
        boolean equip(UUID player, CosmeticType type, String cosmeticId);
        Optional<Cosmetic> equipped(UUID player, CosmeticType type);
        Optional<Cosmetic> effective(UUID player, CosmeticType type);
        Collection<Cosmetic> cosmetics();
        void playTeleportWarmup(Player player, int durationSeconds);
        void playTeleport(Player player, Location origin, Location destination);
        void playFly(Player player);
        boolean visualEnabled(UUID player);
        boolean audioEnabled(UUID player);
        void setVisualEnabled(UUID player, boolean enabled);
        void setAudioEnabled(UUID player, boolean enabled);
    }

    public static final class CosmeticService implements CosmeticsApi {
        private final MiraCosmeticsPlugin plugin;
        private final File file;
        private final Map<String, Cosmetic> registry = new LinkedHashMap<>();
        private final Map<UUID, Set<String>> owned = new HashMap<>();
        private final Map<UUID, EnumMap<CosmeticType, String>> equipped = new HashMap<>();
        private final Map<UUID, Boolean> visualEnabled = new HashMap<>();
        private final Map<UUID, Boolean> audioEnabled = new HashMap<>();
        private final Map<UUID, Long> flyThrottle = new HashMap<>();

        CosmeticService(MiraCosmeticsPlugin plugin) {
            this.plugin = plugin;
            this.file = new File(plugin.getDataFolder(), "cosmetics.yml");
            load();
        }

        @Override
        public synchronized void register(Cosmetic cosmetic) {
            if (cosmetic == null || cosmetic.id() == null || cosmetic.id().isBlank()) return;
            registry.put(cosmetic.id().toLowerCase(Locale.ROOT), cosmetic);
        }

        @Override
        public Optional<Cosmetic> cosmetic(String id) {
            if (id == null) return Optional.empty();
            return Optional.ofNullable(registry.get(id.toLowerCase(Locale.ROOT)));
        }

        @Override
        public synchronized boolean grant(UUID player, String id) {
            Cosmetic cosmetic = cosmetic(id).orElse(null);
            if (cosmetic == null) return false;
            boolean changed = owned.computeIfAbsent(player, ignored -> new HashSet<>())
                    .add(cosmetic.id().toLowerCase(Locale.ROOT));
            if (changed) save();
            return changed;
        }

        @Override
        public synchronized boolean revoke(UUID player, String id) {
            if (id == null) return false;
            String key = id.toLowerCase(Locale.ROOT);
            Set<String> playerOwned = owned.get(player);
            boolean ownershipChanged = playerOwned != null && playerOwned.remove(key);
            EnumMap<CosmeticType, String> slots = equipped.get(player);
            boolean slotChanged = slots != null && slots.entrySet().removeIf(entry -> entry.getValue().equalsIgnoreCase(key));
            if (ownershipChanged || slotChanged) save();
            return ownershipChanged || slotChanged;
        }

        @Override
        public boolean owns(UUID player, String id) {
            return id != null && owned.getOrDefault(player, Set.of()).contains(id.toLowerCase(Locale.ROOT));
        }

        @Override
        public synchronized boolean equip(UUID player, CosmeticType type, String id) {
            EnumMap<CosmeticType, String> slots = equipped.computeIfAbsent(player,
                    ignored -> new EnumMap<>(CosmeticType.class));
            if (id == null || id.isBlank()) {
                slots.remove(type);
                save();
                return true;
            }
            Cosmetic cosmetic = cosmetic(id).orElse(null);
            if (cosmetic == null || cosmetic.type() != type) return false;
            if (!owns(player, cosmetic.id()) && !isDefault(cosmetic)) return false;
            slots.put(type, cosmetic.id().toLowerCase(Locale.ROOT));
            save();
            return true;
        }

        @Override
        public Optional<Cosmetic> equipped(UUID player, CosmeticType type) {
            EnumMap<CosmeticType, String> slots = equipped.get(player);
            String id = slots == null ? null : slots.get(type);
            return cosmetic(id);
        }

        @Override
        public Optional<Cosmetic> effective(UUID player, CosmeticType type) {
            Optional<Cosmetic> selected = equipped(player, type);
            if (selected.isPresent()) return selected;
            String defaultId = switch (type) {
                case TELEPORT -> plugin.getConfig().getString("effects.teleport.default-cosmetic", "teleport_mira");
                case FLY -> plugin.getConfig().getString("effects.fly.default-cosmetic", "fly_white");
                default -> null;
            };
            return cosmetic(defaultId);
        }

        boolean isDefault(Cosmetic cosmetic) {
            if (cosmetic == null) return false;
            return effective(null, cosmetic.type())
                    .map(value -> value.id().equalsIgnoreCase(cosmetic.id())).orElse(false);
        }

        @Override
        public Collection<Cosmetic> cosmetics() { return List.copyOf(registry.values()); }

        @Override
        public boolean visualEnabled(UUID player) { return visualEnabled.getOrDefault(player, true); }

        @Override
        public boolean audioEnabled(UUID player) { return audioEnabled.getOrDefault(player, true); }

        @Override
        public synchronized void setVisualEnabled(UUID player, boolean enabled) {
            visualEnabled.put(player, enabled);
            save();
        }

        @Override
        public synchronized void setAudioEnabled(UUID player, boolean enabled) {
            audioEnabled.put(player, enabled);
            save();
        }

        @Override
        public void playTeleportWarmup(Player player, int durationSeconds) {
            if (player == null || !visualEnabled(player.getUniqueId())) return;
            plugin.playEvent(player, "teleport_warmup", player.getLocation());
        }

        @Override
        public void playTeleport(Player player, Location origin, Location destination) {
            if (player == null || !plugin.getConfig().getBoolean("effects.teleport.enabled", true)) return;
            if (visualEnabled(player.getUniqueId())
                    && plugin.getConfig().getBoolean("effects.teleport.visual.enabled", true)) {
                renderTeleportRings(origin);
                renderTeleportRings(destination);
            }
            Location soundLocation = destination == null ? player.getLocation() : destination;
            plugin.playAudioEventNearby(soundLocation, "teleport_complete",
                    Math.max(0D, plugin.getConfig().getDouble("effects.teleport.audio-radius", 16.0D)));

        }

        private void renderTeleportRings(Location center) {
            if (center == null || center.getWorld() == null) return;
            int points = Math.max(12, plugin.getConfig().getInt("effects.teleport.ring-points", 36));
            double radius = Math.max(0.25D, plugin.getConfig().getDouble("effects.teleport.ring-radius", 1.0D));
            double spacing = Math.max(0.15D, plugin.getConfig().getDouble("effects.teleport.ring-spacing", 0.55D));
            double baseY = plugin.getConfig().getDouble("effects.teleport.base-y-offset", 0.15D);

            Particle.DustOptions blue = new Particle.DustOptions(Color.fromRGB(70, 150, 255), 1.0F);
            Particle.DustOptions white = new Particle.DustOptions(Color.WHITE, 1.0F);
            Particle.DustOptions[] colors = {blue, white, blue};

            for (int ring = 0; ring < 3; ring++) {
                double y = center.getY() + baseY + ring * spacing;
                for (int i = 0; i < points; i++) {
                    double angle = (Math.PI * 2D * i) / points;
                    double x = center.getX() + Math.cos(angle) * radius;
                    double z = center.getZ() + Math.sin(angle) * radius;
                    center.getWorld().spawnParticle(Particle.DUST, x, y, z, 1,
                            0D, 0D, 0D, 0D, colors[ring]);
                }
            }
        }

        @Override
        public void playFly(Player player) {
            if (player == null || !plugin.getConfig().getBoolean("effects.fly.enabled", true)
                    || !visualEnabled(player.getUniqueId()) || !player.isFlying()) return;
            long now = System.currentTimeMillis();
            long throttle = Math.max(50L, plugin.getConfig().getLong("effects.fly.throttle-millis", 100L));
            if (now - flyThrottle.getOrDefault(player.getUniqueId(), 0L) < throttle) return;
            flyThrottle.put(player.getUniqueId(), now);

            Location feet = player.getLocation().clone().add(0D,
                    plugin.getConfig().getDouble("effects.fly.y-offset", -0.15D), 0D);
            Particle.DustOptions white = new Particle.DustOptions(Color.WHITE,
                    (float) Math.max(0.5D, plugin.getConfig().getDouble("effects.fly.dust-size", 0.8D)));
            player.getWorld().spawnParticle(Particle.DUST, feet,
                    Math.max(1, plugin.getConfig().getInt("effects.fly.count", 2)),
                    0.12, 0.03, 0.12, 0D, white);
        }

        private void load() {
            plugin.getDataFolder().mkdirs();
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection players = yaml.getConfigurationSection("players");
            if (players == null) return;
            for (String uuidText : players.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(uuidText);
                    owned.put(id, new HashSet<>(players.getStringList(uuidText + ".owned")));
                    EnumMap<CosmeticType, String> slots = new EnumMap<>(CosmeticType.class);
                    for (CosmeticType type : CosmeticType.values()) {
                        String value = players.getString(uuidText + ".equipped."
                                + type.name().toLowerCase(Locale.ROOT));
                        if (value != null && !value.isBlank()) slots.put(type, value.toLowerCase(Locale.ROOT));
                    }
                    equipped.put(id, slots);
                    visualEnabled.put(id, players.getBoolean(uuidText + ".settings.visual", true));
                    audioEnabled.put(id, players.getBoolean(uuidText + ".settings.audio", true));
                } catch (IllegalArgumentException ignored) { }
            }
        }

        synchronized void save() {
            YamlConfiguration yaml = new YamlConfiguration();
            Set<UUID> players = new HashSet<>();
            players.addAll(owned.keySet());
            players.addAll(equipped.keySet());
            players.addAll(visualEnabled.keySet());
            players.addAll(audioEnabled.keySet());
            for (UUID id : players) {
                yaml.set("players." + id + ".owned", new ArrayList<>(owned.getOrDefault(id, Set.of())));
                yaml.set("players." + id + ".settings.visual", visualEnabled.getOrDefault(id, true));
                yaml.set("players." + id + ".settings.audio", audioEnabled.getOrDefault(id, true));
                EnumMap<CosmeticType, String> slots = equipped.get(id);
                if (slots != null) {
                    for (Map.Entry<CosmeticType, String> entry : slots.entrySet()) {
                        yaml.set("players." + id + ".equipped."
                                + entry.getKey().name().toLowerCase(Locale.ROOT), entry.getValue());
                    }
                }
            }
            try { yaml.save(file); }
            catch (IOException ex) { plugin.getLogger().severe("Could not save cosmetics.yml: " + ex.getMessage()); }
        }
    }
}
