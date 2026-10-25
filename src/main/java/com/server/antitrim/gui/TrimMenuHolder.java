package com.server.antitrim.gui;

import com.server.antitrim.AntiArmorTrim;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TrimMenuHolder implements InventoryHolder {

  public enum MenuType {
    DECOR_HUB, TRIM_MAIN, TRIM_SLOT,
    HAT_STUDIO, HAT_ADMIN, HAT_EDITOR, HAT_GROUP_LIST, HAT_GROUP_SELECTOR,
    KILLEFFECT_STUDIO, KILLEFFECT_ADMIN, KILLEFFECT_EDITOR, KILLEFFECT_GROUP_LIST, KILLEFFECT_GROUP_SELECTOR,
    KILLSOUND_STUDIO, KILLSOUND_ADMIN, KILLSOUND_EDITOR, KILLSOUND_GROUP_LIST, KILLSOUND_GROUP_SELECTOR,
    RENAME_STUDIO
  }

  public record PatternOption(String key, String name, Material icon) {}
  public record MaterialOption(String key, String name, String hexColor, Material icon) {}

  public static final Map<String, PatternOption> PATTERNS = new LinkedHashMap<>();
  public static final Map<String, MaterialOption> MATERIALS = new LinkedHashMap<>();

  static {
    addPattern("sentry", "Sentry", Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE);
    addPattern("dune", "Dune", Material.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE);
    addPattern("coast", "Coast", Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE);
    addPattern("wild", "Wild", Material.WILD_ARMOR_TRIM_SMITHING_TEMPLATE);
    addPattern("ward", "Ward", Material.WARD_ARMOR_TRIM_SMITHING_TEMPLATE);
    addPattern("eye", "Eye", Material.EYE_ARMOR_TRIM_SMITHING_TEMPLATE);
    addPattern("vex", "Vex", Material.VEX_ARMOR_TRIM_SMITHING_TEMPLATE);
    addPattern("tide", "Tide", Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE);
    addPattern("snout", "Snout", Material.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE);
    addPattern("rib", "Rib", Material.RIB_ARMOR_TRIM_SMITHING_TEMPLATE);
    addPattern("spire", "Spire", Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE);
    addPattern("wayfinder", "Wayfinder", Material.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE);
    addPattern("shaper", "Shaper", Material.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE);
    addPattern("silence", "Silence", Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE);
    addPattern("raiser", "Raiser", Material.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE);
    addPattern("host", "Host", Material.HOST_ARMOR_TRIM_SMITHING_TEMPLATE);
    addPattern("flow", "Flow", Material.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE);
    addPattern("bolt", "Bolt", Material.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE);

    addMaterial("quartz", "Quartz", "#f5e0dc", Material.QUARTZ);
    addMaterial("iron", "Iron", "#bac2de", Material.IRON_INGOT);
    addMaterial("netherite", "Netherite", "#6c7086", Material.NETHERITE_INGOT);
    addMaterial("redstone", "Redstone", "#f38ba8", Material.REDSTONE);
    addMaterial("copper", "Copper", "#fab387", Material.COPPER_INGOT);
    addMaterial("gold", "Gold", "#f9e2af", Material.GOLD_INGOT);
    addMaterial("emerald", "Emerald", "#a6e3a1", Material.EMERALD);
    addMaterial("diamond", "Diamond", "#89dceb", Material.DIAMOND);
    addMaterial("lapis", "Lapis", "#89b4fa", Material.LAPIS_LAZULI);
    addMaterial("amethyst", "Amethyst", "#cba6f7", Material.AMETHYST_SHARD);
  }

  private static void addPattern(String key, String name, Material icon) { PATTERNS.put(key, new PatternOption(key, name, icon)); }
  private static void addMaterial(String key, String name, String hexColor, Material icon) { MATERIALS.put(key, new MaterialOption(key, name, hexColor, icon)); }

  private final MenuType menuType;
  private final EquipmentSlot slotType;
  private final String editingId;
  private final int page;
  private Inventory inventory;

  public TrimMenuHolder(MenuType menuType, EquipmentSlot slotType, String editingId, int page) {
    this.menuType = menuType;
    this.slotType = slotType;
    this.editingId = editingId;
    this.page = page;
  }

  public MenuType getMenuType() { return menuType; }
  public EquipmentSlot getSlotType() { return slotType; }
  public String getEditingId() { return editingId; }
  public int getPage() { return page; }
  public void setInventory(Inventory inventory) { this.inventory = inventory; }
  @Override public @NotNull Inventory getInventory() { return inventory; }

  public static ItemStack createFiller(AntiArmorTrim plugin, Material mat) {
    ItemStack item = new ItemStack(mat);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      plugin.getHatManager().hideAllVanillaTooltips(meta);
      meta.displayName(Component.text(" "));
      item.setItemMeta(meta);
    }
    plugin.getHatManager().applyHiddenComponentTooltips(item);
    return item;
  }

  public static ItemStack createStyledButton(AntiArmorTrim plugin, Material mat, String titleMini, List<Component> lore) {
    ItemStack btn = plugin.getHatManager().createCleanIconStack(mat);
    ItemMeta meta = btn.getItemMeta();
    if (meta != null) {
      meta.displayName(plugin.parse(plugin.getItemSpriteTag(mat) + " " + titleMini));
      if (lore != null) meta.lore(lore);
      btn.setItemMeta(meta);
    }
    plugin.getHatManager().applyHiddenComponentTooltips(btn);
    return btn;
  }

  public static ItemStack createBackButton(AntiArmorTrim plugin, String titleMini) {
    return createStyledButton(plugin, Material.ARROW, titleMini, null);
  }

  public static List<String> getLockedLoreTemplate(AntiArmorTrim plugin) {
    List<String> list = plugin.getConfig().getStringList("gui.no-permission-lore");
    if (list == null || list.isEmpty()) {
      return List.of(
          "",
          "<white><sprite:items:item/gray_dye></white> <#f38ba8><bold>ʟᴏᴄᴋᴇᴅ</bold> <#6c7086>▸ <#f38ba8>Cannot select (No Permission)",
          "<white><sprite:items:item/name_tag></white> <#a6adc8>ʀᴇꞯᴜɪʀᴇᴅ ʀᴀɴᴋ <#6c7086>▸ <rank>"
      );
    }
    return list;
  }

  public static void appendLockedLoreIfNeeded(AntiArmorTrim plugin, Player player, List<Component> lore, String requiredPerm) {
    boolean hasPerm = switch (requiredPerm) {
      case AntiArmorTrim.RENAME_PERM -> plugin.getRenameManager().canUseRename(player);
      case AntiArmorTrim.HAT_PERM -> plugin.getHatManager().canUseHat(player);
      case AntiArmorTrim.KILLEFFECT_PERM -> plugin.getKillEffectManager().canUseKillEffect(player);
      case AntiArmorTrim.KILLSOUND_PERM -> plugin.getKillSoundManager().canUseKillSound(player);
      case AntiArmorTrim.BYPASS_PERM -> plugin.getHatManager().canUseBypass(player);
      default -> player.isOp() || player.hasPermission(requiredPerm);
    };
    if (!hasPerm) {
      for (String line : getLockedLoreTemplate(plugin)) {
        lore.add(plugin.parseWithPerm(line, requiredPerm));
      }
    }
  }

  public static void appendSelectionOrLockedLore(AntiArmorTrim plugin, Player player, List<Component> lore, String requiredPerm, boolean selected) {
    boolean hasPerm = switch (requiredPerm) {
      case AntiArmorTrim.RENAME_PERM -> plugin.getRenameManager().canUseRename(player);
      case AntiArmorTrim.HAT_PERM -> plugin.getHatManager().canUseHat(player);
      case AntiArmorTrim.KILLEFFECT_PERM -> plugin.getKillEffectManager().canUseKillEffect(player);
      case AntiArmorTrim.KILLSOUND_PERM -> plugin.getKillSoundManager().canUseKillSound(player);
      case AntiArmorTrim.BYPASS_PERM -> plugin.getHatManager().canUseBypass(player);
      default -> player.isOp() || player.hasPermission(requiredPerm);
    };
    if (hasPerm) {
      String statusRaw = selected
          ? plugin.getConfig().getString("gui.selected-status", "<white><sprite:items:item/lime_dye></white> <#a6e3a1>ѕᴛᴀᴛᴜѕ <#6c7086>▸ <#a6e3a1><bold>Selected</bold> <#6c7086>(ᴀᴄᴛɪᴠᴇ)")
          : plugin.getConfig().getString("gui.unselected-status", "<white><sprite:items:item/ender_pearl></white> <#89dceb>ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Select & Equip <#6c7086>(ᴜɴѕᴇʟᴇᴄᴛᴇᴅ)");
      lore.add(plugin.parse(statusRaw));
    } else {
      for (String line : getLockedLoreTemplate(plugin)) {
        lore.add(plugin.parseWithPerm(line, requiredPerm));
      }
    }
  }
}
