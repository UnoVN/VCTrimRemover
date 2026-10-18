package com.server.antitrim;

import com.google.common.collect.HashMultimap;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.ArrayList;
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

    private static final Map<String, PatternOption> PATTERNS = new LinkedHashMap<>();
    private static final Map<String, MaterialOption> MATERIALS = new LinkedHashMap<>();

    // Reflection methods for Paper 1.21.4 - 1.21.11 Data Components (ItemModel & EquippableComponent)
    private static final Method GET_ITEM_MODEL_METHOD;
    private static final Method SET_ITEM_MODEL_METHOD;
    private static final Method HAS_EQUIPPABLE_METHOD;
    private static final Method GET_EQUIPPABLE_METHOD;
    private static final Method SET_EQUIPPABLE_METHOD;
    private static final Method EQUIP_SET_SLOT_METHOD;
    private static final Method EQUIP_GET_MODEL_METHOD;
    private static final Method EQUIP_SET_MODEL_METHOD;
    private static final Method EQUIP_GET_OVERLAY_METHOD;
    private static final Method EQUIP_SET_OVERLAY_METHOD;
    private static final Method GET_CMD_COMPONENT_METHOD;
    private static final Method SET_CMD_COMPONENT_METHOD;

    public enum MenuType {
        DECOR_HUB,
        TRIM_MAIN,
        TRIM_SLOT,
        HAT_STUDIO,
        HAT_ADMIN,
        RENAME_STUDIO
    }

    static {
        Method getItemModel = null, setItemModel = null;
        Method hasEquippable = null, getEquippable = null, setEquippable = null;
        Method equipSetSlot = null, equipGetModel = null, equipSetModel = null;
        Method equipGetOverlay = null, equipSetOverlay = null;
        Method getCmdComp = null, setCmdComp = null;

        try {
            getItemModel = ItemMeta.class.getMethod("getItemModel");
            setItemModel = ItemMeta.class.getMethod("setItemModel", NamespacedKey.class);
        } catch (Throwable ignored) {
        }

        try {
            Class<?> equipClass = Class.forName("org.bukkit.inventory.meta.components.EquippableComponent");
            hasEquippable = ItemMeta.class.getMethod("hasEquippable");
            getEquippable = ItemMeta.class.getMethod("getEquippable");
            setEquippable = ItemMeta.class.getMethod("setEquippable", equipClass);
            equipSetSlot = equipClass.getMethod("setSlot", EquipmentSlot.class);
            equipGetModel = equipClass.getMethod("getModel");
            equipSetModel = equipClass.getMethod("setModel", NamespacedKey.class);
            equipGetOverlay = equipClass.getMethod("getCameraOverlay");
            equipSetOverlay = equipClass.getMethod("setCameraOverlay", NamespacedKey.class);
        } catch (Throwable ignored) {
        }

        try {
            Class<?> cmdClass = Class.forName("org.bukkit.inventory.meta.components.CustomModelDataComponent");
            getCmdComp = ItemMeta.class.getMethod("getCustomModelDataComponent");
            setCmdComp = ItemMeta.class.getMethod("setCustomModelDataComponent", cmdClass);
        } catch (Throwable ignored) {
        }

        GET_ITEM_MODEL_METHOD = getItemModel;
        SET_ITEM_MODEL_METHOD = setItemModel;
        HAS_EQUIPPABLE_METHOD = hasEquippable;
        GET_EQUIPPABLE_METHOD = getEquippable;
        SET_EQUIPPABLE_METHOD = setEquippable;
        EQUIP_SET_SLOT_METHOD = equipSetSlot;
        EQUIP_GET_MODEL_METHOD = equipGetModel;
        EQUIP_SET_MODEL_METHOD = equipSetModel;
        EQUIP_GET_OVERLAY_METHOD = equipGetOverlay;
        EQUIP_SET_OVERLAY_METHOD = equipSetOverlay;
        GET_CMD_COMPONENT_METHOD = getCmdComp;
        SET_CMD_COMPONENT_METHOD = setCmdComp;

        // All 18 Minecraft 1.21 Trim Patterns
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

        // All 10 Trim Materials styled with Catppuccin Mocha Hex Colors
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

    private static void addPattern(String key, String name, Material icon) {
        PATTERNS.put(key, new PatternOption(key, name, icon));
    }

    private static void addMaterial(String key, String name, String hexColor, Material icon) {
        MATERIALS.put(key, new MaterialOption(key, name, hexColor, icon));
    }

    public TrimGuiManager(AntiArmorTrim plugin) {
        this.plugin = plugin;
    }

    public boolean canUseRename(Player player) {
        return player.hasPermission(AntiArmorTrim.RENAME_PERM) || player.hasPermission(AntiArmorTrim.BYPASS_PERM);
    }

    public boolean canUseHat(Player player) {
        return player.hasPermission(AntiArmorTrim.HAT_PERM) || player.hasPermission(AntiArmorTrim.BYPASS_PERM);
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
                    return stack;
                } catch (Throwable ignored) {
                }
            }
        }
        ItemStack fallback = new ItemStack(visualMaterial);
        ItemMeta meta = fallback.getItemMeta();
        if (meta != null) {
            hideAllVanillaTooltips(meta);
            fallback.setItemMeta(meta);
        }
        return fallback;
    }

    private void hideAllVanillaTooltips(ItemMeta meta) {
        meta.setAttributeModifiers(HashMultimap.create());
        meta.addItemFlags(ItemFlag.values());
    }

    // =========================================================================
    // PERSISTENT DATA CONTAINER MANAGEMENT (TRIMS & CUSTOM HATS)
    // =========================================================================
    private NamespacedKey getPatternKey(EquipmentSlot slot) {
        return new NamespacedKey(plugin, "trim_pattern_" + slot.name());
    }

    private NamespacedKey getMaterialKey(EquipmentSlot slot) {
        return new NamespacedKey(plugin, "trim_material_" + slot.name());
    }

    private NamespacedKey getPlayerHatKey() {
        return new NamespacedKey(plugin, "selected_nexo_hat");
    }

    private NamespacedKey getItemAppliedHatKey() {
        return new NamespacedKey(plugin, "applied_nexo_hat");
    }

    public String getSavedPatternKey(Player player, EquipmentSlot slot) {
        return player.getPersistentDataContainer().get(getPatternKey(slot), PersistentDataType.STRING);
    }

    public String getSavedMaterialKey(Player player, EquipmentSlot slot) {
        return player.getPersistentDataContainer().get(getMaterialKey(slot), PersistentDataType.STRING);
    }

    public void setSavedTrim(Player player, EquipmentSlot slot, String patternKey, String materialKey) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (patternKey == null || materialKey == null) {
            pdc.remove(getPatternKey(slot));
            pdc.remove(getMaterialKey(slot));
        } else {
            pdc.set(getPatternKey(slot), PersistentDataType.STRING, patternKey);
            pdc.set(getMaterialKey(slot), PersistentDataType.STRING, materialKey);
        }
    }

    public String getSavedHatId(Player player) {
        return player.getPersistentDataContainer().get(getPlayerHatKey(), PersistentDataType.STRING);
    }

    public void setSavedHatId(Player player, String hatId) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (hatId == null || hatId.isBlank()) {
            pdc.remove(getPlayerHatKey());
        } else {
            pdc.set(getPlayerHatKey(), PersistentDataType.STRING, hatId.trim());
        }
    }

    public boolean isCustomHatItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(getItemAppliedHatKey(), PersistentDataType.STRING);
    }

    public boolean isTargetHelmet(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        return type == Material.DIAMOND_HELMET || type == Material.NETHERITE_HELMET;
    }

    /**
     * Copies Nexo Hat model, CustomModelData, and EquippableComponent onto a Diamond/Netherite Helmet
     * while preserving 100% of its armor stats, enchantments, and durability, and disabling Armor Trim.
     */
    public boolean applyNexoHatToHelmetItem(ItemStack helmetItem, String hatId) {
        if (!isTargetHelmet(helmetItem)) return false;
        ItemStack nexoHat = plugin.getNexoItem(hatId);
        if (nexoHat == null) return false;

        ItemMeta helmetMeta = helmetItem.getItemMeta();
        ItemMeta hatMeta = nexoHat.getItemMeta();
        if (helmetMeta == null || hatMeta == null) return false;

        // 1. Disable Armor Trim on the helmet when a Custom Hat is equipped
        if (helmetMeta instanceof ArmorMeta armorMeta && armorMeta.hasTrim()) {
            armorMeta.setTrim(null);
        }

        // 2. Copy ItemModel & CustomModelData from the Nexo Hat
        try {
            if (GET_ITEM_MODEL_METHOD != null && SET_ITEM_MODEL_METHOD != null) {
                NamespacedKey modelKey = (NamespacedKey) GET_ITEM_MODEL_METHOD.invoke(hatMeta);
                if (modelKey == null) {
                    modelKey = NamespacedKey.fromString("nexo:" + hatId);
                }
                SET_ITEM_MODEL_METHOD.invoke(helmetMeta, modelKey);
            }
            if (GET_CMD_COMPONENT_METHOD != null && SET_CMD_COMPONENT_METHOD != null) {
                Object cmdComp = GET_CMD_COMPONENT_METHOD.invoke(hatMeta);
                SET_CMD_COMPONENT_METHOD.invoke(helmetMeta, cmdComp);
            } else if (hatMeta.hasCustomModelData()) {
                helmetMeta.setCustomModelData(hatMeta.getCustomModelData());
            } else {
                helmetMeta.setCustomModelData(null);
            }
        } catch (Throwable ignored) {
        }

        // 3. Configure EquippableComponent so the 3D Nexo Hat renders on the head instead of vanilla diamond/netherite layer
        try {
            if (GET_EQUIPPABLE_METHOD != null && SET_EQUIPPABLE_METHOD != null && EQUIP_SET_SLOT_METHOD != null) {
                Object helmetEquip = GET_EQUIPPABLE_METHOD.invoke(helmetMeta);
                EQUIP_SET_SLOT_METHOD.invoke(helmetEquip, EquipmentSlot.HEAD);

                NamespacedKey hatEquipModel = null;
                NamespacedKey hatOverlay = null;
                if (HAS_EQUIPPABLE_METHOD != null && (boolean) HAS_EQUIPPABLE_METHOD.invoke(hatMeta)) {
                    Object nexoEquip = GET_EQUIPPABLE_METHOD.invoke(hatMeta);
                    if (EQUIP_GET_MODEL_METHOD != null) {
                        hatEquipModel = (NamespacedKey) EQUIP_GET_MODEL_METHOD.invoke(nexoEquip);
                    }
                    if (EQUIP_GET_OVERLAY_METHOD != null) {
                        hatOverlay = (NamespacedKey) EQUIP_GET_OVERLAY_METHOD.invoke(nexoEquip);
                    }
                }
                if (EQUIP_SET_MODEL_METHOD != null) {
                    EQUIP_SET_MODEL_METHOD.invoke(helmetEquip, hatEquipModel);
                }
                if (EQUIP_SET_OVERLAY_METHOD != null) {
                    EQUIP_SET_OVERLAY_METHOD.invoke(helmetEquip, hatOverlay);
                }
                SET_EQUIPPABLE_METHOD.invoke(helmetMeta, helmetEquip);
            }
        } catch (Throwable ignored) {
        }

        // 4. Mark the helmet with the applied Nexo Hat ID
        helmetMeta.getPersistentDataContainer().set(getItemAppliedHatKey(), PersistentDataType.STRING, hatId);
        helmetItem.setItemMeta(helmetMeta);
        return true;
    }

    /**
     * Restores a modified Diamond/Netherite Helmet back to its original vanilla appearance.
     */
    public boolean stripCustomHatFromItem(ItemStack item) {
        if (!isCustomHatItem(item)) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        meta.getPersistentDataContainer().remove(getItemAppliedHatKey());
        try {
            if (SET_ITEM_MODEL_METHOD != null) {
                SET_ITEM_MODEL_METHOD.invoke(meta, new Object[]{null});
            }
            if (SET_EQUIPPABLE_METHOD != null) {
                SET_EQUIPPABLE_METHOD.invoke(meta, new Object[]{null});
            }
        } catch (Throwable ignored) {
        }
        meta.setCustomModelData(null);
        item.setItemMeta(meta);
        return true;
    }

    public ArmorTrim buildArmorTrim(String patternKey, String materialKey) {
        if (patternKey == null || materialKey == null) return null;
        TrimPattern pattern = Registry.TRIM_PATTERN.get(NamespacedKey.minecraft(patternKey));
        TrimMaterial material = Registry.TRIM_MATERIAL.get(NamespacedKey.minecraft(materialKey));
        if (pattern != null && material != null) {
            return new ArmorTrim(material, pattern);
        }
        return null;
    }

    /**
     * Synchronizes the player's HEAD slot (Custom Hat takes priority over Trim) and applies saved Trims.
     */
    public void applySavedTrimToSlot(Player player, EquipmentSlot slot) {
        // For HEAD slot: if player has a Custom Hat active, apply the Custom Hat and DO NOT apply Trim
        if (slot == EquipmentSlot.HEAD) {
            String savedHat = getSavedHatId(player);
            if (canUseHat(player) && savedHat != null) {
                ItemStack headItem = player.getInventory().getHelmet();
                if (isTargetHelmet(headItem)) {
                    if (applyNexoHatToHelmetItem(headItem, savedHat)) {
                        player.getInventory().setHelmet(headItem);
                        return;
                    }
                }
                return;
            } else {
                // No active hat selected -> strip any leftover hat visual on the equipped helmet
                ItemStack headItem = player.getInventory().getHelmet();
                if (isCustomHatItem(headItem)) {
                    stripCustomHatFromItem(headItem);
                    player.getInventory().setHelmet(headItem);
                }
            }
        }

        if (!player.hasPermission(AntiArmorTrim.BYPASS_PERM)) return;

        ItemStack item = player.getInventory().getItem(slot);
        if (item == null || item.getType().isAir()) return;
        if (!(item.getItemMeta() instanceof ArmorMeta armorMeta)) return;

        String patternKey = getSavedPatternKey(player, slot);
        String materialKey = getSavedMaterialKey(player, slot);
        ArmorTrim desiredTrim = buildArmorTrim(patternKey, materialKey);

        if (desiredTrim == null) return;

        ArmorTrim currentTrim = armorMeta.getTrim();
        if (currentTrim == null || !currentTrim.equals(desiredTrim)) {
            armorMeta.setTrim(desiredTrim);
            item.setItemMeta(armorMeta);
            player.getInventory().setItem(slot, item);
        }
    }

    public void applyAllSavedTrims(Player player) {
        // Strip custom hat visuals from any unequipped helmets in the main inventory (0..35)
        for (int i = 0; i < 36; i++) {
            ItemStack invItem = player.getInventory().getItem(i);
            if (isCustomHatItem(invItem)) {
                stripCustomHatFromItem(invItem);
                player.getInventory().setItem(i, invItem);
            }
        }

        applySavedTrimToSlot(player, EquipmentSlot.HEAD);
        applySavedTrimToSlot(player, EquipmentSlot.CHEST);
        applySavedTrimToSlot(player, EquipmentSlot.LEGS);
        applySavedTrimToSlot(player, EquipmentSlot.FEET);
    }

    // =========================================================================
    // 1. DECORATION HUB MENU (/decor) - 3 BUTTONS
    // =========================================================================
    public void openDecorHub(Player player) {
        String rawTitle = plugin.getConfig().getString("gui.decor-title", "<#cba6f7><bold>Decoration Studio</bold>");
        Component title = plugin.parse(rawTitle);

        TrimMenuHolder holder = new TrimMenuHolder(MenuType.DECOR_HUB, null);
        Inventory inv = Bukkit.createInventory(holder, 27, title);
        holder.setInventory(inv);

        ItemStack border = createFiller(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, border);
        }

        // Slot 11: Armor Trim Studio Button
        ItemStack trimBtn = createCleanIconStack(Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE);
        ItemMeta trimMeta = trimBtn.getItemMeta();
        if (trimMeta != null) {
            String sprite = plugin.getItemSpriteTag(Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE);
            trimMeta.displayName(plugin.parse(sprite + " <#cba6f7><bold>Armor Trim Studio</bold>"));

            List<Component> lore = new ArrayList<>();
            lore.add(plugin.parse("<#a6adc8>Customize automatic armor trims"));
            lore.add(plugin.parse("<#a6adc8>and materials for your gear."));
            appendLockedLoreIfNeeded(player, lore, AntiArmorTrim.BYPASS_PERM);
            trimMeta.lore(lore);
            trimBtn.setItemMeta(trimMeta);
        }
        inv.setItem(11, trimBtn);

        // Slot 13: Custom Hat Studio Button (/hat)
        ItemStack hatBtn = createCleanIconStack(Material.NETHERITE_HELMET);
        ItemMeta hatMeta = hatBtn.getItemMeta();
        if (hatMeta != null) {
            String sprite = plugin.getItemSpriteTag(Material.NETHERITE_HELMET);
            hatMeta.displayName(plugin.parse(sprite + " <#f9e2af><bold>Custom Hat Studio</bold>"));

            List<Component> lore = new ArrayList<>();
            lore.add(plugin.parse("<#a6adc8>Transform your Diamond or Netherite"));
            lore.add(plugin.parse("<#a6adc8>helmet into a custom Nexo hat."));
            appendLockedLoreIfNeeded(player, lore, AntiArmorTrim.HAT_PERM);
            hatMeta.lore(lore);
            hatBtn.setItemMeta(hatMeta);
        }
        inv.setItem(13, hatBtn);

        // Slot 15: Item Rename Studio Button (/rename)
        ItemStack renameBtn = createCleanIconStack(Material.NAME_TAG);
        ItemMeta renameMeta = renameBtn.getItemMeta();
        if (renameMeta != null) {
            String sprite = plugin.getItemSpriteTag(Material.NAME_TAG);
            renameMeta.displayName(plugin.parse(sprite + " <#74c7ec><bold>Item Rename Studio</bold>"));

            List<Component> lore = new ArrayList<>();
            lore.add(plugin.parse("<#a6adc8>Rename the item in your main hand"));
            lore.add(plugin.parse("<#a6adc8>using Minecraft color & format codes."));
            appendLockedLoreIfNeeded(player, lore, AntiArmorTrim.RENAME_PERM);
            renameMeta.lore(lore);
            renameBtn.setItemMeta(renameMeta);
        }
        inv.setItem(15, renameBtn);

        player.openInventory(inv);
    }

    // =========================================================================
    // 2. CUSTOM HAT STUDIO MENU (/hat) & ADMIN HAT MANAGER (/hat admin)
    // =========================================================================
    public void openHatMenu(Player player) {
        String rawTitle = plugin.getConfig().getString("gui.hat-title",
                "<white><sprite:items:item/netherite_helmet></white> <#cba6f7><bold>Custom Hat Studio</bold>");
        Component title = plugin.parse(rawTitle);

        TrimMenuHolder holder = new TrimMenuHolder(MenuType.HAT_STUDIO, null);
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        ItemStack filler = createFiller(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 36; i < 54; i++) {
            inv.setItem(i, filler);
        }

        List<String> hatIds = plugin.getConfig().getStringList("hats.items");
        String currentHat = getSavedHatId(player);

        int slotIndex = 0;
        for (String hatId : hatIds) {
            if (slotIndex >= 36) break;
            ItemStack nexoStack = plugin.getNexoItem(hatId);
            if (nexoStack == null) {
                continue; // Skip invalid hats in player view (visible in Admin GUI for removal)
            }

            boolean selected = hatId.equalsIgnoreCase(currentHat);
            ItemStack icon = nexoStack.clone();
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                hideAllVanillaTooltips(meta);
                Component baseName = meta.hasDisplayName() && meta.displayName() != null
                        ? meta.displayName()
                        : plugin.parse("<#89dceb>" + hatId);
                String prefixSprite = plugin.getItemSpriteTag(Material.NETHERITE_HELMET);
                meta.displayName(plugin.parse(prefixSprite + " ").append(baseName));

                if (selected) {
                    meta.setEnchantmentGlintOverride(true);
                }

                List<Component> lore = new ArrayList<>();
                lore.add(plugin.parse("<#a6adc8>Replaces Diamond & Netherite Helmet"));
                lore.add(plugin.parse("<#a6adc8>appearance while keeping all stats."));
                appendSelectionOrLockedLore(player, lore, AntiArmorTrim.HAT_PERM, selected);
                meta.lore(lore);
                meta.getPersistentDataContainer().set(getItemAppliedHatKey(), PersistentDataType.STRING, hatId);
                icon.setItemMeta(meta);
            }
            inv.setItem(slotIndex++, icon);
        }

        // Slot 45: Back to Decoration Hub
        inv.setItem(45, createBackButton("<#f9e2af><bold>Back</bold>"));

        // Slot 49: Reset & Clear Hat Button
        ItemStack clearBtn = createCleanIconStack(Material.BARRIER);
        ItemMeta clearMeta = clearBtn.getItemMeta();
        if (clearMeta != null) {
            String sprite = plugin.getItemSpriteTag(Material.BARRIER);
            clearMeta.displayName(plugin.parse(sprite + " <#f38ba8><bold>Reset & Unequip Hat</bold>"));
            List<Component> clearLore = new ArrayList<>();
            clearLore.add(plugin.parse("<#a6adc8>Disable custom hat and restore"));
            clearLore.add(plugin.parse("<#a6adc8>your original helmet appearance."));
            appendLockedLoreIfNeeded(player, clearLore, AntiArmorTrim.HAT_PERM);
            clearMeta.lore(clearLore);
            clearBtn.setItemMeta(clearMeta);
        }
        inv.setItem(49, clearBtn);

        // Slot 53: Admin Hat Manager Button (Only visible to Admins)
        if (player.hasPermission(AntiArmorTrim.ADMIN_PERM)) {
            ItemStack adminBtn = createCleanIconStack(Material.COMMAND_BLOCK);
            ItemMeta adminMeta = adminBtn.getItemMeta();
            if (adminMeta != null) {
                String sprite = plugin.getItemSpriteTag(Material.COMMAND_BLOCK);
                adminMeta.displayName(plugin.parse(sprite + " <#f38ba8><bold>Admin Hat Manager</bold>"));
                List<Component> adminLore = new ArrayList<>();
                adminLore.add(plugin.parse("<#a6adc8>Click to add Nexo hat IDs or"));
                adminLore.add(plugin.parse("<#a6adc8>delete broken hats from config."));
                adminMeta.lore(adminLore);
                adminBtn.setItemMeta(adminMeta);
            }
            inv.setItem(53, adminBtn);
        }

        player.openInventory(inv);
    }

    public void openHatAdminMenu(Player player) {
        String rawTitle = plugin.getConfig().getString("gui.hat-admin-title",
                "<white><sprite:items:item/command_block></white> <#f38ba8><bold>Admin Hat Manager</bold>");
        Component title = plugin.parse(rawTitle);

        TrimMenuHolder holder = new TrimMenuHolder(MenuType.HAT_ADMIN, null);
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        ItemStack filler = createFiller(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 36; i < 54; i++) {
            inv.setItem(i, filler);
        }

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
                    String sprite = plugin.getItemSpriteTag(Material.EMERALD);
                    meta.displayName(plugin.parse(sprite + " <#a6e3a1><bold>" + hatId + "</bold>"));
                    List<Component> lore = new ArrayList<>();
                    lore.add(plugin.parse("<#a6adc8>Status: <#a6e3a1>Valid Nexo Item"));
                    lore.add(Component.empty());
                    lore.add(plugin.parse("<#f38ba8>▸ Click to delete this hat"));
                    meta.lore(lore);
                    meta.getPersistentDataContainer().set(getItemAppliedHatKey(), PersistentDataType.STRING, hatId);
                    icon.setItemMeta(meta);
                }
            } else {
                // Broken / Invalid Nexo Item ID -> Display Barrier so Admin can easily delete it
                icon = createCleanIconStack(Material.BARRIER);
                ItemMeta meta = icon.getItemMeta();
                if (meta != null) {
                    String sprite = plugin.getItemSpriteTag(Material.BARRIER);
                    meta.displayName(plugin.parse(sprite + " <#f38ba8><bold>Broken ID: " + hatId + "</bold>"));
                    List<Component> lore = new ArrayList<>();
                    lore.add(plugin.parse("<#a6adc8>Status: <#f38ba8>Not found in Nexo!"));
                    lore.add(Component.empty());
                    lore.add(plugin.parse("<#f38ba8>▸ Click to delete this broken entry"));
                    meta.lore(lore);
                    meta.getPersistentDataContainer().set(getItemAppliedHatKey(), PersistentDataType.STRING, hatId);
                    icon.setItemMeta(meta);
                }
            }
            inv.setItem(slotIndex++, icon);
        }

        // Slot 45: Back to Player Hat Studio
        inv.setItem(45, createBackButton("<#f9e2af><bold>Back to Hat Studio</bold>"));

        // Slot 49: Add New Hat Button
        ItemStack addBtn = createCleanIconStack(Material.ANVIL);
        ItemMeta addMeta = addBtn.getItemMeta();
        if (addMeta != null) {
            String sprite = plugin.getItemSpriteTag(Material.ANVIL);
            addMeta.displayName(plugin.parse(sprite + " <#a6e3a1><bold>Add New Nexo Hat</bold>"));
            List<Component> addLore = new ArrayList<>();
            addLore.add(plugin.parse("<#a6adc8>Click to type a Nexo Item ID"));
            addLore.add(plugin.parse("<#a6adc8>in chat and add it to this menu."));
            addMeta.lore(addLore);
            addBtn.setItemMeta(addMeta);
        }
        inv.setItem(49, addBtn);

        player.openInventory(inv);
    }

    // =========================================================================
    // 3. ITEM RENAME STUDIO MENU (/rename)
    // =========================================================================
    public void openRenameMenu(Player player) {
        String rawTitle = plugin.getConfig().getString("gui.rename-title",
                "<white><sprite:items:item/name_tag></white> <#cba6f7><bold>Item Rename Studio</bold>");
        Component title = plugin.parse(rawTitle);

        TrimMenuHolder holder = new TrimMenuHolder(MenuType.RENAME_STUDIO, null);
        Inventory inv = Bukkit.createInventory(holder, 27, title);
        holder.setInventory(inv);

        ItemStack border = createFiller(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, border);
        }

        // Slot 11: Standard Vanilla Minecraft Color & Format Guide
        ItemStack guideItem = createCleanIconStack(Material.KNOWLEDGE_BOOK);
        ItemMeta guideMeta = guideItem.getItemMeta();
        if (guideMeta != null) {
            String sprite = plugin.getItemSpriteTag(Material.KNOWLEDGE_BOOK);
            guideMeta.displayName(plugin.parseLiteral(sprite + " <#f9e2af><bold>Minecraft Format Guide</bold>"));

            List<Component> lore = new ArrayList<>();
            lore.add(plugin.parseLiteral("<#cba6f7>Color Codes:"));
            lore.add(plugin.parseLiteral("<white>&0 <dark_gray>▸ <black>Black     <white>&1 <dark_gray>▸ <dark_blue>Dark Blue"));
            lore.add(plugin.parseLiteral("<white>&2 <dark_gray>▸ <dark_green>Green     <white>&3 <dark_gray>▸ <dark_aqua>Dark Aqua"));
            lore.add(plugin.parseLiteral("<white>&4 <dark_gray>▸ <dark_red>Dark Red  <white>&5 <dark_gray>▸ <dark_purple>Dark Purple"));
            lore.add(plugin.parseLiteral("<white>&6 <dark_gray>▸ <gold>Gold      <white>&7 <dark_gray>▸ <gray>Gray"));
            lore.add(plugin.parseLiteral("<white>&8 <dark_gray>▸ <dark_gray>Dark Gray <white>&9 <dark_gray>▸ <blue>Blue"));
            lore.add(plugin.parseLiteral("<white>&a <dark_gray>▸ <green>Green     <white>&b <dark_gray>▸ <aqua>Aqua"));
            lore.add(plugin.parseLiteral("<white>&c <dark_gray>▸ <red>Red       <white>&d <dark_gray>▸ <light_purple>Light Purple"));
            lore.add(plugin.parseLiteral("<white>&e <dark_gray>▸ <yellow>Yellow    <white>&f <dark_gray>▸ <white>White"));
            lore.add(Component.empty());
            lore.add(plugin.parseLiteral("<#cba6f7>Format Codes:"));
            lore.add(plugin.parseLiteral("<white>&k <dark_gray>▸ <white><obfuscated>abc</obfuscated> <gray>(Magic)   <white>&l <dark_gray>▸ <white><bold>Bold</bold>"));
            lore.add(plugin.parseLiteral("<white>&m <dark_gray>▸ <white><strikethrough>Strike</strikethrough>        <white>&n <dark_gray>▸ <white><underlined>Underline</underlined>"));
            lore.add(plugin.parseLiteral("<white>&o <dark_gray>▸ <white><italic>Italic</italic>        <white>&r <dark_gray>▸ <gray>Reset"));
            guideMeta.lore(lore);
            guideItem.setItemMeta(guideMeta);
        }
        inv.setItem(11, guideItem);

        // Slot 13: Held Item Live Preview
        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem == null || handItem.getType().isAir()) {
            ItemStack emptyIcon = createCleanIconStack(Material.BARRIER);
            ItemMeta emptyMeta = emptyIcon.getItemMeta();
            if (emptyMeta != null) {
                String sprite = plugin.getItemSpriteTag(Material.BARRIER);
                emptyMeta.displayName(plugin.parse(sprite + " <#f38ba8><bold>No Item in Main Hand</bold>"));
                List<Component> lore = new ArrayList<>();
                lore.add(plugin.parse("<#a6adc8>Hold an item in your main hand"));
                lore.add(plugin.parse("<#a6adc8>to preview and rename it."));
                emptyMeta.lore(lore);
                emptyIcon.setItemMeta(emptyMeta);
            }
            inv.setItem(13, emptyIcon);
        } else {
            ItemStack preview = handItem.clone();
            ItemMeta previewMeta = preview.getItemMeta();
            if (previewMeta != null) {
                hideAllVanillaTooltips(previewMeta);
                preview.setItemMeta(previewMeta);
            }
            inv.setItem(13, preview);
        }

        // Slot 15: Click to Rename Item Button
        ItemStack renameBtn = createCleanIconStack(Material.NAME_TAG);
        ItemMeta renameMeta = renameBtn.getItemMeta();
        if (renameMeta != null) {
            String sprite = plugin.getItemSpriteTag(Material.NAME_TAG);
            renameMeta.displayName(plugin.parse(sprite + " <#a6e3a1><bold>Rename Held Item</bold>"));

            List<Component> lore = new ArrayList<>();
            lore.add(plugin.parse("<#89dceb>Left-Click: <#cdd6f4>Enter new name in chat"));
            lore.add(plugin.parse("<#fab387>Right-Click: <#cdd6f4>Reset to default name"));
            appendLockedLoreIfNeeded(player, lore, AntiArmorTrim.RENAME_PERM);
            renameMeta.lore(lore);
            renameBtn.setItemMeta(renameMeta);
        }
        inv.setItem(15, renameBtn);

        // Slot 18: Back to Decoration Hub
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
            player.sendMessage(plugin.getMessage("rename-too-long",
                    Placeholder.unparsed("max", String.valueOf(maxLength))));
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        meta.displayName(parsedName);
        item.setItemMeta(meta);
        player.getInventory().setItemInMainHand(item);

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.3f);
        player.sendMessage(plugin.getMessage("rename-success",
                Placeholder.component("name", parsedName)));
    }

    // =========================================================================
    // 4. ARMOR TRIM MAIN MENU (/trim) & SLOT SUB-MENU
    // =========================================================================
    public void openMainMenu(Player player) {
        String rawTitle = plugin.getConfig().getString("gui.main-title", "<#cba6f7><bold>Armor Trim Studio</bold>");
        Component title = plugin.parse(rawTitle);

        TrimMenuHolder holder = new TrimMenuHolder(MenuType.TRIM_MAIN, null);
        Inventory inv = Bukkit.createInventory(holder, 27, title);
        holder.setInventory(inv);

        ItemStack border = createFiller(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, border);
        }

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

        String slotName = getSlotEnglishName(slot);
        String slotSprite = plugin.getItemSpriteTag(iconMat);
        meta.displayName(plugin.parse(slotSprite + " <#74c7ec><bold>" + slotName + " Trim</bold>"));

        String patternKey = getSavedPatternKey(player, slot);
        String materialKey = getSavedMaterialKey(player, slot);

        PatternOption pOpt = patternKey != null ? PATTERNS.get(patternKey) : null;
        MaterialOption mOpt = materialKey != null ? MATERIALS.get(materialKey) : null;

        List<Component> lore = new ArrayList<>();
        String patternDisplay = pOpt != null
                ? plugin.getItemSpriteTag(pOpt.icon) + " <#89dceb>" + pOpt.name
                : "<#6c7086>None";
        String materialDisplay = mOpt != null
                ? plugin.getItemSpriteTag(mOpt.icon) + " <" + mOpt.hexColor + ">" + mOpt.name
                : "<#6c7086>None";

        lore.add(plugin.parse("<#a6adc8>Pattern: " + patternDisplay));
        lore.add(plugin.parse("<#a6adc8>Material: " + materialDisplay));

        if (slot == EquipmentSlot.HEAD && getSavedHatId(player) != null) {
            lore.add(plugin.parse("<#fab387>Custom Hat Active (Trim Paused)"));
        }

        if (meta instanceof ArmorMeta armorMeta && pOpt != null && mOpt != null) {
            ArmorTrim previewTrim = buildArmorTrim(patternKey, materialKey);
            if (previewTrim != null) {
                armorMeta.setTrim(previewTrim);
            }
        }

        boolean configured = (pOpt != null && mOpt != null);
        appendSelectionOrLockedLore(player, lore, AntiArmorTrim.BYPASS_PERM, configured);

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public void openSlotMenu(Player player, EquipmentSlot slot) {
        String slotName = getSlotEnglishName(slot);
        Material previewMat = getPreviewMaterial(slot);
        String slotSprite = plugin.getItemSpriteTag(previewMat);

        String rawTitle = plugin.getConfig().getString("gui.slot-title", "<slot_icon> <#cba6f7><bold>Trim Editor:</bold> <#74c7ec><slot>");
        Component title = plugin.parse(rawTitle,
                Placeholder.unparsed("slot", slotName),
                Placeholder.parsed("slot_icon", slotSprite));

        TrimMenuHolder holder = new TrimMenuHolder(MenuType.TRIM_SLOT, slot);
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        ItemStack filler = createFiller(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 18; i < 54; i++) {
            inv.setItem(i, filler);
        }

        String currentPattern = getSavedPatternKey(player, slot);
        String currentMaterial = getSavedMaterialKey(player, slot);

        // 1. Patterns (Slots 0 -> 17)
        int pIndex = 0;
        for (PatternOption pOpt : PATTERNS.values()) {
            boolean selected = pOpt.key.equalsIgnoreCase(currentPattern);
            inv.setItem(pIndex++, createPatternIcon(player, pOpt, selected));
        }

        // 2. Live Armor Preview Icon (Slot 22)
        inv.setItem(22, createMainSlotIcon(player, slot, previewMat));

        // 3. Materials (Slots 29->33 and 38->42)
        int[] matSlots = {29, 30, 31, 32, 33, 38, 39, 40, 41, 42};
        int mIndex = 0;
        for (MaterialOption mOpt : MATERIALS.values()) {
            if (mIndex >= matSlots.length) break;
            boolean selected = mOpt.key.equalsIgnoreCase(currentMaterial);
            inv.setItem(matSlots[mIndex++], createMaterialIcon(player, mOpt, selected));
        }

        // 4. Navigation & Action Buttons
        inv.setItem(45, createBackButton("<#f9e2af><bold>Back</bold>"));

        // Clear / Disable Auto-Trim Button (Slot 49)
        ItemStack clearBtn = createCleanIconStack(Material.BARRIER);
        ItemMeta clearMeta = clearBtn.getItemMeta();
        if (clearMeta != null) {
            String sprite = plugin.getItemSpriteTag(Material.BARRIER);
            clearMeta.displayName(plugin.parse(sprite + " <#f38ba8><bold>Reset & Clear Trim</bold>"));
            List<Component> clearLore = new ArrayList<>();
            clearLore.add(plugin.parse("<#a6adc8>Disable auto-trim and clear active trim."));
            appendLockedLoreIfNeeded(player, clearLore, AntiArmorTrim.BYPASS_PERM);
            clearMeta.lore(clearLore);
            clearBtn.setItemMeta(clearMeta);
        }
        inv.setItem(49, clearBtn);

        // Apply to All 4 Pieces Button (Slot 53)
        ItemStack applyAllBtn = createCleanIconStack(Material.NETHER_STAR);
        ItemMeta allMeta = applyAllBtn.getItemMeta();
        if (allMeta != null) {
            String sprite = plugin.getItemSpriteTag(Material.NETHER_STAR);
            allMeta.displayName(plugin.parse(sprite + " <#cba6f7><bold>Sync to All 4 Pieces</bold>"));
            List<Component> allLore = new ArrayList<>();
            allLore.add(plugin.parse("<#a6adc8>Sync this slot's state (Trim or Reset)"));
            allLore.add(plugin.parse("<#a6adc8>to Helmet, Chestplate, Leggings & Boots."));
            appendLockedLoreIfNeeded(player, allLore, AntiArmorTrim.BYPASS_PERM);
            allMeta.lore(allLore);
            applyAllBtn.setItemMeta(allMeta);
        }
        inv.setItem(53, applyAllBtn);

        player.openInventory(inv);
    }

    private ItemStack createBackButton(String titleMini) {
        ItemStack backBtn = createCleanIconStack(Material.ARROW);
        ItemMeta backMeta = backBtn.getItemMeta();
        if (backMeta != null) {
            String sprite = plugin.getItemSpriteTag(Material.ARROW);
            backMeta.displayName(plugin.parse(sprite + " " + titleMini));
            backBtn.setItemMeta(backMeta);
        }
        return backBtn;
    }

    private ItemStack createPatternIcon(Player player, PatternOption opt, boolean selected) {
        ItemStack item = createCleanIconStack(opt.icon);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String sprite = plugin.getItemSpriteTag(opt.icon);
        String nameColor = selected ? "<#a6e3a1><bold>" : "<#89dceb>";
        meta.displayName(plugin.parse(sprite + " " + nameColor + opt.name + " Trim"));

        if (selected) {
            meta.setEnchantmentGlintOverride(true);
        }

        List<Component> lore = new ArrayList<>();
        appendSelectionOrLockedLore(player, lore, AntiArmorTrim.BYPASS_PERM, selected);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMaterialIcon(Player player, MaterialOption opt, boolean selected) {
        ItemStack item = createCleanIconStack(opt.icon);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String sprite = plugin.getItemSpriteTag(opt.icon);
        String nameColor = selected ? "<#a6e3a1><bold>" : "<" + opt.hexColor + ">";
        meta.displayName(plugin.parse(sprite + " " + nameColor + opt.name + " Material"));

        if (selected) {
            meta.setEnchantmentGlintOverride(true);
        }

        List<Component> lore = new ArrayList<>();
        appendSelectionOrLockedLore(player, lore, AntiArmorTrim.BYPASS_PERM, selected);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void appendSelectionOrLockedLore(Player player, List<Component> lore, String requiredPerm, boolean selected) {
        boolean hasPerm = switch (requiredPerm) {
            case AntiArmorTrim.RENAME_PERM -> canUseRename(player);
            case AntiArmorTrim.HAT_PERM -> canUseHat(player);
            default -> player.hasPermission(requiredPerm);
        };

        if (hasPerm) {
            String statusRaw = selected
                    ? plugin.getConfig().getString("gui.selected-status", "<#a6e3a1>● Selected")
                    : plugin.getConfig().getString("gui.unselected-status", "<#6c7086>○ Unselected");
            lore.add(plugin.parse(statusRaw));
        } else {
            List<String> rawLines = plugin.getConfig().getStringList("gui.no-permission-lore");
            for (String line : rawLines) {
                lore.add(plugin.parseWithPerm(line, requiredPerm));
            }
        }
    }

    private void appendLockedLoreIfNeeded(Player player, List<Component> lore, String requiredPerm) {
        boolean hasPerm = switch (requiredPerm) {
            case AntiArmorTrim.RENAME_PERM -> canUseRename(player);
            case AntiArmorTrim.HAT_PERM -> canUseHat(player);
            default -> player.hasPermission(requiredPerm);
        };

        if (!hasPerm) {
            List<String> rawLines = plugin.getConfig().getStringList("gui.no-permission-lore");
            for (String line : rawLines) {
                lore.add(plugin.parseWithPerm(line, requiredPerm));
            }
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

    // =========================================================================
    // GUI INTERACTION & CHAT LISTENERS
    // =========================================================================
    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TrimMenuHolder holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getInventory().getSize()) return;

        MenuType menuType = holder.getMenuType();

        // 1. DECORATION HUB (/decor)
        if (menuType == MenuType.DECOR_HUB) {
            if (rawSlot == 11) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                openMainMenu(player);
            } else if (rawSlot == 13) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                openHatMenu(player);
            } else if (rawSlot == 15) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                openRenameMenu(player);
            }
            return;
        }

        // 2. CUSTOM HAT STUDIO (/hat)
        if (menuType == MenuType.HAT_STUDIO) {
            if (rawSlot == 45) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                openDecorHub(player);
                return;
            }

            if (rawSlot == 53 && player.hasPermission(AntiArmorTrim.ADMIN_PERM)) {
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
                applySavedTrimToSlot(player, EquipmentSlot.HEAD);

                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE.value(), 1f, 1.2f);
                player.sendMessage(plugin.getMessage("hat-selected", Placeholder.unparsed("hat", hatId)));
                openHatMenu(player);
            }
            return;
        }

        // 3. ADMIN HAT MANAGER (/hat admin)
        if (menuType == MenuType.HAT_ADMIN) {
            if (!player.hasPermission(AntiArmorTrim.ADMIN_PERM)) {
                player.closeInventory();
                return;
            }

            if (rawSlot == 45) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                openHatMenu(player);
                return;
            }

            // Add New Hat Button (Slot 49)
            if (rawSlot == 49) {
                player.closeInventory();
                pendingRenamePlayers.remove(player.getUniqueId());
                pendingAddHatPlayers.add(player.getUniqueId());
                player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.3f);
                player.sendMessage(plugin.getMessage("hat-admin-prompt"));
                return;
            }

            // Click existing or broken hat to delete it (Slots 0 -> 35)
            if (rawSlot < 36) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked == null || clicked.getType().isAir()) return;
                ItemMeta meta = clicked.getItemMeta();
                if (meta == null) return;

                String hatId = meta.getPersistentDataContainer().get(getItemAppliedHatKey(), PersistentDataType.STRING);
                if (hatId == null) return;

                List<String> hats = new ArrayList<>(plugin.getConfig().getStringList("hats.items"));
                if (hats.removeIf(h -> h.equalsIgnoreCase(hatId))) {
                    plugin.getConfig().set("hats.items", hats);
                    plugin.saveConfig();
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1.2f);
                    player.sendMessage(plugin.getMessage("hat-admin-removed", Placeholder.unparsed("id", hatId)));
                    openHatAdminMenu(player);
                }
            }
            return;
        }

        // 4. ITEM RENAME STUDIO (/rename)
        if (menuType == MenuType.RENAME_STUDIO) {
            if (rawSlot == 18) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                openDecorHub(player);
                return;
            }

            if (rawSlot == 15) {
                if (!canUseRename(player)) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    player.sendMessage(plugin.getMessageWithPerm("no-permission-gui", AntiArmorTrim.RENAME_PERM));
                    return;
                }

                ItemStack handItem = player.getInventory().getItemInMainHand();
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
                pendingAddHatPlayers.remove(player.getUniqueId());
                pendingRenamePlayers.add(player.getUniqueId());
                player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.3f);
                player.sendMessage(plugin.getMessage("rename-prompt"));
            }
            return;
        }

        // 5. ARMOR TRIM MAIN MENU (/trim)
        if (menuType == MenuType.TRIM_MAIN) {
            if (rawSlot == 18) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                openDecorHub(player);
                return;
            }

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

        // 6. ARMOR TRIM SLOT SUB-MENU
        EquipmentSlot armorSlot = holder.getSlotType();
        if (rawSlot == 45) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            openMainMenu(player);
            return;
        }

        boolean isInteractiveSlot = (rawSlot >= 0 && rawSlot < 18)
                || (rawSlot >= 29 && rawSlot <= 33)
                || (rawSlot >= 38 && rawSlot <= 42)
                || rawSlot == 49 || rawSlot == 53;

        if (!isInteractiveSlot) return;

        if (!player.hasPermission(AntiArmorTrim.BYPASS_PERM)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            player.sendMessage(plugin.getMessageWithPerm("no-permission-gui", AntiArmorTrim.BYPASS_PERM));
            return;
        }

        String currentPattern = getSavedPatternKey(player, armorSlot);
        String currentMaterial = getSavedMaterialKey(player, armorSlot);
        String slotName = getSlotEnglishName(armorSlot);
        String slotIcon = plugin.getItemSpriteTag(getPreviewMaterial(armorSlot));

        // Select Pattern (Slots 0 -> 17)
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
                        Placeholder.unparsed("slot", slotName),
                        Placeholder.parsed("slot_icon", slotIcon),
                        Placeholder.unparsed("pattern", chosenPattern.name),
                        Placeholder.parsed("pattern_icon", plugin.getItemSpriteTag(chosenPattern.icon)),
                        Placeholder.unparsed("material", matOpt.name),
                        Placeholder.parsed("material_icon", plugin.getItemSpriteTag(matOpt.icon))));
                if (armorSlot == EquipmentSlot.HEAD && getSavedHatId(player) != null) {
                    player.sendMessage(plugin.getMessage("trim-hat-active"));
                }
                openSlotMenu(player, armorSlot);
            }
            return;
        }

        // Select Material (Slots 29->33 and 38->42)
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
                            Placeholder.unparsed("slot", slotName),
                            Placeholder.parsed("slot_icon", slotIcon),
                            Placeholder.unparsed("pattern", patOpt.name),
                            Placeholder.parsed("pattern_icon", plugin.getItemSpriteTag(patOpt.icon)),
                            Placeholder.unparsed("material", chosenMaterial.name),
                            Placeholder.parsed("material_icon", plugin.getItemSpriteTag(chosenMaterial.icon))));
                    if (armorSlot == EquipmentSlot.HEAD && getSavedHatId(player) != null) {
                        player.sendMessage(plugin.getMessage("trim-hat-active"));
                    }
                    openSlotMenu(player, armorSlot);
                }
                return;
            }
        }

        // Clear / Reset Button (Slot 49)
        if (rawSlot == 49) {
            setSavedTrim(player, armorSlot, null, null);
            ItemStack worn = player.getInventory().getItem(armorSlot);
            if (worn != null && worn.getItemMeta() instanceof ArmorMeta armorMeta && armorMeta.hasTrim()) {
                armorMeta.setTrim(null);
                worn.setItemMeta(armorMeta);
                player.getInventory().setItem(armorSlot, worn);
            }
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1.2f);
            player.sendMessage(plugin.getMessage("trim-cleared",
                    Placeholder.unparsed("slot", slotName),
                    Placeholder.parsed("slot_icon", slotIcon)));
            openSlotMenu(player, armorSlot);
            return;
        }

        // Sync to All 4 Pieces Button (Slot 53)
        if (rawSlot == 53) {
            EquipmentSlot[] allSlots = {
                    EquipmentSlot.HEAD,
                    EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS,
                    EquipmentSlot.FEET
            };

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

            for (EquipmentSlot s : allSlots) {
                setSavedTrim(player, s, currentPattern, currentMaterial);
            }
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
        if (event.getInventory().getHolder() instanceof TrimMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChatInput(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // 1. Handle Admin Add Hat ID Input
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
                } else {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
                    player.sendMessage(plugin.getMessage("hat-admin-added-invalid", Placeholder.unparsed("id", rawId)));
                }
                openHatAdminMenu(player);
            });
            return;
        }

        // 2. Handle Item Rename Input
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
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingRenamePlayers.remove(uuid);
        pendingAddHatPlayers.remove(uuid);
    }

    public static class TrimMenuHolder implements InventoryHolder {
        private final MenuType menuType;
        private final EquipmentSlot slotType;
        private Inventory inventory;

        public TrimMenuHolder(MenuType menuType, EquipmentSlot slotType) {
            this.menuType = menuType;
            this.slotType = slotType;
        }

        public MenuType getMenuType() {
            return menuType;
        }

        public EquipmentSlot getSlotType() {
            return slotType;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private record PatternOption(String key, String name, Material icon) {}
    private record MaterialOption(String key, String name, String hexColor, Material icon) {}
}
