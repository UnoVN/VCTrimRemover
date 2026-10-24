package com.server.antitrim.gui;

import com.server.antitrim.AntiArmorTrim;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class DecorHubGui implements Listener {

  private final AntiArmorTrim plugin;
  private static boolean registered = false;

  public DecorHubGui(AntiArmorTrim plugin) {
    this.plugin = plugin;
  }

  public static synchronized void ensureRegistered(AntiArmorTrim plugin) {
    if (!registered) {
      Bukkit.getPluginManager().registerEvents(new DecorHubGui(plugin), plugin);
      registered = true;
    }
  }

  public static void open(AntiArmorTrim plugin, Player player) {
    ensureRegistered(plugin);
    Component title = plugin.parse(plugin.getConfig().getString("gui.decor-title", "<#cba6f7><bold>Decoration Studio</bold>"));
    TrimMenuHolder holder = new TrimMenuHolder(TrimMenuHolder.MenuType.DECOR_HUB, null, null, 0);
    Inventory inv = Bukkit.createInventory(holder, 27, title);
    holder.setInventory(inv);

    ItemStack border = TrimMenuHolder.createFiller(plugin, Material.GRAY_STAINED_GLASS_PANE);
    for (int i = 0; i < 27; i++) inv.setItem(i, border);

    // Slot 9 - Armor Trim Studio
    List<Component> trimLore = new ArrayList<>(List.of(plugin.parse("<#a6adc8>Customize automatic armor trims"), plugin.parse("<#a6adc8>and materials for your gear.")));
    TrimMenuHolder.appendLockedLoreIfNeeded(plugin, player, trimLore, AntiArmorTrim.BYPASS_PERM);
    inv.setItem(9, TrimMenuHolder.createStyledButton(plugin, Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, "<#cba6f7><bold>Armor Trim Studio</bold>", trimLore));

    // Slot 11 - Custom Hat Studio
    List<Component> hatLore = new ArrayList<>(List.of(plugin.parse("<#a6adc8>Transform your Diamond or Netherite"), plugin.parse("<#a6adc8>helmet into a custom Nexo hat.")));
    TrimMenuHolder.appendLockedLoreIfNeeded(plugin, player, hatLore, AntiArmorTrim.HAT_PERM);
    inv.setItem(11, TrimMenuHolder.createStyledButton(plugin, Material.NETHERITE_HELMET, "<#f9e2af><bold>Custom Hat Studio</bold>", hatLore));

    // Slot 13 - 3D KillEffect Studio (ModelEngine 4)
    List<Component> keLore = new ArrayList<>(List.of(plugin.parse("<#a6adc8>Choose a 3D animated ModelEngine"), plugin.parse("<#a6adc8>effect when eliminating players.")));
    TrimMenuHolder.appendLockedLoreIfNeeded(plugin, player, keLore, AntiArmorTrim.KILLEFFECT_PERM);
    inv.setItem(13, TrimMenuHolder.createStyledButton(plugin, Material.TOTEM_OF_UNDYING, "<#eba0ac><bold>KillEffect Studio</bold>", keLore));

    // Slot 15 - KillSound Studio
    List<Component> ksLore = new ArrayList<>(List.of(plugin.parse("<#a6adc8>Select a custom Nexo sound effect"), plugin.parse("<#a6adc8>played when you eliminate a player.")));
    TrimMenuHolder.appendLockedLoreIfNeeded(plugin, player, ksLore, AntiArmorTrim.KILLSOUND_PERM);
    inv.setItem(15, TrimMenuHolder.createStyledButton(plugin, Material.MUSIC_DISC_OTHERSIDE, "<#a6e3a1><bold>KillSound Studio</bold>", ksLore));

    // Slot 17 - Item Rename Studio
    List<Component> renameLore = new ArrayList<>(List.of(plugin.parse("<#a6adc8>Rename the item in your main hand"), plugin.parse("<#a6adc8>using Minecraft color & format codes.")));
    TrimMenuHolder.appendLockedLoreIfNeeded(plugin, player, renameLore, AntiArmorTrim.RENAME_PERM);
    inv.setItem(17, TrimMenuHolder.createStyledButton(plugin, Material.NAME_TAG, "<#74c7ec><bold>Item Rename Studio</bold>", renameLore));

    player.openInventory(inv);
  }

  @EventHandler
  public void onClick(InventoryClickEvent event) {
    if (!(event.getInventory().getHolder() instanceof TrimMenuHolder holder)) return;
    if (holder.getMenuType() != TrimMenuHolder.MenuType.DECOR_HUB) return;
    event.setCancelled(true);
    if (!(event.getWhoClicked() instanceof Player player)) return;

    int slot = event.getRawSlot();
    if (slot == 9) {
      player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
      TrimStudioGui.openMain(plugin, player);
    } else if (slot == 11) {
      player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
      HatStudioGui.openPlayerMenu(plugin, player, 0);
    } else if (slot == 13) {
      player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
      KillEffectStudioGui.openPlayerMenu(plugin, player, 0);
    } else if (slot == 15) {
      player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
      KillSoundStudioGui.openPlayerMenu(plugin, player, 0);
    } else if (slot == 17) {
      player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
      RenameStudioGui.open(plugin, player);
    }
  }
}
