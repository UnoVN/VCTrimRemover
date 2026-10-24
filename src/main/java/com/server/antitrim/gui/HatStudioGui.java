package com.server.antitrim.gui;

import com.server.antitrim.AntiArmorTrim;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HatStudioGui implements Listener {

  private final AntiArmorTrim plugin;
  private static boolean registered = false;

  private final Set<UUID> pendingAddHatPlayers = ConcurrentHashMap.newKeySet();
  private final Set<UUID> pendingCreateGroupPlayers = ConcurrentHashMap.newKeySet();
  private final Map<UUID, String> pendingHatNameEdit = new ConcurrentHashMap<>();
  private final Map<UUID, String> pendingHatLoreEdit = new ConcurrentHashMap<>();

  public HatStudioGui(AntiArmorTrim plugin) {
    this.plugin = plugin;
  }

  public static synchronized void ensureRegistered(AntiArmorTrim plugin) {
    if (!registered) {
      Bukkit.getPluginManager().registerEvents(new HatStudioGui(plugin), plugin);
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
        plugin.getHatManager().setForceDefaultHat(p, off ? null : rawId, !off);
        return;
      }
      if (sub.equals("add") && args.length >= 2) {
        String rawId = args[1].trim();
        if (plugin.getDbHatItems().stream().anyMatch(h -> h.equalsIgnoreCase(rawId))) {
          p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
          p.sendMessage(plugin.getMessage("hat-admin-exists", Placeholder.unparsed("id", rawId)));
          openEditorMenu(plugin, p, rawId);
          return;
        }
        plugin.addHatToDb(rawId);
        if (plugin.getNexoItem(rawId) != null) {
          p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
          p.sendMessage(plugin.getMessage("hat-admin-added", Placeholder.unparsed("id", rawId)));
          openEditorMenu(plugin, p, rawId);
        } else {
          p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
          p.sendMessage(plugin.getMessage("hat-admin-added-invalid", Placeholder.unparsed("id", rawId)));
          openAdminMenu(plugin, p, 0);
        }
        return;
      }
      if (sub.equals("edit") && args.length >= 2) { openEditorMenu(plugin, p, args[1].trim()); return; }
      if ((sub.equals("remove") || sub.equals("delete")) && args.length >= 2) {
        plugin.getHatManager().deleteHatFromConfig(p, args[1].trim());
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
    boolean hideLocked = plugin.getConfig().getBoolean("hats.hide-locked-hats", true);
    List<String> validHats = new ArrayList<>();
    for (String hatId : plugin.getDbHatItems()) {
      if (plugin.getNexoItem(hatId) == null) continue;
      if (hideLocked && !plugin.getHatManager().canUseSpecificHat(player, hatId)) continue;
      validHats.add(hatId);
    }

    int totalPages = Math.max(1, (int) Math.ceil(validHats.size() / 36.0));
    int currentPage = Math.max(0, Math.min(page, totalPages - 1));

    String rawTitle = plugin.getConfig().getString("gui.hat-title", "<white><sprite:items:item/netherite_helmet></white> <#cba6f7><bold>Custom Hat Studio</bold> <#6c7086>(<page>/<max_page>)");
    Component title = plugin.parse(rawTitle, Placeholder.unparsed("page", String.valueOf(currentPage + 1)), Placeholder.unparsed("max_page", String.valueOf(totalPages)));

    TrimMenuHolder holder = new TrimMenuHolder(TrimMenuHolder.MenuType.HAT_STUDIO, null, null, currentPage);
    Inventory inv = Bukkit.createInventory(holder, 54, title);
    holder.setInventory(inv);

    ItemStack filler = TrimMenuHolder.createFiller(plugin, Material.BLACK_STAINED_GLASS_PANE);
    for (int i = 36; i < 54; i++) inv.setItem(i, filler);

    String currentHat = plugin.getHatManager().getEffectiveHatId(player);
    String defHat = plugin.getHatManager().getDefaultHatId();
    int startIndex = currentPage * 36;
    int endIndex = Math.min(validHats.size(), startIndex + 36);

    int slotIndex = 0;
    for (int i = startIndex; i < endIndex; i++) {
      String hatId = validHats.get(i);
      ItemStack nexoStack = plugin.getNexoItem(hatId);
      if (nexoStack == null) continue;

      boolean selected = hatId.equalsIgnoreCase(currentHat);
      boolean isDefault = plugin.getHatManager().isForceDefaultHat() && defHat != null && hatId.equalsIgnoreCase(defHat);
      boolean unlocked = plugin.getHatManager().canUseSpecificHat(player, hatId);

      ItemStack icon = nexoStack.clone();
      ItemMeta meta = icon.getItemMeta();
      if (meta != null) {
        plugin.getHatManager().hideAllVanillaTooltips(meta);
        meta.displayName(plugin.parse(plugin.getItemSpriteTag(Material.NETHERITE_HELMET) + " ").append(plugin.getHatManager().getHatDisplayName(hatId, nexoStack)));
        if (selected) meta.setEnchantmentGlintOverride(true);

        List<Component> lore = new ArrayList<>(plugin.getHatManager().getHatCustomLore(hatId, nexoStack));
        if (isDefault) lore.add(plugin.parse("<#f9e2af>★ Default Server Hat"));
        if (unlocked) {
          String statusRaw = selected ? plugin.getConfig().getString("gui.selected-status", "<#a6e3a1>● Selected") : plugin.getConfig().getString("gui.unselected-status", "<#6c7086>○ Unselected");
          lore.add(plugin.parse(statusRaw));
        } else {
          String reqPerm = plugin.getHatManager().getHatRequiredPermissionNode(hatId);
          for (String line : plugin.getConfig().getStringList("gui.no-permission-lore")) lore.add(plugin.parseWithPerm(line, reqPerm));
        }
        meta.lore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        for (NamespacedKey k : new ArrayList<>(pdc.getKeys())) if (k.getNamespace().toLowerCase().contains("nexo")) pdc.remove(k);
        pdc.set(plugin.getHatManager().getItemAppliedHatKey(), PersistentDataType.STRING, hatId);
        icon.setItemMeta(meta);
      }
      plugin.getHatManager().applyHiddenComponentTooltips(icon);
      inv.setItem(slotIndex++, icon);
    }

    inv.setItem(45, TrimMenuHolder.createBackButton(plugin, "<#f9e2af><bold>Back</bold>"));
    if (currentPage > 0) inv.setItem(48, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>Previous Page</bold>", List.of(plugin.parse("<#a6adc8>Go to page <#f9e2af>" + currentPage + "<#a6adc8>/" + totalPages))));
    inv.setItem(49, TrimMenuHolder.createStyledButton(plugin, Material.BARRIER, "<#f38ba8><bold>Reset & Unequip Hat</bold>", List.of(plugin.parse("<#a6adc8>Disable custom hat and restore"), plugin.parse("<#a6adc8>your default helmet appearance."))));
    if (currentPage + 1 < totalPages) inv.setItem(50, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>Next Page</bold>", List.of(plugin.parse("<#a6adc8>Go to page <#f9e2af>" + (currentPage + 2) + "<#a6adc8>/" + totalPages))));
    if (plugin.hasAdminPerm(player)) inv.setItem(53, TrimMenuHolder.createStyledButton(plugin, Material.COMMAND_BLOCK, "<#f38ba8><bold>Admin Hat Manager</bold>", List.of(plugin.parse("<#a6adc8>Manage Nexo hats, permission"), plugin.parse("<#a6adc8>groups, or default hat."))));

    player.openInventory(inv);
  }

  public static void openAdminMenu(AntiArmorTrim plugin, Player player, int page) {
    ensureRegistered(plugin);
    List<String> hatIds = plugin.getDbHatItems();
    int totalPages = Math.max(1, (int) Math.ceil(hatIds.size() / 36.0));
    int currentPage = Math.max(0, Math.min(page, totalPages - 1));

    String rawTitle = plugin.getConfig().getString("gui.hat-admin-title", "<white><sprite:items:item/command_block></white> <#f38ba8><bold>Admin Hat Manager</bold> <#6c7086>(<page>/<max_page>)");
    Component title = plugin.parse(rawTitle, Placeholder.unparsed("page", String.valueOf(currentPage + 1)), Placeholder.unparsed("max_page", String.valueOf(totalPages)));

    TrimMenuHolder holder = new TrimMenuHolder(TrimMenuHolder.MenuType.HAT_ADMIN, null, null, currentPage);
    Inventory inv = Bukkit.createInventory(holder, 54, title);
    holder.setInventory(inv);

    ItemStack filler = TrimMenuHolder.createFiller(plugin, Material.BLACK_STAINED_GLASS_PANE);
    for (int i = 36; i < 54; i++) inv.setItem(i, filler);

    String defHat = plugin.getHatManager().getDefaultHatId();
    int startIndex = currentPage * 36;
    int endIndex = Math.min(hatIds.size(), startIndex + 36);
    int slotIndex = 0;

    for (int i = startIndex; i < endIndex; i++) {
      String hatId = hatIds.get(i);
      ItemStack nexoStack = plugin.getNexoItem(hatId);
      ItemStack icon;
      boolean isDefault = defHat != null && hatId.equalsIgnoreCase(defHat);
      List<String> groups = plugin.getHatManager().getGroupsContainingHat(hatId);

      if (nexoStack != null) {
        icon = nexoStack.clone();
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
          plugin.getHatManager().hideAllVanillaTooltips(meta);
          meta.displayName(plugin.getHatManager().getHatDisplayName(hatId, nexoStack).append(plugin.parse(" <#6c7086>(" + hatId + ")")));
          if (isDefault && plugin.getHatManager().isForceDefaultHat()) meta.setEnchantmentGlintOverride(true);
          List<Component> lore = new ArrayList<>(plugin.getHatManager().getHatCustomLore(hatId, nexoStack));
          if (!groups.isEmpty()) lore.add(plugin.parse("<#a6adc8>Groups: <#f9e2af>" + String.join(", ", groups)));
          else lore.add(plugin.parse("<#a6adc8>Perm: <#6c7086>Default (" + AntiArmorTrim.HAT_PERM + ")"));
          if (isDefault) lore.add(plugin.parse("<#f9e2af>★ Default Hat (" + (plugin.getHatManager().isForceDefaultHat() ? "<#a6e3a1>Forced" : "<#f38ba8>Disabled") + "<#f9e2af>)"));
          lore.add(Component.empty());
          lore.add(plugin.parse("<#89dceb>Left-Click: <#cdd6f4>Edit Name & Lore"));
          lore.add(plugin.parse("<#f9e2af>Shift-Left-Click: <#cdd6f4>Set as Forced Default Hat"));
          lore.add(plugin.parse("<#f38ba8>Right-Click: <#cdd6f4>Delete Hat"));
          meta.lore(lore);

          PersistentDataContainer pdc = meta.getPersistentDataContainer();
          for (NamespacedKey k : new ArrayList<>(pdc.getKeys())) if (k.getNamespace().toLowerCase().contains("nexo")) pdc.remove(k);
          pdc.set(plugin.getHatManager().getItemAppliedHatKey(), PersistentDataType.STRING, hatId);
          icon.setItemMeta(meta);
        }
        plugin.getHatManager().applyHiddenComponentTooltips(icon);
      } else {
        icon = TrimMenuHolder.createStyledButton(plugin, Material.BARRIER, "<#f38ba8><bold>Broken ID: " + hatId + "</bold>", List.of(plugin.parse("<#a6adc8>Not found in Nexo!"), Component.empty(), plugin.parse("<#f38ba8>Click to delete this broken entry")));
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
          meta.getPersistentDataContainer().set(plugin.getHatManager().getItemAppliedHatKey(), PersistentDataType.STRING, hatId);
          icon.setItemMeta(meta);
          plugin.getHatManager().applyHiddenComponentTooltips(icon);
        }
      }
      inv.setItem(slotIndex++, icon);
    }

    inv.setItem(45, TrimMenuHolder.createBackButton(plugin, "<#f9e2af><bold>Back</bold>"));
    if (currentPage > 0) inv.setItem(47, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>Previous Page</bold>", List.of(plugin.parse("<#a6adc8>Go to page <#f9e2af>" + currentPage + "<#a6adc8>/" + totalPages))));
    inv.setItem(48, TrimMenuHolder.createStyledButton(plugin, Material.ANVIL, "<#a6e3a1><bold>Add New Nexo Hat</bold>", List.of(plugin.parse("<#a6adc8>Click to type a Nexo Item ID"), plugin.parse("<#a6adc8>in chat and save to SQLite database."))));
    inv.setItem(50, TrimMenuHolder.createStyledButton(plugin, Material.BOOKSHELF, "<#cba6f7><bold>Permission Group Manager</bold>", List.of(plugin.parse("<#a6adc8>Create custom permission nodes"), plugin.parse("<#a6adc8>(e.g. <#f9e2af>armortrim.helper<#a6adc8>, <#f9e2af>armortrim.donator<#a6adc8>)"), plugin.parse("<#a6adc8>and click to select hats for each rank!"))));
    if (currentPage + 1 < totalPages) inv.setItem(51, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>Next Page</bold>", List.of(plugin.parse("<#a6adc8>Go to page <#f9e2af>" + (currentPage + 2) + "<#a6adc8>/" + totalPages))));

    String statusText = plugin.getHatManager().isForceDefaultHat() ? "<#a6e3a1>Enabled" : "<#f38ba8>Disabled";
    String defHatText = (defHat != null) ? "<#89dceb>" + defHat : "<#6c7086>None";
    inv.setItem(53, TrimMenuHolder.createStyledButton(plugin, Material.NETHER_STAR, "<#f9e2af><bold>Force Default Hat</bold>", List.of(
        plugin.parse("<#a6adc8>Status: " + statusText), plugin.parse("<#a6adc8>Default Hat: " + defHatText), Component.empty(),
        plugin.parse("<#89dceb>Left-Click: <#cdd6f4>Toggle Force Default Hat"), plugin.parse("<#f38ba8>Right-Click: <#cdd6f4>Clear Default Hat"))));

    player.openInventory(inv);
  }

  public static void openGroupListMenu(AntiArmorTrim plugin, Player player, int page) {
    ensureRegistered(plugin);
    List<String> permNodes = new ArrayList<>(plugin.getDbPermGroups().keySet());
    int totalPages = Math.max(1, (int) Math.ceil(permNodes.size() / 36.0));
    int currentPage = Math.max(0, Math.min(page, totalPages - 1));

    String rawTitle = plugin.getConfig().getString("gui.hat-groups-title", "<white><sprite:items:item/bookshelf></white> <#f9e2af><bold>Hat Perm Groups</bold> <#6c7086>(<page>/<max_page>)");
    Component title = plugin.parse(rawTitle, Placeholder.unparsed("page", String.valueOf(currentPage + 1)), Placeholder.unparsed("max_page", String.valueOf(totalPages)));

    TrimMenuHolder holder = new TrimMenuHolder(TrimMenuHolder.MenuType.HAT_GROUP_LIST, null, null, currentPage);
    Inventory inv = Bukkit.createInventory(holder, 54, title);
    holder.setInventory(inv);

    ItemStack filler = TrimMenuHolder.createFiller(plugin, Material.BLACK_STAINED_GLASS_PANE);
    for (int i = 36; i < 54; i++) inv.setItem(i, filler);

    int startIndex = currentPage * 36;
    int endIndex = Math.min(permNodes.size(), startIndex + 36);
    int slotIndex = 0;

    for (int i = startIndex; i < endIndex; i++) {
      String permNode = permNodes.get(i);
      Set<String> hatsInGroup = plugin.getDbPermGroups().getOrDefault(permNode, Set.of());
      List<Component> lore = new ArrayList<>(List.of(
          plugin.parse("<#a6adc8>Permission: <#f9e2af>" + permNode),
          plugin.parse("<#a6adc8>Selected Hats: <#a6e3a1>" + hatsInGroup.size())));
      if (!hatsInGroup.isEmpty()) {
        String previewList = String.join(", ", hatsInGroup.stream().limit(5).toList());
        if (hatsInGroup.size() > 5) previewList += "...";
        lore.add(plugin.parse("<#6c7086>(" + previewList + ")"));
      }
      lore.add(Component.empty());
      lore.add(plugin.parse("<#89dceb>Left-Click: <#cdd6f4>Select Hats for this Permission"));
      lore.add(plugin.parse("<#f38ba8>Right-Click: <#cdd6f4>Delete Permission Group"));

      ItemStack icon = TrimMenuHolder.createStyledButton(plugin, Material.TRIPWIRE_HOOK, "<#cba6f7><bold>" + permNode + "</bold>", lore);
      ItemMeta meta = icon.getItemMeta();
      if (meta != null) {
        meta.getPersistentDataContainer().set(plugin.getHatManager().getItemAppliedHatKey(), PersistentDataType.STRING, permNode);
        icon.setItemMeta(meta);
        plugin.getHatManager().applyHiddenComponentTooltips(icon);
      }
      inv.setItem(slotIndex++, icon);
    }

    inv.setItem(45, TrimMenuHolder.createBackButton(plugin, "<#f9e2af><bold>Back</bold>"));
    if (currentPage > 0) inv.setItem(47, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>Previous Page</bold>", List.of(plugin.parse("<#a6adc8>Go to page <#f9e2af>" + currentPage + "<#a6adc8>/" + totalPages))));
    inv.setItem(49, TrimMenuHolder.createStyledButton(plugin, Material.ANVIL, "<#a6e3a1><bold>Create New Permission Group</bold>", List.of(plugin.parse("<#a6adc8>Click to type a new permission node"), plugin.parse("<#a6adc8>(e.g. <#f9e2af>armortrim.helper<#a6adc8>, <#f9e2af>armortrim.media<#a6adc8>)"), plugin.parse("<#a6adc8>and select hats for it!"))));
    if (currentPage + 1 < totalPages) inv.setItem(51, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>Next Page</bold>", List.of(plugin.parse("<#a6adc8>Go to page <#f9e2af>" + (currentPage + 2) + "<#a6adc8>/" + totalPages))));

    player.openInventory(inv);
  }

  public static void openGroupSelectorMenu(AntiArmorTrim plugin, Player player, String permNode, int page) {
    ensureRegistered(plugin);
    List<String> hatIds = plugin.getDbHatItems();
    int totalPages = Math.max(1, (int) Math.ceil(hatIds.size() / 36.0));
    int currentPage = Math.max(0, Math.min(page, totalPages - 1));

    String rawTitle = plugin.getConfig().getString("gui.hat-group-select-title", "<white><sprite:items:item/tripwire_hook></white> <#cba6f7><bold>Group:</bold> <#89dceb><perm> <#6c7086>(<page>/<max_page>)");
    Component title = plugin.parse(rawTitle, Placeholder.unparsed("perm", permNode), Placeholder.unparsed("page", String.valueOf(currentPage + 1)), Placeholder.unparsed("max_page", String.valueOf(totalPages)));

    TrimMenuHolder holder = new TrimMenuHolder(TrimMenuHolder.MenuType.HAT_GROUP_SELECTOR, null, permNode, currentPage);
    Inventory inv = Bukkit.createInventory(holder, 54, title);
    holder.setInventory(inv);

    ItemStack filler = TrimMenuHolder.createFiller(plugin, Material.BLACK_STAINED_GLASS_PANE);
    for (int i = 36; i < 54; i++) inv.setItem(i, filler);

    int startIndex = currentPage * 36;
    int endIndex = Math.min(hatIds.size(), startIndex + 36);
    int slotIndex = 0;

    for (int i = startIndex; i < endIndex; i++) {
      String hatId = hatIds.get(i);
      ItemStack nexoStack = plugin.getNexoItem(hatId);
      if (nexoStack == null) continue;

      boolean inGroup = plugin.isHatInDbPermGroup(permNode, hatId);
      ItemStack icon = nexoStack.clone();
      ItemMeta meta = icon.getItemMeta();
      if (meta != null) {
        plugin.getHatManager().hideAllVanillaTooltips(meta);
        meta.displayName(plugin.getHatManager().getHatDisplayName(hatId, nexoStack).append(plugin.parse(" <#6c7086>(" + hatId + ")")));
        if (inGroup) meta.setEnchantmentGlintOverride(true);

        List<Component> lore = new ArrayList<>(plugin.getHatManager().getHatCustomLore(hatId, nexoStack));
        lore.add(Component.empty());
        if (inGroup) {
          lore.add(plugin.parse("<#a6e3a1>● Included in <#f9e2af>" + permNode));
          lore.add(plugin.parse("<#fab387>Click to remove from this group"));
        } else {
          lore.add(plugin.parse("<#6c7086>○ Not in <#f9e2af>" + permNode));
          lore.add(plugin.parse("<#89dceb>Click to add to this group"));
        }
        meta.lore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        for (NamespacedKey k : new ArrayList<>(pdc.getKeys())) if (k.getNamespace().toLowerCase().contains("nexo")) pdc.remove(k);
        pdc.set(plugin.getHatManager().getItemAppliedHatKey(), PersistentDataType.STRING, hatId);
        icon.setItemMeta(meta);
      }
      plugin.getHatManager().applyHiddenComponentTooltips(icon);
      inv.setItem(slotIndex++, icon);
    }

    inv.setItem(45, TrimMenuHolder.createBackButton(plugin, "<#f9e2af><bold>Back to Groups</bold>"));
    if (currentPage > 0) inv.setItem(47, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>Previous Page</bold>", List.of(plugin.parse("<#a6adc8>Go to page <#f9e2af>" + currentPage + "<#a6adc8>/" + totalPages))));
    Set<String> selectedSet = plugin.getDbPermGroups().getOrDefault(permNode.toLowerCase(), Set.of());
    inv.setItem(49, TrimMenuHolder.createStyledButton(plugin, Material.BOOKSHELF, "<#f9e2af><bold>Editing: " + permNode + "</bold>", List.of(plugin.parse("<#a6adc8>Total Hats Selected: <#a6e3a1>" + selectedSet.size()), plugin.parse("<#a6adc8>Click any hat above to toggle it!"))));
    if (currentPage + 1 < totalPages) inv.setItem(51, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>Next Page</bold>", List.of(plugin.parse("<#a6adc8>Go to page <#f9e2af>" + (currentPage + 2) + "<#a6adc8>/" + totalPages))));

    player.openInventory(inv);
  }

  public static void openEditorMenu(AntiArmorTrim plugin, Player player, String hatId) {
    ensureRegistered(plugin);
    String rawTitle = plugin.getConfig().getString("gui.hat-editor-title", "<white><sprite:items:item/anvil></white> <#cba6f7><bold>Edit Hat:</bold> <#89dceb><id>");
    TrimMenuHolder holder = new TrimMenuHolder(TrimMenuHolder.MenuType.HAT_EDITOR, null, hatId, 0);
    Inventory inv = Bukkit.createInventory(holder, 27, plugin.parse(rawTitle, Placeholder.unparsed("id", hatId)));
    holder.setInventory(inv);

    ItemStack border = TrimMenuHolder.createFiller(plugin, Material.GRAY_STAINED_GLASS_PANE);
    for (int i = 0; i < 27; i++) inv.setItem(i, border);

    ItemStack nexoStack = plugin.getNexoItem(hatId);
    inv.setItem(10, TrimMenuHolder.createStyledButton(plugin, Material.NAME_TAG, "<#89dceb><bold>Edit Display Name</bold>", List.of(
        plugin.parse("<#a6adc8>Current: ").append(plugin.getHatManager().getHatDisplayName(hatId, nexoStack)),
        Component.empty(), plugin.parse("<#89dceb>Left-Click: <#cdd6f4>Set new display name"), plugin.parse("<#fab387>Right-Click: <#cdd6f4>Reset to Nexo default"))));

    List<Component> loreList = new ArrayList<>();
    List<Component> currentLore = plugin.getHatManager().getHatCustomLore(hatId, nexoStack);
    if (currentLore.isEmpty()) loreList.add(plugin.parse("<#6c7086>No lore configured"));
    else loreList.addAll(currentLore);
    loreList.add(Component.empty());
    loreList.add(plugin.parse("<#89dceb>Left-Click: <#cdd6f4>Set or append lore in chat"));
    loreList.add(plugin.parse("<#fab387>Right-Click: <#cdd6f4>Clear custom lore"));
    inv.setItem(11, TrimMenuHolder.createStyledButton(plugin, Material.WRITABLE_BOOK, "<#cba6f7><bold>Edit Hat Lore</bold>", loreList));

    List<String> groups = plugin.getHatManager().getGroupsContainingHat(hatId);
    inv.setItem(12, TrimMenuHolder.createStyledButton(plugin, Material.BOOKSHELF, "<#a6e3a1><bold>Manage Permission Groups</bold>", List.of(
        plugin.parse("<#a6adc8>Assigned Groups: <#f9e2af>" + (groups.isEmpty() ? "None (Default)" : String.join(", ", groups))),
        Component.empty(), plugin.parse("<#89dceb>Click: <#cdd6f4>Open Permission Group Manager"))));

    ItemStack previewHat = plugin.getHatManager().createCosmeticHatFromHelmet(new ItemStack(Material.NETHERITE_HELMET), hatId);
    if (previewHat != null) inv.setItem(14, previewHat);

    boolean isDef = plugin.getHatManager().isForceDefaultHat() && hatId.equalsIgnoreCase(plugin.getHatManager().getDefaultHatId());
    inv.setItem(15, TrimMenuHolder.createStyledButton(plugin, Material.NETHER_STAR, "<#f9e2af><bold>Force Default Hat</bold>", List.of(
        plugin.parse("<#a6adc8>Current: " + (isDef ? "<#a6e3a1>Active Default Hat" : "<#6c7086>Not Default")),
        Component.empty(), plugin.parse("<#89dceb>Left-Click: <#cdd6f4>Set as Forced Default Hat"), plugin.parse("<#fab387>Right-Click: <#cdd6f4>Disable Forced Default Hat"))));

    inv.setItem(16, TrimMenuHolder.createStyledButton(plugin, Material.BARRIER, "<#f38ba8><bold>Delete Hat</bold>", List.of(plugin.parse("<#a6adc8>Remove <#f9e2af>" + hatId + " <#a6adc8>from the database."))));
    inv.setItem(18, TrimMenuHolder.createBackButton(plugin, "<#f9e2af><bold>Back</bold>"));
    player.openInventory(inv);
  }

  @EventHandler
  public void onClick(InventoryClickEvent event) {
    if (!(event.getInventory().getHolder() instanceof TrimMenuHolder holder)) return;
    TrimMenuHolder.MenuType type = holder.getMenuType();
    if (type != TrimMenuHolder.MenuType.HAT_STUDIO && type != TrimMenuHolder.MenuType.HAT_ADMIN
        && type != TrimMenuHolder.MenuType.HAT_GROUP_LIST && type != TrimMenuHolder.MenuType.HAT_GROUP_SELECTOR
        && type != TrimMenuHolder.MenuType.HAT_EDITOR) return;
    event.setCancelled(true);
    if (!(event.getWhoClicked() instanceof Player player)) return;

    int slot = event.getRawSlot();
    int page = holder.getPage();

    if (type == TrimMenuHolder.MenuType.HAT_STUDIO) {
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
        plugin.getHatManager().setSavedHatId(player, null);
        plugin.getHatManager().forceRefreshEquippedHat(player);
        plugin.getHatManager().applyAllSavedTrims(player);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1.2f);
        player.sendMessage(plugin.getMessage("hat-cleared"));
        openPlayerMenu(plugin, player, page);
        return;
      }
      if (slot < 36) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || clicked.getItemMeta() == null) return;
        String hatId = clicked.getItemMeta().getPersistentDataContainer().get(plugin.getHatManager().getItemAppliedHatKey(), PersistentDataType.STRING);
        if (hatId == null) return;

        if (!plugin.getHatManager().canUseSpecificHat(player, hatId)) {
          player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
          player.sendMessage(plugin.getMessageWithPerm("no-permission-gui", plugin.getHatManager().getHatRequiredPermissionNode(hatId)));
          return;
        }

        plugin.getHatManager().setSavedHatId(player, hatId);
        plugin.getHatManager().forceRefreshEquippedHat(player);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 1.2f);
        player.sendMessage(plugin.getMessage("hat-selected", Placeholder.component("hat", plugin.getHatManager().getHatDisplayName(hatId, plugin.getNexoItem(hatId)))));
        openPlayerMenu(plugin, player, page);
      }
      return;
    }

    if (type == TrimMenuHolder.MenuType.HAT_ADMIN) {
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
        pendingAddHatPlayers.add(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.3f);
        player.sendMessage(plugin.getMessage("hat-admin-prompt-id"));
        return;
      }
      if (slot == 50) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        openGroupListMenu(plugin, player, 0);
        return;
      }
      if (slot == 53) {
        if (event.isRightClick()) {
          plugin.getHatManager().setForceDefaultHat(player, null, false);
        } else {
          boolean nextState = !plugin.getHatManager().isForceDefaultHat();
          String def = plugin.getHatManager().getDefaultHatId();
          if (nextState && def == null && !plugin.getDbHatItems().isEmpty()) def = plugin.getDbHatItems().get(0);
          plugin.setDefaultHatInDb(def, nextState);
          for (Player online : Bukkit.getOnlinePlayers()) {
            plugin.getHatManager().forceRefreshEquippedHat(online);
            plugin.getHatManager().applyAllSavedTrims(online);
          }
          player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
          player.sendMessage(plugin.getMessage("hat-admin-default-toggled", Placeholder.parsed("state", nextState ? "<#a6e3a1>Enabled" : "<#f38ba8>Disabled")));
        }
        openAdminMenu(plugin, player, page);
        return;
      }
      if (slot < 36) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || clicked.getItemMeta() == null) return;
        String hatId = clicked.getItemMeta().getPersistentDataContainer().get(plugin.getHatManager().getItemAppliedHatKey(), PersistentDataType.STRING);
        if (hatId == null) return;

        if (plugin.getNexoItem(hatId) == null || event.isRightClick()) {
          plugin.getHatManager().deleteHatFromConfig(player, hatId);
          openAdminMenu(plugin, player, page);
          return;
        }
        if (event.isShiftClick()) {
          plugin.getHatManager().setForceDefaultHat(player, hatId, true);
          openAdminMenu(plugin, player, page);
          return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        openEditorMenu(plugin, player, hatId);
      }
      return;
    }

    if (type == TrimMenuHolder.MenuType.HAT_GROUP_LIST) {
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
        pendingCreateGroupPlayers.add(player.getUniqueId());
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
          plugin.deleteDbPermGroup(permNode);
          for (Player online : Bukkit.getOnlinePlayers()) {
            plugin.getHatManager().forceRefreshEquippedHat(online);
            plugin.getHatManager().applyAllSavedTrims(online);
          }
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

    if (type == TrimMenuHolder.MenuType.HAT_GROUP_SELECTOR) {
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
        String hatId = clicked.getItemMeta().getPersistentDataContainer().get(plugin.getHatManager().getItemAppliedHatKey(), PersistentDataType.STRING);
        if (hatId == null) return;

        boolean added = plugin.toggleHatInDbPermGroup(permNode, hatId);
        for (Player online : Bukkit.getOnlinePlayers()) {
          plugin.getHatManager().forceRefreshEquippedHat(online);
          plugin.getHatManager().applyAllSavedTrims(online);
        }
        if (added) {
          player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
          player.sendMessage(plugin.getMessage("hat-admin-group-hat-added", Placeholder.unparsed("id", hatId), Placeholder.unparsed("perm", permNode)));
        } else {
          player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1.2f);
          player.sendMessage(plugin.getMessage("hat-admin-group-hat-removed", Placeholder.unparsed("id", hatId), Placeholder.unparsed("perm", permNode)));
        }
        openGroupSelectorMenu(plugin, player, permNode, page);
      }
      return;
    }

    if (type == TrimMenuHolder.MenuType.HAT_EDITOR) {
      if (!plugin.hasAdminPerm(player)) { player.closeInventory(); return; }
      String hatId = holder.getEditingId();
      if (hatId == null) return;

      if (slot == 18) { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f); openAdminMenu(plugin, player, 0); return; }
      if (slot == 10) {
        if (event.isRightClick()) {
          plugin.setHatNameInDb(hatId, null);
          for (Player online : Bukkit.getOnlinePlayers()) {
            if (hatId.equalsIgnoreCase(plugin.getHatManager().getEffectiveHatId(online))) plugin.getHatManager().forceRefreshEquippedHat(online);
          }
          player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1.2f);
          player.sendMessage(plugin.getMessage("hat-admin-name-reset", Placeholder.unparsed("id", hatId)));
          openEditorMenu(plugin, player, hatId);
          return;
        }
        player.closeInventory();
        pendingHatNameEdit.put(player.getUniqueId(), hatId);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.3f);
        player.sendMessage(plugin.getMessage("hat-admin-prompt-name", Placeholder.unparsed("id", hatId)));
        return;
      }
      if (slot == 11) {
        if (event.isRightClick()) {
          plugin.setHatLoreInDb(hatId, null);
          for (Player online : Bukkit.getOnlinePlayers()) {
            if (hatId.equalsIgnoreCase(plugin.getHatManager().getEffectiveHatId(online))) plugin.getHatManager().forceRefreshEquippedHat(online);
          }
          player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1.2f);
          player.sendMessage(plugin.getMessage("hat-admin-lore-reset", Placeholder.unparsed("id", hatId)));
          openEditorMenu(plugin, player, hatId);
          return;
        }
        player.closeInventory();
        pendingHatLoreEdit.put(player.getUniqueId(), hatId);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.3f);
        player.sendMessage(plugin.getMessage("hat-admin-prompt-lore", Placeholder.unparsed("id", hatId)));
        return;
      }
      if (slot == 12) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        openGroupListMenu(plugin, player, 0);
        return;
      }
      if (slot == 15) {
        plugin.getHatManager().setForceDefaultHat(player, event.isRightClick() ? null : hatId, !event.isRightClick());
        openEditorMenu(plugin, player, hatId);
        return;
      }
      if (slot == 16) {
        plugin.getHatManager().deleteHatFromConfig(player, hatId);
        openAdminMenu(plugin, player, 0);
      }
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onChatInput(AsyncChatEvent event) {
    Player player = event.getPlayer();
    UUID uuid = player.getUniqueId();

    if (pendingCreateGroupPlayers.remove(uuid)) {
      event.setCancelled(true);
      String rawPerm = event.signedMessage().message().trim();
      Bukkit.getScheduler().runTask(plugin, () -> {
        if (!player.isOnline()) return;
        if (rawPerm.equalsIgnoreCase("cancel")) {
          player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.8f);
          player.sendMessage(plugin.getMessage("hat-admin-cancelled"));
          openGroupListMenu(plugin, player, 0);
          return;
        }
        String normalized = plugin.normalizePermNode(rawPerm);
        plugin.createDbPermGroup(normalized);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
        player.sendMessage(plugin.getMessage("hat-admin-group-created", Placeholder.unparsed("perm", normalized)));
        openGroupSelectorMenu(plugin, player, normalized, 0);
      });
      return;
    }

    if (pendingAddHatPlayers.remove(uuid)) {
      event.setCancelled(true);
      String rawId = event.signedMessage().message().trim();
      Bukkit.getScheduler().runTask(plugin, () -> {
        if (!player.isOnline()) return;
        if (rawId.equalsIgnoreCase("cancel")) {
          player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.8f);
          player.sendMessage(plugin.getMessage("hat-admin-cancelled"));
          openAdminMenu(plugin, player, 0);
          return;
        }
        if (plugin.getDbHatItems().stream().anyMatch(h -> h.equalsIgnoreCase(rawId))) {
          player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
          player.sendMessage(plugin.getMessage("hat-admin-exists", Placeholder.unparsed("id", rawId)));
          openAdminMenu(plugin, player, 0);
          return;
        }
        plugin.addHatToDb(rawId);
        if (plugin.getNexoItem(rawId) != null) {
          player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
          player.sendMessage(plugin.getMessage("hat-admin-added", Placeholder.unparsed("id", rawId)));
          openEditorMenu(plugin, player, rawId);
        } else {
          player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
          player.sendMessage(plugin.getMessage("hat-admin-added-invalid", Placeholder.unparsed("id", rawId)));
          openAdminMenu(plugin, player, 0);
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
          openEditorMenu(plugin, player, editingNameHatId);
          return;
        }
        plugin.setHatNameInDb(editingNameHatId, rawInput);
        for (Player online : Bukkit.getOnlinePlayers()) {
          if (editingNameHatId.equalsIgnoreCase(plugin.getHatManager().getEffectiveHatId(online))) plugin.getHatManager().forceRefreshEquippedHat(online);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.3f);
        player.sendMessage(plugin.getMessage("hat-admin-name-updated", Placeholder.unparsed("id", editingNameHatId), Placeholder.component("name", plugin.parse(rawInput))));
        openEditorMenu(plugin, player, editingNameHatId);
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
          openEditorMenu(plugin, player, editingLoreHatId);
          return;
        }
        List<String> newLore;
        if (rawInput.regionMatches(true, 0, "add ", 0, 4)) {
          List<String> existing = plugin.getDbHatLore(editingLoreHatId);
          newLore = existing != null ? new ArrayList<>(existing) : new ArrayList<>();
          newLore.add(rawInput.substring(4).trim());
        } else {
          newLore = new ArrayList<>(Arrays.asList(rawInput.split("\\|")));
          newLore.replaceAll(String::trim);
        }
        plugin.setHatLoreInDb(editingLoreHatId, newLore);
        for (Player online : Bukkit.getOnlinePlayers()) {
          if (editingLoreHatId.equalsIgnoreCase(plugin.getHatManager().getEffectiveHatId(online))) plugin.getHatManager().forceRefreshEquippedHat(online);
        }
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.2f);
        player.sendMessage(plugin.getMessage("hat-admin-lore-updated", Placeholder.unparsed("id", editingLoreHatId)));
        openEditorMenu(plugin, player, editingLoreHatId);
      });
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    UUID uuid = event.getPlayer().getUniqueId();
    pendingAddHatPlayers.remove(uuid);
    pendingCreateGroupPlayers.remove(uuid);
    pendingHatNameEdit.remove(uuid);
    pendingHatLoreEdit.remove(uuid);
  }
}
