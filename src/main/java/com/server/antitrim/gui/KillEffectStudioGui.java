package com.server.antitrim.gui;

import com.server.antitrim.AntiArmorTrim;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KillEffectStudioGui implements Listener {

  private final AntiArmorTrim plugin;
  private static boolean registered = false;

  private final Set<UUID> pendingAddKillEffectPlayers = ConcurrentHashMap.newKeySet();
  private final Set<UUID> pendingCreateKillEffectGroupPlayers = ConcurrentHashMap.newKeySet();
  private final Map<UUID, String> pendingKillEffectNameEdit = new ConcurrentHashMap<>();
  private final Map<UUID, String> pendingKillEffectLoreEdit = new ConcurrentHashMap<>();

  public KillEffectStudioGui(AntiArmorTrim plugin) {
    this.plugin = plugin;
  }

  public static synchronized void ensureRegistered(AntiArmorTrim plugin) {
    if (!registered) {
      Bukkit.getPluginManager().registerEvents(new KillEffectStudioGui(plugin), plugin);
      registered = true;
    }
  }

  public static void handleCommand(AntiArmorTrim plugin, Player p, String[] args) {
    ensureRegistered(plugin);
    if (args.length == 0) { openPlayerMenu(plugin, p, 0); return; }
    String sub = args[0].toLowerCase();
    if (sub.equals("admin") || sub.equals("add") || sub.equals("edit") || sub.equals("remove")
        || sub.equals("delete") || sub.equals("default") || sub.equals("groups") || sub.equals("group")) {
      if (!plugin.hasAdminPerm(p)) { p.sendMessage(plugin.getMessage("no-permission")); return; }
      if (sub.equals("admin")) {
        if (args.length == 1) { openAdminMenu(plugin, p, 0); return; }
        sub = args[1].toLowerCase();
        args = Arrays.copyOfRange(args, 1, args.length);
      }
      if (sub.equals("groups") || sub.equals("group")) { openGroupListMenu(plugin, p, 0); return; }
      if (sub.equals("default") && args.length >= 2) {
        String rawId = args[1].trim();
        boolean off = rawId.equalsIgnoreCase("off") || rawId.equalsIgnoreCase("none") || rawId.equalsIgnoreCase("clear") || rawId.equalsIgnoreCase("disable");
        plugin.getKillEffectManager().setForceDefaultKillEffect(p, off ? null : rawId, !off);
        return;
      }
      if (sub.equals("add") && args.length >= 2) {
        String rawId = args[1].trim();
        if (plugin.getDbKillEffectItems().stream().anyMatch(s -> s.equalsIgnoreCase(rawId))) {
          p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
          p.sendMessage(plugin.getMessage("hat-admin-exists", Placeholder.unparsed("id", rawId)));
          openEditorMenu(plugin, p, rawId);
          return;
        }
        plugin.addKillEffectToDb(rawId);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
        p.sendMessage(plugin.getMessage("killeffect-admin-added", Placeholder.unparsed("id", rawId)));
        openEditorMenu(plugin, p, rawId);
        return;
      }
      if (sub.equals("edit") && args.length >= 2) { openEditorMenu(plugin, p, args[1].trim()); return; }
      if ((sub.equals("remove") || sub.equals("delete")) && args.length >= 2) {
        if (plugin.removeKillEffectFromDb(args[1].trim())) {
          p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1.2f);
          p.sendMessage(plugin.getMessage("hat-admin-removed", Placeholder.unparsed("id", args[1].trim())));
        }
        openAdminMenu(plugin, p, 0);
        return;
      }
      openAdminMenu(plugin, p, 0);
      return;
    }
    openPlayerMenu(plugin, p, 0);
  }

  public static void openPlayerMenu(AntiArmorTrim plugin, Player player, int page) {
    ensureRegistered(plugin);
    List<String> validEffects = new ArrayList<>(plugin.getDbKillEffectItems());

    int totalPages = Math.max(1, (int) Math.ceil(validEffects.size() / 36.0));
    int currentPage = Math.max(0, Math.min(page, totalPages - 1));

    String rawTitle = plugin.getConfig().getString("gui.killeffect-title", "<#cba6f7><bold>KillEffect Studio</bold> <#6c7086>(<page>/<max_page>)");
    Component title = plugin.parse(rawTitle, Placeholder.unparsed("page", String.valueOf(currentPage + 1)), Placeholder.unparsed("max_page", String.valueOf(totalPages)));

    TrimMenuHolder holder = new TrimMenuHolder(TrimMenuHolder.MenuType.KILLEFFECT_STUDIO, null, null, currentPage);
    Inventory inv = Bukkit.createInventory(holder, 54, title);
    holder.setInventory(inv);

    ItemStack filler = TrimMenuHolder.createFiller(plugin, Material.BLACK_STAINED_GLASS_PANE);
    for (int i = 36; i < 54; i++) inv.setItem(i, filler);

    String currentEffect = plugin.getKillEffectManager().getEffectiveKillEffectId(player);
    String defEffect = plugin.getDbDefaultKillEffect();
    int startIndex = currentPage * 36;
    int endIndex = Math.min(validEffects.size(), startIndex + 36);

    int slotIndex = 0;
    for (int i = startIndex; i < endIndex; i++) {
      String effectId = validEffects.get(i);
      boolean selected = effectId.equalsIgnoreCase(currentEffect);
      boolean isDefault = plugin.isDbForceDefaultKillEffect() && defEffect != null && effectId.equalsIgnoreCase(defEffect);
      boolean unlocked = plugin.getKillEffectManager().canUseSpecificKillEffect(player, effectId);

      Material iconMat = unlocked ? plugin.getKillEffectManager().getKillEffectIconMaterial(effectId) : Material.GRAY_DYE;
      ItemStack icon = plugin.getHatManager().createCleanIconStack(iconMat);
      ItemMeta meta = icon.getItemMeta();
      if (meta != null) {
        if (unlocked) {
          meta.displayName(plugin.getKillEffectManager().getKillEffectDisplayName(effectId));
          if (selected) meta.setEnchantmentGlintOverride(true);
        } else {
          meta.displayName(plugin.getKillEffectManager().getKillEffectDisplayName(effectId).append(plugin.parse(" <#6c7086>• <#f38ba8>[Locked]")));
        }

        List<Component> lore = new ArrayList<>(plugin.getKillEffectManager().getKillEffectCustomLore(effectId));
        lore.add(plugin.parse("<#6c7086>Model: <#a6adc8>" + effectId));
        if (isDefault) lore.add(plugin.parse("<#f9e2af>★ Default Server KillEffect"));
        lore.add(plugin.parse("<glyph:right_click> <#89dceb>Right-Click: <#cdd6f4>Preview 3D Model"));

        if (unlocked) {
          String statusRaw = selected
              ? plugin.getConfig().getString("gui.selected-status", "<#a6e3a1>● Selected")
              : plugin.getConfig().getString("gui.unselected-status", "<glyph:left_click> <#89dceb>Click to Select");
          lore.add(plugin.parse(statusRaw));
        } else {
          String reqPerm = plugin.getKillEffectManager().getKillEffectRequiredPermissionNode(effectId);
          for (String line : TrimMenuHolder.getLockedLoreTemplate(plugin)) {
            lore.add(plugin.parseWithPerm(line, reqPerm));
          }
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(plugin.getHatManager().getItemAppliedHatKey(), PersistentDataType.STRING, effectId);
        icon.setItemMeta(meta);
      }
      plugin.getHatManager().applyHiddenComponentTooltips(icon);
      inv.setItem(slotIndex++, icon);
    }

    inv.setItem(45, TrimMenuHolder.createBackButton(plugin, "<#f9e2af><bold>Back</bold> <#6c7086>| <#cdd6f4>Hub"));
    if (currentPage > 0) inv.setItem(48, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>Previous Page</bold>", null));
    inv.setItem(49, TrimMenuHolder.createStyledButton(plugin, Material.BARRIER, "<#f38ba8><bold>Reset & Disable Effect</bold>", List.of(
        plugin.parse("<#a6adc8>Disable active 3D KillEffect."))));
    if (currentPage + 1 < totalPages) inv.setItem(50, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>Next Page</bold>", null));
    if (plugin.hasAdminPerm(player)) inv.setItem(53, TrimMenuHolder.createStyledButton(plugin, Material.COMMAND_BLOCK, "<#f38ba8><bold>Admin KillEffect Manager</bold>", List.of(
        plugin.parse("<#a6adc8>Manage ModelEngine models,"),
        plugin.parse("<#a6adc8>permission groups, or default effect."))));

    player.openInventory(inv);
  }

  public static void openAdminMenu(AntiArmorTrim plugin, Player player, int page) {
    ensureRegistered(plugin);
    List<String> effectIds = plugin.getDbKillEffectItems();
    int totalPages = Math.max(1, (int) Math.ceil(effectIds.size() / 36.0));
    int currentPage = Math.max(0, Math.min(page, totalPages - 1));

    String rawTitle = plugin.getConfig().getString("gui.killeffect-admin-title", "<#f38ba8><bold>Admin KillEffect Manager</bold> <#6c7086>(<page>/<max_page>)");
    Component title = plugin.parse(rawTitle, Placeholder.unparsed("page", String.valueOf(currentPage + 1)), Placeholder.unparsed("max_page", String.valueOf(totalPages)));

    TrimMenuHolder holder = new TrimMenuHolder(TrimMenuHolder.MenuType.KILLEFFECT_ADMIN, null, null, currentPage);
    Inventory inv = Bukkit.createInventory(holder, 54, title);
    holder.setInventory(inv);

    ItemStack filler = TrimMenuHolder.createFiller(plugin, Material.BLACK_STAINED_GLASS_PANE);
    for (int i = 36; i < 54; i++) inv.setItem(i, filler);

    String defEffect = plugin.getDbDefaultKillEffect();
    int startIndex = currentPage * 36;
    int endIndex = Math.min(effectIds.size(), startIndex + 36);
    int slotIndex = 0;

    for (int i = startIndex; i < endIndex; i++) {
      String effectId = effectIds.get(i);
      boolean isDefault = defEffect != null && effectId.equalsIgnoreCase(defEffect);
      List<String> groups = plugin.getKillEffectManager().getGroupsContainingKillEffect(effectId);
      Material iconMat = plugin.getKillEffectManager().getKillEffectIconMaterial(effectId);

      ItemStack icon = plugin.getHatManager().createCleanIconStack(iconMat);
      ItemMeta meta = icon.getItemMeta();
      if (meta != null) {
        meta.displayName(plugin.getKillEffectManager().getKillEffectDisplayName(effectId).append(plugin.parse(" <#6c7086>(" + effectId + ")")));
        if (isDefault && plugin.isDbForceDefaultKillEffect()) meta.setEnchantmentGlintOverride(true);
        List<Component> lore = new ArrayList<>(plugin.getKillEffectManager().getKillEffectCustomLore(effectId));
        if (!groups.isEmpty()) lore.add(plugin.parse("<#a6adc8>Groups: <#f9e2af>" + String.join(", ", groups)));
        else lore.add(plugin.parse("<#a6adc8>Perm: <#6c7086>Default (" + AntiArmorTrim.KILLEFFECT_PERM + ")"));
        if (isDefault) lore.add(plugin.parse("<#f9e2af>★ Default Effect (" + (plugin.isDbForceDefaultKillEffect() ? "<#a6e3a1>Forced" : "<#f38ba8>Disabled") + "<#f9e2af>)"));
        lore.add(Component.empty());
        lore.add(plugin.parse("<glyph:left_click> <#89dceb>Left-Click: <#cdd6f4>Edit Name & Lore"));
        lore.add(plugin.parse("<glyph:left_click> <#f9e2af>Shift-Left-Click: <#cdd6f4>Set as Forced Default"));
        lore.add(plugin.parse("<glyph:right_click> <#f38ba8>Right-Click: <#cdd6f4>Delete KillEffect"));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(plugin.getHatManager().getItemAppliedHatKey(), PersistentDataType.STRING, effectId);
        icon.setItemMeta(meta);
      }
      plugin.getHatManager().applyHiddenComponentTooltips(icon);
      inv.setItem(slotIndex++, icon);
    }

    inv.setItem(45, TrimMenuHolder.createBackButton(plugin, "<#f9e2af><bold>Back</bold> <#6c7086>| <#cdd6f4>Player View"));
    if (currentPage > 0) inv.setItem(47, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>Previous Page</bold>", null));
    inv.setItem(48, TrimMenuHolder.createStyledButton(plugin, Material.ANVIL, "<#a6e3a1><bold>Add New KillEffect</bold>", List.of(
        plugin.parse("<#a6adc8>Type a ModelEngine Blueprint ID"),
        plugin.parse("<#a6adc8>in chat to save to database."))));
    inv.setItem(50, TrimMenuHolder.createStyledButton(plugin, Material.BOOKSHELF, "<#cba6f7><bold>KillEffect Groups</bold>", List.of(
        plugin.parse("<#a6adc8>Create custom permission nodes"),
        plugin.parse("<#a6adc8>& select effects for each rank!"))));
    if (currentPage + 1 < totalPages) inv.setItem(51, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>Next Page</bold>", null));

    String statusText = plugin.isDbForceDefaultKillEffect() ? "<#a6e3a1>Enabled" : "<#f38ba8>Disabled";
    String defText = (defEffect != null) ? "<#89dceb>" + defEffect : "<#6c7086>None";
    inv.setItem(53, TrimMenuHolder.createStyledButton(plugin, Material.NETHER_STAR, "<#f9e2af><bold>Force Default</bold>", List.of(
        plugin.parse("<#a6adc8>Status: " + statusText),
        plugin.parse("<#a6adc8>Default Effect: " + defText),
        Component.empty(),
        plugin.parse("<glyph:left_click> <#89dceb>Left-Click: <#cdd6f4>Toggle Force Default"),
        plugin.parse("<glyph:right_click> <#f38ba8>Right-Click: <#cdd6f4>Clear Default"))));

    player.openInventory(inv);
  }

  public static void openGroupListMenu(AntiArmorTrim plugin, Player player, int page) {
    ensureRegistered(plugin);
    List<String> permNodes = new ArrayList<>(plugin.getDbKillEffectGroups().keySet());
    int totalPages = Math.max(1, (int) Math.ceil(permNodes.size() / 36.0));
    int currentPage = Math.max(0, Math.min(page, totalPages - 1));

    String rawTitle = plugin.getConfig().getString("gui.killeffect-groups-title", "<#f9e2af><bold>KillEffect Perm Groups</bold> <#6c7086>(<page>/<max_page>)");
    Component title = plugin.parse(rawTitle, Placeholder.unparsed("page", String.valueOf(currentPage + 1)), Placeholder.unparsed("max_page", String.valueOf(totalPages)));

    TrimMenuHolder holder = new TrimMenuHolder(TrimMenuHolder.MenuType.KILLEFFECT_GROUP_LIST, null, null, currentPage);
    Inventory inv = Bukkit.createInventory(holder, 54, title);
    holder.setInventory(inv);

    ItemStack filler = TrimMenuHolder.createFiller(plugin, Material.BLACK_STAINED_GLASS_PANE);
    for (int i = 36; i < 54; i++) inv.setItem(i, filler);

    int startIndex = currentPage * 36;
    int endIndex = Math.min(permNodes.size(), startIndex + 36);
    int slotIndex = 0;

    for (int i = startIndex; i < endIndex; i++) {
      String permNode = permNodes.get(i);
      Set<String> effectsInGroup = plugin.getDbKillEffectGroups().getOrDefault(permNode, Set.of());
      List<Component> lore = new ArrayList<>(List.of(
          plugin.parse("<#a6adc8>Permission: <#f9e2af>" + permNode),
          plugin.parse("<#a6adc8>Selected Effects: <#a6e3a1>" + effectsInGroup.size())));
      if (!effectsInGroup.isEmpty()) {
        String previewList = String.join(", ", effectsInGroup.stream().limit(5).toList());
        if (effectsInGroup.size() > 5) previewList += "...";
        lore.add(plugin.parse("<#6c7086>(" + previewList + ")"));
      }
      lore.add(Component.empty());
      lore.add(plugin.parse("<glyph:left_click> <#89dceb>Left-Click: <#cdd6f4>Select KillEffects"));
      lore.add(plugin.parse("<glyph:right_click> <#f38ba8>Right-Click: <#cdd6f4>Delete Group"));

      ItemStack icon = TrimMenuHolder.createStyledButton(plugin, Material.TRIPWIRE_HOOK, "<#cba6f7><bold>" + permNode + "</bold>", lore);
      ItemMeta meta = icon.getItemMeta();
      if (meta != null) {
        meta.getPersistentDataContainer().set(plugin.getHatManager().getItemAppliedHatKey(), PersistentDataType.STRING, permNode);
        icon.setItemMeta(meta);
        plugin.getHatManager().applyHiddenComponentTooltips(icon);
      }
      inv.setItem(slotIndex++, icon);
    }

    inv.setItem(45, TrimMenuHolder.createBackButton(plugin, "<#f9e2af><bold>Back</bold> <#6c7086>| <#cdd6f4>Admin"));
    if (currentPage > 0) inv.setItem(47, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>Previous Page</bold>", null));
    inv.setItem(49, TrimMenuHolder.createStyledButton(plugin, Material.ANVIL, "<#a6e3a1><bold>Create New Group</bold>", List.of(
        plugin.parse("<#a6adc8>Type a new permission node"),
        plugin.parse("<#a6adc8>in chat to select effects for it!"))));
    if (currentPage + 1 < totalPages) inv.setItem(51, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>Next Page</bold>", null));

    player.openInventory(inv);
  }

  public static void openGroupSelectorMenu(AntiArmorTrim plugin, Player player, String permNode, int page) {
    ensureRegistered(plugin);
    List<String> effectIds = plugin.getDbKillEffectItems();
    int totalPages = Math.max(1, (int) Math.ceil(effectIds.size() / 36.0));
    int currentPage = Math.max(0, Math.min(page, totalPages - 1));

    String rawTitle = plugin.getConfig().getString("gui.killeffect-group-select-title", "<#cba6f7><bold>KE Group:</bold> <#89dceb><perm> <#6c7086>(<page>/<max_page>)");
    Component title = plugin.parse(rawTitle, Placeholder.unparsed("perm", permNode), Placeholder.unparsed("page", String.valueOf(currentPage + 1)), Placeholder.unparsed("max_page", String.valueOf(totalPages)));

    TrimMenuHolder holder = new TrimMenuHolder(TrimMenuHolder.MenuType.KILLEFFECT_GROUP_SELECTOR, null, permNode, currentPage);
    Inventory inv = Bukkit.createInventory(holder, 54, title);
    holder.setInventory(inv);

    ItemStack filler = TrimMenuHolder.createFiller(plugin, Material.BLACK_STAINED_GLASS_PANE);
    for (int i = 36; i < 54; i++) inv.setItem(i, filler);

    int startIndex = currentPage * 36;
    int endIndex = Math.min(effectIds.size(), startIndex + 36);
    int slotIndex = 0;

    for (int i = startIndex; i < endIndex; i++) {
      String effectId = effectIds.get(i);
      boolean inGroup = plugin.isKillEffectInDbGroup(permNode, effectId);
      Material iconMat = plugin.getKillEffectManager().getKillEffectIconMaterial(effectId);

      ItemStack icon = plugin.getHatManager().createCleanIconStack(iconMat);
      ItemMeta meta = icon.getItemMeta();
      if (meta != null) {
        meta.displayName(plugin.getKillEffectManager().getKillEffectDisplayName(effectId).append(plugin.parse(" <#6c7086>(" + effectId + ")")));
        if (inGroup) meta.setEnchantmentGlintOverride(true);

        List<Component> lore = new ArrayList<>(plugin.getKillEffectManager().getKillEffectCustomLore(effectId));
        lore.add(Component.empty());
        if (inGroup) {
          lore.add(plugin.parse("<#a6e3a1>Included in <#f9e2af>" + permNode));
          lore.add(plugin.parse("<glyph:right_click> <#fab387>Click to remove from this group"));
        } else {
          lore.add(plugin.parse("<#6c7086>Not in <#f9e2af>" + permNode));
          lore.add(plugin.parse("<glyph:left_click> <#89dceb>Click to add to this group"));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(plugin.getHatManager().getItemAppliedHatKey(), PersistentDataType.STRING, effectId);
        icon.setItemMeta(meta);
      }
      plugin.getHatManager().applyHiddenComponentTooltips(icon);
      inv.setItem(slotIndex++, icon);
    }

    inv.setItem(45, TrimMenuHolder.createBackButton(plugin, "<#f9e2af><bold>Back</bold> <#6c7086>| <#cdd6f4>Groups"));
    if (currentPage > 0) inv.setItem(47, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>Previous Page</bold>", null));
    Set<String> selectedSet = plugin.getDbKillEffectGroups().getOrDefault(permNode.toLowerCase(), Set.of());
    inv.setItem(49, TrimMenuHolder.createStyledButton(plugin, Material.BOOKSHELF, "<#f9e2af><bold>Editing: " + permNode + "</bold>", List.of(
        plugin.parse("<#a6adc8>Total Effects: <#a6e3a1>" + selectedSet.size()),
        plugin.parse("<#a6adc8>Click any effect above to toggle it!"))));
    if (currentPage + 1 < totalPages) inv.setItem(51, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>Next Page</bold>", null));

    player.openInventory(inv);
  }

  public static void openEditorMenu(AntiArmorTrim plugin, Player player, String effectId) {
    ensureRegistered(plugin);
    String rawTitle = plugin.getConfig().getString("gui.killeffect-editor-title", "<#cba6f7><bold>Edit KillEffect:</bold> <#89dceb><id>");
    TrimMenuHolder holder = new TrimMenuHolder(TrimMenuHolder.MenuType.KILLEFFECT_EDITOR, null, effectId, 0);
    Inventory inv = Bukkit.createInventory(holder, 27, plugin.parse(rawTitle, Placeholder.unparsed("id", effectId)));
    holder.setInventory(inv);

    ItemStack border = TrimMenuHolder.createFiller(plugin, Material.GRAY_STAINED_GLASS_PANE);
    for (int i = 0; i < 27; i++) inv.setItem(i, border);

    inv.setItem(10, TrimMenuHolder.createStyledButton(plugin, Material.NAME_TAG, "<#89dceb><bold>Edit Display Name</bold>", List.of(
        plugin.parse("<#a6adc8>Current: ").append(plugin.getKillEffectManager().getKillEffectDisplayName(effectId)),
        Component.empty(),
        plugin.parse("<glyph:left_click> <#89dceb>Left-Click: <#cdd6f4>Set new display name"),
        plugin.parse("<glyph:right_click> <#fab387>Right-Click: <#cdd6f4>Reset to default"))));

    List<Component> loreList = new ArrayList<>();
    List<Component> currentLore = plugin.getKillEffectManager().getKillEffectCustomLore(effectId);
    if (currentLore.isEmpty()) loreList.add(plugin.parse("<#6c7086>No lore configured"));
    else loreList.addAll(currentLore);
    loreList.add(Component.empty());
    loreList.add(plugin.parse("<glyph:left_click> <#89dceb>Left-Click: <#cdd6f4>Set/append lore in chat"));
    loreList.add(plugin.parse("<glyph:right_click> <#fab387>Right-Click: <#cdd6f4>Clear custom lore"));
    inv.setItem(11, TrimMenuHolder.createStyledButton(plugin, Material.WRITABLE_BOOK, "<#cba6f7><bold>Edit KillEffect Lore</bold>", loreList));

    List<String> groups = plugin.getKillEffectManager().getGroupsContainingKillEffect(effectId);
    inv.setItem(12, TrimMenuHolder.createStyledButton(plugin, Material.BOOKSHELF, "<#a6e3a1><bold>Permission Groups</bold>", List.of(
        plugin.parse("<#a6adc8>Assigned: <#f9e2af>" + (groups.isEmpty() ? "None (Default)" : String.join(", ", groups))),
        Component.empty(),
        plugin.parse("<glyph:left_click> <#89dceb>Click: <#cdd6f4>Open Group Manager"))));

    inv.setItem(14, TrimMenuHolder.createStyledButton(plugin, Material.ENDER_EYE, "<#89dceb><bold>Preview 3D KillEffect</bold>", List.of(
        plugin.parse("<#a6adc8>Model ID: <#f9e2af>" + effectId),
        Component.empty(),
        plugin.parse("<#a6e3a1>Click to test spawn this 3D model!"))));

    boolean isDef = plugin.isDbForceDefaultKillEffect() && effectId.equalsIgnoreCase(plugin.getDbDefaultKillEffect());
    inv.setItem(15, TrimMenuHolder.createStyledButton(plugin, Material.NETHER_STAR, "<#f9e2af><bold>Force Default</bold>", List.of(
        plugin.parse("<#a6adc8>Current: " + (isDef ? "<#a6e3a1>Active Default" : "<#6c7086>Not Default")),
        Component.empty(),
        plugin.parse("<glyph:left_click> <#89dceb>Left-Click: <#cdd6f4>Set as Forced Default"),
        plugin.parse("<glyph:right_click> <#fab387>Right-Click: <#cdd6f4>Disable Forced Default"))));

    inv.setItem(16, TrimMenuHolder.createStyledButton(plugin, Material.BARRIER, "<#f38ba8><bold>Delete KillEffect</bold>", List.of(plugin.parse("<#a6adc8>Remove <#f9e2af>" + effectId + " <#a6adc8>from database."))));
    inv.setItem(18, TrimMenuHolder.createBackButton(plugin, "<#f9e2af><bold>Back</bold> <#6c7086>| <#cdd6f4>Admin"));
    player.openInventory(inv);
  }

  @EventHandler
  public void onClick(InventoryClickEvent event) {
    if (!(event.getInventory().getHolder() instanceof TrimMenuHolder holder)) return;
    TrimMenuHolder.MenuType type = holder.getMenuType();
    if (type != TrimMenuHolder.MenuType.KILLEFFECT_STUDIO && type != TrimMenuHolder.MenuType.KILLEFFECT_ADMIN
        && type != TrimMenuHolder.MenuType.KILLEFFECT_GROUP_LIST && type != TrimMenuHolder.MenuType.KILLEFFECT_GROUP_SELECTOR
        && type != TrimMenuHolder.MenuType.KILLEFFECT_EDITOR) return;
    event.setCancelled(true);
    if (!(event.getWhoClicked() instanceof Player player)) return;

    int slot = event.getRawSlot();
    int page = holder.getPage();

    if (type == TrimMenuHolder.MenuType.KILLEFFECT_STUDIO) {
      if (slot == 45) { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f); DecorHubGui.open(plugin, player); return; }
      if (slot == 48 && page > 0) { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f); openPlayerMenu(plugin, player, page - 1); return; }
      if (slot == 50) {
        ItemStack next = event.getCurrentItem();
        if (next != null && next.getType() != Material.BLACK_STAINED_GLASS_PANE && !next.getType().isAir()) {
          player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
          openPlayerMenu(plugin, player, page + 1);
        }
        return;
      }
      if (slot == 53 && plugin.hasAdminPerm(player)) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        openAdminMenu(plugin, player, 0);
        return;
      }
      if (slot == 49) {
        plugin.getKillEffectManager().setSavedKillEffectId(player, null);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1.2f);
        player.sendMessage(plugin.getMessage("killeffect-cleared"));
        openPlayerMenu(plugin, player, page);
        return;
      }
      if (slot < 36) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || clicked.getItemMeta() == null) return;
        String effectId = clicked.getItemMeta().getPersistentDataContainer().get(plugin.getHatManager().getItemAppliedHatKey(), PersistentDataType.STRING);
        if (effectId == null) return;

        if (event.isRightClick()) {
          plugin.getKillEffectManager().playPreviewKillEffect(player, effectId);
          player.sendMessage(plugin.getMessage("killeffect-preview", Placeholder.component("effect", plugin.getKillEffectManager().getKillEffectDisplayName(effectId))));
          return;
        }

        if (!plugin.getKillEffectManager().canUseSpecificKillEffect(player, effectId)) {
          player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
          player.sendMessage(plugin.getMessageWithPerm("no-permission-gui", plugin.getKillEffectManager().getKillEffectRequiredPermissionNode(effectId)));
          return;
        }

        plugin.getKillEffectManager().setSavedKillEffectId(player, effectId);
        plugin.getKillEffectManager().playPreviewKillEffect(player, effectId);
        player.sendMessage(plugin.getMessage("killeffect-selected", Placeholder.component("effect", plugin.getKillEffectManager().getKillEffectDisplayName(effectId))));
        openPlayerMenu(plugin, player, page);
      }
      return;
    }

    if (type == TrimMenuHolder.MenuType.KILLEFFECT_ADMIN) {
      if (!plugin.hasAdminPerm(player)) { player.closeInventory(); return; }
      if (slot == 45) { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f); openPlayerMenu(plugin, player, 0); return; }
      if (slot == 47 && page > 0) { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f); openAdminMenu(plugin, player, page - 1); return; }
      if (slot == 51) {
        ItemStack next = event.getCurrentItem();
        if (next != null && next.getType() != Material.BLACK_STAINED_GLASS_PANE && !next.getType().isAir()) {
          player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
          openAdminMenu(plugin, player, page + 1);
        }
        return;
      }
      if (slot == 48) {
        player.closeInventory();
        pendingAddKillEffectPlayers.add(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.3f);
        player.sendMessage(plugin.getMessage("killeffect-admin-prompt-id"));
        return;
      }
      if (slot == 50) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        openGroupListMenu(plugin, player, 0);
        return;
      }
      if (slot == 53) {
        if (event.isRightClick()) {
          plugin.getKillEffectManager().setForceDefaultKillEffect(player, null, false);
        } else {
          boolean nextState = !plugin.isDbForceDefaultKillEffect();
          String def = plugin.getDbDefaultKillEffect();
          if (nextState && def == null && !plugin.getDbKillEffectItems().isEmpty()) def = plugin.getDbKillEffectItems().get(0);
          plugin.setDefaultKillEffectInDb(def, nextState);
          player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
          player.sendMessage(plugin.getMessage("hat-admin-default-toggled", Placeholder.parsed("state", nextState ? "<#a6e3a1>Enabled" : "<#f38ba8>Disabled")));
        }
        openAdminMenu(plugin, player, page);
        return;
      }
      if (slot < 36) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || clicked.getItemMeta() == null) return;
        String effectId = clicked.getItemMeta().getPersistentDataContainer().get(plugin.getHatManager().getItemAppliedHatKey(), PersistentDataType.STRING);
        if (effectId == null) return;

        if (event.isRightClick()) {
          if (plugin.removeKillEffectFromDb(effectId)) {
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1.2f);
            player.sendMessage(plugin.getMessage("hat-admin-removed", Placeholder.unparsed("id", effectId)));
          }
          openAdminMenu(plugin, player, page);
          return;
        }
        if (event.isShiftClick()) {
          plugin.getKillEffectManager().setForceDefaultKillEffect(player, effectId, true);
          openAdminMenu(plugin, player, page);
          return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        openEditorMenu(plugin, player, effectId);
      }
      return;
    }

    if (type == TrimMenuHolder.MenuType.KILLEFFECT_GROUP_LIST) {
      if (!plugin.hasAdminPerm(player)) { player.closeInventory(); return; }
      if (slot == 45) { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f); openAdminMenu(plugin, player, 0); return; }
      if (slot == 47 && page > 0) { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f); openGroupListMenu(plugin, player, page - 1); return; }
      if (slot == 51) {
        ItemStack next = event.getCurrentItem();
        if (next != null && next.getType() != Material.BLACK_STAINED_GLASS_PANE && !next.getType().isAir()) {
          player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
          openGroupListMenu(plugin, player, page + 1);
        }
        return;
      }
      if (slot == 49) {
        player.closeInventory();
        pendingCreateKillEffectGroupPlayers.add(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.3f);
        player.sendMessage(plugin.getMessage("hat-admin-prompt-group"));
        return;
      }
      if (slot < 36) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || clicked.getItemMeta() == null) return;
        String permNode = clicked.getItemMeta().getPersistentDataContainer().get(plugin.getHatManager().getItemAppliedHatKey(), PersistentDataType.STRING);
        if (permNode == null) return;

        if (event.isRightClick()) {
          plugin.deleteDbKillEffectGroup(permNode);
          player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1.2f);
          player.sendMessage(plugin.getMessage("hat-admin-group-deleted", Placeholder.unparsed("perm", permNode)));
          openGroupListMenu(plugin, player, page);
          return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        openGroupSelectorMenu(plugin, player, permNode, 0);
      }
      return;
    }

    if (type == TrimMenuHolder.MenuType.KILLEFFECT_GROUP_SELECTOR) {
      if (!plugin.hasAdminPerm(player)) { player.closeInventory(); return; }
      String permNode = holder.getEditingId();
      if (permNode == null) return;

      if (slot == 45) { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f); openGroupListMenu(plugin, player, 0); return; }
      if (slot == 47 && page > 0) { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f); openGroupSelectorMenu(plugin, player, permNode, page - 1); return; }
      if (slot == 51) {
        ItemStack next = event.getCurrentItem();
        if (next != null && next.getType() != Material.BLACK_STAINED_GLASS_PANE && !next.getType().isAir()) {
          player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
          openGroupSelectorMenu(plugin, player, permNode, page + 1);
        }
        return;
      }
      if (slot < 36) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || clicked.getItemMeta() == null) return;
        String effectId = clicked.getItemMeta().getPersistentDataContainer().get(plugin.getHatManager().getItemAppliedHatKey(), PersistentDataType.STRING);
        if (effectId == null) return;

        boolean added = plugin.toggleKillEffectInDbGroup(permNode, effectId);
        if (added) {
          player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
          player.sendMessage(plugin.getMessage("hat-admin-group-hat-added", Placeholder.unparsed("id", effectId), Placeholder.unparsed("perm", permNode)));
        } else {
          player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1.2f);
          player.sendMessage(plugin.getMessage("hat-admin-group-hat-removed", Placeholder.unparsed("id", effectId), Placeholder.unparsed("perm", permNode)));
        }
        openGroupSelectorMenu(plugin, player, permNode, page);
      }
      return;
    }

    if (type == TrimMenuHolder.MenuType.KILLEFFECT_EDITOR) {
      if (!plugin.hasAdminPerm(player)) { player.closeInventory(); return; }
      String effectId = holder.getEditingId();
      if (effectId == null) return;

      if (slot == 18) { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f); openAdminMenu(plugin, player, 0); return; }
      if (slot == 10) {
        if (event.isRightClick()) {
          plugin.setKillEffectNameInDb(effectId, null);
          player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1.2f);
          player.sendMessage(plugin.getMessage("hat-admin-name-reset", Placeholder.unparsed("id", effectId)));
          openEditorMenu(plugin, player, effectId);
          return;
        }
        player.closeInventory();
        pendingKillEffectNameEdit.put(player.getUniqueId(), effectId);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.3f);
        player.sendMessage(plugin.getMessage("hat-admin-prompt-name", Placeholder.unparsed("id", effectId)));
        return;
      }
      if (slot == 11) {
        if (event.isRightClick()) {
          plugin.setKillEffectLoreInDb(effectId, null);
          player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1.2f);
          player.sendMessage(plugin.getMessage("hat-admin-lore-reset", Placeholder.unparsed("id", effectId)));
          openEditorMenu(plugin, player, effectId);
          return;
        }
        player.closeInventory();
        pendingKillEffectLoreEdit.put(player.getUniqueId(), effectId);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.3f);
        player.sendMessage(plugin.getMessage("hat-admin-prompt-lore", Placeholder.unparsed("id", effectId)));
        return;
      }
      if (slot == 12) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        openGroupListMenu(plugin, player, 0);
        return;
      }
      if (slot == 14) {
        plugin.getKillEffectManager().playPreviewKillEffect(player, effectId);
        player.sendMessage(plugin.getMessage("killeffect-preview", Placeholder.component("effect", plugin.getKillEffectManager().getKillEffectDisplayName(effectId))));
        return;
      }
      if (slot == 15) {
        plugin.getKillEffectManager().setForceDefaultKillEffect(player, event.isRightClick() ? null : effectId, !event.isRightClick());
        openEditorMenu(plugin, player, effectId);
        return;
      }
      if (slot == 16) {
        if (plugin.removeKillEffectFromDb(effectId)) {
          player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1.2f);
          player.sendMessage(plugin.getMessage("hat-admin-removed", Placeholder.unparsed("id", effectId)));
        }
        openAdminMenu(plugin, player, 0);
      }
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onChatInput(AsyncChatEvent event) {
    Player player = event.getPlayer();
    UUID uuid = player.getUniqueId();

    if (pendingCreateKillEffectGroupPlayers.remove(uuid)) {
      event.setCancelled(true);
      String rawPerm = null;
      try {
        if (event.signedMessage() != null && event.signedMessage().message() != null && !event.signedMessage().message().isBlank()) {
          rawPerm = event.signedMessage().message().trim();
        }
      } catch (Throwable ignored) {}
      if (rawPerm == null || rawPerm.isBlank()) {
        rawPerm = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
      }
      final String finalPerm = rawPerm;
      Bukkit.getScheduler().runTask(plugin, () -> {
        if (!player.isOnline()) return;
        if (finalPerm.equalsIgnoreCase("cancel")) {
          player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.8f);
          player.sendMessage(plugin.getMessage("hat-admin-cancelled"));
          openGroupListMenu(plugin, player, 0);
          return;
        }
        String normalized = plugin.normalizePermNode(finalPerm);
        plugin.createDbKillEffectGroup(normalized);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
        player.sendMessage(plugin.getMessage("hat-admin-group-created", Placeholder.unparsed("perm", normalized)));
        openGroupSelectorMenu(plugin, player, normalized, 0);
      });
      return;
    }

    if (pendingAddKillEffectPlayers.remove(uuid)) {
      event.setCancelled(true);
      String rawId = null;
      try {
        if (event.signedMessage() != null && event.signedMessage().message() != null && !event.signedMessage().message().isBlank()) {
          rawId = event.signedMessage().message().trim();
        }
      } catch (Throwable ignored) {}
      if (rawId == null || rawId.isBlank()) {
        rawId = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
      }
      final String finalId = rawId;
      Bukkit.getScheduler().runTask(plugin, () -> {
        if (!player.isOnline()) return;
        if (finalId.equalsIgnoreCase("cancel")) {
          player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.8f);
          player.sendMessage(plugin.getMessage("hat-admin-cancelled"));
          openAdminMenu(plugin, player, 0);
          return;
        }
        if (plugin.getDbKillEffectItems().stream().anyMatch(s -> s.equalsIgnoreCase(finalId))) {
          player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
          player.sendMessage(plugin.getMessage("hat-admin-exists", Placeholder.unparsed("id", finalId)));
          openAdminMenu(plugin, player, 0);
          return;
        }
        plugin.addKillEffectToDb(finalId);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
        player.sendMessage(plugin.getMessage("killeffect-admin-added", Placeholder.unparsed("id", finalId)));
        openEditorMenu(plugin, player, finalId);
      });
      return;
    }

    String editingNameEffectId = pendingKillEffectNameEdit.remove(uuid);
    if (editingNameEffectId != null) {
      event.setCancelled(true);
      String rawInput = null;
      try {
        if (event.signedMessage() != null && event.signedMessage().message() != null && !event.signedMessage().message().isBlank()) {
          rawInput = event.signedMessage().message().trim();
        }
      } catch (Throwable ignored) {}
      if (rawInput == null || rawInput.isBlank()) {
        rawInput = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
      }
      final String finalInput = rawInput;
      Bukkit.getScheduler().runTask(plugin, () -> {
        if (!player.isOnline()) return;
        if (finalInput.equalsIgnoreCase("cancel")) {
          player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.8f);
          player.sendMessage(plugin.getMessage("hat-admin-cancelled"));
          openEditorMenu(plugin, player, editingNameEffectId);
          return;
        }
        plugin.setKillEffectNameInDb(editingNameEffectId, finalInput);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.3f);
        player.sendMessage(plugin.getMessage("hat-admin-name-updated", Placeholder.unparsed("id", editingNameEffectId), Placeholder.component("name", plugin.parse(finalInput))));
        openEditorMenu(plugin, player, editingNameEffectId);
      });
      return;
    }

    String editingLoreEffectId = pendingKillEffectLoreEdit.remove(uuid);
    if (editingLoreEffectId != null) {
      event.setCancelled(true);
      String rawInput = null;
      try {
        if (event.signedMessage() != null && event.signedMessage().message() != null && !event.signedMessage().message().isBlank()) {
          rawInput = event.signedMessage().message().trim();
        }
      } catch (Throwable ignored) {}
      if (rawInput == null || rawInput.isBlank()) {
        rawInput = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
      }
      final String finalInput = rawInput;
      Bukkit.getScheduler().runTask(plugin, () -> {
        if (!player.isOnline()) return;
        if (finalInput.equalsIgnoreCase("cancel")) {
          player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.8f);
          player.sendMessage(plugin.getMessage("hat-admin-cancelled"));
          openEditorMenu(plugin, player, editingLoreEffectId);
          return;
        }
        List<String> newLore;
        if (finalInput.regionMatches(true, 0, "add ", 0, 4)) {
          List<String> existing = plugin.getDbKillEffectLore(editingLoreEffectId);
          newLore = existing != null ? new ArrayList<>(existing) : new ArrayList<>();
          newLore.add(finalInput.substring(4).trim());
        } else {
          newLore = new ArrayList<>(Arrays.asList(finalInput.split("\\|")));
          newLore.replaceAll(String::trim);
        }
        plugin.setKillEffectLoreInDb(editingLoreEffectId, newLore);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.2f);
        player.sendMessage(plugin.getMessage("hat-admin-lore-updated", Placeholder.unparsed("id", editingLoreEffectId)));
        openEditorMenu(plugin, player, editingLoreEffectId);
      });
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    UUID uuid = event.getPlayer().getUniqueId();
    pendingAddKillEffectPlayers.remove(uuid);
    pendingCreateKillEffectGroupPlayers.remove(uuid);
    pendingKillEffectNameEdit.remove(uuid);
    pendingKillEffectLoreEdit.remove(uuid);
  }
}
