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

    List<String> validHats = new ArrayList<>();
    for (String hatId : plugin.getDbHatItems()) {
      if (plugin.getNexoItem(hatId) != null) {
        validHats.add(hatId);
      }
    }

    int totalPages = Math.max(1, (int) Math.ceil(validHats.size() / 36.0));
    int currentPage = Math.max(0, Math.min(page, totalPages - 1));

    String rawTitle = plugin.getConfig().getString("gui.hat-title", "<white><sprite:items:item/netherite_helmet></white> <#cba6f7><bold>ᴄᴜѕᴛᴏᴍ ʜᴀᴛ ѕᴛᴜᴅɪᴏ</bold> <#6c7086>(<page>/<max_page>)");
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

      ItemStack icon;
      ItemMeta meta;

      if (unlocked) {
        icon = nexoStack.clone();
        meta = icon.getItemMeta();
        if (meta != null) {
          plugin.getHatManager().hideAllVanillaTooltips(meta);
          meta.displayName(plugin.parse(plugin.getItemSpriteTag(Material.NETHERITE_HELMET) + " ").append(plugin.getHatManager().getHatDisplayName(hatId, nexoStack)));
          if (selected) meta.setEnchantmentGlintOverride(true);

          List<Component> lore = new ArrayList<>(plugin.getHatManager().getHatCustomLore(hatId, nexoStack));
          if (isDefault) lore.add(plugin.parse("<white><sprite:items:item/nether_star></white> <#f9e2af>ᴅᴇꜰᴀᴜʟᴛ <#6c7086>▸ <#cdd6f4>Server Default Hat"));
          lore.add(Component.empty());
          String statusRaw = selected
              ? plugin.getConfig().getString("gui.selected-status", "<white><sprite:items:item/lime_dye></white> <#a6e3a1>ѕᴛᴀᴛᴜѕ <#6c7086>▸ <#a6e3a1><bold>Selected</bold> <#6c7086>(ᴀᴄᴛɪᴠᴇ)")
              : plugin.getConfig().getString("gui.unselected-status", "<white><sprite:items:item/ender_pearl></white> <#89dceb>ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Select & Equip <#6c7086>(ᴜɴѕᴇʟᴇᴄᴛᴇᴅ)");
          lore.add(plugin.parse(statusRaw));
          meta.lore(lore);

          PersistentDataContainer pdc = meta.getPersistentDataContainer();
          for (NamespacedKey k : new ArrayList<>(pdc.getKeys())) if (k.getNamespace().toLowerCase().contains("nexo")) pdc.remove(k);
          pdc.set(plugin.getHatManager().getItemAppliedHatKey(), PersistentDataType.STRING, hatId);
          icon.setItemMeta(meta);
        }
      } else {
        icon = plugin.getHatManager().createCleanIconStack(Material.GRAY_DYE);
        meta = icon.getItemMeta();
        if (meta != null) {
          meta.displayName(plugin.parse(plugin.getItemSpriteTag(Material.GRAY_DYE) + " ")
              .append(plugin.getHatManager().getHatDisplayName(hatId, nexoStack))
              .append(plugin.parse(" <#6c7086>• <#f38ba8><bold>ʟᴏᴄᴋᴇᴅ</bold>")));

          List<Component> lore = new ArrayList<>(plugin.getHatManager().getHatCustomLore(hatId, nexoStack));
          if (isDefault) lore.add(plugin.parse("<white><sprite:items:item/nether_star></white> <#f9e2af>ᴅᴇꜰᴀᴜʟᴛ <#6c7086>▸ <#cdd6f4>Server Default Hat"));

          String reqPerm = plugin.getHatManager().getHatRequiredPermissionNode(hatId);
          for (String line : TrimMenuHolder.getLockedLoreTemplate(plugin)) {
            lore.add(plugin.parseWithPerm(line, reqPerm));
          }
          meta.lore(lore);

          PersistentDataContainer pdc = meta.getPersistentDataContainer();
          pdc.set(plugin.getHatManager().getItemAppliedHatKey(), PersistentDataType.STRING, hatId);
          icon.setItemMeta(meta);
        }
      }
      plugin.getHatManager().applyHiddenComponentTooltips(icon);
      inv.setItem(slotIndex++, icon);
    }

    inv.setItem(45, TrimMenuHolder.createBackButton(plugin, "<#f9e2af><bold>ʙᴀᴄᴋ</bold> <#6c7086>▸ <#cdd6f4>Hub"));
    if (currentPage > 0) inv.setItem(48, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>ᴘʀᴇᴠɪᴏᴜѕ</bold> <#6c7086>▸ <#a6adc8>Page " + currentPage, null));
    inv.setItem(49, TrimMenuHolder.createStyledButton(plugin, Material.BARRIER, "<#f38ba8><bold>ʀᴇѕᴇᴛ</bold> <#6c7086>▸ <#cdd6f4>Unequip Hat", List.of(
        plugin.parse("<#a6adc8>ᴅɪѕᴀʙʟᴇ custom hat & restore"),
        plugin.parse("<#a6adc8>ʏᴏᴜʀ default <#cdd6f4>Helmet Appearance<#a6adc8>."))));
    if (currentPage + 1 < totalPages) inv.setItem(50, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>ɴᴇхᴛ</bold> <#6c7086>▸ <#a6adc8>Page " + (currentPage + 2), null));
    if (plugin.hasAdminPerm(player)) inv.setItem(53, TrimMenuHolder.createStyledButton(plugin, Material.COMMAND_BLOCK, "<#f38ba8><bold>ᴀᴅᴍɪɴ</bold> <#6c7086>▸ <#cdd6f4>Hat Manager", List.of(
        plugin.parse("<#a6adc8>ᴍᴀɴᴀɢᴇ Nexo hats, perm"),
        plugin.parse("<#a6adc8>ɢʀᴏᴜᴘѕ, ᴏʀ default hat."))));

    player.openInventory(inv);
  }

  public static void openAdminMenu(AntiArmorTrim plugin, Player player, int page) {
    ensureRegistered(plugin);
    List<String> hatIds = plugin.getDbHatItems();
    int totalPages = Math.max(1, (int) Math.ceil(hatIds.size() / 36.0));
    int currentPage = Math.max(0, Math.min(page, totalPages - 1));

    String rawTitle = plugin.getConfig().getString("gui.hat-admin-title", "<white><sprite:items:item/command_block></white> <#f38ba8><bold>ᴀᴅᴍɪɴ ʜᴀᴛ ᴍᴀɴᴀɢᴇʀ</bold> <#6c7086>(<page>/<max_page>)");
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
          if (!groups.isEmpty()) lore.add(plugin.parse("<#a6adc8>ɢʀᴏᴜᴘѕ <#6c7086>▸ <#f9e2af>" + String.join(", ", groups)));
          else lore.add(plugin.parse("<#a6adc8>ᴘᴇʀᴍ <#6c7086>▸ <#6c7086>Default (" + AntiArmorTrim.HAT_PERM + ")"));
          if (isDefault) lore.add(plugin.parse("<white><sprite:items:item/nether_star></white> <#f9e2af>★ Default Hat (" + (plugin.getHatManager().isForceDefaultHat() ? "<#a6e3a1>Forced" : "<#f38ba8>Disabled") + "<#f9e2af>)"));
          lore.add(Component.empty());
          lore.add(plugin.parse("<white><sprite:items:item/name_tag></white> <#89dceb>ʟᴇꜰᴛ-ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Edit Name & Lore"));
          lore.add(plugin.parse("<white><sprite:items:item/nether_star></white> <#f9e2af>ѕʜɪꜰᴛ-ʟᴇꜰᴛ <#6c7086>▸ <#cdd6f4>Set as Forced Default"));
          lore.add(plugin.parse("<white><sprite:items:item/barrier></white> <#f38ba8>ʀɪɢʜᴛ-ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Delete Hat"));
          meta.lore(lore);

          PersistentDataContainer pdc = meta.getPersistentDataContainer();
          for (NamespacedKey k : new ArrayList<>(pdc.getKeys())) if (k.getNamespace().toLowerCase().contains("nexo")) pdc.remove(k);
          pdc.set(plugin.getHatManager().getItemAppliedHatKey(), PersistentDataType.STRING, hatId);
          icon.setItemMeta(meta);
        }
        plugin.getHatManager().applyHiddenComponentTooltips(icon);
      } else {
        icon = TrimMenuHolder.createStyledButton(plugin, Material.BARRIER, "<#f38ba8><bold>ʙʀᴏᴋᴇɴ ɪᴅ: " + hatId + "</bold>", List.of(plugin.parse("<#a6adc8>Not found in Nexo!"), Component.empty(), plugin.parse("<#f38ba8>Click to delete this broken entry")));
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
          meta.getPersistentDataContainer().set(plugin.getHatManager().getItemAppliedHatKey(), PersistentDataType.STRING, hatId);
          icon.setItemMeta(meta);
          plugin.getHatManager().applyHiddenComponentTooltips(icon);
        }
      }
      inv.setItem(slotIndex++, icon);
    }

    inv.setItem(45, TrimMenuHolder.createBackButton(plugin, "<#f9e2af><bold>ʙᴀᴄᴋ</bold> <#6c7086>▸ <#cdd6f4>Player View"));
    if (currentPage > 0) inv.setItem(47, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>ᴘʀᴇᴠɪᴏᴜѕ</bold>", null));
    inv.setItem(48, TrimMenuHolder.createStyledButton(plugin, Material.ANVIL, "<#a6e3a1><bold>ᴀᴅᴅ ɴᴇхᴏ ʜᴀᴛ</bold>", List.of(
        plugin.parse("<#a6adc8>ᴛʏᴘᴇ ᴀ Nexo Item ID"),
        plugin.parse("<#a6adc8>ɪɴ ᴄʜᴀᴛ ᴛᴏ ѕᴀᴠᴇ ᴛᴏ database."))));
    inv.setItem(50, TrimMenuHolder.createStyledButton(plugin, Material.BOOKSHELF, "<#cba6f7><bold>ᴘᴇʀᴍɪѕѕɪᴏɴ ɢʀᴏᴜᴘѕ</bold>", List.of(
        plugin.parse("<#a6adc8>ᴄʀᴇᴀᴛᴇ custom permission nodes"),
        plugin.parse("<#a6adc8>& ѕᴇʟᴇᴄᴛ hats for each rank!"))));
    if (currentPage + 1 < totalPages) inv.setItem(51, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>ɴᴇхᴛ</bold>", null));

    String statusText = plugin.getHatManager().isForceDefaultHat() ? "<#a6e3a1>Enabled" : "<#f38ba8>Disabled";
    String defHatText = (defHat != null) ? "<#89dceb>" + defHat : "<#6c7086>None";
    inv.setItem(53, TrimMenuHolder.createStyledButton(plugin, Material.NETHER_STAR, "<#f9e2af><bold>ꜰᴏʀᴄᴇ ᴅᴇꜰᴀᴜʟᴛ ʜᴀᴛ</bold>", List.of(
        plugin.parse("<#a6adc8>ѕᴛᴀᴛᴜѕ <#6c7086>▸ " + statusText),
        plugin.parse("<#a6adc8>ᴅᴇꜰᴀᴜʟᴛ ʜᴀᴛ <#6c7086>▸ " + defHatText),
        Component.empty(),
        plugin.parse("<#89dceb>ʟᴇꜰᴛ-ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Toggle Force Default"),
        plugin.parse("<#f38ba8>ʀɪɢʜᴛ-ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Clear Default Hat"))));

    player.openInventory(inv);
  }

  public static void openGroupListMenu(AntiArmorTrim plugin, Player player, int page) {
    ensureRegistered(plugin);
    List<String> permNodes = new ArrayList<>(plugin.getDbPermGroups().keySet());
    int totalPages = Math.max(1, (int) Math.ceil(permNodes.size() / 36.0));
    int currentPage = Math.max(0, Math.min(page, totalPages - 1));

    String rawTitle = plugin.getConfig().getString("gui.hat-groups-title", "<white><sprite:items:item/bookshelf></white> <#f9e2af><bold>ʜᴀᴛ ᴘᴇʀᴍ ɢʀᴏᴜᴘѕ</bold> <#6c7086>(<page>/<max_page>)");
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
          plugin.parse("<#a6adc8>ᴘᴇʀᴍɪѕѕɪᴏɴ <#6c7086>▸ <#f9e2af>" + permNode),
          plugin.parse("<#a6adc8>ѕᴇʟᴇᴄᴛᴇᴅ ʜᴀᴛѕ <#6c7086>▸ <#a6e3a1>" + hatsInGroup.size())));
      if (!hatsInGroup.isEmpty()) {
        String previewList = String.join(", ", hatsInGroup.stream().limit(5).toList());
        if (hatsInGroup.size() > 5) previewList += "...";
        lore.add(plugin.parse("<#6c7086>(" + previewList + ")"));
      }
      lore.add(Component.empty());
      lore.add(plugin.parse("<white><sprite:items:item/ender_eye></white> <#89dceb>ʟᴇꜰᴛ-ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Select Hats"));
      lore.add(plugin.parse("<white><sprite:items:item/barrier></white> <#f38ba8>ʀɪɢʜᴛ-ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Delete Group"));

      ItemStack icon = TrimMenuHolder.createStyledButton(plugin, Material.TRIPWIRE_HOOK, "<#cba6f7><bold>" + permNode + "</bold>", lore);
      ItemMeta meta = icon.getItemMeta();
      if (meta != null) {
        meta.getPersistentDataContainer().set(plugin.getHatManager().getItemAppliedHatKey(), PersistentDataType.STRING, permNode);
        icon.setItemMeta(meta);
        plugin.getHatManager().applyHiddenComponentTooltips(icon);
      }
      inv.setItem(slotIndex++, icon);
    }

    inv.setItem(45, TrimMenuHolder.createBackButton(plugin, "<#f9e2af><bold>ʙᴀᴄᴋ</bold> <#6c7086>▸ <#cdd6f4>Admin"));
    if (currentPage > 0) inv.setItem(47, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>ᴘʀᴇᴠɪᴏᴜѕ</bold>", null));
    inv.setItem(49, TrimMenuHolder.createStyledButton(plugin, Material.ANVIL, "<#a6e3a1><bold>ᴄʀᴇᴀᴛᴇ ɴᴇᴡ ɢʀᴏᴜᴘ</bold>", List.of(
        plugin.parse("<#a6adc8>ᴛʏᴘᴇ ᴀ new permission node"),
        plugin.parse("<#a6adc8>ɪɴ ᴄʜᴀᴛ ᴛᴏ ѕᴇʟᴇᴄᴛ hats for it!"))));
    if (currentPage + 1 < totalPages) inv.setItem(51, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>ɴᴇхᴛ</bold>", null));

    player.openInventory(inv);
  }

  public static void openGroupSelectorMenu(AntiArmorTrim plugin, Player player, String permNode, int page) {
    ensureRegistered(plugin);
    List<String> hatIds = plugin.getDbHatItems();
    int totalPages = Math.max(1, (int) Math.ceil(hatIds.size() / 36.0));
    int currentPage = Math.max(0, Math.min(page, totalPages - 1));

    String rawTitle = plugin.getConfig().getString("gui.hat-group-select-title", "<white><sprite:items:item/tripwire_hook></white> <#cba6f7><bold>ɢʀᴏᴜᴘ</bold> <#6c7086>▸ <#89dceb><perm> <#6c7086>(<page>/<max_page>)");
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
          lore.add(plugin.parse("<white><sprite:items:item/lime_dye></white> <#a6e3a1>ɪɴᴄʟᴜᴅᴇᴅ ɪɴ <#f9e2af>" + permNode));
          lore.add(plugin.parse("<#fab387>ᴄʟɪᴄᴋ ᴛᴏ remove from this group"));
        } else {
          lore.add(plugin.parse("<white><sprite:items:item/gray_dye></white> <#6c7086>ɴᴏᴛ ɪɴ <#f9e2af>" + permNode));
          lore.add(plugin.parse("<#89dceb>ᴄʟɪᴄᴋ ᴛᴏ add to this group"));
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

    inv.setItem(45, TrimMenuHolder.createBackButton(plugin, "<#f9e2af><bold>ʙᴀᴄᴋ</bold> <#6c7086>▸ <#cdd6f4>Groups"));
    if (currentPage > 0) inv.setItem(47, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>ᴘʀᴇᴠɪᴏᴜѕ</bold>", null));
    Set<String> selectedSet = plugin.getDbPermGroups().getOrDefault(permNode.toLowerCase(), Set.of());
    inv.setItem(49, TrimMenuHolder.createStyledButton(plugin, Material.BOOKSHELF, "<#f9e2af><bold>ᴇᴅɪᴛɪɴɢ: " + permNode + "</bold>", List.of(
        plugin.parse("<#a6adc8>ᴛᴏᴛᴀʟ ʜᴀᴛѕ <#6c7086>▸ <#a6e3a1>" + selectedSet.size()),
        plugin.parse("<#a6adc8>ᴄʟɪᴄᴋ ᴀɴʏ ʜᴀᴛ above to toggle it!"))));
    if (currentPage + 1 < totalPages) inv.setItem(51, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>ɴᴇхᴛ</bold>", null));

    player.openInventory(inv);
  }

  public static void openEditorMenu(AntiArmorTrim plugin, Player player, String hatId) {
    ensureRegistered(plugin);
    String rawTitle = plugin.getConfig().getString("gui.hat-editor-title", "<white><sprite:items:item/anvil></white> <#cba6f7><bold>ᴇᴅɪᴛ ʜᴀᴛ</bold> <#6c7086>▸ <#89dceb><id>");
    TrimMenuHolder holder = new TrimMenuHolder(TrimMenuHolder.MenuType.HAT_EDITOR, null, hatId, 0);
    Inventory inv = Bukkit.createInventory(holder, 27, plugin.parse(rawTitle, Placeholder.unparsed("id", hatId)));
    holder.setInventory(inv);

    ItemStack border = TrimMenuHolder.createFiller(plugin, Material.GRAY_STAINED_GLASS_PANE);
    for (int i = 0; i < 27; i++) inv.setItem(i, border);

    ItemStack nexoStack = plugin.getNexoItem(hatId);
    inv.setItem(10, TrimMenuHolder.createStyledButton(plugin, Material.NAME_TAG, "<#89dceb><bold>ᴇᴅɪᴛ ᴅɪѕᴘʟᴀʏ ɴᴀᴍᴇ</bold>", List.of(
        plugin.parse("<#a6adc8>ᴄᴜʀʀᴇɴᴛ <#6c7086>▸ ").append(plugin.getHatManager().getHatDisplayName(hatId, nexoStack)),
        Component.empty(),
        plugin.parse("<#89dceb>ʟᴇꜰᴛ-ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Set new display name"),
        plugin.parse("<#fab387>ʀɪɢʜᴛ-ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Reset to default"))));

    List<Component> loreList = new ArrayList<>();
    List<Component> currentLore = plugin.getHatManager().getHatCustomLore(hatId, nexoStack);
    if (currentLore.isEmpty()) loreList.add(plugin.parse("<#6c7086>No lore configured"));
    else loreList.addAll(currentLore);
    loreList.add(Component.empty());
    loreList.add(plugin.parse("<#89dceb>ʟᴇꜰᴛ-ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Set/append in chat"));
    loreList.add(plugin.parse("<#fab387>ʀɪɢʜᴛ-ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Clear custom lore"));
    inv.setItem(11, TrimMenuHolder.createStyledButton(plugin, Material.WRITABLE_BOOK, "<#cba6f7><bold>ᴇᴅɪᴛ ʜᴀᴛ ʟᴏʀᴇ</bold>", loreList));

    List<String> groups = plugin.getHatManager().getGroupsContainingHat(hatId);
    inv.setItem(12, TrimMenuHolder.createStyledButton(plugin, Material.BOOKSHELF, "<#a6e3a1><bold>ᴘᴇʀᴍɪѕѕɪᴏɴ ɢʀᴏᴜᴘѕ</bold>", List.of(
        plugin.parse("<#a6adc8>ᴀѕѕɪɢɴᴇᴅ <#6c7086>▸ <#f9e2af>" + (groups.isEmpty() ? "None (Default)" : String.join(", ", groups))),
        Component.empty(),
        plugin.parse("<#89dceb>ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Open Group Manager"))));

    ItemStack previewHat = plugin.getHatManager().createCosmeticHatFromHelmet(new ItemStack(Material.NETHERITE_HELMET), hatId);
    if (previewHat != null) inv.setItem(14, previewHat);

    boolean isDef = plugin.getHatManager().isForceDefaultHat() && hatId.equalsIgnoreCase(plugin.getHatManager().getDefaultHatId());
    inv.setItem(15, TrimMenuHolder.createStyledButton(plugin, Material.NETHER_STAR, "<#f9e2af><bold>ꜰᴏʀᴄᴇ ᴅᴇꜰᴀᴜʟᴛ ʜᴀᴛ</bold>", List.of(
        plugin.parse("<#a6adc8>ᴄᴜʀʀᴇɴᴛ <#6c7086>▸ " + (isDef ? "<#a6e3a1>Active Default" : "<#6c7086>Not Default")),
        Component.empty(),
        plugin.parse("<#89dceb>ʟᴇꜰᴛ-ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Set as Forced Default"),
        plugin.parse("<#fab387>ʀɪɢʜᴛ-ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Disable Forced Default"))));

    inv.setItem(16, TrimMenuHolder.createStyledButton(plugin, Material.BARRIER, "<#f38ba8><bold>ᴅᴇʟᴇᴛᴇ ʜᴀᴛ</bold>", List.of(plugin.parse("<#a6adc8>ʀᴇᴍᴏᴠᴇ <#f9e2af>" + hatId + " <#a6adc8>from database."))));
    inv.setItem(18, TrimMenuHolder.createBackButton(plugin, "<#f9e2af><bold>ʙᴀᴄᴋ</bold> <#6c7086>▸ <#cdd6f4>Admin"));
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
