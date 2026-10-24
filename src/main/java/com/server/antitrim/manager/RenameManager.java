package com.server.antitrim.manager;

import com.server.antitrim.AntiArmorTrim;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RenameManager implements Listener {

  private final AntiArmorTrim plugin;
  private final NamespacedKey renamedItemKey;
  private final Set<UUID> pendingRenamePlayers = ConcurrentHashMap.newKeySet();

  public RenameManager(AntiArmorTrim plugin) {
    this.plugin = plugin;
    this.renamedItemKey = new NamespacedKey(plugin, "custom_renamed");
  }

  public NamespacedKey getRenamedItemKey() {
    return renamedItemKey;
  }

  public boolean canUseRename(Player p) {
    return p.isOp() || p.hasPermission(AntiArmorTrim.RENAME_PERM) || plugin.getHatManager().canUseBypass(p);
  }

  public boolean isPluginRenamedItem(@Nullable ItemStack item) {
    if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
    ItemMeta meta = item.getItemMeta();
    return meta != null && meta.getPersistentDataContainer().has(renamedItemKey, PersistentDataType.BOOLEAN);
  }

  public void queueRenamePrompt(Player player) {
    pendingRenamePlayers.add(player.getUniqueId());
  }

  public void cancelRenamePrompt(Player player) {
    pendingRenamePlayers.remove(player.getUniqueId());
  }

  public void applyItemRename(Player player, String rawInput) {
    if (!canUseRename(player)) {
      player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
      player.sendMessage(plugin.getMessageWithPerm("no-permission-gui", AntiArmorTrim.RENAME_PERM));
      return;
    }
    ItemStack item = player.getInventory().getItemInMainHand();
    if (plugin.getHatManager().isCustomHatItem(item)) {
      item = plugin.getHatManager().restoreOriginalHelmet(item);
      player.getInventory().setItemInMainHand(item);
    }
    if (item == null || item.getType().isAir()) {
      player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
      player.sendMessage(plugin.getMessage("rename-no-item"));
      return;
    }

    Component parsedName = plugin.parseItemName(rawInput);
    String plainVisible = PlainTextComponentSerializer.plainText().serialize(parsedName);
    int maxLength = plugin.getConfig().getInt("rename.max-length", 48);
    if (plainVisible.length() > maxLength) {
      player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
      player.sendMessage(plugin.getMessage("rename-too-long", Placeholder.unparsed("max", String.valueOf(maxLength))));
      return;
    }

    ItemMeta meta = item.getItemMeta();
    if (meta == null) return;
    meta.displayName(parsedName);
    meta.getPersistentDataContainer().set(renamedItemKey, PersistentDataType.BOOLEAN, true);
    item.setItemMeta(meta);
    player.getInventory().setItemInMainHand(item);

    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.3f);
    player.sendMessage(plugin.getMessage("rename-success", Placeholder.component("name", parsedName)));
  }

  public void resetItemRename(Player player) {
    ItemStack handItem = player.getInventory().getItemInMainHand();
    if (plugin.getHatManager().isCustomHatItem(handItem)) {
      handItem = plugin.getHatManager().restoreOriginalHelmet(handItem);
      player.getInventory().setItemInMainHand(handItem);
    }
    if (handItem == null || handItem.getType().isAir()) {
      player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
      player.sendMessage(plugin.getMessage("rename-no-item"));
      return;
    }
    ItemMeta meta = handItem.getItemMeta();
    if (meta != null) {
      meta.displayName(null);
      meta.getPersistentDataContainer().remove(renamedItemKey);
      handItem.setItemMeta(meta);
      player.getInventory().setItemInMainHand(handItem);
    }
    player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1.2f);
    player.sendMessage(plugin.getMessage("rename-reset"));
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onChatRename(AsyncChatEvent event) {
    Player player = event.getPlayer();
    if (pendingRenamePlayers.remove(player.getUniqueId())) {
      event.setCancelled(true);

      String rawInput = null;
      try {
        if (event.signedMessage() != null && event.signedMessage().message() != null && !event.signedMessage().message().isBlank()) {
          rawInput = event.signedMessage().message().trim();
        }
      } catch (Throwable ignored) {}

      if (rawInput == null || rawInput.isBlank()) {
        rawInput = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
      }

      final String finalInput = rawInput;
      Bukkit.getScheduler().runTask(plugin, () -> {
        if (!player.isOnline()) return;
        if (finalInput.equalsIgnoreCase("cancel")) {
          player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.8f);
          player.sendMessage(plugin.getMessage("rename-cancelled"));
          com.server.antitrim.gui.RenameStudioGui.open(plugin, player);
          return;
        }
        applyItemRename(player, finalInput);
        com.server.antitrim.gui.RenameStudioGui.open(plugin, player);
      });
    }
  }
}
