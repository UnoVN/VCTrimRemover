package com.server.antitrim;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;

public class TrimListener implements Listener {

    private final AntiArmorTrim plugin;
    private final String PERMISSION = "armortrim.bypass";

    public TrimListener(AntiArmorTrim plugin) {
        this.plugin = plugin;
    }

    /**
     * Kiem tra xem mot ItemStack co chua Armor Trim hay khong
     */
    private boolean hasArmorTrim(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof ArmorMeta armorMeta) {
            return armorMeta.hasTrim();
        }
        return false;
    }

    /**
     * Quet va xoa toan bo giap Trim trong kho do cua nguoi choi
     */
    private void scanAndClean(Player player) {
        if (player.hasPermission(PERMISSION)) {
            return;
        }

        boolean removed = false;
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (hasArmorTrim(item)) {
                player.getInventory().setItem(i, null);
                removed = true;
            }
        }

        if (removed) {
            player.sendMessage(plugin.getMessage("remove-trim"));
        }
    }

    // 1. Khi nguoi choi mo hoac dong hom do
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            scanAndClean(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            scanAndClean(player);
        }
    }

    // 2. Khi click chuot vao vat pham co Trim trong bat ky giao dien GUI/kho do nao
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (player.hasPermission(PERMISSION)) return;

        boolean triggered = false;

        if (hasArmorTrim(event.getCurrentItem())) {
            event.setCurrentItem(null);
            event.setCancelled(true);
            triggered = true;
        }

        if (hasArmorTrim(event.getCursor())) {
            event.getView().setCursor(null);
            event.setCancelled(true);
            triggered = true;
        }

        if (triggered) {
            player.sendMessage(plugin.getMessage("remove-trim"));
        }
    }

    // 3. Khi cam tren tay chuot phai de mac giap
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(PERMISSION)) return;

        ItemStack item = event.getItem();
        if (hasArmorTrim(item)) {
            event.setCancelled(true);
            player.getInventory().setItemInMainHand(null);
            player.sendMessage(plugin.getMessage("remove-trim"));
        }
    }

    // 4. Khi nhat phai do roi tren dat co Trim
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (player.hasPermission(PERMISSION)) return;

            if (hasArmorTrim(event.getItem().getItemStack())) {
                event.setCancelled(true);
                event.getItem().remove(); // Xoa luon item duoi dat
                player.sendMessage(plugin.getMessage("remove-trim"));
            }
        }
    }

    // 5. Chan ep Trim tai ban ren Smithing Table
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmith(PrepareSmithingEvent event) {
        if (event.getView().getPlayer() instanceof Player player) {
            if (player.hasPermission(PERMISSION)) return;

            ItemStack result = event.getResult();
            if (hasArmorTrim(result)) {
                event.setResult(null); // Chan hien thi va chan lay do ra
                player.sendMessage(plugin.getMessage("deny-smith"));
            }
        }
    }
}
