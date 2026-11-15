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
    Component title = plugin.parse(plugin.getConfig().getString("gui.decor-title", "<white><sprite:items:item/amethyst_shard></white> <#cba6f7><bold>Decoration Studio</bold> <#6c7086>▪ <#a6adc8>Hub"));
    TrimMenuHolder holder = new TrimMenuHolder(TrimMenuHolder.MenuType.DECOR_HUB, null, null, 0);
    Inventory inv = Bukkit.createInventory(holder, 27, title);
    holder.setInventory(inv);

    ItemStack border = TrimMenuHolder.createFiller(plugin, Material.GRAY_STAINED_GLASS_PANE);
    for (int i = 0; i < 27; i++) inv.setItem(i, border);

    // Slot 11 - Armor Trim Studio
    List<Component> trimLore = new ArrayList<>(List.of(
        plugin.parse("<#a6adc8>ᴄᴜѕᴛᴏᴍɪᴢᴇ ᴀᴜᴛᴏᴍᴀᴛɪᴄ <#cba6f7>Armor Trims"),
        plugin.parse("<#a6adc8>ᴀɴᴅ <#fab387>Materials <#a6adc8>ꜰᴏʀ ʏᴏᴜʀ ɢᴇᴀʀ."),
        Component.empty(),
        plugin.parse("<#89dceb>▸ ᴄʟɪᴄᴋ <#6c7086>▪ <#cdd6f4>ᴏᴘᴇɴ ᴛʀɪᴍ ѕᴛᴜᴅɪᴏ")));
    TrimMenuHolder.appendLockedLoreIfNeeded(plugin, player, trimLore, AntiArmorTrim.BYPASS_PERM);
    inv.setItem(11, TrimMenuHolder.createStyledButton(plugin, Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, "<#cba6f7><bold>Armor Trim Studio</bold>", trimLore));

    // Slot 12 - Custom Hat Studio
    List<Component> hatLore = new ArrayList<>(List.of(
        plugin.parse("<#a6adc8>ᴛʀᴀɴѕꜰᴏʀᴍ ʏᴏᴜʀ <#89dceb>Diamond <#a6adc8>ᴏʀ <#cba6f7>Netherite"),
        plugin.parse("<#a6adc8>Helmet into a <#f9e2af>3D Nexo Hat<#a6adc8>."),
        Component.empty(),
        plugin.parse("<#89dceb>▸ ᴄʟɪᴄᴋ <#6c7086>▪ <#cdd6f4>ᴏᴘᴇɴ ʜᴀᴛ ѕᴛᴜᴅɪᴏ")));
    TrimMenuHolder.appendLockedLoreIfNeeded(plugin, player, hatLore, AntiArmorTrim.HAT_PERM);
    inv.setItem(12, TrimMenuHolder.createStyledButton(plugin, Material.NETHERITE_HELMET, "<#f9e2af><bold>Custom Hat Studio</bold>", hatLore));

    // Slot 13 - KillEffect Studio (Combined Classic & Modern 3D Selector)
    List<Component> keLore = new ArrayList<>(List.of(
        plugin.parse("<#a6adc8>ѕᴇʟᴇᴄᴛ <#fab387>Classic Particle Effects"),
        plugin.parse("<#a6adc8>or animated <#eba0ac>3D Model Effects<#a6adc8>."),
        Component.empty(),
        plugin.parse("<#89dceb>▸ ᴄʟɪᴄᴋ <#6c7086>▪ <#cdd6f4>ᴏᴘᴇɴ ᴋɪʟʟᴇꜰꜰᴇᴄᴛ ѕᴛᴜᴅɪᴏ")));
    inv.setItem(13, TrimMenuHolder.createStyledButton(plugin, Material.TOTEM_OF_UNDYING, "<#eba0ac><bold>KillEffect Studio</bold>", keLore));

    // Slot 14 - KillSound Studio
    List<Component> ksLore = new ArrayList<>(List.of(
        plugin.parse("<#a6adc8>ѕᴇʟᴇᴄᴛ ᴀ ᴄᴜѕᴛᴏᴍ <#a6e3a1>Nexo Sound Effect"),
        plugin.parse("<#a6adc8>ᴘʟᴀʏᴇᴅ ᴡʜᴇɴ ʏᴏᴜ <#cdd6f4>Eliminate <#a6adc8>ᴀ ᴘʟᴀʏᴇʀ."),
        Component.empty(),
        plugin.parse("<#89dceb>▸ ᴄʟɪᴄᴋ <#6c7086>▪ <#cdd6f4>ᴏᴘᴇɴ ᴋɪʟʟѕᴏᴜɴᴅ ѕᴛᴜᴅɪᴏ")));
    TrimMenuHolder.appendLockedLoreIfNeeded(plugin, player, ksLore, AntiArmorTrim.KILLSOUND_PERM);
    inv.setItem(14, TrimMenuHolder.createStyledButton(plugin, Material.MUSIC_DISC_OTHERSIDE, "<#a6e3a1><bold>KillSound Studio</bold>", ksLore));

    // Slot 15 - Item Rename Studio
    List<Component> renameLore = new ArrayList<>(List.of(
        plugin.parse("<#a6adc8>ʀᴇɴᴀᴍᴇ ᴛʜᴇ ɪᴛᴇᴍ ɪɴ ʏᴏᴜʀ <#74c7ec>Main Hand"),
        plugin.parse("<#a6adc8>Using <#cdd6f4>Color & Hex Codes <#6c7086>(&0-&f, &#hex)<#a6adc8>."),
        Component.empty(),
        plugin.parse("<#89dceb>▸ ᴄʟɪᴄᴋ <#6c7086>▪ <#cdd6f4>ᴏᴘᴇɴ ʀᴇɴᴀᴍᴇ ѕᴛᴜᴅɪᴏ")));
    TrimMenuHolder.appendLockedLoreIfNeeded(plugin, player, renameLore, AntiArmorTrim.RENAME_PERM);
    inv.setItem(15, TrimMenuHolder.createStyledButton(plugin, Material.NAME_TAG, "<#74c7ec><bold>Item Rename Studio</bold>", renameLore));

    player.openInventory(inv);
  }

  @EventHandler
  public void onClick(InventoryClickEvent event) {
    if (!(event.getInventory().getHolder() instanceof TrimMenuHolder holder)) return;
    if (holder.getMenuType() != TrimMenuHolder.MenuType.DECOR_HUB) return;
    event.setCancelled(true);
    if (!(event.getWhoClicked() instanceof Player player)) return;

    int slot = event.getRawSlot();
    if (slot == 11) {
      player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
      TrimStudioGui.openMain(plugin, player);
    } else if (slot == 12) {
      player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
      HatStudioGui.openPlayerMenu(plugin, player, 0);
    } else if (slot == 13) {
      player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
      KillEffectHubGui.open(plugin, player);
    } else if (slot == 14) {
      player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
      KillSoundStudioGui.openPlayerMenu(plugin, player, 0);
    } else if (slot == 15) {
      player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
      RenameStudioGui.open(plugin, player);
    }
  }
}
