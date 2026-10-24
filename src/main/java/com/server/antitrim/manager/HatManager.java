package com.server.antitrim.manager;

import com.google.common.collect.HashMultimap;
import com.server.antitrim.AntiArmorTrim;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HatManager {

  private final AntiArmorTrim plugin;

  private static final Method SET_ITEM_MODEL_METHOD;
  private static final Method GET_EQUIPPABLE_METHOD;
  private static final Method SET_EQUIPPABLE_METHOD;
  private static final Method EQUIP_SET_SLOT_METHOD;
  private static final Method EQUIP_SET_DAMAGE_ON_HURT_METHOD;
  private static final Method ITEM_SET_DATA_METHOD;
  private static final Object TOOLTIP_DISPLAY_TYPE;
  private static final Object HIDDEN_TOOLTIP_DISPLAY_INSTANCE;

  private static final NamespacedKey ARMOR_MODIFIER_KEY = new NamespacedKey("antitrim", "helmet_armor");
  private static final NamespacedKey TOUGHNESS_MODIFIER_KEY = new NamespacedKey("antitrim", "helmet_toughness");
  private static final NamespacedKey KNOCKBACK_MODIFIER_KEY = new NamespacedKey("antitrim", "helmet_knockback");

  static {
    Method setItemModel = null, getEquippable = null, setEquippable = null, equipSetSlot = null, equipSetDmgOnHurt = null, itemSetData = null;
    Object tooltipDisplayType = null, hiddenTooltipDisplay = null;

    try { setItemModel = ItemMeta.class.getMethod("setItemModel", NamespacedKey.class); } catch (Throwable ignored) {}
    try {
      Class<?> equipClass = Class.forName("org.bukkit.inventory.meta.components.EquippableComponent");
      getEquippable = ItemMeta.class.getMethod("getEquippable");
      setEquippable = ItemMeta.class.getMethod("setEquippable", equipClass);
      equipSetSlot = equipClass.getMethod("setSlot", EquipmentSlot.class);
      equipSetDmgOnHurt = equipClass.getMethod("setDamageOnHurt", boolean.class);
    } catch (Throwable ignored) {}

    try {
      Class<?> dctClass = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes");
      Class<?> dcTypeClass = Class.forName("io.papermc.paper.datacomponent.DataComponentType");
      Class<?> dcBuilderClass = Class.forName("io.papermc.paper.datacomponent.DataComponentBuilder");
      Class<?> tdClass = Class.forName("io.papermc.paper.datacomponent.item.TooltipDisplay");
      Class<?> tdBuilderClass = Class.forName("io.papermc.paper.datacomponent.item.TooltipDisplay\u0024Builder");

      tooltipDisplayType = dctClass.getField("TOOLTIP_DISPLAY").get(null);
      Object builder = tdClass.getMethod("tooltipDisplay").invoke(null);
      Method addHidden = tdBuilderClass.getMethod("addHiddenComponents", dcTypeClass.arrayType());

      String[] hiddenNames = {"DYED_COLOR", "ATTRIBUTE_MODIFIERS", "ENCHANTMENTS", "STORED_ENCHANTMENTS", "TRIM", "BANNER_PATTERNS", "PROVIDES_TRIM_MATERIAL"};
      List<Object> hiddenTypes = new ArrayList<>();
      for (String fn : hiddenNames) {
        try {
          Field f = dctClass.getField(fn);
          Object val = f.get(null);
          if (val != null) hiddenTypes.add(val);
        } catch (NoSuchFieldException ignored) {}
      }

      Object arrayArg = java.lang.reflect.Array.newInstance(dcTypeClass, hiddenTypes.size());
      for (int i = 0; i < hiddenTypes.size(); i++) java.lang.reflect.Array.set(arrayArg, i, hiddenTypes.get(i));
      addHidden.invoke(builder, arrayArg);
      hiddenTooltipDisplay = dcBuilderClass.getMethod("build").invoke(builder);

      for (Method m : ItemStack.class.getMethods()) {
        if (m.getName().equals("setData") && m.getParameterCount() == 2 && !dcBuilderClass.isAssignableFrom(m.getParameterTypes()[1])) {
          itemSetData = m;
          break;
        }
      }
    } catch (Throwable ignored) {}

    SET_ITEM_MODEL_METHOD = setItemModel;
    GET_EQUIPPABLE_METHOD = getEquippable;
    SET_EQUIPPABLE_METHOD = setEquippable;
    EQUIP_SET_SLOT_METHOD = equipSetSlot;
    EQUIP_SET_DAMAGE_ON_HURT_METHOD = equipSetDmgOnHurt;
    ITEM_SET_DATA_METHOD = itemSetData;
    TOOLTIP_DISPLAY_TYPE = tooltipDisplayType;
    HIDDEN_TOOLTIP_DISPLAY_INSTANCE = hiddenTooltipDisplay;
  }

  public HatManager(AntiArmorTrim plugin) {
    this.plugin = plugin;
  }

  public boolean canUseBypass(Player p) {
    return p.isOp() || p.hasPermission(AntiArmorTrim.BYPASS_PERM) || plugin.hasAdminPerm(p);
  }

  public List<String> getGroupsContainingHat(String hatId) {
    List<String> groups = new ArrayList<>();
    for (Map.Entry<String, Set<String>> entry : plugin.getDbPermGroups().entrySet()) {
      if (entry.getValue().stream().anyMatch(h -> h.equalsIgnoreCase(hatId))) groups.add(entry.getKey());
    }
    return groups;
  }

  public String getHatRequiredPermissionNode(String hatId) {
    List<String> groups = getGroupsContainingHat(hatId);
    return !groups.isEmpty() ? groups.get(0) : AntiArmorTrim.HAT_PERM;
  }

  public boolean canUseSpecificHat(Player player, String hatId) {
    if (hatId == null || hatId.isBlank()) return false;
    if (player.isOp() || plugin.hasAdminPerm(player) || player.hasPermission("armortrim.hat.*")) return true;
    String cleanId = hatId.trim();
    if (player.hasPermission("armortrim.hat." + cleanId.toLowerCase())) return true;

    List<String> assignedGroups = getGroupsContainingHat(cleanId);
    if (!assignedGroups.isEmpty()) {
      for (String permNode : assignedGroups) {
        if (player.hasPermission(permNode)) return true;
      }
      return false;
    }
    return player.hasPermission(AntiArmorTrim.HAT_PERM);
  }

  public List<String> getUnlockedHatIds(Player player) {
    List<String> unlocked = new ArrayList<>();
    for (String hatId : plugin.getDbHatItems()) {
      if (canUseSpecificHat(player, hatId) && plugin.getNexoItem(hatId) != null) unlocked.add(hatId);
    }
    return unlocked;
  }

  public boolean canUseHat(Player p) {
    return p.isOp() || plugin.hasAdminPerm(p) || p.hasPermission(AntiArmorTrim.HAT_PERM)
        || p.hasPermission("armortrim.hat.*") || !getUnlockedHatIds(p).isEmpty();
  }

  public boolean isForceDefaultHat() { return plugin.isDbForceDefaultHat(); }
  @Nullable public String getDefaultHatId() { return plugin.getDbDefaultHat(); }

  @Nullable
  public String getEffectiveHatId(Player player) {
    String saved = getSavedHatId(player);
    if (saved != null && !saved.isBlank() && canUseSpecificHat(player, saved) && plugin.getNexoItem(saved) != null) {
      return saved;
    }
    String defHat = getDefaultHatId();
    if (isForceDefaultHat() && defHat != null && plugin.getNexoItem(defHat) != null) {
      return defHat;
    }
    return null;
  }

  public boolean isExternalGui(Inventory inv) {
    if (inv == null || inv.getHolder() instanceof com.server.antitrim.gui.TrimMenuHolder) return false;
    InventoryType type = inv.getType();
    return type != InventoryType.CRAFTING && type != InventoryType.CREATIVE && type != InventoryType.PLAYER;
  }

  public boolean isExternalGuiOpen(Player player) {
    return isExternalGui(player.getOpenInventory().getTopInventory());
  }

  public void restoreAllCustomHatsInInventory(Player player) {
    ItemStack cursor = player.getItemOnCursor();
    if (isCustomHatItem(cursor)) player.setItemOnCursor(restoreOriginalHelmet(cursor));

    ItemStack helmet = player.getInventory().getHelmet();
    if (isCustomHatItem(helmet)) player.getInventory().setHelmet(restoreOriginalHelmet(helmet));

    ItemStack offHand = player.getInventory().getItemInOffHand();
    if (isCustomHatItem(offHand)) player.getInventory().setItemInOffHand(restoreOriginalHelmet(offHand));

    for (int i = 0; i < 36; i++) {
      ItemStack item = player.getInventory().getItem(i);
      if (isCustomHatItem(item)) player.getInventory().setItem(i, restoreOriginalHelmet(item));
    }
  }

  public void setForceDefaultHat(Player admin, @Nullable String hatId, boolean enabled) {
    if (hatId != null && !hatId.isBlank()) {
      String cleanId = hatId.trim();
      plugin.addHatToDb(cleanId);
      plugin.setDefaultHatInDb(cleanId, enabled);
      admin.playSound(admin.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
      admin.sendMessage(plugin.getMessage("hat-admin-default-set", Placeholder.unparsed("id", cleanId)));
    } else {
      plugin.setDefaultHatInDb("", false);
      admin.playSound(admin.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1.2f);
      admin.sendMessage(plugin.getMessage("hat-admin-default-cleared"));
    }
    for (Player online : Bukkit.getOnlinePlayers()) {
      forceRefreshEquippedHat(online);
      applyAllSavedTrims(online);
    }
  }

  public void deleteHatFromConfig(Player player, String hatId) {
    if (plugin.removeHatFromDb(hatId)) {
      for (Player online : Bukkit.getOnlinePlayers()) {
        if (hatId.equalsIgnoreCase(getSavedHatId(online))) setSavedHatId(online, null);
        forceRefreshEquippedHat(online);
      }
      player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1.2f);
      player.sendMessage(plugin.getMessage("hat-admin-removed", Placeholder.unparsed("id", hatId)));
    }
  }

  public String toMiniFont(String text) {
    if (text == null || text.isEmpty()) return "";
    StringBuilder sb = new StringBuilder(text.length());
    for (char c : text.toLowerCase().toCharArray()) {
      sb.append(switch (c) {
        case 'a' -> 'ᴀ'; case 'b' -> 'ʙ'; case 'c' -> 'ᴄ'; case 'd' -> 'ᴅ'; case 'e' -> 'ᴇ'; case 'f' -> 'ꜰ';
        case 'g' -> 'ɢ'; case 'h' -> 'ʜ'; case 'i' -> 'ɪ'; case 'j' -> 'ᴊ'; case 'k' -> 'ᴋ'; case 'l' -> 'ʟ';
        case 'm' -> 'ᴍ'; case 'n' -> 'ɴ'; case 'o' -> 'ᴏ'; case 'p' -> 'ᴘ'; case 'q' -> 'ꞯ'; case 'r' -> 'ʀ';
        case 's' -> 'ѕ'; case 't' -> 'ᴛ'; case 'u' -> 'ᴜ'; case 'v' -> 'ᴠ'; case 'w' -> 'ᴡ'; case 'x' -> 'х';
        case 'y' -> 'ʏ'; case 'z' -> 'ᴢ'; default -> c;
      });
    }
    return sb.toString();
  }

  public void applyHiddenComponentTooltips(ItemStack stack) {
    if (ITEM_SET_DATA_METHOD != null && TOOLTIP_DISPLAY_TYPE != null && HIDDEN_TOOLTIP_DISPLAY_INSTANCE != null) {
      try { ITEM_SET_DATA_METHOD.invoke(stack, TOOLTIP_DISPLAY_TYPE, HIDDEN_TOOLTIP_DISPLAY_INSTANCE); } catch (Throwable ignored) {}
    }
  }

  public ItemStack createCleanIconStack(Material visualMaterial) {
    if (SET_ITEM_MODEL_METHOD != null) {
      ItemStack stack = new ItemStack(Material.PAPER);
      ItemMeta meta = stack.getItemMeta();
      if (meta != null) {
        try {
          SET_ITEM_MODEL_METHOD.invoke(meta, visualMaterial.getKey());
          hideAllVanillaTooltips(meta);
          stack.setItemMeta(meta);
          applyHiddenComponentTooltips(stack);
          return stack;
        } catch (Throwable ignored) {}
      }
    }
    ItemStack fallback = new ItemStack(visualMaterial);
    ItemMeta meta = fallback.getItemMeta();
    if (meta != null) {
      hideAllVanillaTooltips(meta);
      fallback.setItemMeta(meta);
    }
    applyHiddenComponentTooltips(fallback);
    return fallback;
  }

  public void hideAllVanillaTooltips(ItemMeta meta) {
    meta.setAttributeModifiers(HashMultimap.create());
    meta.addItemFlags(ItemFlag.values());
  }

  public NamespacedKey getPatternKey(EquipmentSlot s) { return new NamespacedKey(plugin, "trim_pattern_" + s.name()); }
  public NamespacedKey getMaterialKey(EquipmentSlot s) { return new NamespacedKey(plugin, "trim_material_" + s.name()); }
  public NamespacedKey getPlayerHatKey() { return new NamespacedKey(plugin, "selected_nexo_hat"); }
  public NamespacedKey getItemAppliedHatKey() { return new NamespacedKey(plugin, "applied_nexo_hat"); }
  public NamespacedKey getOriginalHelmetBytesKey() { return new NamespacedKey(plugin, "original_helmet_bytes"); }

  public String getSavedPatternKey(Player p, EquipmentSlot s) { return p.getPersistentDataContainer().get(getPatternKey(s), PersistentDataType.STRING); }
  public String getSavedMaterialKey(Player p, EquipmentSlot s) { return p.getPersistentDataContainer().get(getMaterialKey(s), PersistentDataType.STRING); }

  public void setSavedTrim(Player p, EquipmentSlot s, String patternKey, String materialKey) {
    PersistentDataContainer pdc = p.getPersistentDataContainer();
    if (patternKey == null || materialKey == null) {
      pdc.remove(getPatternKey(s));
      pdc.remove(getMaterialKey(s));
    } else {
      pdc.set(getPatternKey(s), PersistentDataType.STRING, patternKey);
      pdc.set(getMaterialKey(s), PersistentDataType.STRING, materialKey);
    }
  }

  public String getSavedHatId(Player p) { return p.getPersistentDataContainer().get(getPlayerHatKey(), PersistentDataType.STRING); }

  public void setSavedHatId(Player p, String hatId) {
    PersistentDataContainer pdc = p.getPersistentDataContainer();
    if (hatId == null || hatId.isBlank()) pdc.remove(getPlayerHatKey());
    else pdc.set(getPlayerHatKey(), PersistentDataType.STRING, hatId.trim());
  }

  public boolean isCustomHatItem(ItemStack item) {
    if (item == null || item.getType().isAir()) return false;
    ItemMeta meta = item.getItemMeta();
    return meta != null && meta.getPersistentDataContainer().has(getOriginalHelmetBytesKey(), PersistentDataType.BYTE_ARRAY);
  }

  public boolean isTargetHelmet(ItemStack item) {
    if (item == null || item.getType().isAir()) return false;
    Material type = item.getType();
    return type == Material.DIAMOND_HELMET || type == Material.NETHERITE_HELMET;
  }

  public Component getHatDisplayName(String hatId, @Nullable ItemStack nexoTemplate) {
    String customName = plugin.getDbHatDisplayName(hatId);
    if (customName != null && !customName.isBlank()) return plugin.parse(customName);
    if (nexoTemplate != null && nexoTemplate.hasItemMeta()) {
      ItemMeta meta = nexoTemplate.getItemMeta();
      if (meta != null && meta.hasDisplayName() && meta.displayName() != null) return meta.displayName();
    }
    return plugin.parse("<#cba6f7>" + hatId);
  }

  public List<Component> getHatCustomLore(String hatId, @Nullable ItemStack nexoTemplate) {
    List<Component> lore = new ArrayList<>();
    List<String> dbLore = plugin.getDbHatLore(hatId);
    if (dbLore != null) {
      for (String line : dbLore) lore.add(plugin.parse(line));
      return lore;
    }
    if (nexoTemplate != null && nexoTemplate.hasItemMeta()) {
      ItemMeta meta = nexoTemplate.getItemMeta();
      if (meta != null && meta.hasLore() && meta.lore() != null) lore.addAll(meta.lore());
    }
    return lore;
  }

  @Nullable
  public ItemStack createCosmeticHatFromHelmet(ItemStack baseHelmet, String hatId) {
    if (isCustomHatItem(baseHelmet)) baseHelmet = restoreOriginalHelmet(baseHelmet);
    if (!isTargetHelmet(baseHelmet)) return null;

    ItemStack nexoHat = plugin.getNexoItem(hatId);
    if (nexoHat == null) return null;

    ItemStack cleanBase = baseHelmet.clone();
    ItemMeta cleanBaseMeta = cleanBase.getItemMeta();
    if (cleanBaseMeta instanceof ArmorMeta am && am.hasTrim()) {
      am.setTrim(null);
      cleanBase.setItemMeta(cleanBaseMeta);
    }

    byte[] serializedHelmet = cleanBase.serializeAsBytes();
    ItemStack hatStack = nexoHat.clone();
    hatStack.setAmount(1);
    ItemMeta hatMeta = hatStack.getItemMeta();
    if (hatMeta == null || cleanBaseMeta == null) return null;

    hatMeta.displayName(getHatDisplayName(hatId, nexoHat));

    List<Component> combinedLore = new ArrayList<>();
    boolean isNetherite = cleanBase.getType() == Material.NETHERITE_HELMET;
    String miniArmorName = isNetherite ? "<#cba6f7>" + toMiniFont("Netherite Helmet") : "<#89dceb>" + toMiniFont("Diamond Helmet");
    combinedLore.add(plugin.parse(plugin.getItemSpriteTag(cleanBase.getType()) + " " + miniArmorName));

    List<Component> hatLore = getHatCustomLore(hatId, nexoHat);
    if (!hatLore.isEmpty()) combinedLore.addAll(hatLore);
    if (cleanBaseMeta.hasLore() && cleanBaseMeta.lore() != null) combinedLore.addAll(cleanBaseMeta.lore());

    Map<Enchantment, Integer> baseEnchants = cleanBaseMeta.getEnchants();
    for (Map.Entry<Enchantment, Integer> entry : baseEnchants.entrySet()) {
      combinedLore.add(entry.getKey().displayName(entry.getValue()).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
    }

    combinedLore.add(Component.empty());
    combinedLore.add(Component.text("When on Head:").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
    combinedLore.add(Component.text("+3 Armor").color(NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false));
    if (isNetherite) {
      combinedLore.add(Component.text("+3 Armor Toughness").color(NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false));
      combinedLore.add(Component.text("+1 Knockback Resistance").color(NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false));
    } else {
      combinedLore.add(Component.text("+2 Armor Toughness").color(NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false));
    }
    hatMeta.lore(combinedLore);

    for (Enchantment ench : new ArrayList<>(hatMeta.getEnchants().keySet())) hatMeta.removeEnchant(ench);
    for (Map.Entry<Enchantment, Integer> entry : baseEnchants.entrySet()) hatMeta.addEnchant(entry.getKey(), entry.getValue(), true);

    hatMeta.setAttributeModifiers(HashMultimap.create());
    hatMeta.addAttributeModifier(Attribute.GENERIC_ARMOR, new AttributeModifier(ARMOR_MODIFIER_KEY, 3.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD));
    if (isNetherite) {
      hatMeta.addAttributeModifier(Attribute.GENERIC_ARMOR_TOUGHNESS, new AttributeModifier(TOUGHNESS_MODIFIER_KEY, 3.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD));
      hatMeta.addAttributeModifier(Attribute.GENERIC_KNOCKBACK_RESISTANCE, new AttributeModifier(KNOCKBACK_MODIFIER_KEY, 0.1, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD));
    } else {
      hatMeta.addAttributeModifier(Attribute.GENERIC_ARMOR_TOUGHNESS, new AttributeModifier(TOUGHNESS_MODIFIER_KEY, 2.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD));
    }

    hatMeta.addItemFlags(ItemFlag.values());
    try { hatMeta.setMaxStackSize(1); } catch (Throwable ignored) {}

    hatMeta.setUnbreakable(cleanBaseMeta.isUnbreakable());
    if (hatMeta instanceof Damageable hatDamageable && cleanBaseMeta instanceof Damageable baseDamageable) {
      try {
        int maxDurability = baseDamageable.hasMaxDamage() ? baseDamageable.getMaxDamage() : cleanBase.getType().getMaxDurability();
        if (maxDurability > 0) hatDamageable.setMaxDamage(maxDurability);
        hatDamageable.setDamage(Math.max(0, baseDamageable.getDamage()));
      } catch (Throwable ignored) {}
    }

    try {
      if (GET_EQUIPPABLE_METHOD != null && SET_EQUIPPABLE_METHOD != null && EQUIP_SET_SLOT_METHOD != null) {
        Object equipComp = GET_EQUIPPABLE_METHOD.invoke(hatMeta);
        EQUIP_SET_SLOT_METHOD.invoke(equipComp, EquipmentSlot.HEAD);
        if (EQUIP_SET_DAMAGE_ON_HURT_METHOD != null) EQUIP_SET_DAMAGE_ON_HURT_METHOD.invoke(equipComp, true);
        SET_EQUIPPABLE_METHOD.invoke(hatMeta, equipComp);
      }
    } catch (Throwable ignored) {}

    PersistentDataContainer pdc = hatMeta.getPersistentDataContainer();
    for (NamespacedKey existingKey : new ArrayList<>(pdc.getKeys())) {
      String ns = existingKey.getNamespace().toLowerCase();
      if (ns.contains("nexo") || ns.contains("oraxen")) pdc.remove(existingKey);
    }

    pdc.set(getOriginalHelmetBytesKey(), PersistentDataType.BYTE_ARRAY, serializedHelmet);
    pdc.set(getItemAppliedHatKey(), PersistentDataType.STRING, hatId);

    hatStack.setItemMeta(hatMeta);
    applyHiddenComponentTooltips(hatStack);
    return hatStack;
  }

  private boolean isCosmeticHatValid(ItemStack hatItem, String expectedHatId) {
    if (!isCustomHatItem(hatItem)) return false;
    ItemMeta meta = hatItem.getItemMeta();
    if (meta == null) return false;
    String currentHatId = meta.getPersistentDataContainer().get(getItemAppliedHatKey(), PersistentDataType.STRING);
    if (!expectedHatId.equalsIgnoreCase(currentHatId) || !meta.hasAttributeModifiers()) return false;
    var armorMods = meta.getAttributeModifiers(Attribute.GENERIC_ARMOR);
    return armorMods != null && !armorMods.isEmpty();
  }

  public ItemStack restoreOriginalHelmet(ItemStack hatItem) {
    if (!isCustomHatItem(hatItem)) return hatItem;
    ItemMeta hatMeta = hatItem.getItemMeta();
    if (hatMeta == null) return hatItem;
    byte[] bytes = hatMeta.getPersistentDataContainer().get(getOriginalHelmetBytesKey(), PersistentDataType.BYTE_ARRAY);
    if (bytes == null || bytes.length == 0) return hatItem;

    try {
      ItemStack restored = ItemStack.deserializeBytes(bytes);
      if (hatMeta instanceof Damageable hatDmg && restored.getItemMeta() instanceof Damageable restoredDmg) {
        int currentDamage = hatDmg.getDamage();
        int maxDurability = restored.getType().getMaxDurability();
        if (maxDurability > 0 && currentDamage >= maxDurability) return new ItemStack(Material.AIR);
        restoredDmg.setDamage(Math.max(0, currentDamage));
        restored.setItemMeta((ItemMeta) restoredDmg);
      }
      return restored;
    } catch (Throwable ignored) {
      return hatItem;
    }
  }

  public ArmorTrim buildArmorTrim(String patternKey, String materialKey) {
    if (patternKey == null || materialKey == null) return null;
    TrimPattern pattern = Registry.TRIM_PATTERN.get(NamespacedKey.minecraft(patternKey));
    TrimMaterial material = Registry.TRIM_MATERIAL.get(NamespacedKey.minecraft(materialKey));
    return (pattern != null && material != null) ? new ArmorTrim(material, pattern) : null;
  }

  public void applySavedTrimToSlot(Player player, EquipmentSlot slot) {
    if (slot == EquipmentSlot.HEAD) {
      ItemStack headItem = player.getInventory().getHelmet();

      if (isExternalGuiOpen(player)) {
        if (isCustomHatItem(headItem)) {
          headItem = restoreOriginalHelmet(headItem);
          player.getInventory().setHelmet(headItem);
        }
      } else {
        String effectiveHat = getEffectiveHatId(player);
        if (effectiveHat != null) {
          if (isCustomHatItem(headItem)) {
            if (!isCosmeticHatValid(headItem, effectiveHat)) {
              ItemStack restored = restoreOriginalHelmet(headItem);
              ItemStack updatedHat = createCosmeticHatFromHelmet(restored, effectiveHat);
              if (updatedHat != null) player.getInventory().setHelmet(updatedHat);
            }
            return;
          } else if (isTargetHelmet(headItem)) {
            ItemStack cosmeticHat = createCosmeticHatFromHelmet(headItem, effectiveHat);
            if (cosmeticHat != null) player.getInventory().setHelmet(cosmeticHat);
            return;
          }
        } else if (isCustomHatItem(headItem)) {
          player.getInventory().setHelmet(restoreOriginalHelmet(headItem));
        }
      }
    }

    if (!canUseBypass(player)) return;

    ItemStack item = player.getInventory().getItem(slot);
    if (item == null || item.getType().isAir() || !(item.getItemMeta() instanceof ArmorMeta armorMeta)) return;

    ArmorTrim desiredTrim = buildArmorTrim(getSavedPatternKey(player, slot), getSavedMaterialKey(player, slot));
    if (desiredTrim == null) return;

    ArmorTrim currentTrim = armorMeta.getTrim();
    if (currentTrim == null || !currentTrim.equals(desiredTrim)) {
      armorMeta.setTrim(desiredTrim);
      item.setItemMeta(armorMeta);
      player.getInventory().setItem(slot, item);
    }
  }

  public void applyAllSavedTrims(Player player) {
    for (int i = 0; i < 36; i++) {
      ItemStack invItem = player.getInventory().getItem(i);
      if (isCustomHatItem(invItem)) player.getInventory().setItem(i, restoreOriginalHelmet(invItem));
    }
    ItemStack offHand = player.getInventory().getItemInOffHand();
    if (isCustomHatItem(offHand)) player.getInventory().setItemInOffHand(restoreOriginalHelmet(offHand));

    applySavedTrimToSlot(player, EquipmentSlot.HEAD);
    applySavedTrimToSlot(player, EquipmentSlot.CHEST);
    applySavedTrimToSlot(player, EquipmentSlot.LEGS);
    applySavedTrimToSlot(player, EquipmentSlot.FEET);
  }

  public void forceRefreshEquippedHat(Player player) {
    if (isExternalGuiOpen(player)) return;
    ItemStack headItem = player.getInventory().getHelmet();
    if (headItem == null || headItem.getType().isAir()) return;

    String effectiveHat = getEffectiveHatId(player);
    ItemStack baseHelmet = isCustomHatItem(headItem) ? restoreOriginalHelmet(headItem) : headItem;

    if (effectiveHat != null && isTargetHelmet(baseHelmet)) {
      ItemStack updatedHat = createCosmeticHatFromHelmet(baseHelmet, effectiveHat);
      if (updatedHat != null) {
        player.getInventory().setHelmet(updatedHat);
        return;
      }
    }
    if (isCustomHatItem(headItem)) {
      player.getInventory().setHelmet(baseHelmet);
    }
    applySavedTrimToSlot(player, EquipmentSlot.HEAD);
  }
}
