package com.server.antitrim;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;

public class TrimListener implements Listener {

    private final AntiArmorTrim plugin;
    private final TrimGuiManager guiManager;

    public TrimListener(AntiArmorTrim plugin, TrimGuiManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

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

    private void scanAndClean(Player player) {
        if (player.hasPermission(AntiArmorTrim.BYPASS_PERM)) {
            guiManager.applyAllSavedTrims(player);
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorChange(PlayerArmorChangeEvent event) {
        Player player = event.getPlayer();
        EquipmentSlot slot = switch (event.getSlotType()) {
            case HEAD -> EquipmentSlot.HEAD;
            case CHEST -> EquipmentSlot.CHEST;
            case LEGS -> EquipmentSlot.LEGS;
            case FEET -> EquipmentSlot.FEET;
        };

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (player.hasPermission(AntiArmorTrim.BYPASS_PERM)) {
                guiManager.applySavedTrimToSlot(player, slot);
            } else {
                scanAndClean(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        scanAndClean(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder() instanceof TrimGuiManager.TrimMenuHolder) return;
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof TrimGuiManager.TrimMenuHolder) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (player.hasPermission(AntiArmorTrim.BYPASS_PERM)) return;

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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(AntiArmorTrim.BYPASS_PERM)) return;

        ItemStack item = event.getItem();
        if (hasArmorTrim(item)) {
            event.setCancelled(true);
            if (event.getHand() != null) {
                player.getInventory().setItem(event.getHand(), null);
            }
            player.sendMessage(plugin.getMessage("remove-trim"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (player.hasPermission(AntiArmorTrim.BYPASS_PERM)) return;

            if (hasArmorTrim(event.getItem().getItemStack())) {
                event.setCancelled(true);
                event.getItem().remove();
                player.sendMessage(plugin.getMessage("remove-trim"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmith(PrepareSmithingEvent event) {
        if (event.getView().getPlayer() instanceof Player player) {
            if (player.hasPermission(AntiArmorTrim.BYPASS_PERM)) return;

            ItemStack result = event.getResult();
            if (hasArmorTrim(result)) {
                event.setResult(null);
                player.sendActionBar(plugin.getRawMessage("deny-smith"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            }
        }
    }
}
