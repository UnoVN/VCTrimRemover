package com.server.antitrim;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TrimGuiManager implements Listener {

    private final AntiArmorTrim plugin;

    private static final Map<String, PatternOption> PATTERNS = new LinkedHashMap<>();
    private static final Map<String, MaterialOption> MATERIALS = new LinkedHashMap<>();

    static {
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

    // =========================================================================
    // PERSISTENT DATA CONTAINER MANAGEMENT
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
    // MAIN MENU (/trim)
    // =========================================================================
    public void openMainMenu(Player player) {
        String rawTitle = plugin.getConfig().getString("gui.main-title", "<#6c7086>✦ <#cba6f7><bold>Armor Trim Studio</bold> <#6c7086>✦");
        Component title = plugin.parse(rawTitle);

        TrimMenuHolder holder = new TrimMenuHolder(null);
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

        player.openInventory(inv);
    }

    private ItemStack createMainSlotIcon(Player player, EquipmentSlot slot, Material iconMat) {
        ItemStack item = new ItemStack(iconMat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String slotName = getSlotEnglishName(slot);
        Component badge = plugin.createIconBadge("◆", "#74c7ec");
        meta.displayName(badge.append(plugin.parse("<#74c7ec><bold>" + slotName + " Trim</bold>")));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        String patternKey = getSavedPatternKey(player, slot);
        String materialKey = getSavedMaterialKey(player, slot);

        PatternOption pOpt = patternKey != null ? PATTERNS.get(patternKey) : null;
        MaterialOption mOpt = materialKey != null ? MATERIALS.get(materialKey) : null;

        List<Component> lore = new ArrayList<>();
        lore.add(plugin.parse("<#6c7086>▪ <#a6adc8>Pattern: " + (pOpt != null ? "<#89dceb>" + pOpt.name : "<#f38ba8>None")));
        lore.add(plugin.parse("<#6c7086>▪ <#a6adc8>Material: " + (mOpt != null ? "<" + mOpt.hexColor + ">" + mOpt.name : "<#f38ba8>None")));

        if (meta instanceof ArmorMeta armorMeta && pOpt != null && mOpt != null) {
            ArmorTrim previewTrim = buildArmorTrim(patternKey, materialKey);
            if (previewTrim != null) {
                armorMeta.setTrim(previewTrim);
            }
        }

        appendPermissionLore(player, lore);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // =========================================================================
    // SLOT SUB-MENU (PATTERN & MATERIAL SELECTOR)
    // =========================================================================
    public void openSlotMenu(Player player, EquipmentSlot slot) {
        String slotName = getSlotEnglishName(slot);
        String rawTitle = plugin.getConfig().getString("gui.slot-title", "<#6c7086>✦ <#cba6f7><bold>Trim Editor:</bold> <#74c7ec><slot> <#6c7086>✦");
        Component title = plugin.parse(rawTitle, Placeholder.unparsed("slot", slotName));

        TrimMenuHolder holder = new TrimMenuHolder(slot);
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
        inv.setItem(22, createMainSlotIcon(player, slot, getPreviewMaterial(slot)));

        // 3. Materials (Slots 29->33 and 38->42)
        int[] matSlots = {29, 30, 31, 32, 33, 38, 39, 40, 41, 42};
        int mIndex = 0;
        for (MaterialOption mOpt : MATERIALS.values()) {
            if (mIndex >= matSlots.length) break;
            boolean selected = mOpt.key.equalsIgnoreCase(currentMaterial);
            inv.setItem(matSlots[mIndex++], createMaterialIcon(player, mOpt, selected));
        }

        // 4. Navigation & Action Buttons
        // Back Button (Slot 45)
        ItemStack backBtn = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backBtn.getItemMeta();
        if (backMeta != null) {
            backMeta.displayName(plugin.createIconBadge("←", "#f9e2af")
                    .append(plugin.parse("<#f9e2af><bold>Back to Main Menu</bold>")));
            List<Component> backLore = new ArrayList<>();
            backLore.add(plugin.parse("<#a6adc8>Return to the armor slot selector."));
            backMeta.lore(backLore);
            backBtn.setItemMeta(backMeta);
        }
        inv.setItem(45, backBtn);

        // Clear / Disable Auto-Trim Button (Slot 49)
        ItemStack clearBtn = new ItemStack(Material.BARRIER);
        ItemMeta clearMeta = clearBtn.getItemMeta();
        if (clearMeta != null) {
            clearMeta.displayName(plugin.createIconBadge("♻", "#f38ba8")
                    .append(plugin.parse("<#f38ba8><bold>Reset & Clear Trim</bold>")));
            List<Component> clearLore = new ArrayList<>();
            clearLore.add(plugin.parse("<#a6adc8>Disable automatic trim application"));
            clearLore.add(plugin.parse("<#a6adc8>and remove trim from your equipped piece."));
            appendPermissionLore(player, clearLore);
            clearMeta.lore(clearLore);
            clearBtn.setItemMeta(clearMeta);
        }
        inv.setItem(49, clearBtn);

        // Apply to All 4 Pieces Button (Slot 53)
        ItemStack applyAllBtn = new ItemStack(Material.NETHER_STAR);
        ItemMeta allMeta = applyAllBtn.getItemMeta();
        if (allMeta != null) {
            allMeta.displayName(plugin.createIconBadge("★", "#cba6f7")
                    .append(plugin.parse("<#cba6f7><bold>Sync to All 4 Pieces</bold>")));
            List<Component> allLore = new ArrayList<>();
            allLore.add(plugin.parse("<#a6adc8>Copy this slot's Pattern & Material"));
            allLore.add(plugin.parse("<#a6adc8>to Helmet, Chestplate, Leggings & Boots."));
            appendPermissionLore(player, allLore);
            allMeta.lore(allLore);
            applyAllBtn.setItemMeta(allMeta);
        }
        inv.setItem(53, applyAllBtn);

        player.openInventory(inv);
    }

    private ItemStack createPatternIcon(Player player, PatternOption opt, boolean selected) {
        ItemStack item = new ItemStack(opt.icon);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        Component prefixIcon = selected
                ? plugin.createIconBadge("✔", "#a6e3a1")
                : plugin.createIconBadge("✦", "#89dceb");
        String nameColor = selected ? "<#a6e3a1><bold>" : "<#89dceb>";
        meta.displayName(prefixIcon.append(plugin.parse(nameColor + opt.name + " Trim")));

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        if (selected) {
            meta.setEnchantmentGlintOverride(true);
        }

        List<Component> lore = new ArrayList<>();
        lore.add(plugin.parse("<#6c7086>▪ <#a6adc8>Category: <#b4befe>Armor Pattern"));
        if (selected) {
            lore.add(plugin.parse("<#a6e3a1>▸ Currently Selected"));
        }
        appendPermissionLore(player, lore);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMaterialIcon(Player player, MaterialOption opt, boolean selected) {
        ItemStack item = new ItemStack(opt.icon);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        Component prefixIcon = selected
                ? plugin.createIconBadge("✔", "#a6e3a1")
                : plugin.createIconBadge("◈", opt.hexColor);
        String nameColor = selected ? "<#a6e3a1><bold>" : "<" + opt.hexColor + ">";
        meta.displayName(prefixIcon.append(plugin.parse(nameColor + opt.name + " Material")));

        if (selected) {
            meta.setEnchantmentGlintOverride(true);
        }

        List<Component> lore = new ArrayList<>();
        lore.add(plugin.parse("<#6c7086>▪ <#a6adc8>Color Swatch: <" + opt.hexColor + ">" + opt.name));
        if (selected) {
            lore.add(plugin.parse("<#a6e3a1>▸ Currently Selected"));
        }
        appendPermissionLore(player, lore);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void appendPermissionLore(Player player, List<Component> lore) {
        String path = player.hasPermission(AntiArmorTrim.BYPASS_PERM)
                ? "gui.has-permission-lore"
                : "gui.no-permission-lore";
        List<String> rawLines = plugin.getConfig().getStringList(path);
        for (String line : rawLines) {
            lore.add(plugin.parse(line));
        }
    }

    private ItemStack createFiller(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
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
    // GUI INTERACTION HANDLERS
    // =========================================================================
    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TrimMenuHolder holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getInventory().getSize()) return;

        // 1. Main Menu Navigation
        if (holder.getSlotType() == null) {
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

        // 2. Sub-Menu Actions
        EquipmentSlot armorSlot = holder.getSlotType();

        // Back Button (Always Accessible)
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
            player.sendMessage(plugin.getMessage("no-permission-gui"));
            return;
        }

        String currentPattern = getSavedPatternKey(player, armorSlot);
        String currentMaterial = getSavedMaterialKey(player, armorSlot);
        String slotName = getSlotEnglishName(armorSlot);

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
                        Placeholder.unparsed("pattern", chosenPattern.name),
                        Placeholder.unparsed("material", matOpt.name)));
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
                            Placeholder.unparsed("pattern", patOpt.name),
                            Placeholder.unparsed("material", chosenMaterial.name)));
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
            player.sendMessage(plugin.getMessage("trim-cleared", Placeholder.unparsed("slot", slotName)));
            openSlotMenu(player, armorSlot);
            return;
        }

        // Sync to All 4 Pieces Button (Slot 53)
        if (rawSlot == 53) {
            if (currentPattern == null || currentMaterial == null) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                player.sendMessage(plugin.getMessage("trim-missing-selection"));
                return;
            }
            PatternOption patOpt = PATTERNS.get(currentPattern);
            MaterialOption matOpt = MATERIALS.get(currentMaterial);

            for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                setSavedTrim(player, s, currentPattern, currentMaterial);
            }
            applyAllSavedTrims(player);

            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
            player.sendMessage(plugin.getMessage("trim-applied-all",
                    Placeholder.unparsed("pattern", patOpt != null ? patOpt.name : currentPattern),
                    Placeholder.unparsed("material", matOpt != null ? matOpt.name : currentMaterial)));
            openMainMenu(player);
        }
    }

    @EventHandler
    public void onGuiDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof TrimMenuHolder) {
            event.setCancelled(true);
        }
    }

    public static class TrimMenuHolder implements InventoryHolder {
        private final EquipmentSlot slotType;
        private Inventory inventory;

        public TrimMenuHolder(EquipmentSlot slotType) {
            this.slotType = slotType;
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
      
