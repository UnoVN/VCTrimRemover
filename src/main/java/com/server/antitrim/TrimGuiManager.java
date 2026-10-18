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

    private static final Map<String, PatternOption> PATTERNS = new LinkedHashMap<>();
    private static final Map<String, MaterialOption> MATERIALS = new LinkedHashMap<>();
    private static final Method SET_ITEM_MODEL_METHOD;

    public enum MenuType {
        DECOR_HUB,
        TRIM_MAIN,
        TRIM_SLOT,
        RENAME_STUDIO
    }

    static {
        Method m = null;
        try {
            m = ItemMeta.class.getMethod("setItemModel", NamespacedKey.class);
        } catch (NoSuchMethodException ignored) {
        }
        SET_ITEM_MODEL_METHOD = m;

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
    // PERSISTENT DATA CONTAINER MANAGEMENT (ARMOR TRIMS)
    // =========================================================================
    private NamespacedKey getPatternKey(EquipmentSlot slot) {
        return new NamespacedKey(plugin, "trim_pattern_" + slot.name());
    }

    private NamespacedKey getMaterialKey(EquipmentSlot slot) {
        return new NamespacedKey(plugin, "trim_material_" + slot.name());
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

    public ArmorTrim buildArmorTrim(String patternKey, String materialKey) {
        if (patternKey == null || materialKey == null) return null;
        TrimPattern pattern = Registry.TRIM_PATTERN.get(NamespacedKey.minecraft(patternKey));
        TrimMaterial material = Registry.TRIM_MATERIAL.get(NamespacedKey.minecraft(materialKey));
        if (pattern != null && material != null) {
            return new ArmorTrim(material, pattern);
        }
        return null;
    }

    public void applySavedTrimToSlot(Player player, EquipmentSlot slot) {
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
        if (!player.hasPermission(AntiArmorTrim.BYPASS_PERM)) return;
        applySavedTrimToSlot(player, EquipmentSlot.HEAD);
        applySavedTrimToSlot(player, EquipmentSlot.CHEST);
        applySavedTrimToSlot(player, EquipmentSlot.LEGS);
        applySavedTrimToSlot(player, EquipmentSlot.FEET);
    }

    // =========================================================================
    // 1. DECORATION HUB MENU (/decor)
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

        // Slot 15: Item Rename Studio Button
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
    // 2. ITEM RENAME STUDIO MENU (/rename)
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

        // Slot 11: Standard Minecraft Color & Format Guide (Using parseLiteral so &c, &k are visible!)
        ItemStack guideItem = createCleanIconStack(Material.KNOWLEDGE_BOOK);
        ItemMeta guideMeta = guideItem.getItemMeta();
        if (guideMeta != null) {
            String sprite = plugin.getItemSpriteTag(Material.KNOWLEDGE_BOOK);
            guideMeta.displayName(plugin.parseLiteral(sprite + " <#f9e2af><bold>Minecraft Format Guide</bold>"));

            List<Component> lore = new ArrayList<>();
            lore.add(plugin.parseLiteral("<#cba6f7>Color Codes:"));
            lore.add(plugin.parseLiteral("<#cdd6f4>&0 <#6c7086>▸ <black>Black     <#cdd6f4>&1 <#6c7086>▸ <dark_blue>Dark Blue"));
            lore.add(plugin.parseLiteral("<#cdd6f4>&2 <#6c7086>▸ <dark_green>Green     <#cdd6f4>&3 <#6c7086>▸ <dark_aqua>Cyan"));
            lore.add(plugin.parseLiteral("<#cdd6f4>&4 <#6c7086>▸ <dark_red>Dark Red  <#cdd6f4>&5 <#6c7086>▸ <dark_purple>Purple"));
            lore.add(plugin.parseLiteral("<#cdd6f4>&6 <#6c7086>▸ <gold>Gold      <#cdd6f4>&7 <#6c7086>▸ <#a6adc8>Gray"));
            lore.add(plugin.parseLiteral("<#cdd6f4>&8 <#6c7086>▸ <#6c7086>Dark Gray <#cdd6f4>&9 <#6c7086>▸ <#89b4fa>Blue"));
            lore.add(plugin.parseLiteral("<#cdd6f4>&a <#6c7086>▸ <#a6e3a1>Lime      <#cdd6f4>&b <#6c7086>▸ <#89dceb>Aqua"));
            lore.add(plugin.parseLiteral("<#cdd6f4>&c <#6c7086>▸ <#f38ba8>Red       <#cdd6f4>&d <#6c7086>▸ <#f5c2e7>Pink"));
            lore.add(plugin.parseLiteral("<#cdd6f4>&e <#6c7086>▸ <#f9e2af>Yellow    <#cdd6f4>&f <#6c7086>▸ <#cdd6f4>White"));
            lore.add(Component.empty());
            lore.add(plugin.parseLiteral("<#cba6f7>Format Codes:"));
            lore.add(plugin.parseLiteral("<#cdd6f4>&k <#6c7086>▸ <#cdd6f4><obfuscated>abc</obfuscated> <#a6adc8>(Magic)   <#cdd6f4>&l <#6c7086>▸ <#cdd6f4><bold>Bold</bold>"));
            lore.add(plugin.parseLiteral("<#cdd6f4>&m <#6c7086>▸ <#cdd6f4><strikethrough>Strike</strikethrough>        <#cdd6f4>&n <#6c7086>▸ <#cdd6f4><underlined>Underline</underlined>"));
            lore.add(plugin.parseLiteral("<#cdd6f4>&o <#6c7086>▸ <#cdd6f4><italic>Italic</italic>        <#cdd6f4>&r <#6c7086>▸ <#a6adc8>Reset"));
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

        Component parsedName = plugin.parse(rawInput);
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
    // 3. ARMOR TRIM MAIN MENU (/trim) & SLOT SUB-MENU
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
        boolean hasPerm = requiredPerm.equals(AntiArmorTrim.RENAME_PERM)
                ? canUseRename(player)
                : player.hasPermission(requiredPerm);

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
        boolean hasPerm = requiredPerm.equals(AntiArmorTrim.RENAME_PERM)
                ? canUseRename(player)
                : player.hasPermission(requiredPerm);

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
    // GUI INTERACTION & CHAT RENAME LISTENERS
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
            } else if (rawSlot == 15) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                openRenameMenu(player);
            }
            return;
        }

        // 2. ITEM RENAME STUDIO (/rename)
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
                pendingRenamePlayers.add(player.getUniqueId());
                player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.3f);
                player.sendMessage(plugin.getMessage("rename-prompt"));
            }
            return;
        }

        // 3. ARMOR TRIM MAIN MENU (/trim)
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

        // 4. ARMOR TRIM SLOT SUB-MENU
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

            // If unselected or just reset, sync the reset/empty state across all 4 armor slots
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
    public void onChatRenameInput(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!pendingRenamePlayers.remove(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        // Read the raw packet message directly from signedMessage() so chat plugins cannot strip & color codes
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

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingRenamePlayers.remove(event.getPlayer().getUniqueId());
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
