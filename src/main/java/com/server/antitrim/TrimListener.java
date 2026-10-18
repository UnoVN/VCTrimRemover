package com.server.antitrim;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

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
        guiManager.applyAllSavedTrims(player);

        if (!guiManager.canUseHat(player)) {
            ItemStack helmet = player.getInventory().getHelmet();
            if (guiManager.isCustomHatItem(helmet)) {
                player.getInventory().setHelmet(guiManager.restoreOriginalHelmet(helmet));
            }
        }

        if (player.hasPermission(AntiArmorTrim.BYPASS_PERM)) {
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
            guiManager.applySavedTrimToSlot(player, slot);
            scanAndClean(player);
        });
    }

    // Restore the original Diamond/Netherite helmet in death drops so custom hats never drop on death
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        List<ItemStack> drops = event.getDrops();
        for (int i = 0; i < drops.size(); i++) {
            ItemStack drop = drops.get(i);
            if (guiManager.isCustomHatItem(drop)) {
                drops.set(i, guiManager.restoreOriginalHelmet(drop));
            }
        }

        if (event.getKeepInventory()) {
            Player player = event.getPlayer();
            ItemStack helmet = player.getInventory().getHelmet();
            if (guiManager.isCustomHatItem(helmet)) {
                player.getInventory().setHelmet(guiManager.restoreOriginalHelmet(helmet));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (guiManager.isCustomHatItem(dropped)) {
            event.getItemDrop().setItemStack(guiManager.restoreOriginalHelmet(dropped));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (guiManager.isCustomHatItem(event.getOffHandItem())) {
            event.setOffHandItem(guiManager.restoreOriginalHelmet(event.getOffHandItem()));
        }
        if (guiManager.isCustomHatItem(event.getMainHandItem())) {
            event.setMainHandItem(guiManager.restoreOriginalHelmet(event.getMainHandItem()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        scanAndClean(event.getPlayer());
    }

    // Restore original helmet on quit so offline player data always holds the real Diamond/Netherite helmet
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuitRestore(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        ItemStack helmet = player.getInventory().getHelmet();
        if (guiManager.isCustomHatItem(helmet)) {
            player.getInventory().setHelmet(guiManager.restoreOriginalHelmet(helmet));
        }
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
            ItemStack cursor = player.getItemOnCursor();
            if (guiManager.isCustomHatItem(cursor)) {
                player.setItemOnCursor(guiManager.restoreOriginalHelmet(cursor));
            }
            scanAndClean(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof TrimGuiManager.TrimMenuHolder) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Immediately restore original helmet when clicked so players pick up/move the real helmet
        boolean restoredHat = false;
        if (guiManager.isCustomHatItem(event.getCurrentItem())) {
            event.setCurrentItem(guiManager.restoreOriginalHelmet(event.getCurrentItem()));
            restoredHat = true;
        }
        if (guiManager.isCustomHatItem(event.getCursor())) {
            event.getView().setCursor(guiManager.restoreOriginalHelmet(event.getCursor()));
            restoredHat = true;
        }
        if (restoredHat) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    scanAndClean(player);
                }
            });
        }

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
            ItemStack picked = event.getItem().getItemStack();
            if (guiManager.isCustomHatItem(picked)) {
                picked = guiManager.restoreOriginalHelmet(picked);
                event.getItem().setItemStack(picked);
            }

            if (player.hasPermission(AntiArmorTrim.BYPASS_PERM)) return;

            if (hasArmorTrim(picked)) {
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
