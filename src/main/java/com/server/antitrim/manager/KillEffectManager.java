package com.server.antitrim.manager;

import com.server.antitrim.AntiArmorTrim;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class KillEffectManager {

  private final AntiArmorTrim plugin;
  private final Set<Entity> activeKillEffectEntities = ConcurrentHashMap.newKeySet();
  private final NamespacedKey playerKillEffectKey;

  public KillEffectManager(AntiArmorTrim plugin) {
    this.plugin = plugin;
    this.playerKillEffectKey = new NamespacedKey(plugin, "selected_modelengine_killeffect");
  }

  public NamespacedKey getPlayerKillEffectKey() {
    return playerKillEffectKey;
  }

  public String getSavedKillEffectId(Player p) {
    return p.getPersistentDataContainer().get(playerKillEffectKey, PersistentDataType.STRING);
  }

  public void setSavedKillEffectId(Player p, String effectId) {
    PersistentDataContainer pdc = p.getPersistentDataContainer();
    if (effectId == null || effectId.isBlank()) pdc.remove(playerKillEffectKey);
    else pdc.set(playerKillEffectKey, PersistentDataType.STRING, effectId.trim());
  }

  public List<String> getGroupsContainingKillEffect(String effectId) {
    List<String> groups = new ArrayList<>();
    for (Map.Entry<String, Set<String>> entry : plugin.getDbKillEffectGroups().entrySet()) {
      if (entry.getValue().stream().anyMatch(s -> s.equalsIgnoreCase(effectId))) groups.add(entry.getKey());
    }
    return groups;
  }

  public String getKillEffectRequiredPermissionNode(String effectId) {
    List<String> groups = getGroupsContainingKillEffect(effectId);
    return !groups.isEmpty() ? groups.get(0) : AntiArmorTrim.KILLEFFECT_PERM;
  }

  public boolean canUseSpecificKillEffect(Player player, String effectId) {
    if (effectId == null || effectId.isBlank()) return false;
    if (player.isOp() || plugin.hasAdminPerm(player) || player.hasPermission("armortrim.killeffect.*")) return true;
    String cleanId = effectId.trim();
    if (player.hasPermission("armortrim.killeffect." + cleanId.toLowerCase())) return true;

    List<String> assignedGroups = getGroupsContainingKillEffect(cleanId);
    if (!assignedGroups.isEmpty()) {
      for (String permNode : assignedGroups) {
        if (player.hasPermission(permNode)) return true;
      }
      return false;
    }
    return player.hasPermission(AntiArmorTrim.KILLEFFECT_PERM);
  }

  public List<String> getUnlockedKillEffectIds(Player player) {
    List<String> unlocked = new ArrayList<>();
    for (String effectId : plugin.getDbKillEffectItems()) {
      if (canUseSpecificKillEffect(player, effectId)) unlocked.add(effectId);
    }
    return unlocked;
  }

  public boolean canUseKillEffect(Player p) {
    return p.isOp() || plugin.hasAdminPerm(p) || p.hasPermission(AntiArmorTrim.KILLEFFECT_PERM)
        || p.hasPermission("armortrim.killeffect.*") || !getUnlockedKillEffectIds(p).isEmpty();
  }

  @Nullable
  public String getEffectiveKillEffectId(Player player) {
    String saved = getSavedKillEffectId(player);
    if (saved != null && !saved.isBlank() && canUseSpecificKillEffect(player, saved)
        && plugin.getDbKillEffectItems().stream().anyMatch(s -> s.equalsIgnoreCase(saved))) {
      return saved;
    }
    String defEffect = plugin.getDbDefaultKillEffect();
    if (plugin.isDbForceDefaultKillEffect() && defEffect != null) {
      return defEffect;
    }
    return null;
  }

  public Component getKillEffectDisplayName(String effectId) {
    String customName = plugin.getDbKillEffectDisplayName(effectId);
    if (customName != null && !customName.isBlank()) return plugin.parse(customName);
    return plugin.parse("<#cba6f7>" + effectId);
  }

  public List<Component> getKillEffectCustomLore(String effectId) {
    List<Component> lore = new ArrayList<>();
    List<String> dbLore = plugin.getDbKillEffectLore(effectId);
    if (dbLore != null) {
      for (String line : dbLore) lore.add(plugin.parse(line));
    }
    return lore;
  }

  public Material getKillEffectIconMaterial(String effectId) {
    String lower = effectId.toLowerCase();
    if (lower.contains("acidic")) return Material.SLIME_BALL;
    if (lower.contains("clockwork")) return Material.CLOCK;
    if (lower.contains("frost")) return Material.BLUE_ICE;
    if (lower.contains("abstracted")) return Material.ENDER_EYE;
    if (lower.contains("graffiti")) return Material.GLOW_INK_SAC;
    if (lower.contains("origami")) return Material.PAPER;
    if (lower.contains("plasma")) return Material.HEART_OF_THE_SEA;
    if (lower.contains("magnetic")) return Material.LODESTONE;
    if (lower.contains("ink")) return Material.INK_SAC;
    if (lower.contains("pure")) return Material.NETHER_STAR;
    return Material.TOTEM_OF_UNDYING;
  }

  public void setForceDefaultKillEffect(Player admin, @Nullable String effectId, boolean enabled) {
    if (effectId != null && !effectId.isBlank()) {
      String cleanId = effectId.trim();
      plugin.addKillEffectToDb(cleanId);
      plugin.setDefaultKillEffectInDb(cleanId, enabled);
      admin.playSound(admin.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
      admin.sendMessage(plugin.getMessage("hat-admin-default-set", Placeholder.unparsed("id", cleanId)));
    } else {
      plugin.setDefaultKillEffectInDb("", false);
      admin.playSound(admin.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1.2f);
      admin.sendMessage(plugin.getMessage("hat-admin-default-cleared"));
    }
  }

  public void cleanupAllActiveKillEffectEntities() {
    for (Entity entity : activeKillEffectEntities) {
      try { if (entity != null && entity.isValid()) entity.remove(); } catch (Throwable ignored) {}
    }
    activeKillEffectEntities.clear();
  }

  public void playModelEngineKillEffect(Player killer, Player victim) {
    String effectId = getEffectiveKillEffectId(killer);
    if (effectId == null || effectId.isBlank()) return;
    Location spawnLoc = victim.getLocation().clone();
    Bukkit.getScheduler().runTaskLater(plugin, () -> spawnModelEngineEffectAt(spawnLoc, victim, killer, effectId.trim()), 1L);
  }

  public void playPreviewKillEffect(Player player, String effectId) {
    if (effectId == null || effectId.isBlank()) return;
    Location eye = player.getLocation().clone();
    Vector dir = eye.getDirection().setY(0);
    if (dir.lengthSquared() > 0.001) dir.normalize().multiply(2.5);
    Location previewLoc = eye.add(dir);
    previewLoc.setYaw(eye.getYaw() + 180f);
    previewLoc.setPitch(0f);
    spawnModelEngineEffectAt(previewLoc, player, player, effectId.trim());
  }

  private void spawnModelEngineEffectAt(Location loc, Player skinSource, Player killer, String rawEffectId) {
    if (loc.getWorld() == null) return;
    String modelId = rawEffectId.trim().toLowerCase();
    World world = loc.getWorld();

    Plugin mePlugin = Bukkit.getPluginManager().getPlugin("ModelEngine");
    if (mePlugin != null && mePlugin.isEnabled()) {
      try {
        ClassLoader meLoader = mePlugin.getClass().getClassLoader();
        Class<?> meApiClass = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI", true, meLoader);
        Object blueprint = meApiClass.getMethod("getBlueprint", String.class).invoke(null, modelId);
        if (blueprint != null) {
          ArmorStand stand = world.spawn(loc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setInvulnerable(true);
            as.setPersistent(false);
            as.setGravity(false);
            as.setSilent(true);
            as.setBasePlate(false);
            as.setArms(false);
            as.setCollidable(false);
          });
          activeKillEffectEntities.add(stand);

          Object modeledEntity = meApiClass.getMethod("createModeledEntity", Entity.class).invoke(null, stand);
          try {
            modeledEntity.getClass().getMethod("setBaseEntityVisible", boolean.class).invoke(modeledEntity, false);
          } catch (Throwable ignored) {}

          Method createActiveModelMethod = null;
          for (Method m : meApiClass.getMethods()) {
            if (m.getName().equals("createActiveModel") && m.getParameterCount() == 1) {
              createActiveModelMethod = m;
              break;
            }
          }
          Object activeModel = null;
          if (createActiveModelMethod != null) {
            if (createActiveModelMethod.getParameterTypes()[0].equals(String.class)) {
              activeModel = createActiveModelMethod.invoke(null, modelId);
            } else {
              activeModel = createActiveModelMethod.invoke(null, blueprint);
            }
          }

          if (activeModel != null) {
            Class<?> activeModelInterface = Class.forName("com.ticxo.modelengine.api.model.ActiveModel", true, meLoader);
            modeledEntity.getClass().getMethod("addModel", activeModelInterface, boolean.class).invoke(modeledEntity, activeModel, true);

            // KFX_v4-SkinInherit: Copy skin to PlayerLimb
            if (skinSource != null) {
              applyModelEngineSkin(activeModel, skinSource, meLoader);
            }
          }

          Bukkit.getScheduler().runTaskLater(plugin, () -> {
            activeKillEffectEntities.remove(stand);
            if (stand.isValid()) stand.remove();
          }, 51L);
        }
      } catch (Throwable ignored) {}
    }

    playEliteCreaturesEffectExtras(loc, killer, skinSource, modelId);
  }

  private void applyModelEngineSkin(Object activeModel, Player skinSource, ClassLoader meLoader) {
    try {
      Class<?> limbTypesClass = Class.forName("com.ticxo.modelengine.api.model.bone.BoneBehaviorTypes", true, meLoader);
      Object playerLimbType = limbTypesClass.getField("PLAYER_LIMB").get(null);
      Class<?> behaviorTypeClass = Class.forName("com.ticxo.modelengine.api.model.bone.behavior.BoneBehaviorType", true, meLoader);
      Map<?, ?> bones = (Map<?, ?>) activeModel.getClass().getMethod("getBones").invoke(activeModel);
      for (Object bone : bones.values()) {
        try {
          Object optLimb = bone.getClass().getMethod("getBoneBehavior", behaviorTypeClass).invoke(bone, playerLimbType);
          if (optLimb instanceof Optional<?> opt && opt.isPresent()) {
            Object limb = opt.get();
            limb.getClass().getMethod("setTexture", Player.class).invoke(limb, skinSource);
          }
        } catch (Throwable ignored) {}
      }
    } catch (Throwable ignored) {}
  }

  private void playEliteCreaturesEffectExtras(Location loc, Player killer, Player victim, String modelId) {
    World world = loc.getWorld();
    if (world == null) return;

    if (plugin.getConfig().getBoolean("killeffects.play-pack-sound", true)) {
      String suffix = modelId.startsWith("kfx_") ? modelId.substring(4) : modelId;
      String packSound = "ec_killeffects_sfx4:kill_fx." + suffix;
      float vol = (float) plugin.getConfig().getDouble("killeffects.volume", 3.0);
      float pitch = (float) plugin.getConfig().getDouble("killeffects.pitch", 1.0);
      try {
        if (killer != null && killer.isOnline()) {
          killer.stopSound(packSound, SoundCategory.MASTER);
          killer.playSound(killer, packSound, SoundCategory.MASTER, vol, pitch);
        }
        if (victim != null && victim.isOnline() && !victim.equals(killer)) {
          victim.stopSound(packSound, SoundCategory.MASTER);
          victim.playSound(victim, packSound, SoundCategory.MASTER, vol, pitch);
        }
        for (Player nearby : world.getPlayers()) {
          if (nearby.equals(killer) || nearby.equals(victim)) continue;
          if (nearby.getLocation().distanceSquared(loc) <= 256) {
            nearby.playSound(loc, packSound, SoundCategory.MASTER, vol, pitch);
          }
        }
      } catch (Throwable ignored) {}
    }

    Location center = loc.clone().add(0, 1.0, 0);
    if (modelId.contains("frost")) {
      world.spawnParticle(Particle.BLOCK, center, 40, 0.4, 0.6, 0.4, Material.BLUE_ICE.createBlockData());
    } else if (modelId.contains("abstracted")) {
      world.spawnParticle(Particle.ENCHANT, center, 35, 0.4, 0.8, 0.4, 1.5);
    } else if (modelId.contains("graffiti")) {
      world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, center, 50, 0.5, 1.0, 0.5, 0.05);
    } else if (modelId.contains("plasma")) {
      world.spawnParticle(Particle.END_ROD, center, 25, 0.5, 0.6, 0.5, 0.1);
      world.spawnParticle(Particle.ELECTRIC_SPARK, center, 25, 0.5, 0.6, 0.5, 0.1);
    } else if (modelId.contains("magnetic")) {
      world.spawnParticle(Particle.WHITE_ASH, center, 40, 0.5, 0.8, 0.5, 0.2);
    } else if (modelId.contains("ink")) {
      world.spawnParticle(Particle.BLOCK, center, 60, 0.4, 0.5, 0.4, Material.BLACK_CONCRETE.createBlockData());
    } else if (modelId.contains("pure")) {
      world.spawnParticle(Particle.END_ROD, center.clone().add(0, 1.2, 0), 25, 0.5, 0.6, 0.5, 0.15);
      world.spawnParticle(Particle.ELECTRIC_SPARK, center.clone().add(0, 1.2, 0), 25, 0.5, 0.6, 0.5, 0.15);
    }
  }
}
