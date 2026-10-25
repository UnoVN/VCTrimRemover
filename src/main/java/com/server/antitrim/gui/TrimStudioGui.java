package com.server.antitrim.gui;

import com.server.antitrim.AntiArmorTrim;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;

import java.util.ArrayList;
import java.util.List;

public class TrimStudioGui implements Listener {

  private final AntiArmorTrim plugin;
  private static boolean registered = false;

  public TrimStudioGui(AntiArmorTrim plugin) {
    this.plugin = plugin;
  }

  public static synchronized void ensureRegistered(AntiArmorTrim plugin) {
    if (!registered) {
      Bukkit.getPluginManager().registerEvents(new TrimStudioGui(plugin), plugin);
      registered = true;
    }
  }

  public static void openMain(AntiArmorTrim plugin, Player player) {
    ensureRegistered(plugin);
    Component title = plugin.parse(plugin.getConfig().getString("gui.main-title", "<white><sprite:items:item/silence_armor_trim_smithing_template></white> <#cba6f7><bold>ᴀʀᴍᴏʀ ᴛʀɪᴍ ѕᴛᴜᴅɪᴏ</bold>"));
    TrimMenuHolder holder = new TrimMenuHolder(TrimMenuHolder.MenuType.TRIM_MAIN, null, null, 0);
    Inventory inv = Bukkit.createInventory(holder, 27, title);
    holder.setInventory(inv);

    ItemStack border = TrimMenuHolder.createFiller(plugin, Material.GRAY_STAINED_GLASS_PANE);
    for (int i = 0; i < 27; i++) inv.setItem(i, border);

    inv.setItem(10, createMainSlotIcon(plugin, player, EquipmentSlot.HEAD, Material.NETHERITE_HELMET));
    inv.setItem(12, createMainSlotIcon(plugin, player, EquipmentSlot.CHEST, Material.NETHERITE_CHESTPLATE));
    inv.setItem(14, createMainSlotIcon(plugin, player, EquipmentSlot.LEGS, Material.NETHERITE_LEGGINGS));
    inv.setItem(16, createMainSlotIcon(plugin, player, EquipmentSlot.FEET, Material.NETHERITE_BOOTS));
    inv.setItem(18, TrimMenuHolder.createBackButton(plugin, "<#f9e2af><bold>ʙᴀᴄᴋ</bold> <#6c7086>▸ <#cdd6f4>Hub"));
    player.openInventory(inv);
  }

  public static void openSlotMenu(AntiArmorTrim plugin, Player player, EquipmentSlot slot) {
    ensureRegistered(plugin);
    String slotName = getSlotEnglishName(slot);
    Material previewMat = getPreviewMaterial(slot);
    String rawTitle = plugin.getConfig().getString("gui.slot-title", "<slot_icon> <#cba6f7><bold>ᴛʀɪᴍ ᴇᴅɪᴛᴏʀ</bold> <#6c7086>▸ <#74c7ec><slot>");
    TrimMenuHolder holder = new TrimMenuHolder(TrimMenuHolder.MenuType.TRIM_SLOT, slot, null, 0);
    Inventory inv = Bukkit.createInventory(holder, 54, plugin.parse(rawTitle, Placeholder.unparsed("slot", slotName), Placeholder.parsed("slot_icon", plugin.getItemSpriteTag(previewMat))));
    holder.setInventory(inv);

    ItemStack filler = TrimMenuHolder.createFiller(plugin, Material.BLACK_STAINED_GLASS_PANE);
    for (int i = 18; i < 54; i++) inv.setItem(i, filler);

    String currentPattern = plugin.getHatManager().getSavedPatternKey(player, slot);
    String currentMaterial = plugin.getHatManager().getSavedMaterialKey(player, slot);

    int pIndex = 0;
    for (TrimMenuHolder.PatternOption pOpt : TrimMenuHolder.PATTERNS.values()) {
      inv.setItem(pIndex++, createPatternIcon(plugin, player, pOpt, pOpt.key().equalsIgnoreCase(currentPattern)));
    }

    inv.setItem(22, createMainSlotIcon(plugin, player, slot, previewMat));

    int[] matSlots = {29, 30, 31, 32, 33, 38, 39, 40, 41, 42};
    int mIndex = 0;
    for (TrimMenuHolder.MaterialOption mOpt : TrimMenuHolder.MATERIALS.values()) {
      if (mIndex >= matSlots.length) break;
      inv.setItem(matSlots[mIndex++], createMaterialIcon(plugin, player, mOpt, mOpt.key().equalsIgnoreCase(currentMaterial)));
    }

    inv.setItem(45, TrimMenuHolder.createBackButton(plugin, "<#f9e2af><bold>ʙᴀᴄᴋ</bold> <#6c7086>▸ <#cdd6f4>Slots"));

    List<Component> clearLore = new ArrayList<>(List.of(plugin.parse("<#a6adc8>ᴅɪѕᴀʙʟᴇ auto-trim & clear <#cdd6f4>Active Trim<#a6adc8>.")));
    TrimMenuHolder.appendLockedLoreIfNeeded(plugin, player, clearLore, AntiArmorTrim.BYPASS_PERM);
    inv.setItem(49, TrimMenuHolder.createStyledButton(plugin, Material.BARRIER, "<#f38ba8><bold>ʀᴇѕᴇᴛ</bold> <#6c7086>▸ <#cdd6f4>Clear Trim", clearLore));

    List<Component> allLore = new ArrayList<>(List.of(
        plugin.parse("<#a6adc8>ѕʏɴᴄ ᴛʜɪѕ ѕʟᴏᴛ'ѕ <#cdd6f4>Pattern & Material"),
        plugin.parse("<#a6adc8>ᴛᴏ <#cba6f7>All 4 Armor Pieces<#a6adc8>.")));
    TrimMenuHolder.appendLockedLoreIfNeeded(plugin, player, allLore, AntiArmorTrim.BYPASS_PERM);
    inv.setItem(53, TrimMenuHolder.createStyledButton(plugin, Material.NETHER_STAR, "<#cba6f7><bold>ѕʏɴᴄ</bold> <#6c7086>▸ <#cdd6f4>All 4 Pieces", allLore));

    player.openInventory(inv);
  }

  private static ItemStack createMainSlotIcon(AntiArmorTrim plugin, Player player, EquipmentSlot slot, Material iconMat) {
    ItemStack item = new ItemStack(iconMat);
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return item;

    plugin.getHatManager().hideAllVanillaTooltips(meta);
    meta.displayName(plugin.parse(plugin.getItemSpriteTag(iconMat) + " <#74c7ec><bold>" + getSlotEnglishName(slot) + " <#a6adc8>ᴛʀɪᴍ</bold>"));

    String patternKey = plugin.getHatManager().getSavedPatternKey(player, slot);
    String materialKey = plugin.getHatManager().getSavedMaterialKey(player, slot);
    TrimMenuHolder.PatternOption pOpt = patternKey != null ? TrimMenuHolder.PATTERNS.get(patternKey) : null;
    TrimMenuHolder.MaterialOption mOpt = materialKey != null ? TrimMenuHolder.MATERIALS.get(materialKey) : null;

    List<Component> lore = new ArrayList<>();
    String patternDisplay = pOpt != null ? plugin.getItemSpriteTag(pOpt.icon()) + " <#89dceb>" + pOpt.name() : "<#6c7086>None";
    String materialDisplay = mOpt != null ? plugin.getItemSpriteTag(mOpt.icon()) + " <" + mOpt.hexColor() + ">" + mOpt.name() : "<#6c7086>None";

    lore.add(plugin.parse("<#a6adc8>ᴘᴀᴛᴛᴇʀɴ <#6c7086>▸ " + patternDisplay));
    lore.add(plugin.parse("<#a6adc8>ᴍᴀᴛᴇʀɪᴀʟ <#6c7086>▸ " + materialDisplay));
    if (slot == EquipmentSlot.HEAD && plugin.getHatManager().getEffectiveHatId(player) != null) {
      lore.add(plugin.parse("<white><sprite:items:item/bell></white> <#fab387>Custom Hat Active <#6c7086>(Trim Paused)"));
    }

    if (meta instanceof ArmorMeta armorMeta && pOpt != null && mOpt != null) {
      ArmorTrim previewTrim = plugin.getHatManager().buildArmorTrim(patternKey, materialKey);
      if (previewTrim != null) armorMeta.setTrim(previewTrim);
    }

    TrimMenuHolder.appendSelectionOrLockedLore(plugin, player, lore, AntiArmorTrim.BYPASS_PERM, pOpt != null && mOpt != null);
    meta.lore(lore);
    item.setItemMeta(meta);
    plugin.getHatManager().applyHiddenComponentTooltips(item);
    return item;
  }

  private static ItemStack createPatternIcon(AntiArmorTrim plugin, Player player, TrimMenuHolder.PatternOption opt, boolean selected) {
    boolean unlocked = plugin.getHatManager().canUseBypass(player);
    Material mat = unlocked ? opt.icon() : Material.GRAY_DYE;
    ItemStack item = plugin.getHatManager().createCleanIconStack(mat);
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return item;

    if (unlocked) {
      meta.displayName(plugin.parse(plugin.getItemSpriteTag(opt.icon()) + " " + (selected ? "<#a6e3a1><bold>" : "<#89dceb>") + opt.name() + " <#a6adc8>ᴛʀɪᴍ"));
      if (selected) meta.setEnchantmentGlintOverride(true);
    } else {
      meta.displayName(plugin.parse(plugin.getItemSpriteTag(Material.GRAY_DYE) + " <#89dceb>" + opt.name() + " <#a6adc8>ᴛʀɪᴍ <#6c7086>• <#f38ba8><bold>ʟᴏᴄᴋᴇᴅ</bold>"));
    }

    List<Component> lore = new ArrayList<>();
    TrimMenuHolder.appendSelectionOrLockedLore(plugin, player, lore, AntiArmorTrim.BYPASS_PERM, selected);
    meta.lore(lore);
    item.setItemMeta(meta);
    plugin.getHatManager().applyHiddenComponentTooltips(item);
    return item;
  }

  private static ItemStack createMaterialIcon(AntiArmorTrim plugin, Player player, TrimMenuHolder.MaterialOption opt, boolean selected) {
    boolean unlocked = plugin.getHatManager().canUseBypass(player);
    Material mat = unlocked ? opt.icon() : Material.GRAY_DYE;
    ItemStack item = plugin.getHatManager().createCleanIconStack(mat);
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return item;

    if (unlocked) {
      meta.displayName(plugin.parse(plugin.getItemSpriteTag(opt.icon()) + " " + (selected ? "<#a6e3a1><bold>" : "<" + opt.hexColor() + ">") + opt.name() + " <#a6adc8>ᴍᴀᴛᴇʀɪᴀʟ"));
      if (selected) meta.setEnchantmentGlintOverride(true);
    } else {
      meta.displayName(plugin.parse(plugin.getItemSpriteTag(Material.GRAY_DYE) + " <" + opt.hexColor() + ">" + opt.name() + " <#a6adc8>ᴍᴀᴛᴇʀɪᴀʟ <#6c7086>• <#f38ba8><bold>ʟᴏᴄᴋᴇᴅ</bold>"));
    }

    List<Component> lore = new ArrayList<>();
    TrimMenuHolder.appendSelectionOrLockedLore(plugin, player, lore, AntiArmorTrim.BYPASS_PERM, selected);
    meta.lore(lore);
    item.setItemMeta(meta);
    plugin.getHatManager().applyHiddenComponentTooltips(item);
    return item;
  }

  public static String getSlotEnglishName(EquipmentSlot slot) {
    return switch (slot) {
      case HEAD -> "Helmet";
      case CHEST -> "Chestplate";
      case LEGS -> "Leggings";
      case FEET -> "Boots";
      default -> "Armor";
    };
  }

  public static Material getPreviewMaterial(EquipmentSlot slot) {
    return switch (slot) {
      case HEAD -> Material.NETHERITE_HELMET;
      case CHEST -> Material.NETHERITE_CHESTPLATE;
      case LEGS -> Material.NETHERITE_LEGGINGS;
      case FEET -> Material.NETHERITE_BOOTS;
      default -> Material.NETHERITE_CHESTPLATE;
    };
  }

  @EventHandler
  public void onClick(InventoryClickEvent event) {
    if (!(event.getInventory().getHolder() instanceof TrimMenuHolder holder)) return;
    if (holder.getMenuType() != TrimMenuHolder.MenuType.TRIM_MAIN && holder.getMenuType() != TrimMenuHolder.MenuType.TRIM_SLOT) return;
    event.setCancelled(true);
    if (!(event.getWhoClicked() instanceof Player player)) return;

    int slot = event.getRawSlot();
    if (slot < 0 || slot >= event.getInventory().getSize()) return;

    if (holder.getMenuType() == TrimMenuHolder.MenuType.TRIM_MAIN) {
      if (slot == 18) { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f); DecorHubGui.open(plugin, player); return; }
      EquipmentSlot target = switch (slot) {
        case 10 -> EquipmentSlot.HEAD;
        case 12 -> EquipmentSlot.CHEST;
        case 14 -> EquipmentSlot.LEGS;
        case 16 -> EquipmentSlot.FEET;
        default -> null;
      };
      if (target != null) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        openSlotMenu(plugin, player, target);
      }
      return;
    }

    EquipmentSlot armorSlot = holder.getSlotType();
    if (slot == 45) { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f); openMain(plugin, player); return; }

    boolean isInteractiveSlot = (slot >= 0 && slot < 18) || (slot >= 29 && slot <= 33)
        || (slot >= 38 && slot <= 42) || slot == 49 || slot == 53;
    if (!isInteractiveSlot) return;

    if (!plugin.getHatManager().canUseBypass(player)) {
      player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
      player.sendMessage(plugin.getMessageWithPerm("no-permission-gui", AntiArmorTrim.BYPASS_PERM));
      return;
    }

    String currentPattern = plugin.getHatManager().getSavedPatternKey(player, armorSlot);
    String currentMaterial = plugin.getHatManager().getSavedMaterialKey(player, armorSlot);
    String slotName = getSlotEnglishName(armorSlot);
    String slotIcon = plugin.getItemSpriteTag(getPreviewMaterial(armorSlot));

    if (slot < 18) {
      List<TrimMenuHolder.PatternOption> pList = new ArrayList<>(TrimMenuHolder.PATTERNS.values());
      if (slot < pList.size()) {
        TrimMenuHolder.PatternOption chosenPattern = pList.get(slot);
        if (currentMaterial == null) currentMaterial = "diamond";
        TrimMenuHolder.MaterialOption matOpt = TrimMenuHolder.MATERIALS.getOrDefault(currentMaterial, TrimMenuHolder.MATERIALS.get("diamond"));

        plugin.getHatManager().setSavedTrim(player, armorSlot, chosenPattern.key(), currentMaterial);
        plugin.getHatManager().applySavedTrimToSlot(player, armorSlot);

        player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1.2f);
        player.sendMessage(plugin.getMessage("trim-selected",
            Placeholder.unparsed("slot", slotName), Placeholder.parsed("slot_icon", slotIcon),
            Placeholder.unparsed("pattern", chosenPattern.name()), Placeholder.parsed("pattern_icon", plugin.getItemSpriteTag(chosenPattern.icon())),
            Placeholder.unparsed("material", matOpt.name()), Placeholder.parsed("material_icon", plugin.getItemSpriteTag(matOpt.icon()))));
        if (armorSlot == EquipmentSlot.HEAD && plugin.getHatManager().getEffectiveHatId(player) != null) player.sendMessage(plugin.getMessage("trim-hat-active"));
        openSlotMenu(plugin, player, armorSlot);
      }
      return;
    }

    int[] matSlots = {29, 30, 31, 32, 33, 38, 39, 40, 41, 42};
    for (int i = 0; i < matSlots.length; i++) {
      if (slot == matSlots[i]) {
        List<TrimMenuHolder.MaterialOption> mList = new ArrayList<>(TrimMenuHolder.MATERIALS.values());
        if (i < mList.size()) {
          TrimMenuHolder.MaterialOption chosenMaterial = mList.get(i);
          if (currentPattern == null) currentPattern = "silence";
          TrimMenuHolder.PatternOption patOpt = TrimMenuHolder.PATTERNS.getOrDefault(currentPattern, TrimMenuHolder.PATTERNS.get("silence"));

          plugin.getHatManager().setSavedTrim(player, armorSlot, currentPattern, chosenMaterial.key());
          plugin.getHatManager().applySavedTrimToSlot(player, armorSlot);

          player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1.4f);
          player.sendMessage(plugin.getMessage("trim-selected",
              Placeholder.unparsed("slot", slotName), Placeholder.parsed("slot_icon", slotIcon),
              Placeholder.unparsed("pattern", patOpt.name()), Placeholder.parsed("pattern_icon", plugin.getItemSpriteTag(patOpt.icon())),
              Placeholder.unparsed("material", chosenMaterial.name()), Placeholder.parsed("material_icon", plugin.getItemSpriteTag(chosenMaterial.icon()))));
          if (armorSlot == EquipmentSlot.HEAD && plugin.getHatManager().getEffectiveHatId(player) != null) player.sendMessage(plugin.getMessage("trim-hat-active"));
          openSlotMenu(plugin, player, armorSlot);
        }
        return;
      }
    }

    if (slot == 49) {
      plugin.getHatManager().setSavedTrim(player, armorSlot, null, null);
      ItemStack worn = player.getInventory().getItem(armorSlot);
      if (worn != null && worn.getItemMeta() instanceof ArmorMeta armorMeta && armorMeta.hasTrim()) {
        armorMeta.setTrim(null);
        worn.setItemMeta(armorMeta);
        player.getInventory().setItem(armorSlot, worn);
      }
      player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1.2f);
      player.sendMessage(plugin.getMessage("trim-cleared", Placeholder.unparsed("slot", slotName), Placeholder.parsed("slot_icon", slotIcon)));
      openSlotMenu(plugin, player, armorSlot);
      return;
    }

    if (slot == 53) {
      EquipmentSlot[] allSlots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
      if (currentPattern == null || currentMaterial == null) {
        for (EquipmentSlot s : allSlots) {
          plugin.getHatManager().setSavedTrim(player, s, null, null);
          ItemStack worn = player.getInventory().getItem(s);
          if (worn != null && worn.getItemMeta() instanceof ArmorMeta armorMeta && armorMeta.hasTrim()) {
            armorMeta.setTrim(null);
            worn.setItemMeta(armorMeta);
            player.getInventory().setItem(s, worn);
          }
        }
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1.2f);
        player.sendMessage(plugin.getMessage("trim-cleared-all"));
        openMain(plugin, player);
        return;
      }

      TrimMenuHolder.PatternOption patOpt = TrimMenuHolder.PATTERNS.get(currentPattern);
      TrimMenuHolder.MaterialOption matOpt = TrimMenuHolder.MATERIALS.get(currentMaterial);
      for (EquipmentSlot s : allSlots) plugin.getHatManager().setSavedTrim(player, s, currentPattern, currentMaterial);
      plugin.getHatManager().applyAllSavedTrims(player);

      player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
      player.sendMessage(plugin.getMessage("trim-applied-all",
          Placeholder.unparsed("pattern", patOpt != null ? patOpt.name() : currentPattern),
          Placeholder.parsed("pattern_icon", patOpt != null ? plugin.getItemSpriteTag(patOpt.icon()) : ""),
          Placeholder.unparsed("material", matOpt != null ? matOpt.name() : currentMaterial),
          Placeholder.parsed("material_icon", matOpt != null ? plugin.getItemSpriteTag(matOpt.icon()) : "")));
      openMain(plugin, player);
    }
  }
}
