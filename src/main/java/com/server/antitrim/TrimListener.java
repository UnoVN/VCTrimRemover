package com.server.antitrim;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
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
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TrimListener implements Listener {

  private final AntiArmorTrim plugin;
  private final TrimGuiManager guiManager;
  private Method setGameRuleMethod;

  private record DamageRecord(UUID attackerUuid, long timestamp) {}

  private final Map<UUID, DamageRecord> lastDamagerMap = new ConcurrentHashMap<>();
  private final Map<UUID, UUID> crystalAttackerMap = new ConcurrentHashMap<>();
  private volatile UUID lastExplosionTriggerPlayer;
  private volatile Location lastExplosionTriggerLoc;
  private volatile long lastExplosionTriggerTime;

  public TrimListener(AntiArmorTrim plugin, TrimGuiManager guiManager) {
    this.plugin = plugin;
    this.guiManager = guiManager;

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
      plugin.handleHatCommand(player, args);
      return;
    }

    if (lower.equals("/killeffect") || lower.startsWith("/killeffect ") || lower.equals("/ke") || lower.startsWith("/ke ")
        || lower.equals("/kfx") || lower.startsWith("/kfx ") || lower.equals("/killeffects") || lower.startsWith("/killeffects ")) {
      event.setCancelled(true);
      String[] parts = msg.split("\\s+");
      String[] args = parts.length > 1 ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0];
      plugin.handleKillEffectCommand(player, args);
      return;
    }

    if (lower.equals("/killsound") || lower.startsWith("/killsound ") || lower.equals("/ks") || lower.startsWith("/ks ")
        || lower.equals("/killsounds") || lower.startsWith("/killsounds ")) {
      event.setCancelled(true);
      String[] parts = msg.split("\\s+");
      String[] args = parts.length > 1 ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0];
      plugin.handleKillSoundCommand(player, args);
      return;
    }

    String cmdToken = lower.split("\\s+")[0];
    if (cmdToken.contains(":")) cmdToken = "/" + cmdToken.substring(cmdToken.indexOf(':') + 1);
    if (cmdToken.startsWith("/kit") || cmdToken.equals("/k") || cmdToken.matches("/k[1-9]")
        || cmdToken.startsWith("/savepublickit") || cmdToken.startsWith("/spk") || cmdToken.startsWith("/publickit")
        || cmdToken.startsWith("/pk") || cmdToken.startsWith("/regear") || cmdToken.equals("/rg")
        || cmdToken.startsWith("/kitroom") || cmdToken.equals("/kr") || cmdToken.startsWith("/premades")
        || cmdToken.startsWith("/repair") || cmdToken.startsWith("/perplayerkit")) {
      guiManager.restoreAllCustomHatsInInventory(player);
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

  private void scanAndClean(Player player) {
    ItemStack cursor = player.getItemOnCursor();
    if (guiManager.isCustomHatItem(cursor)) player.setItemOnCursor(guiManager.restoreOriginalHelmet(cursor));

    guiManager.applyAllSavedTrims(player);

    if (guiManager.getEffectiveHatId(player) == null || guiManager.isExternalGuiOpen(player)) {
      ItemStack helmet = player.getInventory().getHelmet();
      if (guiManager.isCustomHatItem(helmet)) player.getInventory().setHelmet(guiManager.restoreOriginalHelmet(helmet));
    }

    if (guiManager.canUseBypass(player)) return;

    boolean removed = false;
    ItemStack[] contents = player.getInventory().getContents();
    for (int i = 0; i < contents.length; i++) {
      if (hasArmorTrim(contents[i])) {
        player.getInventory().setItem(i, null);
        removed = true;
      }
    }
    if (removed) player.sendMessage(plugin.getMessage("remove-trim"));
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
      guiManager.applySavedTrimToSlot(player, slot);
      scanAndClean(player);
    });
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onCombatDamageByEntity(EntityDamageByEntityEvent event) {
    Entity damaged = event.getEntity();
    Entity damager = event.getDamager();

    if (damaged instanceof EnderCrystal crystal) {
      Player crystalBreaker = extractAttackerPlayer(damager);
      if (crystalBreaker != null) {
        crystalAttackerMap.put(crystal.getUniqueId(), crystalBreaker.getUniqueId());
        lastExplosionTriggerPlayer = crystalBreaker.getUniqueId();
        lastExplosionTriggerLoc = crystal.getLocation();
        lastExplosionTriggerTime = System.currentTimeMillis();
        Bukkit.getScheduler().runTaskLater(plugin, () -> crystalAttackerMap.remove(crystal.getUniqueId()), 40L);
      }
      return;
    }

    if (damaged instanceof Player victim) {
      Player attacker = extractAttackerPlayer(damager);
      if (attacker != null && !attacker.equals(victim)) {
        lastDamagerMap.put(victim.getUniqueId(), new DamageRecord(attacker.getUniqueId(), System.currentTimeMillis()));
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onExplosionDamage(EntityDamageEvent event) {
    if (!(event.getEntity() instanceof Player victim)) return;
    EntityDamageEvent.DamageCause cause = event.getCause();
    if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
      UUID triggerUuid = lastExplosionTriggerPlayer;
      Location triggerLoc = lastExplosionTriggerLoc;
      long now = System.currentTimeMillis();
      if (triggerUuid != null && !triggerUuid.equals(victim.getUniqueId())
          && (now - lastExplosionTriggerTime) <= 1000L
          && triggerLoc != null && triggerLoc.getWorld() != null
          && triggerLoc.getWorld().equals(victim.getWorld())
          && triggerLoc.distanceSquared(victim.getLocation()) <= 576) {
        lastDamagerMap.put(victim.getUniqueId(), new DamageRecord(triggerUuid, now));
      }
    }
  }

  @Nullable
  private Player extractAttackerPlayer(Entity damager) {
    if (damager instanceof Player p) return p;
    if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
    if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) return p;
    if (damager instanceof EnderCrystal crystal) {
      UUID uuid = crystalAttackerMap.get(crystal.getUniqueId());
      if (uuid != null) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) return p;
      }
      if (lastExplosionTriggerPlayer != null && (System.currentTimeMillis() - lastExplosionTriggerTime) <= 1000L) {
        return Bukkit.getPlayer(lastExplosionTriggerPlayer);
      }
    }
    return null;
  }

  @Nullable
  private Player resolveKiller(PlayerDeathEvent event) {
    Player victim = event.getPlayer();
    Player killer = victim.getKiller();
    if (killer != null && !killer.equals(victim)) return killer;

    try {
      Entity causing = event.getDamageSource().getCausingEntity();
      if (causing instanceof Player p && !p.equals(victim)) return p;
      Entity direct = event.getDamageSource().getDirectEntity();
      Player directPlayer = extractAttackerPlayer(direct);
      if (directPlayer != null && !directPlayer.equals(victim)) return directPlayer;
    } catch (Throwable ignored) {}

    DamageRecord record = lastDamagerMap.get(victim.getUniqueId());
    if (record != null && (System.currentTimeMillis() - record.timestamp()) <= 15000L) {
      Player tracked = Bukkit.getPlayer(record.attackerUuid());
      if (tracked != null && tracked.isOnline() && !tracked.equals(victim)) return tracked;
    }
    return null;
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onDeath(PlayerDeathEvent event) {
    List<ItemStack> drops = event.getDrops();
    for (int i = 0; i < drops.size(); i++) {
      ItemStack drop = drops.get(i);
      if (guiManager.isCustomHatItem(drop)) drops.set(i, guiManager.restoreOriginalHelmet(drop));
    }
    Player victim = event.getPlayer();
    guiManager.restoreAllCustomHatsInInventory(victim);

    Player killer = resolveKiller(event);
    lastDamagerMap.remove(victim.getUniqueId());
    if (killer != null && !killer.equals(victim)) {
      guiManager.playModelEngineKillEffect(killer, victim);
      guiManager.playNexoKillSound(killer, victim);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onRespawn(PlayerRespawnEvent event) {
    Player player = event.getPlayer();
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      if (!player.isOnline()) return;
      guiManager.forceRefreshEquippedHat(player);
      scanAndClean(player);
    }, 2L);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onTeleport(PlayerTeleportEvent event) {
    if (event.getFrom().getWorld() != null && event.getTo() != null && event.getTo().getWorld() != null) {
      if (!event.getFrom().getWorld().equals(event.getTo().getWorld())) {
        guiManager.restoreAllCustomHatsInInventory(event.getPlayer());
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onChangedWorld(PlayerChangedWorldEvent event) {
    Player player = event.getPlayer();
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      if (!player.isOnline()) return;
      guiManager.forceRefreshEquippedHat(player);
      scanAndClean(player);
    }, 2L);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onDrop(PlayerDropItemEvent event) {
    ItemStack dropped = event.getItemDrop().getItemStack();
    if (guiManager.isCustomHatItem(dropped)) event.getItemDrop().setItemStack(guiManager.restoreOriginalHelmet(dropped));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onSwapHand(PlayerSwapHandItemsEvent event) {
    if (guiManager.isCustomHatItem(event.getOffHandItem())) event.setOffHandItem(guiManager.restoreOriginalHelmet(event.getOffHandItem()));
    if (guiManager.isCustomHatItem(event.getMainHandItem())) event.setMainHandItem(guiManager.restoreOriginalHelmet(event.getMainHandItem()));
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    scanAndClean(player);
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      if (!player.isOnline()) return;
      guiManager.forceRefreshEquippedHat(player);
      scanAndClean(player);
    }, 2L);
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onQuitRestore(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    lastDamagerMap.remove(player.getUniqueId());
    guiManager.restoreAllCustomHatsInInventory(player);
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onOpenLowest(InventoryOpenEvent event) {
    if (!guiManager.isExternalGui(event.getInventory())) return;
    if (event.getPlayer() instanceof Player player) {
      guiManager.restoreAllCustomHatsInInventory(player);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onClickLowest(InventoryClickEvent event) {
    if (event.getInventory().getHolder() instanceof TrimGuiManager.TrimMenuHolder) return;
    if (!(event.getWhoClicked() instanceof Player player)) return;

    if (guiManager.isExternalGui(event.getInventory())) {
      guiManager.restoreAllCustomHatsInInventory(player);
      return;
    }

    boolean clickedHat = false;
    if (guiManager.isCustomHatItem(event.getCurrentItem())) {
      event.setCurrentItem(guiManager.restoreOriginalHelmet(event.getCurrentItem()));
      clickedHat = true;
    }
    if (guiManager.isCustomHatItem(event.getCursor())) {
      event.getView().setCursor(guiManager.restoreOriginalHelmet(event.getCursor()));
      clickedHat = true;
    }
    if (event.getClick() == ClickType.NUMBER_KEY) {
      int hotbarBtn = event.getHotbarButton();
      if (hotbarBtn >= 0) {
        ItemStack hotbarItem = player.getInventory().getItem(hotbarBtn);
        if (guiManager.isCustomHatItem(hotbarItem)) {
          player.getInventory().setItem(hotbarBtn, guiManager.restoreOriginalHelmet(hotbarItem));
          clickedHat = true;
        }
      }
    } else if (event.getClick() == ClickType.SWAP_OFFHAND) {
      ItemStack offhand = player.getInventory().getItemInOffHand();
      if (guiManager.isCustomHatItem(offhand)) {
        player.getInventory().setItemInOffHand(guiManager.restoreOriginalHelmet(offhand));
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
    if (guiManager.isCustomHatItem(cursor)) {
      player.setItemOnCursor(guiManager.restoreOriginalHelmet(cursor));
    }

    if (guiManager.isExternalGui(event.getInventory())) {
      guiManager.restoreAllCustomHatsInInventory(player);
      Bukkit.getScheduler().runTaskLater(plugin, () -> {
        if (player.isOnline()) scanAndClean(player);
      }, 1L);
    } else {
      scanAndClean(player);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onClick(InventoryClickEvent event) {
    if (event.getInventory().getHolder() instanceof TrimGuiManager.TrimMenuHolder) return;
    if (!(event.getWhoClicked() instanceof Player player)) return;
    if (guiManager.canUseBypass(player)) return;

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
          lastExplosionTriggerPlayer = player.getUniqueId();
          lastExplosionTriggerLoc = clicked.getLocation();
          lastExplosionTriggerTime = System.currentTimeMillis();
        }
      }
    }

    ItemStack item = event.getItem();
    if (guiManager.isCustomHatItem(item) && event.getHand() != null) {
      item = guiManager.restoreOriginalHelmet(item);
      player.getInventory().setItem(event.getHand(), item);
    }
    if (guiManager.canUseBypass(player)) return;
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
      if (guiManager.isCustomHatItem(picked)) {
        picked = guiManager.restoreOriginalHelmet(picked);
        event.getItem().setItemStack(picked);
      }
      if (guiManager.canUseBypass(player)) return;
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
      if (guiManager.canUseBypass(player)) return;
      if (hasArmorTrim(event.getResult())) {
        event.setResult(null);
        player.sendActionBar(plugin.getRawMessage("deny-smith"));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
      }
    }
  }
}
