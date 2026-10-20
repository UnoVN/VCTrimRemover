package com.server.antitrim;

import com.google.common.collect.HashMultimap;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TrimGuiManager implements Listener {

  private final AntiArmorTrim plugin;
  private final Set<UUID> pendingRenamePlayers = ConcurrentHashMap.newKeySet();
  private final Set<UUID> pendingAddHatPlayers = ConcurrentHashMap.newKeySet();
  private final Map<UUID, String> pendingHatNameEdit = new ConcurrentHashMap<>();
  private final Map<UUID, String> pendingHatLoreEdit = new ConcurrentHashMap<>();

  private static final Map<String, PatternOption> PATTERNS = new LinkedHashMap<>();
  private static final Map<String, MaterialOption> MATERIALS = new LinkedHashMap<>();

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

  public enum MenuType {
    DECOR_HUB, TRIM_MAIN, TRIM_SLOT, HAT_STUDIO, HAT_ADMIN, HAT_EDITOR, RENAME_STUDIO
  }

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

  public TrimGuiManager(AntiArmorTrim plugin) { this.plugin = plugin; }

  public boolean canUseBypass(Player p) { return p.isOp() || p.hasPermission(AntiArmorTrim.BYPASS_PERM) || plugin.hasAdminPerm(p); }
  public boolean canUseRename(Player p) { return p.isOp() || p.hasPermission(AntiArmorTrim.RENAME_PERM) || canUseBypass(p); }
  public boolean canUseHat(Player p) { return p.isOp() || p.hasPermission(AntiArmorTrim.HAT_PERM) || canUseBypass(p); }

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

  private void applyHiddenComponentTooltips(ItemStack stack) {
    if (ITEM_SET_DATA_METHOD != null && TOOLTIP_DISPLAY_TYPE != null && HIDDEN_TOOLTIP_DISPLAY_INSTANCE != null) {
      try { ITEM_SET_DATA_METHOD.invoke(stack, TOOLTIP_DISPLAY_TYPE, HIDDEN_TOOLTIP_DISPLAY_INSTANCE); } catch (Throwable ignored) {}
    }
  }

  private ItemStack createCleanIconStack(Material visualMaterial) {
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

  private ItemStack createStyledButton(Material mat, String titleMini, List<Component> lore) {
    ItemStack btn = createCleanIconStack(mat);
    ItemMeta meta = btn.getItemMeta();
    if (meta != null) {
      meta.displayName(plugin.parse(plugin.getItemSpriteTag(mat) + " " + titleMini));
      if (lore != null) meta.lore(lore);
      btn.setItemMeta(meta);
    }
    applyHiddenComponentTooltips(btn);
    return btn;
  }

  private void hideAllVanillaTooltips(ItemMeta meta) {
    meta.setAttributeModifiers(HashMultimap.create());
    meta.addItemFlags(ItemFlag.values());
  }

  private NamespacedKey getPatternKey(EquipmentSlot s) { return new NamespacedKey(plugin, "trim_pattern_" + s.name()); }
  private NamespacedKey getMaterialKey(EquipmentSlot s) { return new NamespacedKey(plugin, "trim_material_" + s.name()); }
  private NamespacedKey getPlayerHatKey() { return new NamespacedKey(plugin, "selected_nexo_hat"); }
  private NamespacedKey getItemAppliedHatKey() { return new NamespacedKey(plugin, "applied_nexo_hat"); }
  private NamespacedKey getOriginalHelmetBytesKey() { return new NamespacedKey(plugin, "original_helmet_bytes"); }

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
    String customName = plugin.getConfig().getString("hats.definitions." + hatId + ".display-name");
    if (customName != null && !customName.isBlank()) return plugin.parse(customName);
    if (nexoTemplate != null && nexoTemplate.hasItemMeta()) {
      ItemMeta meta = nexoTemplate.getItemMeta();
      if (meta != null && meta.hasDisplayName() && meta.displayName() != null) return meta.displayName();
    }
    return plugin.parse("<#cba6f7>" + hatId);
  }

  public List<Component> getHatCustomLore(String hatId, @Nullable ItemStack nexoTemplate) {
    List<Component> lore = new ArrayList<>();
    String path = "hats.definitions." + hatId + ".lore";
    if (plugin.getConfig().contains(path)) {
      for (String line : plugin.getConfig().getStringList(path)) lore.add(plugin.parse(line));
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
      String savedHat = getSavedHatId(player);
      ItemStack headItem = player.getInventory().getHelmet();

      if (canUseHat(player) && savedHat != null && plugin.getNexoItem(savedHat) != null) {
        if (isCustomHatItem(headItem)) {
          if (!isCosmeticHatValid(headItem, savedHat)) {
            ItemStack restored = restoreOriginalHelmet(headItem);
            ItemStack updatedHat = createCosmeticHatFromHelmet(restored, savedHat);
            if (updatedHat != null) player.getInventory().setHelmet(updatedHat);
          }
          return;
        } else if (isTargetHelmet(headItem)) {
          ItemStack cosmeticHat = createCosmeticHatFromHelmet(headItem, savedHat);
          if (cosmeticHat != null) player.getInventory().setHelmet(cosmeticHat);
          return;
        }
      } else if (isCustomHatItem(headItem)) {
        player.getInventory().setHelmet(restoreOriginalHelmet(headItem));
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
    ItemStack headItem = player.getInventory().getHelmet();
    if (isCustomHatItem(headItem)) player.getInventory().setHelmet(restoreOriginalHelmet(headItem));
    applySavedTrimToSlot(player, EquipmentSlot.HEAD);
  }

  public void openDecorHub(Player player) {
    Component title = plugin.parse(plugin.getConfig().getString("gui.decor-title", "<#cba6f7><bold>Decoration Studio</bold>"));
    TrimMenuHolder holder = new TrimMenuHolder(MenuType.DECOR_HUB, null, null);
    Inventory inv = Bukkit.createInventory(holder, 27, title);
    holder.setInventory(inv);

    ItemStack border = createFiller(Material.GRAY_STAINED_GLASS_PANE);
    for (int i = 0; i < 27; i++) inv.setItem(i, border);

    List<Component> trimLore = new ArrayList<>(List.of(plugin.parse("<#a6adc8>Customize automatic armor trims"), plugin.parse("<#a6adc8>and materials for your gear.")));
    appendLockedLoreIfNeeded(player, trimLore, AntiArmorTrim.BYPASS_PERM);
    inv.setItem(11, createStyledButton(Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, "<#cba6f7><bold>Armor Trim Studio</bold>", trimLore));

    List<Component> hatLore = new ArrayList<>(List.of(plugin.parse("<#a6adc8>Transform your Diamond or Netherite"), plugin.parse("<#a6adc8>helmet into a custom Nexo hat.")));
    appendLockedLoreIfNeeded(player, hatLore, AntiArmorTrim.HAT_PERM);
    inv.setItem(13, createStyledButton(Material.NETHERITE_HELMET, "<#f9e2af><bold>Custom Hat Studio</bold>", hatLore));

    List<Component> renameLore = new ArrayList<>(List.of(plugin.parse("<#a6adc8>Rename the item in your main hand"), plugin.parse("<#a6adc8>using Minecraft color & format codes.")));
    appendLockedLoreIfNeeded(player, renameLore, AntiArmorTrim.RENAME_PERM);
    inv.setItem(15, createStyledButton(Material.NAME_TAG, "<#74c7ec><bold>Item Rename Studio</bold>", renameLore));

    player.openInventory(inv);
  }

  public void openHatMenu(Player player) {
    Component title = plugin.parse(plugin.getConfig().getString("gui.hat-title", "<white><sprite:items:item/netherite_helmet></white> <#cba6f7><bold>Custom Hat Studio</bold>"));
    TrimMenuHolder holder = new TrimMenuHolder(MenuType.HAT_STUDIO, null, null);
    Inventory inv = Bukkit.createInventory(holder, 54, title);
    holder.setInventory(inv);

    ItemStack filler = createFiller(Material.BLACK_STAINED_GLASS_PANE);
    for (int i = 36; i < 54; i++) inv.setItem(i, filler);

    List<String> hatIds = plugin.getConfig().getStringList("hats.items");
    String currentHat = getSavedHatId(player);
    int slotIndex = 0;

    for (String hatId : hatIds) {
      if (slotIndex >= 36) break;
      ItemStack nexoStack = plugin.getNexoItem(hatId);
      if (nexoStack == null) continue;

      boolean selected = hatId.equalsIgnoreCase(currentHat);
      ItemStack icon = nexoStack.clone();
      ItemMeta meta = icon.getItemMeta();
      if (meta != null) {
        hideAllVanillaTooltips(meta);
        meta.displayName(plugin.parse(plugin.getItemSpriteTag(Material.NETHERITE_HELMET) + " ").append(getHatDisplayName(hatId, nexoStack)));
        if (selected) meta.setEnchantmentGlintOverride(true);

        List<Component> lore = new ArrayList<>(getHatCustomLore(hatId, nexoStack));
        appendSelectionOrLockedLore(player, lore, AntiArmorTrim.HAT_PERM, selected);
        meta.lore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        for (NamespacedKey k : new ArrayList<>(pdc.getKeys())) {
          if (k.getNamespace().toLowerCase().contains("nexo")) pdc.remove(k);
        }
        pdc.set(getItemAppliedHatKey(), PersistentDataType.STRING, hatId);
        icon.setItemMeta(meta);
      }
      applyHiddenComponentTooltips(icon);
      inv.setItem(slotIndex++, icon);
    }

    inv.setItem(45, createBackButton("<#f9e2af><bold>Back</bold>"));

    List<Component> clearLore = new ArrayList<>(List.of(plugin.parse("<#a6adc8>Disable custom hat and restore"), plugin.parse("<#a6adc8>your original helmet appearance.")));
    appendLockedLoreIfNeeded(player, clearLore, AntiArmorTrim.HAT_PERM);
    inv.setItem(49, createStyledButton(Material.BARRIER, "<#f38ba8><bold>Reset & Unequip Hat</bold>", clearLore));

    if (plugin.hasAdminPerm(player)) {
      inv.setItem(53, createStyledButton(Material.COMMAND_BLOCK, "<#f38ba8><bold>Admin Hat Manager</bold>", List.of(
          plugin.parse("<#a6adc8>Manage Nexo hats, edit display"), plugin.parse("<#a6adc8>names, lore, or remove broken IDs."))));
    }
    player.openInventory(inv);
  }

  public void openHatAdminMenu(Player player) {
    Component title = plugin.parse(plugin.getConfig().getString("gui.hat-admin-title", "<white><sprite:items:item/command_block></white> <#f38ba8><bold>Admin Hat Manager</bold>"));
    TrimMenuHolder holder = new TrimMenuHolder(MenuType.HAT_ADMIN, null, null);
    Inventory inv = Bukkit.createInventory(holder, 54, title);
    holder.setInventory(inv);

    ItemStack filler = createFiller(Material.BLACK_STAINED_GLASS_PANE);
    for (int i = 36; i < 54; i++) inv.setItem(i, filler);

    List<String> hatIds = plugin.getConfig().getStringList("hats.items");
    int slotIndex = 0;

    for (String hatId : hatIds) {
      if (slotIndex >= 36) break;
      ItemStack nexoStack = plugin.getNexoItem(hatId);
      ItemStack icon;

      if (nexoStack != null) {
        icon = nexoStack.clone();
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
          hideAllVanillaTooltips(meta);
          meta.displayName(getHatDisplayName(hatId, nexoStack).append(plugin.parse(" <#6c7086>(" + hatId + ")")));
          List<Component> lore = new ArrayList<>(getHatCustomLore(hatId, nexoStack));
          lore.add(Component.empty());
          lore.add(plugin.parse("<#89dceb>Left-Click: <#cdd6f4>Edit Display Name & Lore"));
          lore.add(plugin.parse("<#f38ba8>Right-Click: <#cdd6f4>Delete Hat"));
          meta.lore(lore);

          PersistentDataContainer pdc = meta.getPersistentDataContainer();
          for (NamespacedKey k : new ArrayList<>(pdc.getKeys())) {
            if (k.getNamespace().toLowerCase().contains("nexo")) pdc.remove(k);
          }
          pdc.set(getItemAppliedHatKey(), PersistentDataType.STRING, hatId);
          icon.setItemMeta(meta);
        }
        applyHiddenComponentTooltips(icon);
      } else {
        icon = createStyledButton(Material.BARRIER, "<#f38ba8><bold>Broken ID: " + hatId + "</bold>", List.of(
            plugin.parse("<#a6adc8>Not found in Nexo!"), Component.empty(), plugin.parse("<#f38ba8>Click to delete this broken entry")));
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
          meta.getPersistentDataContainer().set(getItemAppliedHatKey(), PersistentDataType.STRING, hatId);
          icon.setItemMeta(meta);
          applyHiddenComponentTooltips(icon);
        }
      }
      inv.setItem(slotIndex++, icon);
    }

    inv.setItem(45, createBackButton("<#f9e2af><bold>Back</bold>"));
    inv.setItem(49, createStyledButton(Material.ANVIL, "<#a6e3a1><bold>Add New Nexo Hat</bold>", List.of(
        plugin.parse("<#a6adc8>Click to type a Nexo Item ID"), plugin.parse("<#a6adc8>in chat and add it to the studio."))));
    player.openInventory(inv);
  }

  public void openHatEditorMenu(Player player, String hatId) {
    String rawTitle = plugin.getConfig().getString("gui.hat-editor-title", "<white><sprite:items:item/anvil></white> <#cba6f7><bold>Edit Hat:</bold> <#89dceb><id>");
    TrimMenuHolder holder = new TrimMenuHolder(MenuType.HAT_EDITOR, null, hatId);
    Inventory inv = Bukkit.createInventory(holder, 27, plugin.parse(rawTitle, Placeholder.unparsed("id", hatId)));
    holder.setInventory(inv);

    ItemStack border = createFiller(Material.GRAY_STAINED_GLASS_PANE);
    for (int i = 0; i < 27; i++) inv.setItem(i, border);

    ItemStack nexoStack = plugin.getNexoItem(hatId);
    inv.setItem(10, createStyledButton(Material.NAME_TAG, "<#89dceb><bold>Edit Display Name</bold>", List.of(
        plugin.parse("<#a6adc8>Current: ").append(getHatDisplayName(hatId, nexoStack)),
        Component.empty(),
        plugin.parse("<#89dceb>Left-Click: <#cdd6f4>Set new display name"),
        plugin.parse("<#fab387>Right-Click: <#cdd6f4>Reset to Nexo default"))));

    List<Component> loreList = new ArrayList<>();
    List<Component> currentLore = getHatCustomLore(hatId, nexoStack);
    if (currentLore.isEmpty()) loreList.add(plugin.parse("<#6c7086>No lore configured"));
    else loreList.addAll(currentLore);
    loreList.add(Component.empty());
    loreList.add(plugin.parse("<#89dceb>Left-Click: <#cdd6f4>Set or append lore in chat"));
    loreList.add(plugin.parse("<#fab387>Right-Click: <#cdd6f4>Clear custom lore"));
    inv.setItem(12, createStyledButton(Material.WRITABLE_BOOK, "<#cba6f7><bold>Edit Hat Lore</bold>", loreList));

    ItemStack previewHat = createCosmeticHatFromHelmet(new ItemStack(Material.NETHERITE_HELMET), hatId);
    if (previewHat != null) inv.setItem(14, previewHat);

    inv.setItem(16, createStyledButton(Material.BARRIER, "<#f38ba8><bold>Delete Hat</bold>", List.of(plugin.parse("<#a6adc8>Remove <#f9e2af>" + hatId + " <#a6adc8>from the studio."))));
    inv.setItem(18, createBackButton("<#f9e2af><bold>Back</bold>"));
    player.openInventory(inv);
  }

  public void deleteHatByCommand(Player player, String hatId) {
    deleteHatFromConfig(player, hatId);
    openHatAdminMenu(player);
  }

  private void deleteHatFromConfig(Player player, String hatId) {
    List<String> hats = new ArrayList<>(plugin.getConfig().getStringList("hats.items"));
    if (hats.removeIf(h -> h.equalsIgnoreCase(hatId))) {
      plugin.getConfig().set("hats.items", hats);
      plugin.getConfig().set("hats.definitions." + hatId, null);
      plugin.saveConfig();
      for (Player online : Bukkit.getOnlinePlayers()) {
        if (hatId.equalsIgnoreCase(getSavedHatId(online))) {
          setSavedHatId(online, null);
          forceRefreshEquippedHat(online);
        }
      }
      player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1.2f);
      player.sendMessage(plugin.getMessage("hat-admin-removed", Placeholder.unparsed("id", hatId)));
    }
  }

  public void openRenameMenu(Player player) {
    Component title = plugin.parse(plugin.getConfig().getString("gui.rename-title", "<white><sprite:items:item/name_tag></white> <#cba6f7><bold>Item Rename Studio</bold>"));
    TrimMenuHolder holder = new TrimMenuHolder(MenuType.RENAME_STUDIO, null, null);
    Inventory inv = Bukkit.createInventory(holder, 27, title);
    holder.setInventory(inv);

    ItemStack border = createFiller(Material.GRAY_STAINED_GLASS_PANE);
    for (int i = 0; i < 27; i++) inv.setItem(i, border);

    ItemStack guideItem = createCleanIconStack(Material.KNOWLEDGE_BOOK);
    ItemMeta guideMeta = guideItem.getItemMeta();
    if (guideMeta != null) {
      guideMeta.displayName(plugin.parseLiteral(plugin.getItemSpriteTag(Material.KNOWLEDGE_BOOK) + " <#f9e2af><bold>Minecraft Format Guide</bold>"));
      guideMeta.lore(List.of(
          plugin.parseLiteral("<#cba6f7>Color Codes:"),
          plugin.parseLiteral("<white>&0 <dark_gray>▸ <black>Black     <white>&1 <dark_gray>▸ <dark_blue>Dark Blue"),
          plugin.parseLiteral("<white>&2 <dark_gray>▸ <dark_green>Green     <white>&3 <dark_gray>▸ <dark_aqua>Dark Aqua"),
          plugin.parseLiteral("<white>&4 <dark_gray>▸ <dark_red>Dark Red  <white>&5 <dark_gray>▸ <dark_purple>Dark Purple"),
          plugin.parseLiteral("<white>&6 <dark_gray>▸ <gold>Gold      <white>&7 <dark_gray>▸ <gray>Gray"),
          plugin.parseLiteral("<white>&8 <dark_gray>▸ <dark_gray>Dark Gray <white>&9 <dark_gray>▸ <blue>Blue"),
          plugin.parseLiteral("<white>&a <dark_gray>▸ <green>Green     <white>&b <dark_gray>▸ <aqua>Aqua"),
          plugin.parseLiteral("<white>&c <dark_gray>▸ <red>Red       <white>&d <dark_gray>▸ <light_purple>Light Purple"),
          plugin.parseLiteral("<white>&e <dark_gray>▸ <yellow>Yellow    <white>&f <dark_gray>▸ <white>White"),
          Component.empty(),
          plugin.parseLiteral("<#cba6f7>Format Codes:"),
          plugin.parseLiteral("<white>&k <dark_gray>▸ <white><obfuscated>abc</obfuscated> <gray>(Magic)   <white>&l <dark_gray>▸ <white><bold>Bold</bold>"),
          plugin.parseLiteral("<white>&m <dark_gray>▸ <white><strikethrough>Strike</strikethrough>        <white>&n <dark_gray>▸ <white><underlined>Underline</underlined>"),
          plugin.parseLiteral("<white>&o <dark_gray>▸ <white><italic>Italic</italic>        <white>&r <dark_gray>▸ <gray>Reset")));
      guideItem.setItemMeta(guideMeta);
    }
    applyHiddenComponentTooltips(guideItem);
    inv.setItem(11, guideItem);

    ItemStack handItem = player.getInventory().getItemInMainHand();
    if (isCustomHatItem(handItem)) {
      handItem = restoreOriginalHelmet(handItem);
      player.getInventory().setItemInMainHand(handItem);
    }

    if (handItem == null || handItem.getType().isAir()) {
      inv.setItem(13, createStyledButton(Material.BARRIER, "<#f38ba8><bold>No Item in Main Hand</bold>", List.of(
          plugin.parse("<#a6adc8>Hold an item in your main hand"), plugin.parse("<#a6adc8>to preview and rename it."))));
    } else {
      ItemStack preview = handItem.clone();
      ItemMeta previewMeta = preview.getItemMeta();
      if (previewMeta != null) {
        hideAllVanillaTooltips(previewMeta);
        preview.setItemMeta(previewMeta);
      }
      applyHiddenComponentTooltips(preview);
      inv.setItem(13, preview);
    }

    List<Component> renameLore = new ArrayList<>(List.of(
        plugin.parse("<#89dceb>Left-Click: <#cdd6f4>Enter new name in chat"),
        plugin.parse("<#fab387>Right-Click: <#cdd6f4>Reset to default name")));
    appendLockedLoreIfNeeded(player, renameLore, AntiArmorTrim.RENAME_PERM);
    inv.setItem(15, createStyledButton(Material.NAME_TAG, "<#a6e3a1><bold>Rename Held Item</bold>", renameLore));
    inv.setItem(18, createBackButton("<#f9e2af><bold>Back</bold>"));
    player.openInventory(inv);
  }

  public void applyItemRename(Player player, String rawInput) {
    if (!canUseRename(player)) {
      player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
      player.sendMessage(plugin.getMessageWithPerm("no-permission-gui", AntiArmorTrim.RENAME_PERM));
      return;
    }
    ItemStack item = player.getInventory().getItemInMainHand();
    if (isCustomHatItem(item)) {
      item = restoreOriginalHelmet(item);
      player.getInventory().setItemInMainHand(item);
    }
    if (item == null || item.getType().isAir()) {
      player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
      player.sendMessage(plugin.getMessage("rename-no-item"));
      return;
    }

    Component parsedName = plugin.parseMinecraftFormat(rawInput);
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
    item.setItemMeta(meta);
    player.getInventory().setItemInMainHand(item);

    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.3f);
    player.sendMessage(plugin.getMessage("rename-success", Placeholder.component("name", parsedName)));
  }

  public void openMainMenu(Player player) {
    Component title = plugin.parse(plugin.getConfig().getString("gui.main-title", "<#cba6f7><bold>Armor Trim Studio</bold>"));
    TrimMenuHolder holder = new TrimMenuHolder(MenuType.TRIM_MAIN, null, null);
    Inventory inv = Bukkit.createInventory(holder, 27, title);
    holder.setInventory(inv);

    ItemStack border = createFiller(Material.GRAY_STAINED_GLASS_PANE);
    for (int i = 0; i < 27; i++) inv.setItem(i, border);

    inv.setItem(10, createMainSlotIcon(player, EquipmentSlot.HEAD, Material.NETHERITE_HELMET));
    inv.setItem(12, createMainSlotIcon(player, EquipmentSlot.CHEST, Material.NETHERITE_CHESTPLATE));
    inv.setItem(14, createMainSlotIcon(player, EquipmentSlot.LEGS, Material.NETHERITE_LEGGINGS));
    inv.setItem(16, createMainSlotIcon(player, EquipmentSlot.FEET, Material.NETHERITE_BOOTS));
    inv.setItem(18, createBackButton("<#f9e2af><bold>Back</bold>"));
    player.openInventory(inv);
  }

  private ItemStack createMainSlotIcon(Player player, EquipmentSlot slot, Material iconMat) {
    ItemStack item = new ItemStack(iconMat);
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return item;

    hideAllVanillaTooltips(meta);
    meta.displayName(plugin.parse(plugin.getItemSpriteTag(iconMat) + " <#74c7ec><bold>" + getSlotEnglishName(slot) + " Trim</bold>"));

    String patternKey = getSavedPatternKey(player, slot);
    String materialKey = getSavedMaterialKey(player, slot);
    PatternOption pOpt = patternKey != null ? PATTERNS.get(patternKey) : null;
    MaterialOption mOpt = materialKey != null ? MATERIALS.get(materialKey) : null;

    List<Component> lore = new ArrayList<>();
    String patternDisplay = pOpt != null ? plugin.getItemSpriteTag(pOpt.icon) + " <#89dceb>" + pOpt.name : "<#6c7086>None";
    String materialDisplay = mOpt != null ? plugin.getItemSpriteTag(mOpt.icon) + " <" + mOpt.hexColor + ">" + mOpt.name : "<#6c7086>None";

    lore.add(plugin.parse("<#a6adc8>Pattern: " + patternDisplay));
    lore.add(plugin.parse("<#a6adc8>Material: " + materialDisplay));
    if (slot == EquipmentSlot.HEAD && getSavedHatId(player) != null) lore.add(plugin.parse("<#fab387>Custom Hat Active (Trim Paused)"));

    if (meta instanceof ArmorMeta armorMeta && pOpt != null && mOpt != null) {
      ArmorTrim previewTrim = buildArmorTrim(patternKey, materialKey);
      if (previewTrim != null) armorMeta.setTrim(previewTrim);
    }

    appendSelectionOrLockedLore(player, lore, AntiArmorTrim.BYPASS_PERM, pOpt != null && mOpt != null);
    meta.lore(lore);
    item.setItemMeta(meta);
    applyHiddenComponentTooltips(item);
    return item;
  }

  public void openSlotMenu(Player player, EquipmentSlot slot) {
    String slotName = getSlotEnglishName(slot);
    Material previewMat = getPreviewMaterial(slot);
    String rawTitle = plugin.getConfig().getString("gui.slot-title", "<slot_icon> <#cba6f7><bold>Trim Editor:</bold> <#74c7ec><slot>");
    TrimMenuHolder holder = new TrimMenuHolder(MenuType.TRIM_SLOT, slot, null);
    Inventory inv = Bukkit.createInventory(holder, 54, plugin.parse(rawTitle, Placeholder.unparsed("slot", slotName), Placeholder.parsed("slot_icon", plugin.getItemSpriteTag(previewMat))));
    holder.setInventory(inv);

    ItemStack filler = createFiller(Material.BLACK_STAINED_GLASS_PANE);
    for (int i = 18; i < 54; i++) inv.setItem(i, filler);

    String currentPattern = getSavedPatternKey(player, slot);
    String currentMaterial = getSavedMaterialKey(player, slot);

    int pIndex = 0;
    for (PatternOption pOpt : PATTERNS.values()) inv.setItem(pIndex++, createPatternIcon(player, pOpt, pOpt.key.equalsIgnoreCase(currentPattern)));

    inv.setItem(22, createMainSlotIcon(player, slot, previewMat));

    int[] matSlots = {29, 30, 31, 32, 33, 38, 39, 40, 41, 42};
    int mIndex = 0;
    for (MaterialOption mOpt : MATERIALS.values()) {
      if (mIndex >= matSlots.length) break;
      inv.setItem(matSlots[mIndex++], createMaterialIcon(player, mOpt, mOpt.key.equalsIgnoreCase(currentMaterial)));
    }

    inv.setItem(45, createBackButton("<#f9e2af><bold>Back</bold>"));

    List<Component> clearLore = new ArrayList<>(List.of(plugin.parse("<#a6adc8>Disable auto-trim and clear active trim.")));
    appendLockedLoreIfNeeded(player, clearLore, AntiArmorTrim.BYPASS_PERM);
    inv.setItem(49, createStyledButton(Material.BARRIER, "<#f38ba8><bold>Reset & Clear Trim</bold>", clearLore));

    List<Component> allLore = new ArrayList<>(List.of(plugin.parse("<#a6adc8>Sync this slot's state (Trim or Reset)"), plugin.parse("<#a6adc8>to Helmet, Chestplate, Leggings & Boots.")));
    appendLockedLoreIfNeeded(player, allLore, AntiArmorTrim.BYPASS_PERM);
    inv.setItem(53, createStyledButton(Material.NETHER_STAR, "<#cba6f7><bold>Sync to All 4 Pieces</bold>", allLore));

    player.openInventory(inv);
  }

  private ItemStack createBackButton(String titleMini) { return createStyledButton(Material.ARROW, titleMini, null); }

  private ItemStack createPatternIcon(Player player, PatternOption opt, boolean selected) {
    ItemStack item = createCleanIconStack(opt.icon);
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return item;
    meta.displayName(plugin.parse(plugin.getItemSpriteTag(opt.icon) + " " + (selected ? "<#a6e3a1><bold>" : "<#89dceb>") + opt.name + " Trim"));
    if (selected) meta.setEnchantmentGlintOverride(true);
    List<Component> lore = new ArrayList<>();
    appendSelectionOrLockedLore(player, lore, AntiArmorTrim.BYPASS_PERM, selected);
    meta.lore(lore);
    item.setItemMeta(meta);
    applyHiddenComponentTooltips(item);
    return item;
  }

  private ItemStack createMaterialIcon(Player player, MaterialOption opt, boolean selected) {
    ItemStack item = createCleanIconStack(opt.icon);
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return item;
    meta.displayName(plugin.parse(plugin.getItemSpriteTag(opt.icon) + " " + (selected ? "<#a6e3a1><bold>" : "<" + opt.hexColor + ">") + opt.name + " Material"));
    if (selected) meta.setEnchantmentGlintOverride(true);
    List<Component> lore = new ArrayList<>();
    appendSelectionOrLockedLore(player, lore, AntiArmorTrim.BYPASS_PERM, selected);
    meta.lore(lore);
    item.setItemMeta(meta);
    applyHiddenComponentTooltips(item);
    return item;
  }

  private void appendSelectionOrLockedLore(Player player, List<Component> lore, String requiredPerm, boolean selected) {
    boolean hasPerm = switch (requiredPerm) {
      case AntiArmorTrim.RENAME_PERM -> canUseRename(player);
      case AntiArmorTrim.HAT_PERM -> canUseHat(player);
      case AntiArmorTrim.BYPASS_PERM -> canUseBypass(player);
      default -> player.isOp() || player.hasPermission(requiredPerm);
    };
    if (hasPerm) {
      String statusRaw = selected
          ? plugin.getConfig().getString("gui.selected-status", "<#a6e3a1>● Selected")
          : plugin.getConfig().getString("gui.unselected-status", "<#6c7086>○ Unselected");
      lore.add(plugin.parse(statusRaw));
    } else {
      for (String line : plugin.getConfig().getStringList("gui.no-permission-lore")) lore.add(plugin.parseWithPerm(line, requiredPerm));
    }
  }

  private void appendLockedLoreIfNeeded(Player player, List<Component> lore, String requiredPerm) {
    boolean hasPerm = switch (requiredPerm) {
      case AntiArmorTrim.RENAME_PERM -> canUseRename(player);
      case AntiArmorTrim.HAT_PERM -> canUseHat(player);
      case AntiArmorTrim.BYPASS_PERM -> canUseBypass(player);
      default -> player.isOp() || player.hasPermission(requiredPerm);
    };
    if (!hasPerm) {
      for (String line : plugin.getConfig().getStringList("gui.no-permission-lore")) lore.add(plugin.parseWithPerm(line, requiredPerm));
    }
  }

  private ItemStack createFiller(Material mat) {
    ItemStack item = new ItemStack(mat);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      hideAllVanillaTooltips(meta);
      meta.displayName(Component.text(" "));
      item.setItemMeta(meta);
    }
    applyHiddenComponentTooltips(item);
    return item;
  }

  private Material getPreviewMaterial(EquipmentSlot slot) {
    return switch (slot) {
      case HEAD -> Material.NETHERITE_HELMET;
      case CHEST -> Material.NETHERITE_CHESTPLATE;
      case LEGS -> Material.NETHERITE_LEGGINGS;
      case FEET -> Material.NETHERITE_BOOTS;
      default -> Material.NETHERITE_CHESTPLATE;
    };
  }

  public String getSlotEnglishName(EquipmentSlot slot) {
    return switch (slot) {
      case HEAD -> "Helmet";
      case CHEST -> "Chestplate";
      case LEGS -> "Leggings";
      case FEET -> "Boots";
      default -> "Armor";
    };
  }

  private void clearPendingPrompts(UUID uuid) {
    pendingRenamePlayers.remove(uuid);
    pendingAddHatPlayers.remove(uuid);
    pendingHatNameEdit.remove(uuid);
    pendingHatLoreEdit.remove(uuid);
  }

  @EventHandler
  public void onGuiClick(InventoryClickEvent event) {
    if (!(event.getInventory().getHolder() instanceof TrimMenuHolder holder)) return;
    event.setCancelled(true);
    if (!(event.getWhoClicked() instanceof Player player)) return;

    int rawSlot = event.getRawSlot();
    if (rawSlot < 0 || rawSlot >= event.getInventory().getSize()) return;
    MenuType menuType = holder.getMenuType();

    if (menuType == MenuType.DECOR_HUB) {
      if (rawSlot == 11) { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f); openMainMenu(player); }
      else if (rawSlot == 13) { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f); openHatMenu(player); }
      else if (rawSlot == 15) { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f); openRenameMenu(player); }
      return;
    }

    if (menuType == MenuType.HAT_STUDIO) {
      if (rawSlot == 45) { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f); openDecorHub(player); return; }
      if (rawSlot == 53 && plugin.hasAdminPerm(player)) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        openHatAdminMenu(player);
        return;
      }
      if (rawSlot == 49) {
        if (!canUseHat(player)) {
          player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
          player.sendMessage(plugin.getMessageWithPerm("no-permission-gui", AntiArmorTrim.HAT_PERM));
          return;
        }
        setSavedHatId(player, null);
        applyAllSavedTrims(player);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1.2f);
        player.sendMessage(plugin.getMessage("hat-cleared"));
        openHatMenu(player);
        return;
      }
      if (rawSlot < 36) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;
        if (!canUseHat(player)) {
          player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
          player.sendMessage(plugin.getMessageWithPerm("no-permission-gui", AntiArmorTrim.HAT_PERM));
          return;
        }
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        String hatId = meta.getPersistentDataContainer().get(getItemAppliedHatKey(), PersistentDataType.STRING);
        if (hatId == null) return;

        setSavedHatId(player, hatId);
        forceRefreshEquippedHat(player);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 1.2f);
        player.sendMessage(plugin.getMessage("hat-selected", Placeholder.component("hat", getHatDisplayName(hatId, plugin.getNexoItem(hatId)))));
        openHatMenu(player);
      }
      return;
    }

    if (menuType == MenuType.HAT_ADMIN) {
      if (!plugin.hasAdminPerm(player)) { player.closeInventory(); return; }
      if (rawSlot == 45) { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f); openHatMenu(player); return; }
      if (rawSlot == 49) {
        player.closeInventory();
        clearPendingPrompts(player.getUniqueId());
        pendingAddHatPlayers.add(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.3f);
        player.sendMessage(plugin.getMessage("hat-admin-prompt-id"));
        return;
      }
      if (rawSlot < 36) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || clicked.getItemMeta() == null) return;
        String hatId = clicked.getItemMeta().getPersistentDataContainer().get(getItemAppliedHatKey(), PersistentDataType.STRING);
        if (hatId == null) return;

        if (plugin.getNexoItem(hatId) == null || event.isRightClick()) {
          deleteHatFromConfig(player, hatId);
          openHatAdminMenu(player);
          return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        openHatEditorMenu(player, hatId);
      }
      return;
    }

    if (menuType == MenuType.HAT_EDITOR) {
      if (!plugin.hasAdminPerm(player)) { player.closeInventory(); return; }
      String hatId = holder.getEditingHatId();
      if (hatId == null) return;

      if (rawSlot == 18) { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f); openHatAdminMenu(player); return; }
      if (rawSlot == 10) {
        if (event.isRightClick()) {
          plugin.getConfig().set("hats.definitions." + hatId + ".display-name", null);
          plugin.saveConfig();
          for (Player online : Bukkit.getOnlinePlayers()) {
            if (hatId.equalsIgnoreCase(getSavedHatId(online))) forceRefreshEquippedHat(online);
          }
          player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1.2f);
          player.sendMessage(plugin.getMessage("hat-admin-name-reset", Placeholder.unparsed("id", hatId)));
          openHatEditorMenu(player, hatId);
          return;
        }
        player.closeInventory();
        clearPendingPrompts(player.getUniqueId());
        pendingHatNameEdit.put(player.getUniqueId(), hatId);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.3f);
        player.sendMessage(plugin.getMessage("hat-admin-prompt-name", Placeholder.unparsed("id", hatId)));
        return;
      }
      if (rawSlot == 12) {
        if (event.isRightClick()) {
          plugin.getConfig().set("hats.definitions." + hatId + ".lore", null);
          plugin.saveConfig();
          for (Player online : Bukkit.getOnlinePlayers()) {
            if (hatId.equalsIgnoreCase(getSavedHatId(online))) forceRefreshEquippedHat(online);
          }
          player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1.2f);
          player.sendMessage(plugin.getMessage("hat-admin-lore-reset", Placeholder.unparsed("id", hatId)));
          openHatEditorMenu(player, hatId);
          return;
        }
        player.closeInventory();
        clearPendingPrompts(player.getUniqueId());
        pendingHatLoreEdit.put(player.getUniqueId(), hatId);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.3f);
        player.sendMessage(plugin.getMessage("hat-admin-prompt-lore", Placeholder.unparsed("id", hatId)));
        return;
      }
      if (rawSlot == 16) {
        deleteHatFromConfig(player, hatId);
        openHatAdminMenu(player);
      }
      return;
    }

    if (menuType == MenuType.RENAME_STUDIO) {
      if (rawSlot == 18) { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f); openDecorHub(player); return; }
      if (rawSlot == 15) {
        if (!canUseRename(player)) {
          player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
          player.sendMessage(plugin.getMessageWithPerm("no-permission-gui", AntiArmorTrim.RENAME_PERM));
          return;
        }
        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (isCustomHatItem(handItem)) {
          handItem = restoreOriginalHelmet(handItem);
          player.getInventory().setItemInMainHand(handItem);
        }
        if (handItem == null || handItem.getType().isAir()) {
          player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
          player.sendMessage(plugin.getMessage("rename-no-item"));
          return;
        }
        if (event.isRightClick()) {
          ItemMeta meta = handItem.getItemMeta();
          if (meta != null) {
            meta.displayName(null);
            handItem.setItemMeta(meta);
            player.getInventory().setItemInMainHand(handItem);
          }
          player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1.2f);
          player.sendMessage(plugin.getMessage("rename-reset"));
          openRenameMenu(player);
          return;
        }
        player.closeInventory();
        clearPendingPrompts(player.getUniqueId());
        pendingRenamePlayers.add(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.3f);
        player.sendMessage(plugin.getMessage("rename-prompt"));
      }
      return;
    }

    if (menuType == MenuType.TRIM_MAIN) {
      if (rawSlot == 18) { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f); openDecorHub(player); return; }
      EquipmentSlot targetSlot = switch (rawSlot) {
        case 10 -> EquipmentSlot.HEAD;
        case 12 -> EquipmentSlot.CHEST;
        case 14 -> EquipmentSlot.LEGS;
        case 16 -> EquipmentSlot.FEET;
        default -> null;
      };
      if (targetSlot != null) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        openSlotMenu(player, targetSlot);
      }
      return;
    }

    EquipmentSlot armorSlot = holder.getSlotType();
    if (rawSlot == 45) { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f); openMainMenu(player); return; }

    boolean isInteractiveSlot = (rawSlot >= 0 && rawSlot < 18) || (rawSlot >= 29 && rawSlot <= 33)
        || (rawSlot >= 38 && rawSlot <= 42) || rawSlot == 49 || rawSlot == 53;
    if (!isInteractiveSlot) return;

    if (!canUseBypass(player)) {
      player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
      player.sendMessage(plugin.getMessageWithPerm("no-permission-gui", AntiArmorTrim.BYPASS_PERM));
      return;
    }

    String currentPattern = getSavedPatternKey(player, armorSlot);
    String currentMaterial = getSavedMaterialKey(player, armorSlot);
    String slotName = getSlotEnglishName(armorSlot);
    String slotIcon = plugin.getItemSpriteTag(getPreviewMaterial(armorSlot));

    if (rawSlot < 18) {
      List<PatternOption> pList = new ArrayList<>(PATTERNS.values());
      if (rawSlot < pList.size()) {
        PatternOption chosenPattern = pList.get(rawSlot);
        if (currentMaterial == null) currentMaterial = "diamond";
        MaterialOption matOpt = MATERIALS.getOrDefault(currentMaterial, MATERIALS.get("diamond"));

        setSavedTrim(player, armorSlot, chosenPattern.key, currentMaterial);
        applySavedTrimToSlot(player, armorSlot);

        player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1.2f);
        player.sendMessage(plugin.getMessage("trim-selected",
            Placeholder.unparsed("slot", slotName), Placeholder.parsed("slot_icon", slotIcon),
            Placeholder.unparsed("pattern", chosenPattern.name), Placeholder.parsed("pattern_icon", plugin.getItemSpriteTag(chosenPattern.icon)),
            Placeholder.unparsed("material", matOpt.name), Placeholder.parsed("material_icon", plugin.getItemSpriteTag(matOpt.icon))));
        if (armorSlot == EquipmentSlot.HEAD && getSavedHatId(player) != null) player.sendMessage(plugin.getMessage("trim-hat-active"));
        openSlotMenu(player, armorSlot);
      }
      return;
    }

    int[] matSlots = {29, 30, 31, 32, 33, 38, 39, 40, 41, 42};
    for (int i = 0; i < matSlots.length; i++) {
      if (rawSlot == matSlots[i]) {
        List<MaterialOption> mList = new ArrayList<>(MATERIALS.values());
        if (i < mList.size()) {
          MaterialOption chosenMaterial = mList.get(i);
          if (currentPattern == null) currentPattern = "silence";
          PatternOption patOpt = PATTERNS.getOrDefault(currentPattern, PATTERNS.get("silence"));

          setSavedTrim(player, armorSlot, currentPattern, chosenMaterial.key);
          applySavedTrimToSlot(player, armorSlot);

          player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1.4f);
          player.sendMessage(plugin.getMessage("trim-selected",
              Placeholder.unparsed("slot", slotName), Placeholder.parsed("slot_icon", slotIcon),
              Placeholder.unparsed("pattern", patOpt.name), Placeholder.parsed("pattern_icon", plugin.getItemSpriteTag(patOpt.icon)),
              Placeholder.unparsed("material", chosenMaterial.name), Placeholder.parsed("material_icon", plugin.getItemSpriteTag(chosenMaterial.icon))));
          if (armorSlot == EquipmentSlot.HEAD && getSavedHatId(player) != null) player.sendMessage(plugin.getMessage("trim-hat-active"));
          openSlotMenu(player, armorSlot);
        }
        return;
      }
    }

    if (rawSlot == 49) {
      setSavedTrim(player, armorSlot, null, null);
      ItemStack worn = player.getInventory().getItem(armorSlot);
      if (worn != null && worn.getItemMeta() instanceof ArmorMeta armorMeta && armorMeta.hasTrim()) {
        armorMeta.setTrim(null);
        worn.setItemMeta(armorMeta);
        player.getInventory().setItem(armorSlot, worn);
      }
      player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1.2f);
      player.sendMessage(plugin.getMessage("trim-cleared", Placeholder.unparsed("slot", slotName), Placeholder.parsed("slot_icon", slotIcon)));
      openSlotMenu(player, armorSlot);
      return;
    }

    if (rawSlot == 53) {
      EquipmentSlot[] allSlots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
      if (currentPattern == null || currentMaterial == null) {
        for (EquipmentSlot s : allSlots) {
          setSavedTrim(player, s, null, null);
          ItemStack worn = player.getInventory().getItem(s);
          if (worn != null && worn.getItemMeta() instanceof ArmorMeta armorMeta && armorMeta.hasTrim()) {
            armorMeta.setTrim(null);
            worn.setItemMeta(armorMeta);
            player.getInventory().setItem(s, worn);
          }
        }
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1.2f);
        player.sendMessage(plugin.getMessage("trim-cleared-all"));
        openMainMenu(player);
        return;
      }

      PatternOption patOpt = PATTERNS.get(currentPattern);
      MaterialOption matOpt = MATERIALS.get(currentMaterial);
      for (EquipmentSlot s : allSlots) setSavedTrim(player, s, currentPattern, currentMaterial);
      applyAllSavedTrims(player);

      player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
      player.sendMessage(plugin.getMessage("trim-applied-all",
          Placeholder.unparsed("pattern", patOpt != null ? patOpt.name : currentPattern),
          Placeholder.parsed("pattern_icon", patOpt != null ? plugin.getItemSpriteTag(patOpt.icon) : ""),
          Placeholder.unparsed("material", matOpt != null ? matOpt.name : currentMaterial),
          Placeholder.parsed("material_icon", matOpt != null ? plugin.getItemSpriteTag(matOpt.icon) : "")));
      openMainMenu(player);
    }
  }

  @EventHandler
  public void onGuiDrag(InventoryDragEvent event) {
    if (event.getInventory().getHolder() instanceof TrimMenuHolder) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onChatInput(AsyncChatEvent event) {
    Player player = event.getPlayer();
    UUID uuid = player.getUniqueId();

    if (pendingAddHatPlayers.remove(uuid)) {
      event.setCancelled(true);
      String rawId = event.signedMessage().message().trim();
      Bukkit.getScheduler().runTask(plugin, () -> {
        if (!player.isOnline()) return;
        if (rawId.equalsIgnoreCase("cancel")) {
          player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.8f);
          player.sendMessage(plugin.getMessage("hat-admin-cancelled"));
          openHatAdminMenu(player);
          return;
        }
        List<String> hats = new ArrayList<>(plugin.getConfig().getStringList("hats.items"));
        if (hats.stream().anyMatch(h -> h.equalsIgnoreCase(rawId))) {
          player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
          player.sendMessage(plugin.getMessage("hat-admin-exists", Placeholder.unparsed("id", rawId)));
          openHatAdminMenu(player);
          return;
        }
        hats.add(rawId);
        plugin.getConfig().set("hats.items", hats);
        plugin.saveConfig();

        if (plugin.getNexoItem(rawId) != null) {
          player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
          player.sendMessage(plugin.getMessage("hat-admin-added", Placeholder.unparsed("id", rawId)));
          openHatEditorMenu(player, rawId);
        } else {
          player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
          player.sendMessage(plugin.getMessage("hat-admin-added-invalid", Placeholder.unparsed("id", rawId)));
          openHatAdminMenu(player);
        }
      });
      return;
    }

    String editingNameHatId = pendingHatNameEdit.remove(uuid);
    if (editingNameHatId != null) {
      event.setCancelled(true);
      String rawInput = event.signedMessage().message().trim();
      Bukkit.getScheduler().runTask(plugin, () -> {
        if (!player.isOnline()) return;
        if (rawInput.equalsIgnoreCase("cancel")) {
          player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.8f);
          player.sendMessage(plugin.getMessage("hat-admin-cancelled"));
          openHatEditorMenu(player, editingNameHatId);
          return;
        }
        plugin.getConfig().set("hats.definitions." + editingNameHatId + ".display-name", rawInput);
        plugin.saveConfig();
        for (Player online : Bukkit.getOnlinePlayers()) {
          if (editingNameHatId.equalsIgnoreCase(getSavedHatId(online))) forceRefreshEquippedHat(online);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.3f);
        player.sendMessage(plugin.getMessage("hat-admin-name-updated", Placeholder.unparsed("id", editingNameHatId), Placeholder.component("name", plugin.parse(rawInput))));
        openHatEditorMenu(player, editingNameHatId);
      });
      return;
    }

    String editingLoreHatId = pendingHatLoreEdit.remove(uuid);
    if (editingLoreHatId != null) {
      event.setCancelled(true);
      String rawInput = event.signedMessage().message().trim();
      Bukkit.getScheduler().runTask(plugin, () -> {
        if (!player.isOnline()) return;
        if (rawInput.equalsIgnoreCase("cancel")) {
          player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.8f);
          player.sendMessage(plugin.getMessage("hat-admin-cancelled"));
          openHatEditorMenu(player, editingLoreHatId);
          return;
        }
        String lorePath = "hats.definitions." + editingLoreHatId + ".lore";
        List<String> newLore;
        if (rawInput.regionMatches(true, 0, "add ", 0, 4)) {
          newLore = new ArrayList<>(plugin.getConfig().getStringList(lorePath));
          newLore.add(rawInput.substring(4).trim());
        } else {
          newLore = new ArrayList<>(Arrays.asList(rawInput.split("\\|")));
          newLore.replaceAll(String::trim);
        }
        plugin.getConfig().set(lorePath, newLore);
        plugin.saveConfig();
        for (Player online : Bukkit.getOnlinePlayers()) {
          if (editingLoreHatId.equalsIgnoreCase(getSavedHatId(online))) forceRefreshEquippedHat(online);
        }
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.2f);
        player.sendMessage(plugin.getMessage("hat-admin-lore-updated", Placeholder.unparsed("id", editingLoreHatId)));
        openHatEditorMenu(player, editingLoreHatId);
      });
      return;
    }

    if (pendingRenamePlayers.remove(uuid)) {
      event.setCancelled(true);
      String rawInput = event.signedMessage().message().trim();
      Bukkit.getScheduler().runTask(plugin, () -> {
        if (!player.isOnline()) return;
        if (rawInput.equalsIgnoreCase("cancel")) {
          player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.8f);
          player.sendMessage(plugin.getMessage("rename-cancelled"));
          openRenameMenu(player);
          return;
        }
        applyItemRename(player, rawInput);
        openRenameMenu(player);
      });
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) { clearPendingPrompts(event.getPlayer().getUniqueId()); }

  public static class TrimMenuHolder implements InventoryHolder {
    private final MenuType menuType;
    private final EquipmentSlot slotType;
    private final String editingHatId;
    private Inventory inventory;

    public TrimMenuHolder(MenuType menuType, EquipmentSlot slotType, String editingHatId) {
      this.menuType = menuType;
      this.slotType = slotType;
      this.editingHatId = editingHatId;
    }

    public MenuType getMenuType() { return menuType; }
    public EquipmentSlot getSlotType() { return slotType; }
    public String getEditingHatId() { return editingHatId; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }
    @Override public @NotNull Inventory getInventory() { return inventory; }
  }

  private record PatternOption(String key, String name, Material icon) {}
  private record MaterialOption(String key, String name, String hexColor, Material icon) {}
}
