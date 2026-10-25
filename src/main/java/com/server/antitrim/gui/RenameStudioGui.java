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
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class RenameStudioGui implements Listener {

  private final AntiArmorTrim plugin;
  private static boolean registered = false;

  public RenameStudioGui(AntiArmorTrim plugin) {
    this.plugin = plugin;
  }

  public static synchronized void ensureRegistered(AntiArmorTrim plugin) {
    if (!registered) {
      Bukkit.getPluginManager().registerEvents(new RenameStudioGui(plugin), plugin);
      registered = true;
    }
  }

  public static void open(AntiArmorTrim plugin, Player player) {
    ensureRegistered(plugin);
    Component title = plugin.parse(plugin.getConfig().getString("gui.rename-title", "<white><sprite:items:item/name_tag></white> <#cba6f7><bold>ɪᴛᴇᴍ ʀᴇɴᴀᴍᴇ ѕᴛᴜᴅɪᴏ</bold>"));
    TrimMenuHolder holder = new TrimMenuHolder(TrimMenuHolder.MenuType.RENAME_STUDIO, null, null, 0);
    Inventory inv = Bukkit.createInventory(holder, 27, title);
    holder.setInventory(inv);

    ItemStack border = TrimMenuHolder.createFiller(plugin, Material.GRAY_STAINED_GLASS_PANE);
    for (int i = 0; i < 27; i++) inv.setItem(i, border);

    ItemStack guideItem = plugin.getHatManager().createCleanIconStack(Material.KNOWLEDGE_BOOK);
    ItemMeta guideMeta = guideItem.getItemMeta();
    if (guideMeta != null) {
      guideMeta.displayName(plugin.parseLiteral("<white><sprite:items:item/knowledge_book></white> <#f9e2af><bold>ᴍɪɴᴇᴄʀᴀꜰᴛ ꜰᴏʀᴍᴀᴛ ɢᴜɪᴅᴇ</bold>"));
      guideMeta.lore(List.of(
          plugin.parseLiteral("<#cba6f7>ᴄᴏʟᴏʀ ᴄᴏᴅᴇѕ:"),
          plugin.parseLiteral("<white>&0 <dark_gray>▸ <black>Black     <white>&1 <dark_gray>▸ <dark_blue>Dark Blue"),
          plugin.parseLiteral("<white>&2 <dark_gray>▸ <dark_green>Green     <white>&3 <dark_gray>▸ <dark_aqua>Dark Aqua"),
          plugin.parseLiteral("<white>&4 <dark_gray>▸ <dark_red>Dark Red  <white>&5 <dark_gray>▸ <dark_purple>Dark Purple"),
          plugin.parseLiteral("<white>&6 <dark_gray>▸ <gold>Gold      <white>&7 <dark_gray>▸ <gray>Gray"),
          plugin.parseLiteral("<white>&8 <dark_gray>▸ <dark_gray>Dark Gray <white>&9 <dark_gray>▸ <blue>Blue"),
          plugin.parseLiteral("<white>&a <dark_gray>▸ <green>Green     <white>&b <dark_gray>▸ <aqua>Aqua"),
          plugin.parseLiteral("<white>&c <dark_gray>▸ <red>Red       <white>&d <dark_gray>▸ <light_purple>Light Purple"),
          plugin.parseLiteral("<white>&e <dark_gray>▸ <yellow>Yellow    <white>&f <dark_gray>▸ <white>White"),
          Component.empty(),
          plugin.parseLiteral("<#cba6f7>ꜰᴏʀᴍᴀᴛ ᴄᴏᴅᴇѕ:"),
          plugin.parseLiteral("<white>&k <dark_gray>▸ <white><obfuscated>abc</obfuscated> <gray>(Magic)   <white>&l <dark_gray>▸ <white><bold>Bold</bold>"),
          plugin.parseLiteral("<white>&m <dark_gray>▸ <white><strikethrough>Strike</strikethrough>        <white>&n <dark_gray>▸ <white><underlined>Underline</underlined>"),
          plugin.parseLiteral("<white>&o <dark_gray>▸ <white><italic>Italic</italic>        <white>&r <dark_gray>▸ <gray>Reset"),
          Component.empty(),
          plugin.parseLiteral("<#89dceb>ʜᴇх ѕᴜᴘᴘᴏʀᴛ <#6c7086>▸ <#cdd6f4>&#rrggbb <#a6adc8>(e.g. &#ff5555)")));
      guideItem.setItemMeta(guideMeta);
    }
    plugin.getHatManager().applyHiddenComponentTooltips(guideItem);
    inv.setItem(11, guideItem);

    ItemStack handItem = player.getInventory().getItemInMainHand();
    if (plugin.getHatManager().isCustomHatItem(handItem)) {
      handItem = plugin.getHatManager().restoreOriginalHelmet(handItem);
      player.getInventory().setItemInMainHand(handItem);
    }

    if (handItem == null || handItem.getType().isAir()) {
      inv.setItem(13, TrimMenuHolder.createStyledButton(plugin, Material.BARRIER, "<#f38ba8><bold>ɴᴏ ɪᴛᴇᴍ</bold> <#6c7086>▸ <#cdd6f4>Main Hand Empty", List.of(
          plugin.parse("<#a6adc8>ʜᴏʟᴅ ᴀɴ ɪᴛᴇᴍ ɪɴ ʏᴏᴜʀ <#f9e2af>Main Hand"),
          plugin.parse("<#a6adc8>ᴛᴏ preview & rename it."))));
    } else {
      ItemStack preview = handItem.clone();
      ItemMeta previewMeta = preview.getItemMeta();
      if (previewMeta != null) {
        plugin.getHatManager().hideAllVanillaTooltips(previewMeta);
        preview.setItemMeta(previewMeta);
      }
      plugin.getHatManager().applyHiddenComponentTooltips(preview);
      inv.setItem(13, preview);
    }

    List<Component> renameLore = new ArrayList<>(List.of(
        plugin.parse("<white><sprite:items:item/name_tag></white> <#89dceb>ʟᴇꜰᴛ-ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Enter new name in chat"),
        plugin.parse("<white><sprite:items:item/grindstone></white> <#fab387>ʀɪɢʜᴛ-ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Reset to default name")));
    TrimMenuHolder.appendLockedLoreIfNeeded(plugin, player, renameLore, AntiArmorTrim.RENAME_PERM);
    inv.setItem(15, TrimMenuHolder.createStyledButton(plugin, Material.NAME_TAG, "<#a6e3a1><bold>ʀᴇɴᴀᴍᴇ</bold> <#6c7086>▪ <#cdd6f4>Held Item", renameLore));
    inv.setItem(18, TrimMenuHolder.createBackButton(plugin, "<#f9e2af><bold>ʙᴀᴄᴋ</bold> <#6c7086>▸ <#cdd6f4>Hub"));
    player.openInventory(inv);
  }

  @EventHandler
  public void onClick(InventoryClickEvent event) {
    if (!(event.getInventory().getHolder() instanceof TrimMenuHolder holder)) return;
    if (holder.getMenuType() != TrimMenuHolder.MenuType.RENAME_STUDIO) return;
    event.setCancelled(true);
    if (!(event.getWhoClicked() instanceof Player player)) return;

    int slot = event.getRawSlot();
    if (slot == 18) {
      player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
      DecorHubGui.open(plugin, player);
      return;
    }

    if (slot == 15) {
      if (!plugin.getRenameManager().canUseRename(player)) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        player.sendMessage(plugin.getMessageWithPerm("no-permission-gui", AntiArmorTrim.RENAME_PERM));
        return;
      }

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

      if (event.isRightClick()) {
        plugin.getRenameManager().resetItemRename(player);
        open(plugin, player);
        return;
      }

      player.closeInventory();
      plugin.getRenameManager().queueRenamePrompt(player);
      player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.3f);
      player.sendMessage(plugin.getMessage("rename-prompt"));
    }
  }
}
