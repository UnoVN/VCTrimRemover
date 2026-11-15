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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KillEffectManager {

  private final AntiArmorTrim plugin;
  private final NamespacedKey playerKillEffectKey;

  // Track all running effects and per-player active previews
  private final Set<ActiveEffect> activeEffects = ConcurrentHashMap.newKeySet();
  private final Map<UUID, ActiveEffect> activePreviews = new ConcurrentHashMap<>();

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
    for (ActiveEffect effect : activeEffects) {
      try { effect.destroy(); } catch (Throwable ignored) {}
    }
    activeEffects.clear();
    activePreviews.clear();
  }

  public void cancelPlayerPreview(UUID playerUuid) {
    if (playerUuid == null) return;
    ActiveEffect prev = activePreviews.remove(playerUuid);
    if (prev != null) {
      activeEffects.remove(prev);
      prev.destroy();
    }
  }

  public void playModelEngineKillEffect(Player killer, Player victim) {
    String effectId = getEffectiveKillEffectId(killer);
    if (effectId == null || effectId.isBlank()) return;
    Location spawnLoc = victim.getLocation().clone();
    spawnLoc.setPitch(0f);
    if (killer != null && !killer.equals(victim)) {
      Vector toKiller = killer.getLocation().toVector().subtract(spawnLoc.toVector()).setY(0);
      if (toKiller.lengthSquared() > 0.01) {
        spawnLoc.setDirection(toKiller);
        spawnLoc.setPitch(0f);
      }
    }
    Bukkit.getScheduler().runTaskLater(plugin, () -> spawnModelEngineEffectAt(spawnLoc, victim, killer, effectId.trim(), null), 1L);
  }

  public void playPreviewKillEffect(Player player, String effectId) {
    if (effectId == null || effectId.isBlank() || player == null || !player.isOnline()) return;

    // Clean up any existing active preview for this player to prevent overlapping models
    cancelPlayerPreview(player.getUniqueId());

    Location eye = player.getLocation().clone();
    Vector dir = eye.getDirection().setY(0);
    if (dir.lengthSquared() > 0.001) dir.normalize().multiply(2.5);
    Location previewLoc = eye.add(dir);
    previewLoc.setYaw(eye.getYaw() + 180f);
    previewLoc.setPitch(0f);
    spawnModelEngineEffectAt(previewLoc, player, player, effectId.trim(), player.getUniqueId());
  }

  private void spawnModelEngineEffectAt(Location loc, Player skinSource, Player killer, String rawEffectId, @Nullable UUID previewPlayerUuid) {
    if (loc.getWorld() == null) return;
    String modelId = rawEffectId.trim().toLowerCase();
    World world = loc.getWorld();

    Location targetLoc = loc.clone();
    targetLoc.setPitch(0f);

    Plugin mePlugin = Bukkit.getPluginManager().getPlugin("ModelEngine");
    if (mePlugin != null && mePlugin.isEnabled()) {
      try {
        ClassLoader meLoader = mePlugin.getClass().getClassLoader();
        Class<?> meApiClass = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI", true, meLoader);
        Object blueprint = meApiClass.getMethod("getBlueprint", String.class).invoke(null, modelId);
        if (blueprint != null) {
          ArmorStand stand = world.spawn(targetLoc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setInvulnerable(true);
            as.setPersistent(false);
            as.setGravity(false);
            as.setSilent(true);
            as.setBasePlate(false);
            as.setArms(false);
            as.setCollidable(false);
            as.setMarker(true);
            as.setSmall(false);
            as.setCanTick(true);
          });

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

          long effectDurationTicks = 50L;

          if (activeModel != null) {
            Class<?> activeModelInterface = Class.forName("com.ticxo.modelengine.api.model.ActiveModel", true, meLoader);
            modeledEntity.getClass().getMethod("addModel", activeModelInterface, boolean.class).invoke(modeledEntity, activeModel, true);

            // 1. Configure bones: Lock HEAD rotation to local coordinate system & apply Player skin
            configureModelBones(activeModel, skinSource, meLoader);

            // 2. Play blueprint animation with LoopMode.HOLD and lerpOut=0.0 to prevent end-of-effect flicker
            long animTicks = playModelAnimation(activeModel, blueprint, meLoader);
            if (animTicks > 0) {
              effectDurationTicks = animTicks;
            }

            // 3. Trigger immediate player detection so the model & skin are rendered without latency
            try {
              modeledEntity.getClass().getMethod("detectPlayers").invoke(modeledEntity);
            } catch (Throwable ignored) {}
          }

          ActiveEffect activeEffect = new ActiveEffect(stand, modeledEntity, activeModel, modelId);
          activeEffects.add(activeEffect);
          if (previewPlayerUuid != null) {
            activePreviews.put(previewPlayerUuid, activeEffect);
          }

          Bukkit.getScheduler().runTaskLater(plugin, () -> {
            activeEffects.remove(activeEffect);
            if (previewPlayerUuid != null) {
              activePreviews.remove(previewPlayerUuid, activeEffect);
            }
            activeEffect.destroy();
          }, effectDurationTicks);
        }
      } catch (Throwable ignored) {}
    }

    playEliteCreaturesEffectExtras(targetLoc, killer, skinSource, modelId);
  }

  private void configureModelBones(Object activeModel, Player skinSource, ClassLoader meLoader) {
    if (activeModel == null) return;
    try {
      Class<?> limbTypesClass = Class.forName("com.ticxo.modelengine.api.model.bone.BoneBehaviorTypes", true, meLoader);
      Class<?> behaviorTypeClass = Class.forName("com.ticxo.modelengine.api.model.bone.behavior.BoneBehaviorType", true, meLoader);

      Object headType = null;
      try { headType = limbTypesClass.getField("HEAD").get(null); } catch (Throwable ignored) {}

      Object playerLimbType = null;
      try { playerLimbType = limbTypesClass.getField("PLAYER_LIMB").get(null); } catch (Throwable ignored) {}

      Object itemType = null;
      try { itemType = limbTypesClass.getField("ITEM").get(null); } catch (Throwable ignored) {}

      Map<?, ?> bones = (Map<?, ?>) activeModel.getClass().getMethod("getBones").invoke(activeModel);
      if (bones == null) return;

      for (Map.Entry<?, ?> entry : bones.entrySet()) {
        Object bone = entry.getValue();
        if (bone == null) continue;
        String boneName = String.valueOf(entry.getKey()).toLowerCase();

        // 1. Lock HEAD behavior to local so head tracking does not twist/offset the head bone
        if (headType != null) {
          try {
            Object optHead = bone.getClass().getMethod("getBoneBehavior", behaviorTypeClass).invoke(bone, headType);
            Object head = (optHead instanceof Optional<?> opt) ? (opt.isPresent() ? opt.get() : null) : optHead;
            if (head != null) {
              for (Method m : head.getClass().getMethods()) {
                if (m.getName().equals("setLocal") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == boolean.class) {
                  m.setAccessible(true);
                  m.invoke(head, true);
                  break;
                }
              }
            }
          } catch (Throwable ignored) {}
        }

        // 2. Apply skin texture to PLAYER_LIMB (Head, limbs, body)
        if (playerLimbType != null && skinSource != null) {
          try {
            Object optLimb = bone.getClass().getMethod("getBoneBehavior", behaviorTypeClass).invoke(bone, playerLimbType);
            Object limb = (optLimb instanceof Optional<?> opt) ? (opt.isPresent() ? opt.get() : null) : optLimb;
            if (limb != null) {
              applySkinToLimb(limb, skinSource);
            }
          } catch (Throwable ignored) {}
        }

        // 3. Fallback: If bone uses ITEM behavior and is a head/skull bone, attach custom player skull
        if (itemType != null && skinSource != null && (boneName.contains("head") || boneName.contains("skull"))) {
          try {
            Object optItem = bone.getClass().getMethod("getBoneBehavior", behaviorTypeClass).invoke(bone, itemType);
            Object itemBehavior = (optItem instanceof Optional<?> opt) ? (opt.isPresent() ? opt.get() : null) : optItem;
            if (itemBehavior != null) {
              applySkullItemToBone(itemBehavior, skinSource);
            }
          } catch (Throwable ignored) {}
        }
      }
    } catch (Throwable ignored) {}
  }

  private void applySkinToLimb(Object limb, Player skinSource) {
    if (limb == null || skinSource == null) return;
    for (Method m : limb.getClass().getMethods()) {
      if (m.getName().equals("setTexture") || m.getName().equals("setSkin")) {
        m.setAccessible(true);
        Class<?>[] params = m.getParameterTypes();
        if (params.length == 1) {
          try {
            if (params[0].isAssignableFrom(Player.class)) {
              m.invoke(limb, skinSource);
              return;
            } else if (params[0].isAssignableFrom(java.util.UUID.class)) {
              m.invoke(limb, skinSource.getUniqueId());
              return;
            } else if (params[0].isAssignableFrom(String.class)) {
              m.invoke(limb, skinSource.getName());
              return;
            } else if (params[0].isAssignableFrom(org.bukkit.OfflinePlayer.class)) {
              m.invoke(limb, skinSource);
              return;
            }
          } catch (Throwable ignored) {}
        }
      }
    }
  }

  private void applySkullItemToBone(Object itemBehavior, Player skinSource) {
    try {
      ItemStack head = new ItemStack(Material.PLAYER_HEAD);
      ItemMeta meta = head.getItemMeta();
      if (meta instanceof SkullMeta skullMeta) {
        skullMeta.setOwningPlayer(skinSource);
        head.setItemMeta(skullMeta);
      }
      for (Method m : itemBehavior.getClass().getMethods()) {
        if (m.getName().equals("setItemStack") || m.getName().equals("setItem")) {
          m.setAccessible(true);
          if (m.getParameterCount() == 1 && m.getParameterTypes()[0].isAssignableFrom(ItemStack.class)) {
            m.invoke(itemBehavior, head);
            return;
          }
        }
      }
    } catch (Throwable ignored) {}
  }

  private long playModelAnimation(Object activeModel, Object blueprint, ClassLoader meLoader) {
    if (activeModel == null || blueprint == null) return -1L;
    try {
      Object animationHandler = activeModel.getClass().getMethod("getAnimationHandler").invoke(activeModel);
      if (animationHandler == null) return -1L;

      Map<?, ?> anims = null;
      try {
        anims = (Map<?, ?>) blueprint.getClass().getMethod("getAnimations").invoke(blueprint);
      } catch (Throwable ignored) {}

      if (anims == null || anims.isEmpty()) return -1L;

      Method playAnimMethod5 = null;
      Method playAnimMethod2 = null;
      Method playAnimMethod1 = null;

      for (Method m : animationHandler.getClass().getMethods()) {
        if (m.getName().equals("playAnimation")) {
          m.setAccessible(true);
          if (m.getParameterCount() == 5) playAnimMethod5 = m;
          else if (m.getParameterCount() == 2) playAnimMethod2 = m;
          else if (m.getParameterCount() == 1) playAnimMethod1 = m;
        }
      }

      String chosenAnim = null;
      for (String prio : List.of("play", "activate", "trigger", "spawn", "death", "appear", "idle", "animation", "kfx")) {
        for (Object k : anims.keySet()) {
          if (String.valueOf(k).equalsIgnoreCase(prio)) {
            chosenAnim = String.valueOf(k);
            break;
          }
        }
        if (chosenAnim != null) break;
      }
      if (chosenAnim == null) {
        chosenAnim = String.valueOf(anims.keySet().iterator().next());
      }

      Object animObj = anims.get(chosenAnim);

      // Find ModelEngine's LoopMode enum and its HOLD constant to freeze on the final frame
      Object holdMode = null;
      if (animObj != null) {
        for (Method m : animObj.getClass().getMethods()) {
          if (m.getName().equals("getLoopMode") && m.getParameterCount() == 0 && m.getReturnType().isEnum()) {
            try {
              holdMode = Enum.valueOf((Class<Enum>) m.getReturnType(), "HOLD");
            } catch (Throwable ignored) {}
            break;
          }
        }
      }
      if (holdMode == null) {
        for (String candidate : List.of(
            "com.ticxo.modelengine.api.animation.blueprint.LoopMode",
            "com.ticxo.modelengine.api.animation.sfx.LoopMode",
            "com.ticxo.modelengine.api.animation.LoopMode"
        )) {
          try {
            Class<?> clazz = Class.forName(candidate, true, meLoader);
            if (clazz.isEnum()) {
              holdMode = Enum.valueOf((Class<Enum>) clazz, "HOLD");
              break;
            }
          } catch (Throwable ignored) {}
        }
      }

      // Configure BlueprintAnimation to HOLD on last frame
      if (animObj != null && holdMode != null) {
        for (Method m : animObj.getClass().getMethods()) {
          if (m.getName().equals("setLoopMode") && m.getParameterCount() == 1) {
            try {
              m.setAccessible(true);
              m.invoke(animObj, holdMode);
            } catch (Throwable ignored) {}
            break;
          }
        }
      }

      long calculatedTicks = -1L;
      if (animObj != null) {
        try {
          Method getLength = animObj.getClass().getMethod("getLength");
          getLength.setAccessible(true);
          Object len = getLength.invoke(animObj);
          if (len instanceof Number n) {
            float sec = n.floatValue();
            if (sec > 0.05f) {
              // Exact tick duration required to reach the final frame (no extra delay causing flicker)
              calculatedTicks = Math.max(5L, (long) Math.ceil(sec * 20.0f));
            }
          }
        } catch (Throwable ignored) {}
      }

      // Play animation with lerpIn=0.05, lerpOut=0.0 (NO lerp-out back to rest pose!), speed=1.0, force=true
      Object animProperty = null;
      try {
        if (playAnimMethod5 != null) {
          animProperty = playAnimMethod5.invoke(animationHandler, chosenAnim, 0.05, 0.0, 1.0, true);
        } else if (playAnimMethod2 != null) {
          animProperty = playAnimMethod2.invoke(animationHandler, chosenAnim, true);
        } else if (playAnimMethod1 != null) {
          animProperty = playAnimMethod1.invoke(animationHandler, chosenAnim);
        }
      } catch (Throwable ignored) {}

      // Configure runtime IAnimationProperty to HOLD & forceOverride
      if (animProperty != null) {
        for (Method m : animProperty.getClass().getMethods()) {
          m.setAccessible(true);
          if (holdMode != null && (m.getName().equals("setForceLoopMode") || m.getName().equals("setLoopMode")) && m.getParameterCount() == 1) {
            try {
              m.invoke(animProperty, holdMode);
            } catch (Throwable ignored) {}
          } else if (m.getName().equals("setForceOverride") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == boolean.class) {
            try {
              m.invoke(animProperty, true);
            } catch (Throwable ignored) {}
          }
        }
      }

      return calculatedTicks;
    } catch (Throwable ignored) {}
    return -1L;
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

  /**
   * Helper class tracking active ModelEngine entity instances for safe and flicker-free destruction.
   */
  private static class ActiveEffect {
    final ArmorStand stand;
    final Object modeledEntity;
    final Object activeModel;
    final String modelId;

    ActiveEffect(ArmorStand stand, Object modeledEntity, Object activeModel, String modelId) {
      this.stand = stand;
      this.modeledEntity = modeledEntity;
      this.activeModel = activeModel;
      this.modelId = modelId;
    }

    void destroy() {
      try {
        if (activeModel != null) {
          try {
            activeModel.getClass().getMethod("destroy").invoke(activeModel);
          } catch (Throwable ignored) {}
        }
        if (modeledEntity != null) {
          try {
            modeledEntity.getClass().getMethod("removeModel", String.class).invoke(modeledEntity, modelId);
          } catch (Throwable ignored) {}
          try {
            modeledEntity.getClass().getMethod("destroy").invoke(modeledEntity);
          } catch (Throwable ignored) {}
        }
        Plugin mePlugin = Bukkit.getPluginManager().getPlugin("ModelEngine");
        if (mePlugin != null && mePlugin.isEnabled()) {
          try {
            Class<?> meApiClass = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI", true, mePlugin.getClass().getClassLoader());
            for (Method m : meApiClass.getMethods()) {
              if (m.getName().equals("removeModeledEntity") && m.getParameterCount() == 1) {
                if (m.getParameterTypes()[0].equals(java.util.UUID.class)) {
                  m.invoke(null, stand.getUniqueId());
                  break;
                } else if (m.getParameterTypes()[0].equals(Entity.class)) {
                  m.invoke(null, stand);
                  break;
                }
              }
            }
          } catch (Throwable ignored) {}
        }
      } catch (Throwable ignored) {}
      try {
        if (stand != null && stand.isValid()) {
          stand.remove();
        }
      } catch (Throwable ignored) {}
    }
  }
}
