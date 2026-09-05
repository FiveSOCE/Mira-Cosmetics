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

public final class CosmeticsMenu implements Listener {
    private final MiraCosmeticsPlugin plugin;

    public CosmeticsMenu(MiraCosmeticsPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(new Holder(), 27,
                Component.text("Mira Cosmetics", NamedTextColor.DARK_PURPLE));

        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY, List.of());
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);

        boolean visuals = plugin.visualsEnabled(player);
        boolean audio = plugin.audioEnabled(player);

        inventory.setItem(11, item(visuals ? Material.LIME_DYE : Material.RED_DYE,
                "Visual Effects: " + (visuals ? "ON" : "OFF"),
                visuals ? NamedTextColor.GREEN : NamedTextColor.RED,
                List.of("Click to toggle all cosmetic particles.", "Individual events remain configurable server-side.")));

        inventory.setItem(15, item(audio ? Material.NOTE_BLOCK : Material.BARRIER,
                "Audio Effects: " + (audio ? "ON" : "OFF"),
                audio ? NamedTextColor.AQUA : NamedTextColor.RED,
                List.of("Click to toggle all cosmetic audio.", "Sound selections are not active yet.")));

        inventory.setItem(22, item(Material.BARRIER, "Close", NamedTextColor.RED, List.of()));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot == 11) {
            plugin.setVisualsEnabled(player, !plugin.visualsEnabled(player));
            open(player);
        } else if (slot == 15) {
            plugin.setAudioEnabled(player, !plugin.audioEnabled(player));
            open(player);
        } else if (slot == 22) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private ItemStack item(Material material, String name, NamedTextColor color, List<String> loreLines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(loreLines.stream()
                .map(line -> Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                .toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
