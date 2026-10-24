package com.server.antitrim.gui;

import com.server.antitrim.AntiArmorTrim;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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

public class KillSoundStudioGui implements Listener {

  private final AntiArmorTrim plugin;
  private static boolean registered = false;

  private final Set<UUID> pendingAddKillSoundPlayers = ConcurrentHashMap.newKeySet();
  private final Set<UUID> pendingCreateKillSoundGroupPlayers = ConcurrentHashMap.newKeySet();
  private final Map<UUID, String> pendingKillSoundNameEdit = new ConcurrentHashMap<>();
  private final Map<UUID, String> pendingKillSoundLoreEdit = new ConcurrentHashMap<>();

  public KillSoundStudioGui(AntiArmorTrim plugin) {
    this.plugin = plugin;
  }

  public static synchronized void ensureRegistered(AntiArmorTrim plugin) {
    if (!registered) {
      Bukkit.getPluginManager().registerEvents(new KillSoundStudioGui(plugin), plugin);
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
        plugin.getKillSoundManager().setForceDefaultKillSound(p, off ? null : rawId, !off);
        return;
      }
      if (sub.equals("add") && args.length >= 2) {
        String rawId = args[1].trim();
        if (plugin.getDbKillSoundItems().stream().anyMatch(s -> s.equalsIgnoreCase(rawId))) {
          p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
          p.sendMessage(plugin.getMessage("hat-admin-exists", Placeholder.unparsed("id", rawId)));
          openEditorMenu(plugin, p, rawId);
          return;
        }
        plugin.addKillSoundToDb(rawId);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
        p.sendMessage(plugin.getMessage("killsound-admin-added", Placeholder.unparsed("id", rawId)));
        openEditorMenu(plugin, p, rawId);
        return;
      }
      if (sub.equals("edit") && args.length >= 2) { openEditorMenu(plugin, p, args[1].trim()); return; }
      if ((sub.equals("remove") || sub.equals("delete")) && args.length >= 2) {
        if (plugin.removeKillSoundFromDb(args[1].trim())) {
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
    boolean hideLocked = plugin.getConfig().getBoolean("killsounds.hide-locked-killsounds", true);
    List<String> validSounds = new ArrayList<>();
    for (String soundId : plugin.getDbKillSoundItems()) {
      if (hideLocked && !plugin.getKillSoundManager().canUseSpecificKillSound(player, soundId)) continue;
      validSounds.add(soundId);
    }

    int totalPages = Math.max(1, (int) Math.ceil(validSounds.size() / 36.0));
    int currentPage = Math.max(0, Math.min(page, totalPages - 1));

    String rawTitle = plugin.getConfig().getString("gui.killsound-title", "<white><sprite:items:item/music_disc_otherside></white> <#cba6f7><bold>KillSound Studio</bold> <#6c7086>(<page>/<max_page>)");
    Component title = plugin.parse(rawTitle, Placeholder.unparsed("page", String.valueOf(currentPage + 1)), Placeholder.unparsed("max_page", String.valueOf(totalPages)));

    TrimMenuHolder holder = new TrimMenuHolder(TrimMenuHolder.MenuType.KILLSOUND_STUDIO, null, null, currentPage);
    Inventory inv = Bukkit.createInventory(holder, 54, title);
    holder.setInventory(inv);

    ItemStack filler = TrimMenuHolder.createFiller(plugin, Material.BLACK_STAINED_GLASS_PANE);
    for (int i = 36; i < 54; i++) inv.setItem(i, filler);

    String currentSound = plugin.getKillSoundManager().getEffectiveKillSoundId(player);
    String defSound = plugin.getDbDefaultKillSound();
    int startIndex = currentPage * 36;
    int endIndex = Math.min(validSounds.size(), startIndex + 36);

    int slotIndex = 0;
    for (int i = startIndex; i < endIndex; i++) {
      String soundId = validSounds.get(i);
      boolean selected = soundId.equalsIgnoreCase(currentSound);
      boolean isDefault = plugin.isDbForceDefaultKillSound() && defSound != null && soundId.equalsIgnoreCase(defSound);
      boolean unlocked = plugin.getKillSoundManager().canUseSpecificKillSound(player, soundId);

      ItemStack icon = plugin.getHatManager().createCleanIconStack(Material.MUSIC_DISC_OTHERSIDE);
      ItemMeta meta = icon.getItemMeta();
      if (meta != null) {
        meta.displayName(plugin.parse(plugin.getItemSpriteTag(Material.MUSIC_DISC_OTHERSIDE) + " ").append(plugin.getKillSoundManager().getKillSoundDisplayName(soundId)));
        if (selected) meta.setEnchantmentGlintOverride(true);

        List<Component> lore = new ArrayList<>(plugin.getKillSoundManager().getKillSoundCustomLore(soundId));
        lore.add(plugin.parse("<#6c7086>Sound: " + soundId));
        if (isDefault) lore.add(plugin.parse("<#f9e2af>★ Default Server KillSound"));
        lore.add(plugin.parse("<#89dceb>Right-Click: <#cdd6f4>Preview Sound"));
        if (unlocked) {
          String statusRaw = selected ? plugin.getConfig().getString("gui.selected-status", "<#a6e3a1>● Selected") : plugin.getConfig().getString("gui.unselected-status", "<#6c7086>○ Unselected");
          lore.add(plugin.parse(statusRaw));
        } else {
          String reqPerm = plugin.getKillSoundManager().getKillSoundRequiredPermissionNode(soundId);
          for (String line : plugin.getConfig().getStringList("gui.no-permission-lore")) lore.add(plugin.parseWithPerm(line, reqPerm));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(plugin.getHatManager().getItemAppliedHatKey(), PersistentDataType.STRING, soundId);
        icon.setItemMeta(meta);
      }
      plugin.getHatManager().applyHiddenComponentTooltips(icon);
      inv.setItem(slotIndex++, icon);
    }

    inv.setItem(45, TrimMenuHolder.createBackButton(plugin, "<#f9e2af><bold>Back</bold>"));
    if (currentPage > 0) inv.setItem(48, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>Previous Page</bold>", List.of(plugin.parse("<#a6adc8>Go to page <#f9e2af>" + currentPage + "<#a6adc8>/" + totalPages))));
    inv.setItem(49, TrimMenuHolder.createStyledButton(plugin, Material.BARRIER, "<#f38ba8><bold>Reset & Disable KillSound</bold>", List.of(plugin.parse("<#a6adc8>Disable custom KillSound."))));
    if (currentPage + 1 < totalPages) inv.setItem(50, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>Next Page</bold>", List.of(plugin.parse("<#a6adc8>Go to page <#f9e2af>" + (currentPage + 2) + "<#a6adc8>/" + totalPages))));
    if (plugin.hasAdminPerm(player)) inv.setItem(53, TrimMenuHolder.createStyledButton(plugin, Material.COMMAND_BLOCK, "<#f38ba8><bold>Admin KillSound Manager</bold>", List.of(plugin.parse("<#a6adc8>Manage Nexo KillSounds, permission"), plugin.parse("<#a6adc8>groups, or default sound."))));

    player.openInventory(inv);
  }

  public static void openAdminMenu(AntiArmorTrim plugin, Player player, int page) {
    ensureRegistered(plugin);
    List<String> soundIds = plugin.getDbKillSoundItems();
    int totalPages = Math.max(1, (int) Math.ceil(soundIds.size() / 36.0));
    int currentPage = Math.max(0, Math.min(page, totalPages - 1));

    String rawTitle = plugin.getConfig().getString("gui.killsound-admin-title", "<white><sprite:items:item/command_block></white> <#f38ba8><bold>Admin KillSound Manager</bold> <#6c7086>(<page>/<max_page>)");
    Component title = plugin.parse(rawTitle, Placeholder.unparsed("page", String.valueOf(currentPage + 1)), Placeholder.unparsed("max_page", String.valueOf(totalPages)));

    TrimMenuHolder holder = new TrimMenuHolder(TrimMenuHolder.MenuType.KILLSOUND_ADMIN, null, null, currentPage);
    Inventory inv = Bukkit.createInventory(holder, 54, title);
    holder.setInventory(inv);

    ItemStack filler = TrimMenuHolder.createFiller(plugin, Material.BLACK_STAINED_GLASS_PANE);
    for (int i = 36; i < 54; i++) inv.setItem(i, filler);

    String defSound = plugin.getDbDefaultKillSound();
    int startIndex = currentPage * 36;
    int endIndex = Math.min(soundIds.size(), startIndex + 36);
    int slotIndex = 0;

    for (int i = startIndex; i < endIndex; i++) {
      String soundId = soundIds.get(i);
      boolean isDefault = defSound != null && soundId.equalsIgnoreCase(defSound);
      List<String> groups = plugin.getKillSoundManager().getGroupsContainingKillSound(soundId);

      ItemStack icon = plugin.getHatManager().createCleanIconStack(Material.MUSIC_DISC_OTHERSIDE);
      ItemMeta meta = icon.getItemMeta();
      if (meta != null) {
        meta.displayName(plugin.getKillSoundManager().getKillSoundDisplayName(soundId).append(plugin.parse(" <#6c7086>(" + soundId + ")")));
        if (isDefault && plugin.isDbForceDefaultKillSound()) meta.setEnchantmentGlintOverride(true);
        List<Component> lore = new ArrayList<>(plugin.getKillSoundManager().getKillSoundCustomLore(soundId));
        if (!groups.isEmpty()) lore.add(plugin.parse("<#a6adc8>Groups: <#f9e2af>" + String.join(", ", groups)));
        else lore.add(plugin.parse("<#a6adc8>Perm: <#6c7086>Default (" + AntiArmorTrim.KILLSOUND_PERM + ")"));
        if (isDefault) lore.add(plugin.parse("<#f9e2af>★ Default KillSound (" + (plugin.isDbForceDefaultKillSound() ? "<#a6e3a1>Forced" : "<#f38ba8>Disabled") + "<#f9e2af>)"));
        lore.add(Component.empty());
        lore.add(plugin.parse("<#89dceb>Left-Click: <#cdd6f4>Edit Name & Lore"));
        lore.add(plugin.parse("<#f9e2af>Shift-Left-Click: <#cdd6f4>Set as Forced Default KillSound"));
        lore.add(plugin.parse("<#f38ba8>Right-Click: <#cdd6f4>Delete KillSound"));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(plugin.getHatManager().getItemAppliedHatKey(), PersistentDataType.STRING, soundId);
        icon.setItemMeta(meta);
      }
      plugin.getHatManager().applyHiddenComponentTooltips(icon);
      inv.setItem(slotIndex++, icon);
    }

    inv.setItem(45, TrimMenuHolder.createBackButton(plugin, "<#f9e2af><bold>Back</bold>"));
    if (currentPage > 0) inv.setItem(47, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>Previous Page</bold>", List.of(plugin.parse("<#a6adc8>Go to page <#f9e2af>" + currentPage + "<#a6adc8>/" + totalPages))));
    inv.setItem(48, TrimMenuHolder.createStyledButton(plugin, Material.ANVIL, "<#a6e3a1><bold>Add New Nexo KillSound</bold>", List.of(plugin.parse("<#a6adc8>Click to type a Nexo Sound ID"), plugin.parse("<#a6adc8>(from Nexo/sounds.yml) in chat."))));
    inv.setItem(50, TrimMenuHolder.createStyledButton(plugin, Material.BOOKSHELF, "<#cba6f7><bold>KillSound Perm Groups</bold>", List.of(plugin.parse("<#a6adc8>Create custom permission nodes"), plugin.parse("<#a6adc8>(e.g. <#f9e2af>armortrim.helper<#a6adc8>, <#f9e2af>armortrim.donator<#a6adc8>)"), plugin.parse("<#a6adc8>and select KillSounds for each rank!"))));
    if (currentPage + 1 < totalPages) inv.setItem(51, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>Next Page</bold>", List.of(plugin.parse("<#a6adc8>Go to page <#f9e2af>" + (currentPage + 2) + "<#a6adc8>/" + totalPages))));

    String statusText = plugin.isDbForceDefaultKillSound() ? "<#a6e3a1>Enabled" : "<#f38ba8>Disabled";
    String defText = (defSound != null) ? "<#89dceb>" + defSound : "<#6c7086>None";
    inv.setItem(53, TrimMenuHolder.createStyledButton(plugin, Material.NETHER_STAR, "<#f9e2af><bold>Force Default KillSound</bold>", List.of(
        plugin.parse("<#a6adc8>Status: " + statusText), plugin.parse("<#a6adc8>Default Sound: " + defText), Component.empty(),
        plugin.parse("<#89dceb>Left-Click: <#cdd6f4>Toggle Force Default"), plugin.parse("<#f38ba8>Right-Click: <#cdd6f4>Clear Default"))));

    player.openInventory(inv);
  }

  public static void openGroupListMenu(AntiArmorTrim plugin, Player player, int page) {
    ensureRegistered(plugin);
    List<String> permNodes = new ArrayList<>(plugin.getDbKillSoundGroups().keySet());
    int totalPages = Math.max(1, (int) Math.ceil(permNodes.size() / 36.0));
    int currentPage = Math.max(0, Math.min(page, totalPages - 1));

    String rawTitle = plugin.getConfig().getString("gui.killsound-groups-title", "<white><sprite:items:item/bookshelf></white> <#f9e2af><bold>KillSound Perm Groups</bold> <#6c7086>(<page>/<max_page>)");
    Component title = plugin.parse(rawTitle, Placeholder.unparsed("page", String.valueOf(currentPage + 1)), Placeholder.unparsed("max_page", String.valueOf(totalPages)));

    TrimMenuHolder holder = new TrimMenuHolder(TrimMenuHolder.MenuType.KILLSOUND_GROUP_LIST, null, null, currentPage);
    Inventory inv = Bukkit.createInventory(holder, 54, title);
    holder.setInventory(inv);

    ItemStack filler = TrimMenuHolder.createFiller(plugin, Material.BLACK_STAINED_GLASS_PANE);
    for (int i = 36; i < 54; i++) inv.setItem(i, filler);

    int startIndex = currentPage * 36;
    int endIndex = Math.min(permNodes.size(), startIndex + 36);
    int slotIndex = 0;

    for (int i = startIndex; i < endIndex; i++) {
      String permNode = permNodes.get(i);
      Set<String> soundsInGroup = plugin.getDbKillSoundGroups().getOrDefault(permNode, Set.of());
      List<Component> lore = new ArrayList<>(List.of(
          plugin.parse("<#a6adc8>Permission: <#f9e2af>" + permNode),
          plugin.parse("<#a6adc8>Selected KillSounds: <#a6e3a1>" + soundsInGroup.size())));
      if (!soundsInGroup.isEmpty()) {
        String previewList = String.join(", ", soundsInGroup.stream().limit(5).toList());
        if (soundsInGroup.size() > 5) previewList += "...";
        lore.add(plugin.parse("<#6c7086>(" + previewList + ")"));
      }
      lore.add(Component.empty());
      lore.add(plugin.parse("<#89dceb>Left-Click: <#cdd6f4>Select KillSounds for this Permission"));
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
    inv.setItem(49, TrimMenuHolder.createStyledButton(plugin, Material.ANVIL, "<#a6e3a1><bold>Create New Permission Group</bold>", List.of(plugin.parse("<#a6adc8>Click to type a new permission node"), plugin.parse("<#a6adc8>(e.g. <#f9e2af>armortrim.helper<#a6adc8>, <#f9e2af>armortrim.media<#a6adc8>)"), plugin.parse("<#a6adc8>and select KillSounds for it!"))));
    if (currentPage + 1 < totalPages) inv.setItem(51, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>Next Page</bold>", List.of(plugin.parse("<#a6adc8>Go to page <#f9e2af>" + (currentPage + 2) + "<#a6adc8>/" + totalPages))));

    player.openInventory(inv);
  }

  public static void openGroupSelectorMenu(AntiArmorTrim plugin, Player player, String permNode, int page) {
    ensureRegistered(plugin);
    List<String> soundIds = plugin.getDbKillSoundItems();
    int totalPages = Math.max(1, (int) Math.ceil(soundIds.size() / 36.0));
    int currentPage = Math.max(0, Math.min(page, totalPages - 1));

    String rawTitle = plugin.getConfig().getString("gui.killsound-group-select-title", "<white><sprite:items:item/tripwire_hook></white> <#cba6f7><bold>KS Group:</bold> <#89dceb><perm> <#6c7086>(<page>/<max_page>)");
    Component title = plugin.parse(rawTitle, Placeholder.unparsed("perm", permNode), Placeholder.unparsed("page", String.valueOf(currentPage + 1)), Placeholder.unparsed("max_page", String.valueOf(totalPages)));

    TrimMenuHolder holder = new TrimMenuHolder(TrimMenuHolder.MenuType.KILLSOUND_GROUP_SELECTOR, null, permNode, currentPage);
    Inventory inv = Bukkit.createInventory(holder, 54, title);
    holder.setInventory(inv);

    ItemStack filler = TrimMenuHolder.createFiller(plugin, Material.BLACK_STAINED_GLASS_PANE);
    for (int i = 36; i < 54; i++) inv.setItem(i, filler);

    int startIndex = currentPage * 36;
    int endIndex = Math.min(soundIds.size(), startIndex + 36);
    int slotIndex = 0;

    for (int i = startIndex; i < endIndex; i++) {
      String soundId = soundIds.get(i);
      boolean inGroup = plugin.isKillSoundInDbGroup(permNode, soundId);

      ItemStack icon = plugin.getHatManager().createCleanIconStack(Material.MUSIC_DISC_OTHERSIDE);
      ItemMeta meta = icon.getItemMeta();
      if (meta != null) {
        meta.displayName(plugin.getKillSoundManager().getKillSoundDisplayName(soundId).append(plugin.parse(" <#6c7086>(" + soundId + ")")));
        if (inGroup) meta.setEnchantmentGlintOverride(true);

        List<Component> lore = new ArrayList<>(plugin.getKillSoundManager().getKillSoundCustomLore(soundId));
        lore.add(Component.empty());
        if (inGroup) {
          lore.add(plugin.parse("<#a6e3a1>● Included in <#f9e2af>" + permNode));
          lore.add(plugin.parse("<#fab387>Click to remove from this group"));
        } else {
          lore.add(plugin.parse("<#6c7086>○ Not in <#f9e2af>" + permNode));
          lore.add(plugin.parse("<#89dceb>Click to add to this group"));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(plugin.getHatManager().getItemAppliedHatKey(), PersistentDataType.STRING, soundId);
        icon.setItemMeta(meta);
      }
      plugin.getHatManager().applyHiddenComponentTooltips(icon);
      inv.setItem(slotIndex++, icon);
    }

    inv.setItem(45, TrimMenuHolder.createBackButton(plugin, "<#f9e2af><bold>Back to Groups</bold>"));
    if (currentPage > 0) inv.setItem(47, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>Previous Page</bold>", List.of(plugin.parse("<#a6adc8>Go to page <#f9e2af>" + currentPage + "<#a6adc8>/" + totalPages))));
    Set<String> selectedSet = plugin.getDbKillSoundGroups().getOrDefault(permNode.toLowerCase(), Set.of());
    inv.setItem(49, TrimMenuHolder.createStyledButton(plugin, Material.BOOKSHELF, "<#f9e2af><bold>Editing: " + permNode + "</bold>", List.of(plugin.parse("<#a6adc8>Total KillSounds Selected: <#a6e3a1>" + selectedSet.size()), plugin.parse("<#a6adc8>Click any sound above to toggle it!"))));
    if (currentPage + 1 < totalPages) inv.setItem(51, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>Next Page</bold>", List.of(plugin.parse("<#a6adc8>Go to page <#f9e2af>" + (currentPage + 2) + "<#a6adc8>/" + totalPages))));

    player.openInventory(inv);
  }

  public static void openEditorMenu(AntiArmorTrim plugin, Player player, String soundId) {
    ensureRegistered(plugin);
    String rawTitle = plugin.getConfig().getString("gui.killsound-editor-title", "<white><sprite:items:item/jukebox></white> <#cba6f7><bold>Edit KillSound:</bold> <#89dceb><id>");
    TrimMenuHolder holder = new TrimMenuHolder(TrimMenuHolder.MenuType.KILLSOUND_EDITOR, null, soundId, 0);
    Inventory inv = Bukkit.createInventory(holder, 27, plugin.parse(rawTitle, Placeholder.unparsed("id", soundId)));
    holder.setInventory(inv);

    ItemStack border = TrimMenuHolder.createFiller(plugin, Material.GRAY_STAINED_GLASS_PANE);
    for (int i = 0; i < 27; i++) inv.setItem(i, border);

    inv.setItem(10, TrimMenuHolder.createStyledButton(plugin, Material.NAME_TAG, "<#89dceb><bold>Edit Display Name</bold>", List.of(
        plugin.parse("<#a6adc8>Current: ").append(plugin.getKillSoundManager().getKillSoundDisplayName(soundId)),
        Component.empty(), plugin.parse("<#89dceb>Left-Click: <#cdd6f4>Set new display name"), plugin.parse("<#fab387>Right-Click: <#cdd6f4>Reset to default"))));

    List<Component> loreList = new ArrayList<>();
    List<Component> currentLore = plugin.getKillSoundManager().getKillSoundCustomLore(soundId);
    if (currentLore.isEmpty()) loreList.add(plugin.parse("<#6c7086>No lore configured"));
    else loreList.addAll(currentLore);
    loreList.add(Component.empty());
    loreList.add(plugin.parse("<#89dceb>Left-Click: <#cdd6f4>Set or append lore in chat"));
    loreList.add(plugin.parse("<#fab387>Right-Click: <#cdd6f4>Clear custom lore"));
    inv.setItem(11, TrimMenuHolder.createStyledButton(plugin, Material.WRITABLE_BOOK, "<#cba6f7><bold>Edit KillSound Lore</bold>", loreList));

    List<String> groups = plugin.getKillSoundManager().getGroupsContainingKillSound(soundId);
    inv.setItem(12, TrimMenuHolder.createStyledButton(plugin, Material.BOOKSHELF, "<#a6e3a1><bold>Manage Permission Groups</bold>", List.of(
        plugin.parse("<#a6adc8>Assigned Groups: <#f9e2af>" + (groups.isEmpty() ? "None (Default)" : String.join(", ", groups))),
        Component.empty(), plugin.parse("<#89dceb>Click: <#cdd6f4>Open KillSound Group Manager"))));

    inv.setItem(14, TrimMenuHolder.createStyledButton(plugin, Material.JUKEBOX, "<#89dceb><bold>Preview KillSound</bold>", List.of(
        plugin.parse("<#a6adc8>Sound ID: <#f9e2af>" + soundId), Component.empty(), plugin.parse("<#a6e3a1>Click to test play this sound!"))));

    boolean isDef = plugin.isDbForceDefaultKillSound() && soundId.equalsIgnoreCase(plugin.getDbDefaultKillSound());
    inv.setItem(15, TrimMenuHolder.createStyledButton(plugin, Material.NETHER_STAR, "<#f9e2af><bold>Force Default KillSound</bold>", List.of(
        plugin.parse("<#a6adc8>Current: " + (isDef ? "<#a6e3a1>Active Default" : "<#6c7086>Not Default")),
        Component.empty(), plugin.parse("<#89dceb>Left-Click: <#cdd6f4>Set as Forced Default"), plugin.parse("<#fab387>Right-Click: <#cdd6f4>Disable Forced Default"))));

    inv.setItem(16, TrimMenuHolder.createStyledButton(plugin, Material.BARRIER, "<#f38ba8><bold>Delete KillSound</bold>", List.of(plugin.parse("<#a6adc8>Remove <#f9e2af>" + soundId + " <#a6adc8>from the database."))));
    inv.setItem(18, TrimMenuHolder.createBackButton(plugin, "<#f9e2af><bold>Back</bold>"));
    player.openInventory(inv);
  }

  @EventHandler
  public void onClick(InventoryClickEvent event) {
    if (!(event.getInventory().getHolder() instanceof TrimMenuHolder holder)) return;
    TrimMenuHolder.MenuType type = holder.getMenuType();
    if (type != TrimMenuHolder.MenuType.KILLSOUND_STUDIO && type != TrimMenuHolder.MenuType.KILLSOUND_ADMIN
        && type != TrimMenuHolder.MenuType.KILLSOUND_GROUP_LIST && type != TrimMenuHolder.MenuType.KILLSOUND_GROUP_SELECTOR
        && type != TrimMenuHolder.MenuType.KILLSOUND_EDITOR) return;
    event.setCancelled(true);
    if (!(event.getWhoClicked() instanceof Player player)) return;

    int slot = event.getRawSlot();
    int page = holder.getPage();

    if (type == TrimMenuHolder.MenuType.KILLSOUND_STUDIO) {
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
        plugin.getKillSoundManager().setSavedKillSoundId(player, null);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1.2f);
        player.sendMessage(plugin.getMessage("killsound-cleared"));
        openPlayerMenu(plugin, player, page);
        return;
      }
      if (slot < 36) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || clicked.getItemMeta() == null) return;
        String soundId = clicked.getItemMeta().getPersistentDataContainer().get(plugin.getHatManager().getItemAppliedHatKey(), PersistentDataType.STRING);
        if (soundId == null) return;

        if (event.isRightClick()) {
          plugin.getKillSoundManager().playPreviewKillSound(player, soundId);
          player.sendMessage(plugin.getMessage("killsound-preview", Placeholder.component("sound", plugin.getKillSoundManager().getKillSoundDisplayName(soundId))));
          return;
        }

        if (!plugin.getKillSoundManager().canUseSpecificKillSound(player, soundId)) {
          player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
          player.sendMessage(plugin.getMessageWithPerm("no-permission-gui", plugin.getKillSoundManager().getKillSoundRequiredPermissionNode(soundId)));
          return;
        }

        plugin.getKillSoundManager().setSavedKillSoundId(player, soundId);
        plugin.getKillSoundManager().playPreviewKillSound(player, soundId);
        player.sendMessage(plugin.getMessage("killsound-selected", Placeholder.component("sound", plugin.getKillSoundManager().getKillSoundDisplayName(soundId))));
        openPlayerMenu(plugin, player, page);
      }
      return;
    }

    if (type == TrimMenuHolder.MenuType.KILLSOUND_ADMIN) {
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
        pendingAddKillSoundPlayers.add(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.3f);
        player.sendMessage(plugin.getMessage("killsound-admin-prompt-id"));
        return;
      }
      if (slot == 50) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        openGroupListMenu(plugin, player, 0);
        return;
      }
      if (slot == 53) {
        if (event.isRightClick()) {
          plugin.getKillSoundManager().setForceDefaultKillSound(player, null, false);
        } else {
          boolean nextState = !plugin.isDbForceDefaultKillSound();
          String def = plugin.getDbDefaultKillSound();
          if (nextState && def == null && !plugin.getDbKillSoundItems().isEmpty()) def = plugin.getDbKillSoundItems().get(0);
          plugin.setDefaultKillSoundInDb(def, nextState);
          player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
          player.sendMessage(plugin.getMessage("hat-admin-default-toggled", Placeholder.parsed("state", nextState ? "<#a6e3a1>Enabled" : "<#f38ba8>Disabled")));
        }
        openAdminMenu(plugin, player, page);
        return;
      }
      if (slot < 36) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || clicked.getItemMeta() == null) return;
        String soundId = clicked.getItemMeta().getPersistentDataContainer().get(plugin.getHatManager().getItemAppliedHatKey(), PersistentDataType.STRING);
        if (soundId == null) return;

        if (event.isRightClick()) {
          if (plugin.removeKillSoundFromDb(soundId)) {
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1.2f);
            player.sendMessage(plugin.getMessage("hat-admin-removed", Placeholder.unparsed("id", soundId)));
          }
          openAdminMenu(plugin, player, page);
          return;
        }
        if (event.isShiftClick()) {
          plugin.getKillSoundManager().setForceDefaultKillSound(player, soundId, true);
          openAdminMenu(plugin, player, page);
          return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        openEditorMenu(plugin, player, soundId);
      }
      return;
    }

    if (type == TrimMenuHolder.MenuType.KILLSOUND_GROUP_LIST) {
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
        pendingCreateKillSoundGroupPlayers.add(player.getUniqueId());
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
          plugin.deleteDbKillSoundGroup(permNode);
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

    if (type == TrimMenuHolder.MenuType.KILLSOUND_GROUP_SELECTOR) {
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
        String soundId = clicked.getItemMeta().getPersistentDataContainer().get(plugin.getHatManager().getItemAppliedHatKey(), PersistentDataType.STRING);
        if (soundId == null) return;

        boolean added = plugin.toggleKillSoundInDbGroup(permNode, soundId);
        if (added) {
          player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
          player.sendMessage(plugin.getMessage("hat-admin-group-hat-added", Placeholder.unparsed("id", soundId), Placeholder.unparsed("perm", permNode)));
        } else {
          player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1.2f);
          player.sendMessage(plugin.getMessage("hat-admin-group-hat-removed", Placeholder.unparsed("id", soundId), Placeholder.unparsed("perm", permNode)));
        }
        openGroupSelectorMenu(plugin, player, permNode, page);
      }
      return;
    }

    if (type == TrimMenuHolder.MenuType.KILLSOUND_EDITOR) {
      if (!plugin.hasAdminPerm(player)) { player.closeInventory(); return; }
      String soundId = holder.getEditingId();
      if (soundId == null) return;

      if (slot == 18) { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f); openAdminMenu(plugin, player, 0); return; }
      if (slot == 10) {
        if (event.isRightClick()) {
          plugin.setKillSoundNameInDb(soundId, null);
          player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1.2f);
          player.sendMessage(plugin.getMessage("hat-admin-name-reset", Placeholder.unparsed("id", soundId)));
          openEditorMenu(plugin, player, soundId);
          return;
        }
        player.closeInventory();
        pendingKillSoundNameEdit.put(player.getUniqueId(), soundId);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.3f);
        player.sendMessage(plugin.getMessage("hat-admin-prompt-name", Placeholder.unparsed("id", soundId)));
        return;
      }
      if (slot == 11) {
        if (event.isRightClick()) {
          plugin.setKillSoundLoreInDb(soundId, null);
          player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1.2f);
          player.sendMessage(plugin.getMessage("hat-admin-lore-reset", Placeholder.unparsed("id", soundId)));
          openEditorMenu(plugin, player, soundId);
          return;
        }
        player.closeInventory();
        pendingKillSoundLoreEdit.put(player.getUniqueId(), soundId);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.3f);
        player.sendMessage(plugin.getMessage("hat-admin-prompt-lore", Placeholder.unparsed("id", soundId)));
        return;
      }
      if (slot == 12) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        openGroupListMenu(plugin, player, 0);
        return;
      }
      if (slot == 14) {
        plugin.getKillSoundManager().playPreviewKillSound(player, soundId);
        player.sendMessage(plugin.getMessage("killsound-preview", Placeholder.component("sound", plugin.getKillSoundManager().getKillSoundDisplayName(soundId))));
        return;
      }
      if (slot == 15) {
        plugin.getKillSoundManager().setForceDefaultKillSound(player, event.isRightClick() ? null : soundId, !event.isRightClick());
        openEditorMenu(plugin, player, soundId);
        return;
      }
      if (slot == 16) {
        if (plugin.removeKillSoundFromDb(soundId)) {
          player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1.2f);
          player.sendMessage(plugin.getMessage("hat-admin-removed", Placeholder.unparsed("id", soundId)));
        }
        openAdminMenu(plugin, player, 0);
      }
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onChatInput(AsyncChatEvent event) {
    Player player = event.getPlayer();
    UUID uuid = player.getUniqueId();

    if (pendingCreateKillSoundGroupPlayers.remove(uuid)) {
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
        plugin.createDbKillSoundGroup(normalized);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
        player.sendMessage(plugin.getMessage("hat-admin-group-created", Placeholder.unparsed("perm", normalized)));
        openGroupSelectorMenu(plugin, player, normalized, 0);
      });
      return;
    }

    if (pendingAddKillSoundPlayers.remove(uuid)) {
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
        if (plugin.getDbKillSoundItems().stream().anyMatch(s -> s.equalsIgnoreCase(rawId))) {
          player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
          player.sendMessage(plugin.getMessage("hat-admin-exists", Placeholder.unparsed("id", rawId)));
          openAdminMenu(plugin, player, 0);
          return;
        }
        plugin.addKillSoundToDb(rawId);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
        player.sendMessage(plugin.getMessage("killsound-admin-added", Placeholder.unparsed("id", rawId)));
        openEditorMenu(plugin, player, rawId);
      });
      return;
    }

    String editingNameSoundId = pendingKillSoundNameEdit.remove(uuid);
    if (editingNameSoundId != null) {
      event.setCancelled(true);
      String rawInput = event.signedMessage().message().trim();
      Bukkit.getScheduler().runTask(plugin, () -> {
        if (!player.isOnline()) return;
        if (rawInput.equalsIgnoreCase("cancel")) {
          player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.8f);
          player.sendMessage(plugin.getMessage("hat-admin-cancelled"));
          openEditorMenu(plugin, player, editingNameSoundId);
          return;
        }
        plugin.setKillSoundNameInDb(editingNameSoundId, rawInput);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.3f);
        player.sendMessage(plugin.getMessage("hat-admin-name-updated", Placeholder.unparsed("id", editingNameSoundId), Placeholder.component("name", plugin.parse(rawInput))));
        openEditorMenu(plugin, player, editingNameSoundId);
      });
      return;
    }

    String editingLoreSoundId = pendingKillSoundLoreEdit.remove(uuid);
    if (editingLoreSoundId != null) {
      event.setCancelled(true);
      String rawInput = event.signedMessage().message().trim();
      Bukkit.getScheduler().runTask(plugin, () -> {
        if (!player.isOnline()) return;
        if (rawInput.equalsIgnoreCase("cancel")) {
          player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.8f);
          player.sendMessage(plugin.getMessage("hat-admin-cancelled"));
          openEditorMenu(plugin, player, editingLoreSoundId);
          return;
        }
        List<String> newLore;
        if (rawInput.regionMatches(true, 0, "add ", 0, 4)) {
          List<String> existing = plugin.getDbKillSoundLore(editingLoreSoundId);
          newLore = existing != null ? new ArrayList<>(existing) : new ArrayList<>();
          newLore.add(rawInput.substring(4).trim());
        } else {
          newLore = new ArrayList<>(Arrays.asList(rawInput.split("\\|")));
          newLore.replaceAll(String::trim);
        }
        plugin.setKillSoundLoreInDb(editingLoreSoundId, newLore);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.2f);
        player.sendMessage(plugin.getMessage("hat-admin-lore-updated", Placeholder.unparsed("id", editingLoreSoundId)));
        openEditorMenu(plugin, player, editingLoreSoundId);
      });
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    UUID uuid = event.getPlayer().getUniqueId();
    pendingAddKillSoundPlayers.remove(uuid);
    pendingCreateKillSoundGroupPlayers.remove(uuid);
    pendingKillSoundNameEdit.remove(uuid);
    pendingKillSoundLoreEdit.remove(uuid);
  }
}
