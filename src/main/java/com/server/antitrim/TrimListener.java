package com.server.antitrim;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class TrimListener implements Listener {

  private final AntiArmorTrim plugin;
  private Method setGameRuleMethod;

  public TrimListener(AntiArmorTrim plugin) {
    this.plugin = plugin;

    try {
      for (Method m : World.class.getMethods()) {
        if (m.getName().equals("setGameRule") && m.getParameterCount() == 2) {
          this.setGameRuleMethod = m;
          break;
        }
      }
    } catch (Throwable ignored) {}

    for (World world : Bukkit.getWorlds()) lockAlwaysDaytime(world);

    Bukkit.getScheduler().runTaskTimer(plugin, () -> {
      for (World world : Bukkit.getWorlds()) {
        if (world.getEnvironment() == World.Environment.NORMAL) {
          if (world.getTime() != 6000L) world.setTime(6000L);
          if (world.hasStorm()) world.setStorm(false);
          if (world.isThundering()) world.setThundering(false);
        }
      }
    }, 60L, 60L);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onServerLoad(ServerLoadEvent event) {
    plugin.forceTakeoverCommands();
    Bukkit.getScheduler().runTaskLater(plugin, plugin::forceTakeoverCommands, 20L);
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
    Player player = event.getPlayer();
    String msg = event.getMessage().trim();
    String lower = msg.toLowerCase();

    if (lower.equals("/hat") || lower.startsWith("/hat ") || lower.equals("/hats") || lower.startsWith("/hats ")
        || lower.equals("/customhat") || lower.startsWith("/customhat ")) {
      event.setCancelled(true);
      String[] parts = msg.split("\\s+");
      String[] args = parts.length > 1 ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0];
      com.server.antitrim.gui.HatStudioGui.handleCommand(plugin, player, args);
      return;
    }

    // Chỉ chặn các lệnh 3D KillEffect của plugin này, KHÔNG chặn /killeffects của plugin Classic
    if (lower.equals("/killeffect") || lower.startsWith("/killeffect ") || lower.equals("/ke") || lower.startsWith("/ke ")
        || lower.equals("/kfx") || lower.startsWith("/kfx ") || lower.equals("/3dkilleffect") || lower.startsWith("/3dkilleffect ")) {
      event.setCancelled(true);
      String[] parts = msg.split("\\s+");
      String[] args = parts.length > 1 ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0];
      com.server.antitrim.gui.KillEffectStudioGui.handleCommand(plugin, player, args);
      return;
    }

    if (lower.equals("/killsound") || lower.startsWith("/killsound ") || lower.equals("/ks") || lower.startsWith("/ks ")
        || lower.equals("/killsounds") || lower.startsWith("/killsounds ")) {
      event.setCancelled(true);
      String[] parts = msg.split("\\s+");
      String[] args = parts.length > 1 ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0];
      com.server.antitrim.gui.KillSoundStudioGui.handleCommand(plugin, player, args);
      return;
    }

    String cmdToken = lower.split("\\s+")[0];
    if (cmdToken.contains(":")) cmdToken = "/" + cmdToken.substring(cmdToken.indexOf(':') + 1);
    if (cmdToken.startsWith("/kit") || cmdToken.equals("/k") || cmdToken.matches("/k[1-9]")
        || cmdToken.startsWith("/savepublickit") || cmdToken.startsWith("/spk") || cmdToken.startsWith("/publickit")
        || cmdToken.startsWith("/pk") || cmdToken.startsWith("/regear") || cmdToken.equals("/rg")
        || cmdToken.startsWith("/kitroom") || cmdToken.equals("/kr") || cmdToken.startsWith("/premades")
        || cmdToken.startsWith("/repair") || cmdToken.startsWith("/perplayerkit")) {
      plugin.getHatManager().restoreAllCustomHatsInInventory(player);
      Bukkit.getScheduler().runTaskLater(plugin, () -> {
        if (player.isOnline()) scanAndClean(player);
      }, 2L);
    }
  }

  public void lockAlwaysDaytime(World world) {
    if (world == null) return;
    setRuleFalse(world, "org.bukkit.GameRules", "ADVANCE_TIME");
    setRuleFalse(world, "org.bukkit.GameRules", "ADVANCE_WEATHER");
    setRuleFalse(world, "org.bukkit.GameRule", "ADVANCE_TIME");
    setRuleFalse(world, "org.bukkit.GameRule", "ADVANCE_WEATHER");
    setRuleFalse(world, "org.bukkit.GameRule", "DO_DAYLIGHT_CYCLE");
    setRuleFalse(world, "org.bukkit.GameRule", "DO_WEATHER_CYCLE");

    if (world.getEnvironment() == World.Environment.NORMAL) {
      if (world.getTime() != 6000L) world.setTime(6000L);
      if (world.hasStorm()) world.setStorm(false);
      if (world.isThundering()) world.setThundering(false);
      world.setClearWeatherDuration(Integer.MAX_VALUE);
    }
  }

  private void setRuleFalse(World world, String className, String fieldName) {
    if (setGameRuleMethod == null) return;
    try {
      Class<?> clazz = Class.forName(className);
      Field field = clazz.getField(fieldName);
      Object ruleObj = field.get(null);
      if (ruleObj != null) setGameRuleMethod.invoke(world, ruleObj, Boolean.FALSE);
    } catch (Throwable ignored) {}
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onWorldLoad(WorldLoadEvent event) { lockAlwaysDaytime(event.getWorld()); }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onWeatherChange(WeatherChangeEvent event) {
    if (event.toWeatherState()) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onThunderChange(ThunderChangeEvent event) {
    if (event.toThunderState()) event.setCancelled(true);
  }

  private boolean hasArmorTrim(ItemStack item) {
    if (item == null || item.getType().isAir()) return false;
    return item.getItemMeta() instanceof ArmorMeta armorMeta && armorMeta.hasTrim();
  }

  public void scanAndClean(Player player) {
    ItemStack cursor = player.getItemOnCursor();
    if (plugin.getHatManager().isCustomHatItem(cursor)) player.setItemOnCursor(plugin.getHatManager().restoreOriginalHelmet(cursor));

    plugin.getHatManager().applyAllSavedTrims(player);

    if (plugin.getHatManager().getEffectiveHatId(player) == null || plugin.getHatManager().isExternalGuiOpen(player)) {
      ItemStack helmet = player.getInventory().getHelmet();
      if (plugin.getHatManager().isCustomHatItem(helmet)) player.getInventory().setHelmet(plugin.getHatManager().restoreOriginalHelmet(helmet));
    }

    boolean removedTrim = false;
    boolean removedRename = false;
    boolean canBypass = plugin.getHatManager().canUseBypass(player);
    boolean canRename = plugin.getRenameManager().canUseRename(player);

    ItemStack[] contents = player.getInventory().getContents();
    for (int i = 0; i < contents.length; i++) {
      ItemStack it = contents[i];
      if (it == null || it.getType().isAir()) continue;

      if (!canRename && plugin.getRenameManager().isPluginRenamedItem(it)) {
        player.getInventory().setItem(i, null);
        removedRename = true;
        continue;
      }

      if (!canBypass && hasArmorTrim(it)) {
        player.getInventory().setItem(i, null);
        removedTrim = true;
      }
    }
    if (removedRename) {
      player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
      player.sendMessage(plugin.getMessage("rename-unauthorized-pickup"));
    }
    if (removedTrim) {
      player.sendMessage(plugin.getMessage("remove-trim"));
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onArmorChange(PlayerArmorChangeEvent event) {
    Player player = event.getPlayer();
    EquipmentSlot slot = switch (event.getSlotType()) {
      case HEAD -> EquipmentSlot.HEAD;
      case CHEST -> EquipmentSlot.CHEST;
      case LEGS -> EquipmentSlot.LEGS;
      case FEET -> EquipmentSlot.FEET;
    };
    Bukkit.getScheduler().runTask(plugin, () -> {
      if (!player.isOnline()) return;
      plugin.getHatManager().applySavedTrimToSlot(player, slot);
      scanAndClean(player);
    });
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onDeath(PlayerDeathEvent event) {
    List<ItemStack> drops = event.getDrops();
    for (int i = 0; i < drops.size(); i++) {
      ItemStack drop = drops.get(i);
      if (plugin.getHatManager().isCustomHatItem(drop)) drops.set(i, plugin.getHatManager().restoreOriginalHelmet(drop));
    }
    Player victim = event.getPlayer();
    plugin.getHatManager().restoreAllCustomHatsInInventory(victim);

    Player killer = plugin.getCombatTracker().resolveKiller(event);
    if (killer != null) {
      // Kích hoạt mô hình 3D KillEffect kể cả khi tự sát
      plugin.getKillEffectManager().playModelEngineKillEffect(killer, victim);

      // KillSound chỉ phát khi người khác giết trong PvP
      if (!killer.equals(victim)) {
        plugin.getKillSoundManager().playNexoKillSound(killer, victim);
      }
      plugin.getCombatTracker().formatDeathMessageWeapon(event, killer);
    }
    plugin.getCombatTracker().clearVictimRecord(victim.getUniqueId());
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onRespawn(PlayerRespawnEvent event) {
    Player player = event.getPlayer();
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      if (!player.isOnline()) return;
      plugin.getHatManager().forceRefreshEquippedHat(player);
      scanAndClean(player);
    }, 2L);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onTeleport(PlayerTeleportEvent event) {
    if (event.getFrom().getWorld() != null && event.getTo() != null && event.getTo().getWorld() != null) {
      if (!event.getFrom().getWorld().equals(event.getTo().getWorld())) {
        plugin.getHatManager().restoreAllCustomHatsInInventory(event.getPlayer());
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onChangedWorld(PlayerChangedWorldEvent event) {
    Player player = event.getPlayer();
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      if (!player.isOnline()) return;
      plugin.getHatManager().forceRefreshEquippedHat(player);
      scanAndClean(player);
    }, 2L);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onDrop(PlayerDropItemEvent event) {
    ItemStack dropped = event.getItemDrop().getItemStack();
    if (plugin.getHatManager().isCustomHatItem(dropped)) event.getItemDrop().setItemStack(plugin.getHatManager().restoreOriginalHelmet(dropped));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onSwapHand(PlayerSwapHandItemsEvent event) {
    if (plugin.getHatManager().isCustomHatItem(event.getOffHandItem())) event.setOffHandItem(plugin.getHatManager().restoreOriginalHelmet(event.getOffHandItem()));
    if (plugin.getHatManager().isCustomHatItem(event.getMainHandItem())) event.setMainHandItem(plugin.getHatManager().restoreOriginalHelmet(event.getMainHandItem()));
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    scanAndClean(player);
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      if (!player.isOnline()) return;
      plugin.getHatManager().forceRefreshEquippedHat(player);
      scanAndClean(player);
    }, 2L);
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onQuitRestore(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    plugin.getCombatTracker().clearVictimRecord(player.getUniqueId());
    plugin.getHatManager().restoreAllCustomHatsInInventory(player);
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onOpenLowest(InventoryOpenEvent event) {
    if (!plugin.getHatManager().isExternalGui(event.getInventory())) return;
    if (event.getPlayer() instanceof Player player) {
      plugin.getHatManager().restoreAllCustomHatsInInventory(player);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onClickLowest(InventoryClickEvent event) {
    if (event.getInventory().getHolder() instanceof com.server.antitrim.gui.TrimMenuHolder) return;
    if (!(event.getWhoClicked() instanceof Player player)) return;

    if (plugin.getHatManager().isExternalGui(event.getInventory())) {
      plugin.getHatManager().restoreAllCustomHatsInInventory(player);
      return;
    }

    boolean clickedHat = false;
    if (plugin.getHatManager().isCustomHatItem(event.getCurrentItem())) {
      event.setCurrentItem(plugin.getHatManager().restoreOriginalHelmet(event.getCurrentItem()));
      clickedHat = true;
    }
    if (plugin.getHatManager().isCustomHatItem(event.getCursor())) {
      event.getView().setCursor(plugin.getHatManager().restoreOriginalHelmet(event.getCursor()));
      clickedHat = true;
    }
    if (event.getClick() == ClickType.NUMBER_KEY) {
      int hotbarBtn = event.getHotbarButton();
      if (hotbarBtn >= 0) {
        ItemStack hotbarItem = player.getInventory().getItem(hotbarBtn);
        if (plugin.getHatManager().isCustomHatItem(hotbarItem)) {
          player.getInventory().setItem(hotbarBtn, plugin.getHatManager().restoreOriginalHelmet(hotbarItem));
          clickedHat = true;
        }
      }
    } else if (event.getClick() == ClickType.SWAP_OFFHAND) {
      ItemStack offhand = player.getInventory().getItemInOffHand();
      if (plugin.getHatManager().isCustomHatItem(offhand)) {
        player.getInventory().setItemInOffHand(plugin.getHatManager().restoreOriginalHelmet(offhand));
        clickedHat = true;
      }
    }

    if (clickedHat) {
      Bukkit.getScheduler().runTask(plugin, () -> {
        if (player.isOnline()) scanAndClean(player);
      });
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onCloseLowest(InventoryCloseEvent event) {
    if (!(event.getPlayer() instanceof Player player)) return;

    ItemStack cursor = player.getItemOnCursor();
    if (plugin.getHatManager().isCustomHatItem(cursor)) {
      player.setItemOnCursor(plugin.getHatManager().restoreOriginalHelmet(cursor));
    }

    if (plugin.getHatManager().isExternalGui(event.getInventory())) {
      plugin.getHatManager().restoreAllCustomHatsInInventory(player);
      Bukkit.getScheduler().runTaskLater(plugin, () -> {
        if (player.isOnline()) scanAndClean(player);
      }, 1L);
    } else {
      scanAndClean(player);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onClick(InventoryClickEvent event) {
    if (event.getInventory().getHolder() instanceof com.server.antitrim.gui.TrimMenuHolder) return;
    if (!(event.getWhoClicked() instanceof Player player)) return;

    if (!plugin.getRenameManager().canUseRename(player)) {
      if (plugin.getRenameManager().isPluginRenamedItem(event.getCurrentItem())) {
        event.setCurrentItem(null);
        event.setCancelled(true);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
        player.sendMessage(plugin.getMessage("rename-unauthorized-pickup"));
        return;
      }
      if (plugin.getRenameManager().isPluginRenamedItem(event.getCursor())) {
        event.getView().setCursor(null);
        event.setCancelled(true);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
        player.sendMessage(plugin.getMessage("rename-unauthorized-pickup"));
        return;
      }
    }

    if (plugin.getHatManager().canUseBypass(player)) return;

    boolean triggered = false;
    if (hasArmorTrim(event.getCurrentItem())) {
      event.setCurrentItem(null);
      event.setCancelled(true);
      triggered = true;
    }
    if (hasArmorTrim(event.getCursor())) {
      event.getView().setCursor(null);
      event.setCancelled(true);
      triggered = true;
    }
    if (event.getClick() == ClickType.NUMBER_KEY) {
      int hotbarBtn = event.getHotbarButton();
      if (hotbarBtn >= 0 && hasArmorTrim(player.getInventory().getItem(hotbarBtn))) {
        player.getInventory().setItem(hotbarBtn, null);
        event.setCancelled(true);
        triggered = true;
      }
    }
    if (triggered) player.sendMessage(plugin.getMessage("remove-trim"));
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onInteract(PlayerInteractEvent event) {
    Player player = event.getPlayer();

    if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
      Block clicked = event.getClickedBlock();
      if (clicked != null) {
        Material type = clicked.getType();
        if (type == Material.RESPAWN_ANCHOR || type.name().endsWith("_BED")) {
          plugin.getCombatTracker().recordBlockExplosionTrigger(player, clicked.getLocation());
        }
      }
    }

    ItemStack item = event.getItem();
    if (plugin.getHatManager().isCustomHatItem(item) && event.getHand() != null) {
      item = plugin.getHatManager().restoreOriginalHelmet(item);
      player.getInventory().setItem(event.getHand(), item);
    }

    if (item != null && !plugin.getRenameManager().canUseRename(player) && plugin.getRenameManager().isPluginRenamedItem(item)) {
      event.setCancelled(true);
      if (event.getHand() != null) player.getInventory().setItem(event.getHand(), null);
      player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
      player.sendMessage(plugin.getMessage("rename-unauthorized-pickup"));
      return;
    }

    if (plugin.getHatManager().canUseBypass(player)) return;
    if (hasArmorTrim(item)) {
      event.setCancelled(true);
      if (event.getHand() != null) player.getInventory().setItem(event.getHand(), null);
      player.sendMessage(plugin.getMessage("remove-trim"));
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onPickup(EntityPickupItemEvent event) {
    if (event.getEntity() instanceof Player player) {
      ItemStack picked = event.getItem().getItemStack();
      if (plugin.getHatManager().isCustomHatItem(picked)) {
        picked = plugin.getHatManager().restoreOriginalHelmet(picked);
        event.getItem().setItemStack(picked);
      }

      if (!plugin.getRenameManager().canUseRename(player) && plugin.getRenameManager().isPluginRenamedItem(picked)) {
        event.setCancelled(true);
        event.getItem().remove();
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
        player.sendMessage(plugin.getMessage("rename-unauthorized-pickup"));
        return;
      }

      if (plugin.getHatManager().canUseBypass(player)) return;
      if (hasArmorTrim(picked)) {
        event.setCancelled(true);
        event.getItem().remove();
        player.sendMessage(plugin.getMessage("remove-trim"));
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onSmith(PrepareSmithingEvent event) {
    if (event.getView().getPlayer() instanceof Player player) {
      if (plugin.getHatManager().canUseBypass(player)) return;
      if (hasArmorTrim(event.getResult())) {
        event.setResult(null);
        player.sendActionBar(plugin.getRawMessage("deny-smith"));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
      }
    }
  }
}
