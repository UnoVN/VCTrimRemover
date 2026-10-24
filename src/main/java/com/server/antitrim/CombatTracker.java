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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
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

  // 1. Lưu lại cây cung ngay khi bắn ra mũi tên (bất kể kéo cung 10% hay 100% lực)
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onShootBow(EntityShootBowEvent event) {
    if (event.getEntity() instanceof Player && event.getBow() != null) {
      projectileWeaponMap.put(event.getProjectile().getUniqueId(), event.getBow().clone());
      Bukkit.getScheduler().runTaskLater(plugin, () -> projectileWeaponMap.remove(event.getProjectile().getUniqueId()), 300L);
    }
  }

  // 2. Bắt cả sự kiện xe bị tác động (VehicleDamageEvent) dành riêng cho TNT Cart khi bị bắn nhẹ
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onVehicleDamage(VehicleDamageEvent event) {
    Entity vehicle = event.getVehicle();
    Entity attackerEntity = event.getAttacker();

    if (vehicle.getType() == EntityType.TNT_MINECART && attackerEntity != null) {
      markExplosiveTrigger(vehicle, attackerEntity);
    }
  }

  // 3. Bắt sự kiện gây sát thương lên End Crystal, TNT và TNT Cart
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onCombatDamage(EntityDamageByEntityEvent event) {
    Entity damaged = event.getEntity();
    Entity damager = event.getDamager();

    if (damaged.getType() == EntityType.END_CRYSTAL || damaged.getType() == EntityType.TNT || damaged.getType() == EntityType.TNT_MINECART) {
      markExplosiveTrigger(damaged, damager);
      return;
    }

    // Khi người chơi nhận sát thương từ thực thể khác (kể cả khi tự làm nổ chính mình)
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
        } else if (lastExplosionTriggerUUID != null && (System.currentTimeMillis() - lastExplosionTriggerTime) <= 3000L) {
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

      // Giữ bộ nhớ xe nổ trong 5 giây để đề phòng xe nổ trễ sau va chạm
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
      long now = System.currentTimeMillis();
      if (triggerUuid != null
          && (now - lastExplosionTriggerTime) <= 3000L
          && triggerLoc != null && triggerLoc.getWorld() != null
          && triggerLoc.getWorld().equals(victim.getWorld())
          && triggerLoc.distanceSquared(victim.getLocation()) <= 576) {
        lastDamagerMap.put(victim.getUniqueId(), new CombatRecord(triggerUuid, lastExplosionTriggerWeapon, now));
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
      if (tracked != null && tracked.isOnline()) return tracked;
    }
    return null;
  }

  @Nullable
  public ItemStack resolveKillerWeapon(Player victim, Player killer) {
    CombatRecord record = lastDamagerMap.get(victim.getUniqueId());
    if (record != null && record.weaponUsed() != null) {
      return record.weaponUsed();
    }
    if (lastExplosionTriggerWeapon != null && (System.currentTimeMillis() - lastExplosionTriggerTime) <= 3000L) {
      return lastExplosionTriggerWeapon;
    }
    ItemStack hand = killer.getInventory().getItemInMainHand();
    return (hand != null && !hand.getType().isAir()) ? hand : null;
  }

  public void clearVictimRecord(UUID victimUuid) {
    lastDamagerMap.remove(victimUuid);
  }

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
      String plainMsg = PlainTextComponentSerializer.plainText().serialize(originalMsg);
      String plainName = PlainTextComponentSerializer.plainText().serialize(weaponName);
      if (plainMsg.contains(plainName)) return;

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
