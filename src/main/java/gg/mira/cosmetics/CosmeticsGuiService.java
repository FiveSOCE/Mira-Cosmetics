package gg.mira.cosmetics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class CosmeticsGuiService implements Listener {
    private final MiraCosmeticsPlugin plugin;
    private final MiraCosmeticsPlugin.CosmeticService service;

    public CosmeticsGuiService(MiraCosmeticsPlugin plugin, MiraCosmeticsPlugin.CosmeticService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(new Holder(), 27,
                Component.text("Mira Cosmetics", NamedTextColor.DARK_PURPLE));

        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY, List.of());
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);

        boolean visual = service.visualEnabled(player.getUniqueId());
        boolean audio = service.audioEnabled(player.getUniqueId());

        inventory.setItem(11, item(visual ? Material.LIME_DYE : Material.GRAY_DYE,
                "Visual Effects: " + (visual ? "ON" : "OFF"),
                visual ? NamedTextColor.GREEN : NamedTextColor.RED,
                List.of("Click to toggle all Mira cosmetic particles.")));

        inventory.setItem(15, item(audio ? Material.NOTE_BLOCK : Material.BARRIER,
                "Audio Effects: " + (audio ? "ON" : "OFF"),
                audio ? NamedTextColor.GREEN : NamedTextColor.RED,
                List.of("Click to toggle all Mira cosmetic audio.",
                        "Actual sounds are not selected yet.")));

        inventory.setItem(13, item(Material.ENDER_EYE, "Teleport Effects", NamedTextColor.AQUA,
                List.of("Warmup: blue/white spiral",
                        "Complete: blue-white-blue rings")));

        inventory.setItem(22, item(Material.FEATHER, "Flight Effects", NamedTextColor.WHITE,
                List.of("White particle trail beneath your feet.")));

        player.openInventory(inventory);
    }

    private ItemStack item(Material material, String name, NamedTextColor color, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        if (!loreLines.isEmpty()) {
            meta.lore(loreLines.stream().map(line -> Component.text(line, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)).toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() == 11) {
            service.setVisualEnabled(player.getUniqueId(), !service.visualEnabled(player.getUniqueId()));
            open(player);
        } else if (event.getRawSlot() == 15) {
            service.setAudioEnabled(player.getUniqueId(), !service.audioEnabled(player.getUniqueId()));
            open(player);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    public static final class Holder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
