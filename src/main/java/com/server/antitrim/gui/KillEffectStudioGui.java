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

    String rawTitle = plugin.getConfig().getString("gui.killeffect-title", "<white><sprite:items:item/totem_of_undying></white> <#cba6f7><bold>ᴋɪʟʟᴇꜰꜰᴇᴄᴛ ѕᴛᴜᴅɪᴏ</bold> <#6c7086>(<page>/<max_page>)");
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
          meta.displayName(plugin.parse(plugin.getItemSpriteTag(iconMat) + " ").append(plugin.getKillEffectManager().getKillEffectDisplayName(effectId)));
          if (selected) meta.setEnchantmentGlintOverride(true);
        } else {
          meta.displayName(plugin.parse(plugin.getItemSpriteTag(Material.GRAY_DYE) + " ")
              .append(plugin.getKillEffectManager().getKillEffectDisplayName(effectId))
              .append(plugin.parse(" <#6c7086>• <#f38ba8><bold>ʟᴏᴄᴋᴇᴅ</bold>")));
        }

        List<Component> lore = new ArrayList<>(plugin.getKillEffectManager().getKillEffectCustomLore(effectId));
        lore.add(plugin.parse("<white><sprite:items:item/armor_stand></white> <#a6adc8>ᴍᴏᴅᴇʟ <#6c7086>▸ <#cdd6f4>" + effectId));
        if (isDefault) lore.add(plugin.parse("<white><sprite:items:item/nether_star></white> <#f9e2af>ᴅᴇꜰᴀᴜʟᴛ <#6c7086>▸ <#cdd6f4>Server Default KillEffect"));
        lore.add(plugin.parse("<white><sprite:items:item/ender_eye></white> <#89dceb>ʀɪɢʜᴛ-ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Preview 3D Model"));

        if (unlocked) {
          String statusRaw = selected
              ? plugin.getConfig().getString("gui.selected-status", "<white><sprite:items:item/lime_dye></white> <#a6e3a1>ѕᴛᴀᴛᴜѕ <#6c7086>▸ <#a6e3a1><bold>Selected</bold> <#6c7086>(ᴀᴄᴛɪᴠᴇ)")
              : plugin.getConfig().getString("gui.unselected-status", "<white><sprite:items:item/ender_pearl></white> <#89dceb>ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Select & Equip <#6c7086>(ᴜɴѕᴇʟᴇᴄᴛᴇᴅ)");
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

    inv.setItem(45, TrimMenuHolder.createBackButton(plugin, "<#f9e2af><bold>ʙᴀᴄᴋ</bold> <#6c7086>▸ <#cdd6f4>Hub"));
    if (currentPage > 0) inv.setItem(48, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>ᴘʀᴇᴠɪᴏᴜѕ</bold> <#6c7086>▸ <#a6adc8>Page " + currentPage, null));
    inv.setItem(49, TrimMenuHolder.createStyledButton(plugin, Material.BARRIER, "<#f38ba8><bold>ʀᴇѕᴇᴛ</bold> <#6c7086>▸ <#cdd6f4>Disable Effect", List.of(
        plugin.parse("<#a6adc8>ᴅɪѕᴀʙʟᴇ active <#cdd6f4>3D KillEffect<#a6adc8>."))));
    if (currentPage + 1 < totalPages) inv.setItem(50, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>ɴᴇхᴛ</bold> <#6c7086>▸ <#a6adc8>Page " + (currentPage + 2), null));
    if (plugin.hasAdminPerm(player)) inv.setItem(53, TrimMenuHolder.createStyledButton(plugin, Material.COMMAND_BLOCK, "<#f38ba8><bold>ᴀᴅᴍɪɴ</bold> <#6c7086>▸ <#cdd6f4>KillEffect Manager", List.of(
        plugin.parse("<#a6adc8>ᴍᴀɴᴀɢᴇ ModelEngine models,"),
        plugin.parse("<#a6adc8>ᴘᴇʀᴍ ɢʀᴏᴜᴘѕ, ᴏʀ default effect."))));

    player.openInventory(inv);
  }

  public static void openAdminMenu(AntiArmorTrim plugin, Player player, int page) {
    ensureRegistered(plugin);
    List<String> effectIds = plugin.getDbKillEffectItems();
    int totalPages = Math.max(1, (int) Math.ceil(effectIds.size() / 36.0));
    int currentPage = Math.max(0, Math.min(page, totalPages - 1));

    String rawTitle = plugin.getConfig().getString("gui.killeffect-admin-title", "<white><sprite:items:item/command_block></white> <#f38ba8><bold>ᴀᴅᴍɪɴ ᴋɪʟʟᴇꜰꜰᴇᴄᴛ</bold> <#6c7086>(<page>/<max_page>)");
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
        if (!groups.isEmpty()) lore.add(plugin.parse("<#a6adc8>ɢʀᴏᴜᴘѕ <#6c7086>▸ <#f9e2af>" + String.join(", ", groups)));
        else lore.add(plugin.parse("<#a6adc8>ᴘᴇʀᴍ <#6c7086>▸ <#6c7086>Default (" + AntiArmorTrim.KILLEFFECT_PERM + ")"));
        if (isDefault) lore.add(plugin.parse("<white><sprite:items:item/nether_star></white> <#f9e2af>★ Default Effect (" + (plugin.isDbForceDefaultKillEffect() ? "<#a6e3a1>Forced" : "<#f38ba8>Disabled") + "<#f9e2af>)"));
        lore.add(Component.empty());
        lore.add(plugin.parse("<white><sprite:items:item/name_tag></white> <#89dceb>ʟᴇꜰᴛ-ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Edit Name & Lore"));
        lore.add(plugin.parse("<white><sprite:items:item/nether_star></white> <#f9e2af>ѕʜɪꜰᴛ-ʟᴇꜰᴛ <#6c7086>▸ <#cdd6f4>Set as Forced Default"));
        lore.add(plugin.parse("<white><sprite:items:item/barrier></white> <#f38ba8>ʀɪɢʜᴛ-ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Delete Effect"));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(plugin.getHatManager().getItemAppliedHatKey(), PersistentDataType.STRING, effectId);
        icon.setItemMeta(meta);
      }
      plugin.getHatManager().applyHiddenComponentTooltips(icon);
      inv.setItem(slotIndex++, icon);
    }

    inv.setItem(45, TrimMenuHolder.createBackButton(plugin, "<#f9e2af><bold>ʙᴀᴄᴋ</bold> <#6c7086>▸ <#cdd6f4>Player View"));
    if (currentPage > 0) inv.setItem(47, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>ᴘʀᴇᴠɪᴏᴜѕ</bold>", null));
    inv.setItem(48, TrimMenuHolder.createStyledButton(plugin, Material.ANVIL, "<#a6e3a1><bold>ᴀᴅᴅ ᴋɪʟʟᴇꜰꜰᴇᴄᴛ</bold>", List.of(
        plugin.parse("<#a6adc8>ᴛʏᴘᴇ ᴀ ModelEngine ID"),
        plugin.parse("<#a6adc8>ɪɴ ᴄʜᴀᴛ ᴛᴏ ѕᴀᴠᴇ ᴛᴏ database."))));
    inv.setItem(50, TrimMenuHolder.createStyledButton(plugin, Material.BOOKSHELF, "<#cba6f7><bold>ᴘᴇʀᴍɪѕѕɪᴏɴ ɢʀᴏᴜᴘѕ</bold>", List.of(
        plugin.parse("<#a6adc8>ᴄʀᴇᴀᴛᴇ custom permission nodes"),
        plugin.parse("<#a6adc8>& ѕᴇʟᴇᴄᴛ effects for each rank!"))));
    if (currentPage + 1 < totalPages) inv.setItem(51, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>ɴᴇхᴛ</bold>", null));

    String statusText = plugin.isDbForceDefaultKillEffect() ? "<#a6e3a1>Enabled" : "<#f38ba8>Disabled";
    String defText = (defEffect != null) ? "<#89dceb>" + defEffect : "<#6c7086>None";
    inv.setItem(53, TrimMenuHolder.createStyledButton(plugin, Material.NETHER_STAR, "<#f9e2af><bold>ꜰᴏʀᴄᴇ ᴅᴇꜰᴀᴜʟᴛ</bold>", List.of(
        plugin.parse("<#a6adc8>ѕᴛᴀᴛᴜѕ <#6c7086>▸ " + statusText),
        plugin.parse("<#a6adc8>ᴅᴇꜰᴀᴜʟᴛ ᴇꜰꜰᴇᴄᴛ <#6c7086>▸ " + defText),
        Component.empty(),
        plugin.parse("<#89dceb>ʟᴇꜰᴛ-ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Toggle Force Default"),
        plugin.parse("<#f38ba8>ʀɪɢʜᴛ-ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Clear Default"))));

    player.openInventory(inv);
  }

  public static void openGroupListMenu(AntiArmorTrim plugin, Player player, int page) {
    ensureRegistered(plugin);
    List<String> permNodes = new ArrayList<>(plugin.getDbKillEffectGroups().keySet());
    int totalPages = Math.max(1, (int) Math.ceil(permNodes.size() / 36.0));
    int currentPage = Math.max(0, Math.min(page, totalPages - 1));

    String rawTitle = plugin.getConfig().getString("gui.killeffect-groups-title", "<white><sprite:items:item/bookshelf></white> <#f9e2af><bold>ᴋɪʟʟᴇꜰꜰᴇᴄᴛ ɢʀᴏᴜᴘѕ</bold> <#6c7086>(<page>/<max_page>)");
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
          plugin.parse("<#a6adc8>ᴘᴇʀᴍɪѕѕɪᴏɴ <#6c7086>▸ <#f9e2af>" + permNode),
          plugin.parse("<#a6adc8>ѕᴇʟᴇᴄᴛᴇᴅ ᴇꜰꜰᴇᴄᴛѕ <#6c7086>▸ <#a6e3a1>" + effectsInGroup.size())));
      if (!effectsInGroup.isEmpty()) {
        String previewList = String.join(", ", effectsInGroup.stream().limit(5).toList());
        if (effectsInGroup.size() > 5) previewList += "...";
        lore.add(plugin.parse("<#6c7086>(" + previewList + ")"));
      }
      lore.add(Component.empty());
      lore.add(plugin.parse("<white><sprite:items:item/ender_eye></white> <#89dceb>ʟᴇꜰᴛ-ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Select KillEffects"));
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
        plugin.parse("<#a6adc8>ɪɴ ᴄʜᴀᴛ ᴛᴏ ѕᴇʟᴇᴄᴛ effects for it!"))));
    if (currentPage + 1 < totalPages) inv.setItem(51, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>ɴᴇхᴛ</bold>", null));

    player.openInventory(inv);
  }

  public static void openGroupSelectorMenu(AntiArmorTrim plugin, Player player, String permNode, int page) {
    ensureRegistered(plugin);
    List<String> effectIds = plugin.getDbKillEffectItems();
    int totalPages = Math.max(1, (int) Math.ceil(effectIds.size() / 36.0));
    int currentPage = Math.max(0, Math.min(page, totalPages - 1));

    String rawTitle = plugin.getConfig().getString("gui.killeffect-group-select-title", "<white><sprite:items:item/tripwire_hook></white> <#cba6f7><bold>ᴋᴇ ɢʀᴏᴜᴘ</bold> <#6c7086>▸ <#89dceb><perm> <#6c7086>(<page>/<max_page>)");
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
          lore.add(plugin.parse("<white><sprite:items:item/lime_dye></white> <#a6e3a1>ɪɴᴄʟᴜᴅᴇᴅ ɪɴ <#f9e2af>" + permNode));
          lore.add(plugin.parse("<#fab387>ᴄʟɪᴄᴋ ᴛᴏ remove from this group"));
        } else {
          lore.add(plugin.parse("<white><sprite:items:item/gray_dye></white> <#6c7086>ɴᴏᴛ ɪɴ <#f9e2af>" + permNode));
          lore.add(plugin.parse("<#89dceb>ᴄʟɪᴄᴋ ᴛᴏ add to this group"));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(plugin.getHatManager().getItemAppliedHatKey(), PersistentDataType.STRING, effectId);
        icon.setItemMeta(meta);
      }
      plugin.getHatManager().applyHiddenComponentTooltips(icon);
      inv.setItem(slotIndex++, icon);
    }

    inv.setItem(45, TrimMenuHolder.createBackButton(plugin, "<#f9e2af><bold>ʙᴀᴄᴋ</bold> <#6c7086>▸ <#cdd6f4>Groups"));
    if (currentPage > 0) inv.setItem(47, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>ᴘʀᴇᴠɪᴏᴜѕ</bold>", null));
    Set<String> selectedSet = plugin.getDbKillEffectGroups().getOrDefault(permNode.toLowerCase(), Set.of());
    inv.setItem(49, TrimMenuHolder.createStyledButton(plugin, Material.BOOKSHELF, "<#f9e2af><bold>ᴇᴅɪᴛɪɴɢ: " + permNode + "</bold>", List.of(
        plugin.parse("<#a6adc8>ᴛᴏᴛᴀʟ ᴇꜰꜰᴇᴄᴛѕ <#6c7086>▸ <#a6e3a1>" + selectedSet.size()),
        plugin.parse("<#a6adc8>ᴄʟɪᴄᴋ ᴀɴʏ ᴇꜰꜰᴇᴄᴛ above to toggle it!"))));
    if (currentPage + 1 < totalPages) inv.setItem(51, TrimMenuHolder.createStyledButton(plugin, Material.ARROW, "<#89dceb><bold>ɴᴇхᴛ</bold>", null));

    player.openInventory(inv);
  }

  public static void openEditorMenu(AntiArmorTrim plugin, Player player, String effectId) {
    ensureRegistered(plugin);
    String rawTitle = plugin.getConfig().getString("gui.killeffect-editor-title", "<white><sprite:items:item/totem_of_undying></white> <#cba6f7><bold>ᴇᴅɪᴛ ᴋɪʟʟᴇꜰꜰᴇᴄᴛ</bold> <#6c7086>▸ <#89dceb><id>");
    TrimMenuHolder holder = new TrimMenuHolder(TrimMenuHolder.MenuType.KILLEFFECT_EDITOR, null, effectId, 0);
    Inventory inv = Bukkit.createInventory(holder, 27, plugin.parse(rawTitle, Placeholder.unparsed("id", effectId)));
    holder.setInventory(inv);

    ItemStack border = TrimMenuHolder.createFiller(plugin, Material.GRAY_STAINED_GLASS_PANE);
    for (int i = 0; i < 27; i++) inv.setItem(i, border);

    inv.setItem(10, TrimMenuHolder.createStyledButton(plugin, Material.NAME_TAG, "<#89dceb><bold>ᴇᴅɪᴛ ᴅɪѕᴘʟᴀʏ ɴᴀᴍᴇ</bold>", List.of(
        plugin.parse("<#a6adc8>ᴄᴜʀʀᴇɴᴛ <#6c7086>▸ ").append(plugin.getKillEffectManager().getKillEffectDisplayName(effectId)),
        Component.empty(),
        plugin.parse("<#89dceb>ʟᴇꜰᴛ-ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Set new display name"),
        plugin.parse("<#fab387>ʀɪɢʜᴛ-ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Reset to default"))));

    List<Component> loreList = new ArrayList<>();
    List<Component> currentLore = plugin.getKillEffectManager().getKillEffectCustomLore(effectId);
    if (currentLore.isEmpty()) loreList.add(plugin.parse("<#6c7086>No lore configured"));
    else loreList.addAll(currentLore);
    loreList.add(Component.empty());
    loreList.add(plugin.parse("<#89dceb>ʟᴇꜰᴛ-ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Set/append in chat"));
    loreList.add(plugin.parse("<#fab387>ʀɪɢʜᴛ-ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Clear custom lore"));
    inv.setItem(11, TrimMenuHolder.createStyledButton(plugin, Material.WRITABLE_BOOK, "<#cba6f7><bold>ᴇᴅɪᴛ ᴋɪʟʟᴇꜰꜰᴇᴄᴛ ʟᴏʀᴇ</bold>", loreList));

    List<String> groups = plugin.getKillEffectManager().getGroupsContainingKillEffect(effectId);
    inv.setItem(12, TrimMenuHolder.createStyledButton(plugin, Material.BOOKSHELF, "<#a6e3a1><bold>ᴘᴇʀᴍɪѕѕɪᴏɴ ɢʀᴏᴜᴘѕ</bold>", List.of(
        plugin.parse("<#a6adc8>ᴀѕѕɪɢɴᴇᴅ <#6c7086>▸ <#f9e2af>" + (groups.isEmpty() ? "None (Default)" : String.join(", ", groups))),
        Component.empty(),
        plugin.parse("<#89dceb>ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Open Group Manager"))));

    inv.setItem(14, TrimMenuHolder.createStyledButton(plugin, Material.ENDER_EYE, "<#89dceb><bold>ᴘʀᴇᴠɪᴇᴡ 3ᴅ ᴋɪʟʟᴇꜰꜰᴇᴄᴛ</bold>", List.of(
        plugin.parse("<#a6adc8>ᴍᴏᴅᴇʟ ɪᴅ <#6c7086>▸ <#f9e2af>" + effectId),
        Component.empty(),
        plugin.parse("<#a6e3a1>ᴄʟɪᴄᴋ ᴛᴏ test spawn this 3D model!"))));

    boolean isDef = plugin.isDbForceDefaultKillEffect() && effectId.equalsIgnoreCase(plugin.getDbDefaultKillEffect());
    inv.setItem(15, TrimMenuHolder.createStyledButton(plugin, Material.NETHER_STAR, "<#f9e2af><bold>ꜰᴏʀᴄᴇ ᴅᴇꜰᴀᴜʟᴛ</bold>", List.of(
        plugin.parse("<#a6adc8>ᴄᴜʀʀᴇɴᴛ <#6c7086>▸ " + (isDef ? "<#a6e3a1>Active Default" : "<#6c7086>Not Default")),
        Component.empty(),
        plugin.parse("<#89dceb>ʟᴇꜰᴛ-ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Set as Forced Default"),
        plugin.parse("<#fab387>ʀɪɢʜᴛ-ᴄʟɪᴄᴋ <#6c7086>▸ <#cdd6f4>Disable Forced Default"))));

    inv.setItem(16, TrimMenuHolder.createStyledButton(plugin, Material.BARRIER, "<#f38ba8><bold>ᴅᴇʟᴇᴛᴇ ᴋɪʟʟᴇꜰꜰᴇᴄᴛ</bold>", List.of(plugin.parse("<#a6adc8>ʀᴇᴍᴏᴠᴇ <#f9e2af>" + effectId + " <#a6adc8>from database."))));
    inv.setItem(18, TrimMenuHolder.createBackButton(plugin, "<#f9e2af><bold>ʙᴀᴄᴋ</bold> <#6c7086>▸ <#cdd6f4>Admin"));
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
        plugin.createDbKillEffectGroup(normalized);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
        player.sendMessage(plugin.getMessage("hat-admin-group-created", Placeholder.unparsed("perm", normalized)));
        openGroupSelectorMenu(plugin, player, normalized, 0);
      });
      return;
    }

    if (pendingAddKillEffectPlayers.remove(uuid)) {
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
        if (plugin.getDbKillEffectItems().stream().anyMatch(s -> s.equalsIgnoreCase(rawId))) {
          player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
          player.sendMessage(plugin.getMessage("hat-admin-exists", Placeholder.unparsed("id", rawId)));
          openAdminMenu(plugin, player, 0);
          return;
        }
        plugin.addKillEffectToDb(rawId);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
        player.sendMessage(plugin.getMessage("killeffect-admin-added", Placeholder.unparsed("id", rawId)));
        openEditorMenu(plugin, player, rawId);
      });
      return;
    }

    String editingNameEffectId = pendingKillEffectNameEdit.remove(uuid);
    if (editingNameEffectId != null) {
      event.setCancelled(true);
      String rawInput = event.signedMessage().message().trim();
      Bukkit.getScheduler().runTask(plugin, () -> {
        if (!player.isOnline()) return;
        if (rawInput.equalsIgnoreCase("cancel")) {
          player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.8f);
          player.sendMessage(plugin.getMessage("hat-admin-cancelled"));
          openEditorMenu(plugin, player, editingNameEffectId);
          return;
        }
        plugin.setKillEffectNameInDb(editingNameEffectId, rawInput);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.3f);
        player.sendMessage(plugin.getMessage("hat-admin-name-updated", Placeholder.unparsed("id", editingNameEffectId), Placeholder.component("name", plugin.parse(rawInput))));
        openEditorMenu(plugin, player, editingNameEffectId);
      });
      return;
    }

    String editingLoreEffectId = pendingKillEffectLoreEdit.remove(uuid);
    if (editingLoreEffectId != null) {
      event.setCancelled(true);
      String rawInput = event.signedMessage().message().trim();
      Bukkit.getScheduler().runTask(plugin, () -> {
        if (!player.isOnline()) return;
        if (rawInput.equalsIgnoreCase("cancel")) {
          player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.8f);
          player.sendMessage(plugin.getMessage("hat-admin-cancelled"));
          openEditorMenu(plugin, player, editingLoreEffectId);
          return;
        }
        List<String> newLore;
        if (rawInput.regionMatches(true, 0, "add ", 0, 4)) {
          List<String> existing = plugin.getDbKillEffectLore(editingLoreEffectId);
          newLore = existing != null ? new ArrayList<>(existing) : new ArrayList<>();
          newLore.add(rawInput.substring(4).trim());
        } else {
          newLore = new ArrayList<>(Arrays.asList(rawInput.split("\\|")));
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
