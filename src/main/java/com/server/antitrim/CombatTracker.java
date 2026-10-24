package com.server.antitrim;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
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

  private volatile UUID lastExplosionTriggerUUID;
  private volatile Location lastExplosionTriggerLoc;
  private volatile ItemStack lastExplosionTriggerWeapon;
  private volatile long lastExplosionTriggerTime;

  public CombatTracker(AntiArmorTrim plugin) {
    this.plugin = plugin;
  }

  public void recordBlockExplosionTrigger(Player player, Location loc) {
    this.lastExplosionTriggerUUID = player.getUniqueId();
    this.lastExplosionTriggerLoc = loc.clone();
    ItemStack held = player.getInventory().getItemInMainHand();
    this.lastExplosionTriggerWeapon = (held != null && !held.getType().isAir()) ? held.clone() : null;
    this.lastExplosionTriggerTime = System.currentTimeMillis();
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onShootBow(EntityShootBowEvent event) {
    if (event.getEntity() instanceof Player && event.getBow() != null) {
      projectileWeaponMap.put(event.getProjectile().getUniqueId(), event.getBow().clone());
      Bukkit.getScheduler().runTaskLater(plugin, () -> projectileWeaponMap.remove(event.getProjectile().getUniqueId()), 300L);
    }
  }

  /**
   * Bắt sự kiện mũi tên chạm vào thực thể.
   * Kích nổ TNT Minecart ngay lập tức kể cả khi chỉ nhấp nhẹ cung (tap-shot không cần full charge),
   * đồng thời ghi nhận vũ khí cung đã bắn.
   */
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onProjectileHit(ProjectileHitEvent event) {
    Entity hitEntity = event.getHitEntity();
    if (hitEntity == null) return;
    Projectile proj = event.getEntity();

    if (hitEntity.getType() == EntityType.TNT_MINECART) {
      markExplosiveTrigger(hitEntity, proj);
      if (hitEntity instanceof ExplosiveMinecart em) {
        Bukkit.getScheduler().runTask(plugin, () -> {
          if (em.isValid()) {
            em.explode();
          }
        });
      }
    } else if (hitEntity.getType() == EntityType.END_CRYSTAL || hitEntity.getType() == EntityType.TNT) {
      markExplosiveTrigger(hitEntity, proj);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onVehicleDamage(VehicleDamageEvent event) {
    Entity vehicle = event.getVehicle();
    Entity attackerEntity = event.getAttacker();
    if (vehicle.getType() == EntityType.TNT_MINECART && attackerEntity != null) {
      markExplosiveTrigger(vehicle, attackerEntity);
      if (vehicle instanceof ExplosiveMinecart em) {
        Bukkit.getScheduler().runTask(plugin, () -> {
          if (em.isValid()) {
            em.explode();
          }
        });
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onCombatDamage(EntityDamageByEntityEvent event) {
    Entity damaged = event.getEntity();
    Entity damager = event.getDamager();

    if (damaged.getType() == EntityType.END_CRYSTAL || damaged.getType() == EntityType.TNT || damaged.getType() == EntityType.TNT_MINECART) {
      markExplosiveTrigger(damaged, damager);
      return;
    }

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
      } else if (damager.getType() == EntityType.END_CRYSTAL || damager.getType() == EntityType.TNT || damager.getType() == EntityType.TNT_MINECART) {
        UUID detonatorUuid = explosiveDetonatorMap.get(damager.getUniqueId());
        if (detonatorUuid != null) {
          attacker = Bukkit.getPlayer(detonatorUuid);
          weapon = explosiveWeaponMap.get(damager.getUniqueId());
        } else if (lastExplosionTriggerUUID != null && (System.currentTimeMillis() - lastExplosionTriggerTime) <= 3500L) {
          attacker = Bukkit.getPlayer(lastExplosionTriggerUUID);
          weapon = lastExplosionTriggerWeapon;
        }
      }

      if (attacker != null) {
        lastDamagerMap.put(victim.getUniqueId(), new CombatRecord(attacker.getUniqueId(), weapon, System.currentTimeMillis()));
      }
    }
  }

  private void markExplosiveTrigger(Entity explosiveEntity, Entity damager) {
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
      explosiveDetonatorMap.put(explosiveEntity.getUniqueId(), triggerPlayer.getUniqueId());
      if (weaponUsed != null) explosiveWeaponMap.put(explosiveEntity.getUniqueId(), weaponUsed);
      lastExplosionTriggerUUID = triggerPlayer.getUniqueId();
      lastExplosionTriggerLoc = explosiveEntity.getLocation();
      lastExplosionTriggerWeapon = weaponUsed;
      lastExplosionTriggerTime = System.currentTimeMillis();

      Bukkit.getScheduler().runTaskLater(plugin, () -> {
        explosiveDetonatorMap.remove(explosiveEntity.getUniqueId());
        explosiveWeaponMap.remove(explosiveEntity.getUniqueId());
      }, 100L);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onExplosionDamage(EntityDamageEvent event) {
    if (!(event.getEntity() instanceof Player victim)) return;
    EntityDamageEvent.DamageCause cause = event.getCause();
    if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
      UUID triggerUuid = lastExplosionTriggerUUID;
      Location triggerLoc = lastExplosionTriggerLoc;
      ItemStack triggerWeapon = lastExplosionTriggerWeapon;
      long now = System.currentTimeMillis();
      if (triggerUuid != null
          && (now - lastExplosionTriggerTime) <= 3500L
          && triggerLoc != null && triggerLoc.getWorld() != null
          && triggerLoc.getWorld().equals(victim.getWorld())
          && triggerLoc.distanceSquared(victim.getLocation()) <= 576) {
        lastDamagerMap.put(victim.getUniqueId(), new CombatRecord(triggerUuid, triggerWeapon, now));
      }
    }
  }

  @Nullable
  public Player resolveKiller(PlayerDeathEvent event) {
    Player victim = event.getPlayer();
    Player killer = victim.getKiller();
    if (killer != null) return killer;

    try {
      Entity causing = event.getDamageSource().getCausingEntity();
      if (causing instanceof Player p) return p;
    } catch (Throwable ignored) {}

    CombatRecord record = lastDamagerMap.get(victim.getUniqueId());
    if (record != null && (System.currentTimeMillis() - record.timestamp()) <= 15000L) {
      Player tracked = Bukkit.getPlayer(record.attackerUuid());
      if (tracked != null && tracked.isOnline()) return tracked;
    }

    if (lastExplosionTriggerUUID != null && (System.currentTimeMillis() - lastExplosionTriggerTime) <= 3500L) {
      Location loc = lastExplosionTriggerLoc;
      if (loc != null && loc.getWorld() != null && loc.getWorld().equals(victim.getWorld())
          && loc.distanceSquared(victim.getLocation()) <= 576) {
        Player p = Bukkit.getPlayer(lastExplosionTriggerUUID);
        if (p != null && p.isOnline()) return p;
      }
    }
    return null;
  }

  @Nullable
  public ItemStack resolveKillerWeapon(Player victim, Player killer) {
    CombatRecord record = lastDamagerMap.get(victim.getUniqueId());
    if (record != null && record.weaponUsed() != null && !record.weaponUsed().getType().isAir()) {
      return record.weaponUsed().clone();
    }
    if (lastExplosionTriggerWeapon != null && !lastExplosionTriggerWeapon.getType().isAir()
        && (System.currentTimeMillis() - lastExplosionTriggerTime) <= 3500L) {
      return lastExplosionTriggerWeapon.clone();
    }
    if (killer != null && killer.isOnline()) {
      ItemStack hand = killer.getInventory().getItemInMainHand();
      if (hand != null && !hand.getType().isAir()) return hand.clone();
    }
    return null;
  }

  public void clearVictimRecord(UUID victimUuid) {
    lastDamagerMap.remove(victimUuid);
  }

  /**
   * Định dạng tin nhắn chết:
   * - Nạn nhân chết do người khác: "<Victim> was blown up by <Killer> using [Tên Cung]"
   * - Tự sát bằng vụ nổ của chính mình: "<Player> was blown up by [Tên Cung]"
   * - Hover hiển thị toàn bộ tooltip của vũ khí đã đổi tên.
   */
  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPlayerDeathMessage(PlayerDeathEvent event) {
    Player victim = event.getPlayer();
    Player killer = resolveKiller(event);
    if (killer == null) return;

    Component originalMsg = event.deathMessage();
    if (originalMsg == null) return;

    ItemStack weapon = resolveKillerWeapon(victim, killer);
    if (weapon == null || weapon.getType().isAir()) return;

    Component weaponName = null;
    if (weapon.hasItemMeta() && weapon.getItemMeta().hasDisplayName()) {
      weaponName = weapon.getItemMeta().displayName();
    }

    if (weaponName != null) {
      String plainMsg = PlainTextComponentSerializer.plainText().serialize(originalMsg);
      String plainName = PlainTextComponentSerializer.plainText().serialize(weaponName);
      if (plainMsg.contains(plainName)) return;

      Component weaponWithHover = Component.text("[")
          .append(weaponName)
          .append(Component.text("]"))
          .color(NamedTextColor.WHITE)
          .hoverEvent(weapon.asHoverEvent());

      Component finalMsg;
      boolean isSelf = killer.getUniqueId().equals(victim.getUniqueId());

      if (isSelf) {
        // Trường hợp tự sát bằng vụ nổ do mình bắn ra:
        // Đưa về định dạng chuẩn: "<Player> was blown up by [Tên Cung]"
        if (plainMsg.endsWith("blew up") || plainMsg.contains("blew up")) {
          finalMsg = Component.text(victim.getName() + " was blown up by ")
              .color(NamedTextColor.WHITE)
              .append(weaponWithHover);
        } else {
          finalMsg = originalMsg.append(Component.text(" using ").color(NamedTextColor.GRAY))
              .append(weaponWithHover);
        }
      } else {
        // Trường hợp PvP giết đối thủ:
        finalMsg = originalMsg.append(Component.text(" using ").color(NamedTextColor.GRAY))
            .append(weaponWithHover);
      }

      event.deathMessage(finalMsg);
    }
  }
}
