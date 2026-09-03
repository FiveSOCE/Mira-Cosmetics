package gg.mira.cosmetics;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class MiraCosmeticsPlugin extends JavaPlugin implements Listener {
    private static final String PREFIX = "&5&lMira &8>> &r";
    private CosmeticService service;
    private final Map<UUID, Long> trailThrottle = new HashMap<>();

    @Override public void onEnable() {
        service = new CosmeticService(this);
        service.register(new Cosmetic("trail_flame", CosmeticType.TRAIL, "Flame Trail", Particle.FLAME));
        service.register(new Cosmetic("trail_hearts", CosmeticType.TRAIL, "Heart Trail", Particle.HEART));
        service.register(new Cosmetic("join_totem", CosmeticType.JOIN, "Totem Arrival", Particle.TOTEM_OF_UNDYING));
        service.register(new Cosmetic("kill_soul", CosmeticType.KILL, "Soul Kill", Particle.SOUL_FIRE_FLAME));
        getServer().getServicesManager().register(CosmeticsApi.class, service, this, ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override public void onDisable() { service.save(); getServer().getServicesManager().unregisterAll(this); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) && (args.length == 0 || !args[0].equalsIgnoreCase("grant"))) { msg(sender, "&cPlayers only."); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            Player p = (Player) sender;
            msg(sender, "&6Mira Cosmetics");
            for (Cosmetic c : service.registry.values()) msg(sender, (service.owns(p.getUniqueId(), c.id()) ? "&a" : "&7") + c.id() + " &8- &f" + c.displayName() + " &7(" + c.type() + ")");
            return true;
        }
        if (args[0].equalsIgnoreCase("equip")) {
            if (args.length < 2) { msg(sender, "&cUsage: /cosmetics equip <id>"); return true; }
            Cosmetic c = service.registry.get(args[1].toLowerCase(Locale.ROOT));
            if (c == null) { msg(sender, "&cUnknown cosmetic."); return true; }
            Player p = (Player) sender;
            if (!service.owns(p.getUniqueId(), c.id())) { msg(sender, "&cYou have not unlocked that cosmetic."); return true; }
            service.equip(p.getUniqueId(), c.type(), c.id());
            msg(sender, "&aEquipped " + c.displayName() + ".");
            return true;
        }
        if (args[0].equalsIgnoreCase("clear")) {
            if (args.length < 2) { msg(sender, "&cUsage: /cosmetics clear <trail|join|kill>"); return true; }
            try { service.equip(((Player)sender).getUniqueId(), CosmeticType.valueOf(args[1].toUpperCase(Locale.ROOT)), null); msg(sender, "&aCosmetic slot cleared."); }
            catch (IllegalArgumentException ex) { msg(sender, "&cUnknown cosmetic type."); }
            return true;
        }
        if (args[0].equalsIgnoreCase("grant")) {
            if (!sender.hasPermission("miracosmetics.admin")) { msg(sender, "&cNo permission."); return true; }
            if (args.length < 3) { msg(sender, "&cUsage: /cosmetics grant <player> <id>"); return true; }
            Player target = Bukkit.getPlayerExact(args[1]);
            Cosmetic c = service.registry.get(args[2].toLowerCase(Locale.ROOT));
            if (target == null || c == null) { msg(sender, "&cPlayer or cosmetic not found."); return true; }
            service.grant(target.getUniqueId(), c.id());
            msg(target, "&aUnlocked cosmetic: &f" + c.displayName());
            msg(sender, "&aGranted.");
            return true;
        }
        msg(sender, "&7/cosmetics list, equip <id>, clear <type>");
        return true;
    }

    @EventHandler public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (event.getTo() == null || event.getFrom().distanceSquared(event.getTo()) == 0) return;
        long now = System.currentTimeMillis();
        if (now - trailThrottle.getOrDefault(p.getUniqueId(), 0L) < 250L) return;
        trailThrottle.put(p.getUniqueId(), now);
        service.equipped(p.getUniqueId(), CosmeticType.TRAIL).ifPresent(c -> p.getWorld().spawnParticle(c.particle(), p.getLocation().add(0, 0.1, 0), 2, 0.15, 0.05, 0.15, 0));
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        service.equipped(event.getPlayer().getUniqueId(), CosmeticType.JOIN).ifPresent(c -> event.getPlayer().getWorld().spawnParticle(c.particle(), event.getPlayer().getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.05));
    }

    @EventHandler public void onDeath(PlayerDeathEvent event) {
        Player killer = event.getPlayer().getKiller();
        if (killer == null) return;
        service.equipped(killer.getUniqueId(), CosmeticType.KILL).ifPresent(c -> event.getPlayer().getWorld().spawnParticle(c.particle(), event.getPlayer().getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.03));
    }

    private void msg(CommandSender sender, String raw) { sender.sendMessage(ChatColor.translateAlternateColorCodes('&', PREFIX + raw)); }

    public enum CosmeticType { TRAIL, JOIN, KILL }
    public record Cosmetic(String id, CosmeticType type, String displayName, Particle particle) {}

    public interface CosmeticsApi {
        void register(Cosmetic cosmetic);
        boolean grant(UUID player, String cosmeticId);
        boolean owns(UUID player, String cosmeticId);
        Optional<Cosmetic> equipped(UUID player, CosmeticType type);
        Collection<Cosmetic> cosmetics();
    }

    public static final class CosmeticService implements CosmeticsApi {
        private final MiraCosmeticsPlugin plugin;
        private final File file;
        private final Map<String, Cosmetic> registry = new LinkedHashMap<>();
        private final Map<UUID, Set<String>> owned = new HashMap<>();
        private final Map<UUID, EnumMap<CosmeticType, String>> equipped = new HashMap<>();

        CosmeticService(MiraCosmeticsPlugin plugin) { this.plugin = plugin; this.file = new File(plugin.getDataFolder(), "cosmetics.yml"); load(); }
        @Override public void register(Cosmetic c) { registry.put(c.id().toLowerCase(Locale.ROOT), c); }
        @Override public boolean grant(UUID player, String id) { if (!registry.containsKey(id.toLowerCase(Locale.ROOT))) return false; boolean changed = owned.computeIfAbsent(player, k -> new HashSet<>()).add(id.toLowerCase(Locale.ROOT)); if (changed) save(); return changed; }
        @Override public boolean owns(UUID player, String id) { return owned.getOrDefault(player, Set.of()).contains(id.toLowerCase(Locale.ROOT)); }
        void equip(UUID player, CosmeticType type, String id) { EnumMap<CosmeticType, String> map = equipped.computeIfAbsent(player, k -> new EnumMap<>(CosmeticType.class)); if (id == null) map.remove(type); else map.put(type, id.toLowerCase(Locale.ROOT)); save(); }
        @Override public Optional<Cosmetic> equipped(UUID player, CosmeticType type) { String id = equipped.getOrDefault(player, new EnumMap<>(CosmeticType.class)).get(type); return Optional.ofNullable(id == null ? null : registry.get(id)); }
        @Override public Collection<Cosmetic> cosmetics() { return List.copyOf(registry.values()); }

        void load() {
            plugin.getDataFolder().mkdirs(); YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection players = y.getConfigurationSection("players"); if (players == null) return;
            for (String uuidText : players.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(uuidText);
                    owned.put(id, new HashSet<>(players.getStringList(uuidText + ".owned")));
                    EnumMap<CosmeticType,String> map = new EnumMap<>(CosmeticType.class);
                    for (CosmeticType type : CosmeticType.values()) { String value = players.getString(uuidText + ".equipped." + type.name().toLowerCase(Locale.ROOT)); if (value != null) map.put(type, value); }
                    equipped.put(id, map);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        synchronized void save() {
            YamlConfiguration y = new YamlConfiguration();
            for (UUID id : owned.keySet()) {
                y.set("players." + id + ".owned", new ArrayList<>(owned.get(id)));
                EnumMap<CosmeticType,String> map = equipped.get(id); if (map != null) for (var e : map.entrySet()) y.set("players." + id + ".equipped." + e.getKey().name().toLowerCase(Locale.ROOT), e.getValue());
            }
            try { y.save(file); } catch (IOException ex) { plugin.getLogger().severe("Could not save cosmetics.yml: " + ex.getMessage()); }
        }
    }
}
