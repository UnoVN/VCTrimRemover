package com.server.antitrim;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatTracker implements Listener {

  private final AntiArmorTrim plugin;

  public record CombatRecord(UUID attackerUuid, @Nullable ItemStack weaponUsed, long timestamp) {}

  private final Map<UUID, CombatRecord> lastDamagerMap = new ConcurrentHashMap<>();
  private final Map<UUID, ItemStack> projectileWeaponMap = new ConcurrentHashMap<>();
  private final Map<UUID, UUID> explosiveDetonatorMap = new ConcurrentHashMap<>();
  private final Map<UUID, ItemStack> explosiveWeaponMap = new ConcurrentHashMap<>();

  private volatile UUID lastBlockExploderUUID;
  private volatile Location lastBlockExploderLoc;
  private volatile ItemStack lastBlockExploderWeapon;
  private volatile long lastBlockExploderTime;

  public CombatTracker(AntiArmorTrim plugin) {
    this.plugin = plugin;
  }

  public void recordBlockExplosionTrigger(Player player, Location loc) {
    this.lastBlockExploderUUID = player.getUniqueId();
    this.lastBlockExploderLoc = loc.clone();
    ItemStack held = player.getInventory().getItemInMainHand();
    this.lastBlockExploderWeapon = (held != null && !held.getType().isAir()) ? held.clone() : null;
    this.lastBlockExploderTime = System.currentTimeMillis();
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onShootBow(EntityShootBowEvent event) {
    if (event.getEntity() instanceof Player && event.getBow() != null) {
      projectileWeaponMap.put(event.getProjectile().getUniqueId(), event.getBow().clone());
      Bukkit.getScheduler().runTaskLater(plugin, () -> projectileWeaponMap.remove(event.getProjectile().getUniqueId()), 100L);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onCombatDamage(EntityDamageByEntityEvent event) {
    Entity damaged = event.getEntity();
    Entity damager = event.getDamager();

    // 1. Khi bắn trúng hoặc đánh vào End Crystal / TNT / TNT Minecart
    if (damaged instanceof EnderCrystal || damaged instanceof TNTPrimed || damaged instanceof ExplosiveMinecart) {
      Player triggerPlayer = null;
      ItemStack weaponUsed = null;

      if (damager instanceof Player p) {
        triggerPlayer = p;
        ItemStack hand = p.getInventory().getItemInMainHand();
        weaponUsed = (hand != null && !hand.getType().isAir()) ? hand.clone() : null;
      } else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
        triggerPlayer = p;
        weaponUsed = projectileWeaponMap.get(proj.getUniqueId());
        if (weaponUsed == null) {
          ItemStack hand = p.getInventory().getItemInMainHand();
          weaponUsed = (hand != null && !hand.getType().isAir()) ? hand.clone() : null;
        }
      }

      if (triggerPlayer != null) {
        explosiveDetonatorMap.put(damaged.getUniqueId(), triggerPlayer.getUniqueId());
        if (weaponUsed != null) explosiveWeaponMap.put(damaged.getUniqueId(), weaponUsed);
        lastBlockExploderUUID = triggerPlayer.getUniqueId();
        lastBlockExploderLoc = damaged.getLocation();
        lastBlockExploderWeapon = weaponUsed;
        lastBlockExploderTime = System.currentTimeMillis();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
          explosiveDetonatorMap.remove(damaged.getUniqueId());
          explosiveWeaponMap.remove(damaged.getUniqueId());
        }, 60L);
      }
      return;
    }

    // 2. Khi người chơi nhận sát thương từ thực thể khác
    if (damaged instanceof Player victim) {
      Player attacker = null;
      ItemStack weapon = null;

      if (damager instanceof Player p) {
        attacker = p;
        ItemStack hand = p.getInventory().getItemInMainHand();
        weapon = (hand != null && !hand.getType().isAir()) ? hand.clone() : null;
      } else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
        attacker = p;
        weapon = projectileWeaponMap.get(proj.getUniqueId());
        if (weapon == null) {
          ItemStack hand = p.getInventory().getItemInMainHand();
          weapon = (hand != null && !hand.getType().isAir()) ? hand.clone() : null;
        }
      } else if (damager instanceof EnderCrystal || damager instanceof TNTPrimed || damager instanceof ExplosiveMinecart) {
        UUID detonatorUuid = explosiveDetonatorMap.get(damager.getUniqueId());
        if (detonatorUuid != null) {
          attacker = Bukkit.getPlayer(detonatorUuid);
          weapon = explosiveWeaponMap.get(damager.getUniqueId());
        } else if (lastBlockExploderUUID != null && (System.currentTimeMillis() - lastBlockExploderTime) <= 1000L) {
          attacker = Bukkit.getPlayer(lastBlockExploderUUID);
          weapon = lastBlockExploderWeapon;
        }
      }

      if (attacker != null && !attacker.equals(victim)) {
        lastDamagerMap.put(victim.getUniqueId(), new CombatRecord(attacker.getUniqueId(), weapon, System.currentTimeMillis()));
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onExplosionDamage(EntityDamageEvent event) {
    if (!(event.getEntity() instanceof Player victim)) return;
    EntityDamageEvent.DamageCause cause = event.getCause();
    if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
      UUID triggerUuid = lastBlockExploderUUID;
      Location triggerLoc = lastBlockExploderLoc;
      long now = System.currentTimeMillis();
      if (triggerUuid != null && !triggerUuid.equals(victim.getUniqueId())
          && (now - lastBlockExploderTime) <= 1200L
          && triggerLoc != null && triggerLoc.getWorld() != null
          && triggerLoc.getWorld().equals(victim.getWorld())
          && triggerLoc.distanceSquared(victim.getLocation()) <= 576) {
        lastDamagerMap.put(victim.getUniqueId(), new CombatRecord(triggerUuid, lastBlockExploderWeapon, now));
      }
    }
  }

  @Nullable
  public Player resolveKiller(PlayerDeathEvent event) {
    Player victim = event.getPlayer();
    Player killer = victim.getKiller();
    if (killer != null && !killer.equals(victim)) return killer;

    try {
      Entity causing = event.getDamageSource().getCausingEntity();
      if (causing instanceof Player p && !p.equals(victim)) return p;
    } catch (Throwable ignored) {}

    CombatRecord record = lastDamagerMap.get(victim.getUniqueId());
    if (record != null && (System.currentTimeMillis() - record.timestamp()) <= 15000L) {
      Player tracked = Bukkit.getPlayer(record.attackerUuid());
      if (tracked != null && tracked.isOnline() && !tracked.equals(victim)) return tracked;
    }
    return null;
  }

  @Nullable
  public ItemStack resolveKillerWeapon(Player victim, Player killer) {
    CombatRecord record = lastDamagerMap.get(victim.getUniqueId());
    if (record != null && record.attackerUuid().equals(killer.getUniqueId()) && record.weaponUsed() != null) {
      return record.weaponUsed();
    }
    ItemStack hand = killer.getInventory().getItemInMainHand();
    return (hand != null && !hand.getType().isAir()) ? hand : null;
  }

  public void clearVictimRecord(UUID victimUuid) {
    lastDamagerMap.remove(victimUuid);
  }

  // Tự động kiểm tra và thêm tên vũ khí đã rename kèm Hover Tooltip vào tin nhắn chết
  public void formatDeathMessageWeapon(PlayerDeathEvent event, Player killer) {
    Component originalMsg = event.deathMessage();
    if (originalMsg == null) return;

    ItemStack weapon = resolveKillerWeapon(event.getPlayer(), killer);
    if (weapon == null || weapon.getType().isAir()) return;

    Component weaponName = null;
    if (weapon.hasItemMeta() && weapon.getItemMeta().hasDisplayName()) {
      weaponName = weapon.getItemMeta().displayName();
    }

    if (weaponName != null) {
      Component weaponWithHover = Component.text("[")
          .append(weaponName)
          .append(Component.text("]"))
          .color(NamedTextColor.WHITE)
          .hoverEvent(weapon.asHoverEvent());

      Component finalMsg = originalMsg.append(Component.text(" using ").color(NamedTextColor.GRAY))
          .append(weaponWithHover);
      event.deathMessage(finalMsg);
    }
  }
}
