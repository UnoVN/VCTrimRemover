package com.server.antitrim.manager;

import com.server.antitrim.AntiArmorTrim;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KillSoundManager {

  private final AntiArmorTrim plugin;
  private final NamespacedKey playerKillSoundKey;

  public KillSoundManager(AntiArmorTrim plugin) {
    this.plugin = plugin;
    this.playerKillSoundKey = new NamespacedKey(plugin, "selected_nexo_killsound");
  }

  public NamespacedKey getPlayerKillSoundKey() {
    return playerKillSoundKey;
  }

  public String getSavedKillSoundId(Player p) {
    return p.getPersistentDataContainer().get(playerKillSoundKey, PersistentDataType.STRING);
  }

  public void setSavedKillSoundId(Player p, String soundId) {
    PersistentDataContainer pdc = p.getPersistentDataContainer();
    if (soundId == null || soundId.isBlank()) pdc.remove(playerKillSoundKey);
    else pdc.set(playerKillSoundKey, PersistentDataType.STRING, soundId.trim());
  }

  public List<String> getGroupsContainingKillSound(String soundId) {
    List<String> groups = new ArrayList<>();
    for (Map.Entry<String, Set<String>> entry : plugin.getDbKillSoundGroups().entrySet()) {
      if (entry.getValue().stream().anyMatch(s -> s.equalsIgnoreCase(soundId))) groups.add(entry.getKey());
    }
    return groups;
  }

  public String getKillSoundRequiredPermissionNode(String soundId) {
    List<String> groups = getGroupsContainingKillSound(soundId);
    return !groups.isEmpty() ? groups.get(0) : AntiArmorTrim.KILLSOUND_PERM;
  }

  public boolean canUseSpecificKillSound(Player player, String soundId) {
    if (soundId == null || soundId.isBlank()) return false;
    if (player.isOp() || plugin.hasAdminPerm(player) || player.hasPermission("armortrim.killsound.*")) return true;
    String cleanId = soundId.trim();
    if (player.hasPermission("armortrim.killsound." + cleanId.toLowerCase())) return true;

    List<String> assignedGroups = getGroupsContainingKillSound(cleanId);
    if (!assignedGroups.isEmpty()) {
      for (String permNode : assignedGroups) {
        if (player.hasPermission(permNode)) return true;
      }
      return false;
    }
    return player.hasPermission(AntiArmorTrim.KILLSOUND_PERM);
  }

  public List<String> getUnlockedKillSoundIds(Player player) {
    List<String> unlocked = new ArrayList<>();
    for (String soundId : plugin.getDbKillSoundItems()) {
      if (canUseSpecificKillSound(player, soundId)) unlocked.add(soundId);
    }
    return unlocked;
  }

  public boolean canUseKillSound(Player p) {
    return p.isOp() || plugin.hasAdminPerm(p) || p.hasPermission(AntiArmorTrim.KILLSOUND_PERM)
        || p.hasPermission("armortrim.killsound.*") || !getUnlockedKillSoundIds(p).isEmpty();
  }

  @Nullable
  public String getEffectiveKillSoundId(Player player) {
    String saved = getSavedKillSoundId(player);
    if (saved != null && !saved.isBlank() && canUseSpecificKillSound(player, saved)
        && plugin.getDbKillSoundItems().stream().anyMatch(s -> s.equalsIgnoreCase(saved))) {
      return saved;
    }
    String defSound = plugin.getDbDefaultKillSound();
    if (plugin.isDbForceDefaultKillSound() && defSound != null) {
      return defSound;
    }
    return null;
  }

  public Component getKillSoundDisplayName(String soundId) {
    String customName = plugin.getDbKillSoundDisplayName(soundId);
    if (customName != null && !customName.isBlank()) return plugin.parse(customName);
    return plugin.parse("<#cba6f7>" + soundId);
  }

  public List<Component> getKillSoundCustomLore(String soundId) {
    List<Component> lore = new ArrayList<>();
    List<String> dbLore = plugin.getDbKillSoundLore(soundId);
    if (dbLore != null) {
      for (String line : dbLore) lore.add(plugin.parse(line));
    }
    return lore;
  }

  public void setForceDefaultKillSound(Player admin, @Nullable String soundId, boolean enabled) {
    if (soundId != null && !soundId.isBlank()) {
      String cleanId = soundId.trim();
      plugin.addKillSoundToDb(cleanId);
      plugin.setDefaultKillSoundInDb(cleanId, enabled);
      admin.playSound(admin.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
      admin.sendMessage(plugin.getMessage("hat-admin-default-set", Placeholder.unparsed("id", cleanId)));
    } else {
      plugin.setDefaultKillSoundInDb("", false);
      admin.playSound(admin.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1.2f);
      admin.sendMessage(plugin.getMessage("hat-admin-default-cleared"));
    }
  }

  public void playNexoKillSound(Player killer, Player victim) {
    String soundId = getEffectiveKillSoundId(killer);
    if (soundId == null || soundId.isBlank()) return;
    String cleanSoundId = soundId.trim().toLowerCase();
    float vol = (float) plugin.getConfig().getDouble("killsounds.volume", 1.0);
    float pitch = (float) plugin.getConfig().getDouble("killsounds.pitch", 1.0);
    Location deathLoc = victim.getLocation().clone();

    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      try {
        if (killer.isOnline()) {
          killer.stopSound(cleanSoundId, SoundCategory.MASTER);
          killer.stopSound(cleanSoundId, SoundCategory.PLAYERS);
          killer.playSound(killer, cleanSoundId, SoundCategory.MASTER, vol, pitch);
        }
        if (victim.isOnline() && !victim.equals(killer)) {
          victim.stopSound(cleanSoundId, SoundCategory.MASTER);
          victim.stopSound(cleanSoundId, SoundCategory.PLAYERS);
          victim.playSound(victim, cleanSoundId, SoundCategory.MASTER, vol, pitch);
        }
        if (deathLoc.getWorld() != null) {
          for (Player nearby : deathLoc.getWorld().getPlayers()) {
            if (nearby.equals(killer) || nearby.equals(victim)) continue;
            if (nearby.getLocation().distanceSquared(deathLoc) <= 256) {
              nearby.stopSound(cleanSoundId, SoundCategory.MASTER);
              nearby.playSound(deathLoc, cleanSoundId, SoundCategory.MASTER, vol, pitch);
            }
          }
        }
      } catch (Throwable ignored) {}
    }, 1L);
  }

  public void playPreviewKillSound(Player player, String soundId) {
    if (soundId == null || soundId.isBlank()) return;
    String cleanSoundId = soundId.trim().toLowerCase();
    float vol = (float) plugin.getConfig().getDouble("killsounds.volume", 1.0);
    float pitch = (float) plugin.getConfig().getDouble("killsounds.pitch", 1.0);
    try {
      player.stopSound(cleanSoundId, SoundCategory.MASTER);
      player.stopSound(cleanSoundId, SoundCategory.PLAYERS);
      player.playSound(player, cleanSoundId, SoundCategory.MASTER, vol, pitch);
    } catch (Throwable ignored) {}
  }
}
