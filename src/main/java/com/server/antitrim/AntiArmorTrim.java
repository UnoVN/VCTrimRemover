package com.server.antitrim;

import com.server.antitrim.manager.HatManager;
import com.server.antitrim.manager.KillEffectManager;
import com.server.antitrim.manager.KillSoundManager;
import com.server.antitrim.manager.RenameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AntiArmorTrim extends JavaPlugin implements CommandExecutor, TabCompleter {

  private static AntiArmorTrim instance;
  private TrimListener trimListener;
  private CombatTracker combatTracker;
  private HatManager hatManager;
  private KillEffectManager killEffectManager;
  private KillSoundManager killSoundManager;
  private RenameManager renameManager;

  private final MiniMessage miniMessage = MiniMessage.miniMessage();

  public static final String BYPASS_PERM = "armortrim.bypass";
  public static final String HAT_PERM = "armortrim.hat";
  public static final String KILLEFFECT_PERM = "armortrim.killeffect";
  public static final String KILLSOUND_PERM = "armortrim.killsound";
  public static final String RENAME_PERM = "armortrim.rename";
  public static final String ADMIN_PERM = "armortrim.admin";

  private static final Pattern HEX_LEGACY_PATTERN = Pattern.compile("(?i)[&§]#([A-Fa-f0-9]{6})");
  private static final Pattern LEGACY_CODE_PATTERN = Pattern.compile("(?i)[&§]([0-9a-fk-or])");

  private Class<?> nexoItemsClass;
  private Method nexoItemFromIdMethod;
  private Method nexoItemNamesMethod;
  private Method nexoBuilderBuildMethod;

  private String jdbcUrl;
  private final List<String> dbHatItems = new ArrayList<>();
  private final Map<String, String> dbHatNames = new LinkedHashMap<>();
  private final Map<String, List<String>> dbHatLores = new LinkedHashMap<>();
  private final Map<String, Set<String>> dbPermGroups = new LinkedHashMap<>();
  private boolean dbForceDefaultHat = false;
  private String dbDefaultHat = "";

  private final List<String> dbKillEffectItems = new ArrayList<>();
  private final Map<String, String> dbKillEffectNames = new LinkedHashMap<>();
  private final Map<String, List<String>> dbKillEffectLores = new LinkedHashMap<>();
  private final Map<String, Set<String>> dbKillEffectGroups = new LinkedHashMap<>();
  private boolean dbForceDefaultKillEffect = false;
  private String dbDefaultKillEffect = "";

  private final List<String> dbKillSoundItems = new ArrayList<>();
  private final Map<String, String> dbKillSoundNames = new LinkedHashMap<>();
  private final Map<String, List<String>> dbKillSoundLores = new LinkedHashMap<>();
  private final Map<String, Set<String>> dbKillSoundGroups = new LinkedHashMap<>();
  private boolean dbForceDefaultKillSound = false;
  private String dbDefaultKillSound = "";

  @Override
  public void onEnable() {
    instance = this;
    saveDefaultConfig();
    initDatabase();

    this.hatManager = new HatManager(this);
    this.killEffectManager = new KillEffectManager(this);
    this.killSoundManager = new KillSoundManager(this);
    this.renameManager = new RenameManager(this);
    this.combatTracker = new CombatTracker(this);
    this.trimListener = new TrimListener(this);

    getServer().getPluginManager().registerEvents(trimListener, this);
    getServer().getPluginManager().registerEvents(combatTracker, this);
    getServer().getPluginManager().registerEvents(renameManager, this);

    registerCmd("antitrim");
    registerCmd("decor");
    registerCmd("trim");
    registerCmd("hat");
    registerCmd("killeffect");
    registerCmd("killsound");
    registerCmd("rename");

    Bukkit.getScheduler().runTaskLater(this, this::forceTakeoverCommands, 20L);
    getLogger().info("AntiArmorTrim v2.6.0 (Modular + KillEffects & Weapon Death Names) enabled!");
  }

  public void initDatabase() {
    try {
      if (!getDataFolder().exists()) getDataFolder().mkdirs();
      File dbFile = new File(getDataFolder(), "database.db");
      this.jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
      try (Connection conn = DriverManager.getConnection(jdbcUrl);
           Statement st = conn.createStatement()) {
        st.executeUpdate("CREATE TABLE IF NOT EXISTS hat_items (hat_id TEXT PRIMARY KEY, display_name TEXT, lore TEXT, sort_order INTEGER)");
        st.executeUpdate("CREATE TABLE IF NOT EXISTS hat_settings (setting_key TEXT PRIMARY KEY, setting_value TEXT)");
        st.executeUpdate("CREATE TABLE IF NOT EXISTS hat_groups (perm_node TEXT PRIMARY KEY, hat_ids TEXT)");
        st.executeUpdate("CREATE TABLE IF NOT EXISTS killeffect_items (effect_id TEXT PRIMARY KEY, display_name TEXT, lore TEXT, sort_order INTEGER)");
        st.executeUpdate("CREATE TABLE IF NOT EXISTS killeffect_groups (perm_node TEXT PRIMARY KEY, effect_ids TEXT)");
        st.executeUpdate("CREATE TABLE IF NOT EXISTS killsound_items (sound_id TEXT PRIMARY KEY, display_name TEXT, lore TEXT, sort_order INTEGER)");
        st.executeUpdate("CREATE TABLE IF NOT EXISTS killsound_groups (perm_node TEXT PRIMARY KEY, sound_ids TEXT)");
      }
      loadDatabase();

      if (dbHatItems.isEmpty()) {
        List<String> cfgItems = getConfig().getStringList("hats.items");
        for (String id : cfgItems) {
          String name = getConfig().getString("hats.definitions." + id + ".display-name");
          List<String> lore = getConfig().contains("hats.definitions." + id + ".lore")
              ? getConfig().getStringList("hats.definitions." + id + ".lore") : null;
          addHatToDb(id);
          if (name != null) setHatNameInDb(id, name);
          if (lore != null) setHatLoreInDb(id, lore);
        }
        String cfgDef = getConfig().getString("hats.default-hat", "");
        boolean cfgForce = getConfig().getBoolean("hats.force-default-hat", false);
        if (cfgForce || (cfgDef != null && !cfgDef.isBlank())) {
          setDefaultHatInDb(cfgDef, cfgForce);
        }
      }

      if (dbKillEffectItems.isEmpty()) {
        seedDefaultEliteCreaturesKillEffects();
      }
    } catch (Throwable e) {
      getLogger().warning("Failed to initialize SQLite database: " + e.getMessage());
    }
  }

  private void seedDefaultEliteCreaturesKillEffects() {
    String[][] defaults = {
        {"kfx_acidic_corrosion", "<#a6e3a1><bold>Acidic Corrosion</bold>", "<#a6adc8>Dissolves your victim in toxic acid."},
        {"kfx_clockwork_disassembly", "<#f9e2af><bold>Clockwork Disassembly</bold>", "<#a6adc8>Shatters your victim into golden gears."},
        {"kfx_frost_infection", "<#89dceb><bold>Frost Infection</bold>", "<#a6adc8>Freezes and shatters your victim in blue ice."},
        {"kfx_abstracted", "<#cba6f7><bold>Abstracted</bold>", "<#a6adc8>Warps your victim into a glitching dimension."},
        {"kfx_graffiti_spray", "<#f38ba8><bold>Graffiti Spray</bold>", "<#a6adc8>Blasts your victim into street art smoke."},
        {"kfx_origami_fold", "<#f5c2e7><bold>Origami Fold</bold>", "<#a6adc8>Folds your victim into paper origami."},
        {"kfx_plasma_orb", "<#74c7ec><bold>Plasma Orb</bold>", "<#a6adc8>Engulfs your victim in an unstable plasma sphere."},
        {"kfx_magnetic_resonance", "<#eba0ac><bold>Magnetic Resonance</bold>", "<#a6adc8>Tears your victim apart with magnetic waves."},
        {"kfx_ink_blots", "<#89b4fa><bold>Ink Blots</bold>", "<#a6adc8>Splashes your victim into dark ink puddles."},
        {"kfx_pure_form", "<#fab387><bold>Pure Form</bold>", "<#a6adc8>Ascends your victim into pure celestial energy."}
    };
    for (String[] entry : defaults) {
      addKillEffectToDb(entry[0]);
      setKillEffectNameInDb(entry[0], entry[1]);
      setKillEffectLoreInDb(entry[0], List.of(entry[2]));
    }
  }

  public void loadDatabase() {
    dbHatItems.clear();
    dbHatNames.clear();
    dbHatLores.clear();
    dbPermGroups.clear();
    dbKillEffectItems.clear();
    dbKillEffectNames.clear();
    dbKillEffectLores.clear();
    dbKillEffectGroups.clear();
    dbKillSoundItems.clear();
    dbKillSoundNames.clear();
    dbKillSoundLores.clear();
    dbKillSoundGroups.clear();
    try (Connection conn = DriverManager.getConnection(jdbcUrl);
         Statement st = conn.createStatement()) {
      try (ResultSet rs = st.executeQuery("SELECT hat_id, display_name, lore FROM hat_items ORDER BY sort_order ASC, rowid ASC")) {
        while (rs.next()) {
          String id = rs.getString("hat_id");
          String name = rs.getString("display_name");
          String loreRaw = rs.getString("lore");
          dbHatItems.add(id);
          if (name != null && !name.isBlank()) dbHatNames.put(id, name);
          if (loreRaw != null) {
            dbHatLores.put(id, loreRaw.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(loreRaw.split("\n", -1))));
          }
        }
      }
      try (ResultSet rs = st.executeQuery("SELECT perm_node, hat_ids FROM hat_groups ORDER BY rowid ASC")) {
        while (rs.next()) {
          String perm = rs.getString("perm_node");
          String rawIds = rs.getString("hat_ids");
          Set<String> set = new LinkedHashSet<>();
          if (rawIds != null && !rawIds.isBlank()) {
            for (String s : rawIds.split(",")) if (!s.isBlank()) set.add(s.trim());
          }
          if (perm != null && !perm.isBlank()) dbPermGroups.put(perm.trim().toLowerCase(), set);
        }
      }
      try (ResultSet rs = st.executeQuery("SELECT effect_id, display_name, lore FROM killeffect_items ORDER BY sort_order ASC, rowid ASC")) {
        while (rs.next()) {
          String id = rs.getString("effect_id");
          String name = rs.getString("display_name");
          String loreRaw = rs.getString("lore");
          dbKillEffectItems.add(id);
          if (name != null && !name.isBlank()) dbKillEffectNames.put(id, name);
          if (loreRaw != null) {
            dbKillEffectLores.put(id, loreRaw.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(loreRaw.split("\n", -1))));
          }
        }
      }
      try (ResultSet rs = st.executeQuery("SELECT perm_node, effect_ids FROM killeffect_groups ORDER BY rowid ASC")) {
        while (rs.next()) {
          String perm = rs.getString("perm_node");
          String rawIds = rs.getString("effect_ids");
          Set<String> set = new LinkedHashSet<>();
          if (rawIds != null && !rawIds.isBlank()) {
            for (String s : rawIds.split(",")) if (!s.isBlank()) set.add(s.trim());
          }
          if (perm != null && !perm.isBlank()) dbKillEffectGroups.put(perm.trim().toLowerCase(), set);
        }
      }
      try (ResultSet rs = st.executeQuery("SELECT sound_id, display_name, lore FROM killsound_items ORDER BY sort_order ASC, rowid ASC")) {
        while (rs.next()) {
          String id = rs.getString("sound_id");
          String name = rs.getString("display_name");
          String loreRaw = rs.getString("lore");
          dbKillSoundItems.add(id);
          if (name != null && !name.isBlank()) dbKillSoundNames.put(id, name);
          if (loreRaw != null) {
            dbKillSoundLores.put(id, loreRaw.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(loreRaw.split("\n", -1))));
          }
        }
      }
      try (ResultSet rs = st.executeQuery("SELECT perm_node, sound_ids FROM killsound_groups ORDER BY rowid ASC")) {
        while (rs.next()) {
          String perm = rs.getString("perm_node");
          String rawIds = rs.getString("sound_ids");
          Set<String> set = new LinkedHashSet<>();
          if (rawIds != null && !rawIds.isBlank()) {
            for (String s : rawIds.split(",")) if (!s.isBlank()) set.add(s.trim());
          }
          if (perm != null && !perm.isBlank()) dbKillSoundGroups.put(perm.trim().toLowerCase(), set);
        }
      }
      try (ResultSet rs = st.executeQuery("SELECT setting_key, setting_value FROM hat_settings")) {
        while (rs.next()) {
          String k = rs.getString("setting_key");
          String v = rs.getString("setting_value");
          if ("force_default_hat".equalsIgnoreCase(k)) dbForceDefaultHat = Boolean.parseBoolean(v);
          else if ("default_hat".equalsIgnoreCase(k)) dbDefaultHat = v != null ? v : "";
          else if ("force_default_killeffect".equalsIgnoreCase(k)) dbForceDefaultKillEffect = Boolean.parseBoolean(v);
          else if ("default_killeffect".equalsIgnoreCase(k)) dbDefaultKillEffect = v != null ? v : "";
          else if ("force_default_killsound".equalsIgnoreCase(k)) dbForceDefaultKillSound = Boolean.parseBoolean(v);
          else if ("default_killsound".equalsIgnoreCase(k)) dbDefaultKillSound = v != null ? v : "";
        }
      }
    } catch (Throwable ignored) {}
  }

  public List<String> getDbHatItems() { return Collections.unmodifiableList(dbHatItems); }
  public Map<String, Set<String>> getDbPermGroups() { return Collections.unmodifiableMap(dbPermGroups); }
  @Nullable public String getDbHatDisplayName(String hatId) { return dbHatNames.get(hatId); }
  @Nullable public List<String> getDbHatLore(String hatId) { return dbHatLores.get(hatId); }
  public boolean isDbForceDefaultHat() { return dbForceDefaultHat; }
  @Nullable public String getDbDefaultHat() { return (dbDefaultHat == null || dbDefaultHat.isBlank()) ? null : dbDefaultHat.trim(); }

  public List<String> getDbKillEffectItems() { return Collections.unmodifiableList(dbKillEffectItems); }
  public Map<String, Set<String>> getDbKillEffectGroups() { return Collections.unmodifiableMap(dbKillEffectGroups); }
  @Nullable public String getDbKillEffectDisplayName(String effectId) { return dbKillEffectNames.get(effectId); }
  @Nullable public List<String> getDbKillEffectLore(String effectId) { return dbKillEffectLores.get(effectId); }
  public boolean isDbForceDefaultKillEffect() { return dbForceDefaultKillEffect; }
  @Nullable public String getDbDefaultKillEffect() { return (dbDefaultKillEffect == null || dbDefaultKillEffect.isBlank()) ? null : dbDefaultKillEffect.trim(); }

  public List<String> getDbKillSoundItems() { return Collections.unmodifiableList(dbKillSoundItems); }
  public Map<String, Set<String>> getDbKillSoundGroups() { return Collections.unmodifiableMap(dbKillSoundGroups); }
  @Nullable public String getDbKillSoundDisplayName(String soundId) { return dbKillSoundNames.get(soundId); }
  @Nullable public List<String> getDbKillSoundLore(String soundId) { return dbKillSoundLores.get(soundId); }
  public boolean isDbForceDefaultKillSound() { return dbForceDefaultKillSound; }
  @Nullable public String getDbDefaultKillSound() { return (dbDefaultKillSound == null || dbDefaultKillSound.isBlank()) ? null : dbDefaultKillSound.trim(); }

  public String normalizePermNode(String rawInput) {
    String clean = rawInput.trim().toLowerCase().replace(" ", "_");
    return clean.contains(".") ? clean : "armortrim." + clean;
  }

  // --- HAT DB METHODS ---
  public void createDbPermGroup(String rawPerm) {
    String perm = normalizePermNode(rawPerm);
    if (!dbPermGroups.containsKey(perm)) {
      dbPermGroups.put(perm, new LinkedHashSet<>());
      saveDbPermGroup(perm);
    }
  }

  public void deleteDbPermGroup(String permNode) {
    String perm = permNode.trim().toLowerCase();
    dbPermGroups.remove(perm);
    try (Connection conn = DriverManager.getConnection(jdbcUrl);
         PreparedStatement ps = conn.prepareStatement("DELETE FROM hat_groups WHERE perm_node = ?")) {
      ps.setString(1, perm);
      ps.executeUpdate();
    } catch (Throwable ignored) {}
  }

  public boolean isHatInDbPermGroup(String permNode, String hatId) {
    Set<String> set = dbPermGroups.get(permNode.toLowerCase());
    return set != null && set.stream().anyMatch(h -> h.equalsIgnoreCase(hatId));
  }

  public boolean toggleHatInDbPermGroup(String permNode, String hatId) {
    String perm = permNode.trim().toLowerCase();
    Set<String> set = dbPermGroups.computeIfAbsent(perm, k -> new LinkedHashSet<>());
    String existing = set.stream().filter(h -> h.equalsIgnoreCase(hatId)).findFirst().orElse(null);
    boolean added;
    if (existing != null) { set.remove(existing); added = false; }
    else { set.add(hatId.trim()); added = true; }
    saveDbPermGroup(perm);
    return added;
  }

  private void saveDbPermGroup(String perm) {
    String joined = String.join(",", dbPermGroups.getOrDefault(perm, Collections.emptySet()));
    try (Connection conn = DriverManager.getConnection(jdbcUrl);
         PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO hat_groups (perm_node, hat_ids) VALUES (?, ?)")) {
      ps.setString(1, perm);
      ps.setString(2, joined);
      ps.executeUpdate();
    } catch (Throwable ignored) {}
  }

  public void addHatToDb(String hatId) {
    String clean = hatId.trim();
    if (dbHatItems.stream().noneMatch(h -> h.equalsIgnoreCase(clean))) {
      dbHatItems.add(clean);
      try (Connection conn = DriverManager.getConnection(jdbcUrl);
           PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO hat_items (hat_id, sort_order) VALUES (?, ?)")) {
        ps.setString(1, clean);
        ps.setInt(2, dbHatItems.size());
        ps.executeUpdate();
      } catch (Throwable ignored) {}
    }
  }

  public boolean removeHatFromDb(String hatId) {
    String matched = dbHatItems.stream().filter(h -> h.equalsIgnoreCase(hatId)).findFirst().orElse(null);
    if (matched == null) return false;
    dbHatItems.remove(matched);
    dbHatNames.remove(matched);
    dbHatLores.remove(matched);
    for (Map.Entry<String, Set<String>> entry : dbPermGroups.entrySet()) {
      if (entry.getValue().removeIf(h -> h.equalsIgnoreCase(matched))) saveDbPermGroup(entry.getKey());
    }
    try (Connection conn = DriverManager.getConnection(jdbcUrl);
         PreparedStatement ps = conn.prepareStatement("DELETE FROM hat_items WHERE hat_id = ?")) {
      ps.setString(1, matched);
      ps.executeUpdate();
    } catch (Throwable ignored) {}
    if (matched.equalsIgnoreCase(dbDefaultHat)) setDefaultHatInDb("", false);
    return true;
  }

  public void setHatNameInDb(String hatId, @Nullable String displayName) {
    if (displayName == null || displayName.isBlank()) dbHatNames.remove(hatId);
    else dbHatNames.put(hatId, displayName);
    try (Connection conn = DriverManager.getConnection(jdbcUrl);
         PreparedStatement ps = conn.prepareStatement("UPDATE hat_items SET display_name = ? WHERE hat_id = ?")) {
      ps.setString(1, displayName);
      ps.setString(2, hatId);
      ps.executeUpdate();
    } catch (Throwable ignored) {}
  }

  public void setHatLoreInDb(String hatId, @Nullable List<String> lore) {
    if (lore == null) dbHatLores.remove(hatId);
    else dbHatLores.put(hatId, new ArrayList<>(lore));
    String joined = lore == null ? null : String.join("\n", lore);
    try (Connection conn = DriverManager.getConnection(jdbcUrl);
         PreparedStatement ps = conn.prepareStatement("UPDATE hat_items SET lore = ? WHERE hat_id = ?")) {
      ps.setString(1, joined);
      ps.setString(2, hatId);
      ps.executeUpdate();
    } catch (Throwable ignored) {}
  }

  public void setDefaultHatInDb(@Nullable String defaultHatId, boolean force) {
    this.dbDefaultHat = defaultHatId == null ? "" : defaultHatId.trim();
    this.dbForceDefaultHat = force;
    try (Connection conn = DriverManager.getConnection(jdbcUrl);
         PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO hat_settings (setting_key, setting_value) VALUES (?, ?)")) {
      ps.setString(1, "default_hat"); ps.setString(2, this.dbDefaultHat); ps.executeUpdate();
      ps.setString(1, "force_default_hat"); ps.setString(2, String.valueOf(this.dbForceDefaultHat)); ps.executeUpdate();
    } catch (Throwable ignored) {}
  }

  // --- KILLEFFECT DB METHODS ---
  public void createDbKillEffectGroup(String rawPerm) {
    String perm = normalizePermNode(rawPerm);
    if (!dbKillEffectGroups.containsKey(perm)) {
      dbKillEffectGroups.put(perm, new LinkedHashSet<>());
      saveDbKillEffectGroup(perm);
    }
  }

  public void deleteDbKillEffectGroup(String permNode) {
    String perm = permNode.trim().toLowerCase();
    dbKillEffectGroups.remove(perm);
    try (Connection conn = DriverManager.getConnection(jdbcUrl);
         PreparedStatement ps = conn.prepareStatement("DELETE FROM killeffect_groups WHERE perm_node = ?")) {
      ps.setString(1, perm);
      ps.executeUpdate();
    } catch (Throwable ignored) {}
  }

  public boolean isKillEffectInDbGroup(String permNode, String effectId) {
    Set<String> set = dbKillEffectGroups.get(permNode.toLowerCase());
    return set != null && set.stream().anyMatch(s -> s.equalsIgnoreCase(effectId));
  }

  public boolean toggleKillEffectInDbGroup(String permNode, String effectId) {
    String perm = permNode.trim().toLowerCase();
    Set<String> set = dbKillEffectGroups.computeIfAbsent(perm, k -> new LinkedHashSet<>());
    String existing = set.stream().filter(s -> s.equalsIgnoreCase(effectId)).findFirst().orElse(null);
    boolean added;
    if (existing != null) { set.remove(existing); added = false; }
    else { set.add(effectId.trim()); added = true; }
    saveDbKillEffectGroup(perm);
    return added;
  }

  private void saveDbKillEffectGroup(String perm) {
    String joined = String.join(",", dbKillEffectGroups.getOrDefault(perm, Collections.emptySet()));
    try (Connection conn = DriverManager.getConnection(jdbcUrl);
         PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO killeffect_groups (perm_node, effect_ids) VALUES (?, ?)")) {
      ps.setString(1, perm);
      ps.setString(2, joined);
      ps.executeUpdate();
    } catch (Throwable ignored) {}
  }

  public void addKillEffectToDb(String effectId) {
    String clean = effectId.trim();
    if (dbKillEffectItems.stream().noneMatch(s -> s.equalsIgnoreCase(clean))) {
      dbKillEffectItems.add(clean);
      try (Connection conn = DriverManager.getConnection(jdbcUrl);
           PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO killeffect_items (effect_id, sort_order) VALUES (?, ?)")) {
        ps.setString(1, clean);
        ps.setInt(2, dbKillEffectItems.size());
        ps.executeUpdate();
      } catch (Throwable ignored) {}
    }
  }

  public boolean removeKillEffectFromDb(String effectId) {
    String matched = dbKillEffectItems.stream().filter(s -> s.equalsIgnoreCase(effectId)).findFirst().orElse(null);
    if (matched == null) return false;
    dbKillEffectItems.remove(matched);
    dbKillEffectNames.remove(matched);
    dbKillEffectLores.remove(matched);
    for (Map.Entry<String, Set<String>> entry : dbKillEffectGroups.entrySet()) {
      if (entry.getValue().removeIf(s -> s.equalsIgnoreCase(matched))) saveDbKillEffectGroup(entry.getKey());
    }
    try (Connection conn = DriverManager.getConnection(jdbcUrl);
         PreparedStatement ps = conn.prepareStatement("DELETE FROM killeffect_items WHERE effect_id = ?")) {
      ps.setString(1, matched);
      ps.executeUpdate();
    } catch (Throwable ignored) {}
    if (matched.equalsIgnoreCase(dbDefaultKillEffect)) setDefaultKillEffectInDb("", false);
    return true;
  }

  public void setKillEffectNameInDb(String effectId, @Nullable String displayName) {
    if (displayName == null || displayName.isBlank()) dbKillEffectNames.remove(effectId);
    else dbKillEffectNames.put(effectId, displayName);
    try (Connection conn = DriverManager.getConnection(jdbcUrl);
         PreparedStatement ps = conn.prepareStatement("UPDATE killeffect_items SET display_name = ? WHERE effect_id = ?")) {
      ps.setString(1, displayName);
      ps.setString(2, effectId);
      ps.executeUpdate();
    } catch (Throwable ignored) {}
  }

  public void setKillEffectLoreInDb(String effectId, @Nullable List<String> lore) {
    if (lore == null) dbKillEffectLores.remove(effectId);
    else dbKillEffectLores.put(effectId, new ArrayList<>(lore));
    String joined = lore == null ? null : String.join("\n", lore);
    try (Connection conn = DriverManager.getConnection(jdbcUrl);
         PreparedStatement ps = conn.prepareStatement("UPDATE killeffect_items SET lore = ? WHERE effect_id = ?")) {
      ps.setString(1, joined);
      ps.setString(2, effectId);
      ps.executeUpdate();
    } catch (Throwable ignored) {}
  }

  public void setDefaultKillEffectInDb(@Nullable String defaultEffectId, boolean force) {
    this.dbDefaultKillEffect = defaultEffectId == null ? "" : defaultEffectId.trim();
    this.dbForceDefaultKillEffect = force;
    try (Connection conn = DriverManager.getConnection(jdbcUrl);
         PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO hat_settings (setting_key, setting_value) VALUES (?, ?)")) {
      ps.setString(1, "default_killeffect"); ps.setString(2, this.dbDefaultKillEffect); ps.executeUpdate();
      ps.setString(1, "force_default_killeffect"); ps.setString(2, String.valueOf(this.dbForceDefaultKillEffect)); ps.executeUpdate();
    } catch (Throwable ignored) {}
  }

  public List<String> getAllModelEngineBlueprintIds() {
    Set<String> ids = new LinkedHashSet<>();
    File bpFolder = new File("plugins/ModelEngine/blueprints");
    if (bpFolder.exists()) scanBlueprintFiles(bpFolder, ids);
    ids.addAll(dbKillEffectItems);
    return new ArrayList<>(ids);
  }

  private void scanBlueprintFiles(File dir, Set<String> out) {
    File[] files = dir.listFiles();
    if (files == null) return;
    for (File f : files) {
      if (f.isDirectory()) {
        scanBlueprintFiles(f, out);
      } else if (f.getName().toLowerCase().endsWith(".bbmodel")) {
        String name = f.getName().substring(0, f.getName().length() - 8).trim().toLowerCase();
        if (!name.isEmpty()) out.add(name);
      }
    }
  }

  // --- KILLSOUND DB METHODS ---
  public void createDbKillSoundGroup(String rawPerm) {
    String perm = normalizePermNode(rawPerm);
    if (!dbKillSoundGroups.containsKey(perm)) {
      dbKillSoundGroups.put(perm, new LinkedHashSet<>());
      saveDbKillSoundGroup(perm);
    }
  }

  public void deleteDbKillSoundGroup(String permNode) {
    String perm = permNode.trim().toLowerCase();
    dbKillSoundGroups.remove(perm);
    try (Connection conn = DriverManager.getConnection(jdbcUrl);
         PreparedStatement ps = conn.prepareStatement("DELETE FROM killsound_groups WHERE perm_node = ?")) {
      ps.setString(1, perm);
      ps.executeUpdate();
    } catch (Throwable ignored) {}
  }

  public boolean isKillSoundInDbGroup(String permNode, String soundId) {
    Set<String> set = dbKillSoundGroups.get(permNode.toLowerCase());
    return set != null && set.stream().anyMatch(s -> s.equalsIgnoreCase(soundId));
  }

  public boolean toggleKillSoundInDbGroup(String permNode, String soundId) {
    String perm = permNode.trim().toLowerCase();
    Set<String> set = dbKillSoundGroups.computeIfAbsent(perm, k -> new LinkedHashSet<>());
    String existing = set.stream().filter(s -> s.equalsIgnoreCase(soundId)).findFirst().orElse(null);
    boolean added;
    if (existing != null) { set.remove(existing); added = false; }
    else { set.add(soundId.trim()); added = true; }
    saveDbKillSoundGroup(perm);
    return added;
  }

  private void saveDbKillSoundGroup(String perm) {
    String joined = String.join(",", dbKillSoundGroups.getOrDefault(perm, Collections.emptySet()));
    try (Connection conn = DriverManager.getConnection(jdbcUrl);
         PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO killsound_groups (perm_node, sound_ids) VALUES (?, ?)")) {
      ps.setString(1, perm);
      ps.setString(2, joined);
      ps.executeUpdate();
    } catch (Throwable ignored) {}
  }

  public void addKillSoundToDb(String soundId) {
    String clean = soundId.trim();
    if (dbKillSoundItems.stream().noneMatch(s -> s.equalsIgnoreCase(clean))) {
      dbKillSoundItems.add(clean);
      try (Connection conn = DriverManager.getConnection(jdbcUrl);
           PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO killsound_items (sound_id, sort_order) VALUES (?, ?)")) {
        ps.setString(1, clean);
        ps.setInt(2, dbKillSoundItems.size());
        ps.executeUpdate();
      } catch (Throwable ignored) {}
    }
  }

  public boolean removeKillSoundFromDb(String soundId) {
    String matched = dbKillSoundItems.stream().filter(s -> s.equalsIgnoreCase(soundId)).findFirst().orElse(null);
    if (matched == null) return false;
    dbKillSoundItems.remove(matched);
    dbKillSoundNames.remove(matched);
    dbKillSoundLores.remove(matched);
    for (Map.Entry<String, Set<String>> entry : dbKillSoundGroups.entrySet()) {
      if (entry.getValue().removeIf(s -> s.equalsIgnoreCase(matched))) saveDbKillSoundGroup(entry.getKey());
    }
    try (Connection conn = DriverManager.getConnection(jdbcUrl);
         PreparedStatement ps = conn.prepareStatement("DELETE FROM killsound_items WHERE sound_id = ?")) {
      ps.setString(1, matched);
      ps.executeUpdate();
    } catch (Throwable ignored) {}
    if (matched.equalsIgnoreCase(dbDefaultKillSound)) setDefaultKillSoundInDb("", false);
    return true;
  }

  public void setKillSoundNameInDb(String soundId, @Nullable String displayName) {
    if (displayName == null || displayName.isBlank()) dbKillSoundNames.remove(soundId);
    else dbKillSoundNames.put(soundId, displayName);
    try (Connection conn = DriverManager.getConnection(jdbcUrl);
         PreparedStatement ps = conn.prepareStatement("UPDATE killsound_items SET display_name = ? WHERE sound_id = ?")) {
      ps.setString(1, displayName);
      ps.setString(2, soundId);
      ps.executeUpdate();
    } catch (Throwable ignored) {}
  }

  public void setKillSoundLoreInDb(String soundId, @Nullable List<String> lore) {
    if (lore == null) dbKillSoundLores.remove(soundId);
    else dbKillSoundLores.put(soundId, new ArrayList<>(lore));
    String joined = lore == null ? null : String.join("\n", lore);
    try (Connection conn = DriverManager.getConnection(jdbcUrl);
         PreparedStatement ps = conn.prepareStatement("UPDATE killsound_items SET lore = ? WHERE sound_id = ?")) {
      ps.setString(1, joined);
      ps.setString(2, soundId);
      ps.executeUpdate();
    } catch (Throwable ignored) {}
  }

  public void setDefaultKillSoundInDb(@Nullable String defaultSoundId, boolean force) {
    this.dbDefaultKillSound = defaultSoundId == null ? "" : defaultSoundId.trim();
    this.dbForceDefaultKillSound = force;
    try (Connection conn = DriverManager.getConnection(jdbcUrl);
         PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO hat_settings (setting_key, setting_value) VALUES (?, ?)")) {
      ps.setString(1, "default_killsound"); ps.setString(2, this.dbDefaultKillSound); ps.executeUpdate();
      ps.setString(1, "force_default_killsound"); ps.setString(2, String.valueOf(this.dbForceDefaultKillSound)); ps.executeUpdate();
    } catch (Throwable ignored) {}
  }

  public List<String> getAllNexoSoundIds() {
    List<String> soundIds = new ArrayList<>();
    try {
      File soundsFile = new File("plugins/Nexo/sounds.yml");
      if (soundsFile.exists()) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(soundsFile);
        for (Map<?, ?> map : yml.getMapList("sounds")) {
          Object idObj = map.get("id");
          if (idObj != null) soundIds.add(idObj.toString().trim());
        }
      }
    } catch (Throwable ignored) {}
    return soundIds;
  }

  private void registerCmd(String name) {
    var cmd = getCommand(name);
    if (cmd != null) {
      cmd.setExecutor(this);
      cmd.setTabCompleter(this);
    }
  }

  public void forceTakeoverCommands() {
    try {
      Map<String, Command> knownCommands = Bukkit.getCommandMap().getKnownCommands();
      String[] cmds = {"antitrim", "decor", "trim", "hat", "killeffect", "killsound", "rename"};
      for (String name : cmds) {
        PluginCommand pc = getCommand(name);
        if (pc != null) {
          knownCommands.put(name, pc);
          for (String alias : pc.getAliases()) {
            knownCommands.put(alias.toLowerCase(), pc);
          }
        }
      }
      Method syncMethod = Bukkit.getServer().getClass().getMethod("syncCommands");
      syncMethod.setAccessible(true);
      syncMethod.invoke(Bukkit.getServer());
    } catch (Throwable ignored) {}
  }

  public boolean hasAdminPerm(CommandSender sender) {
    return sender.isOp() || sender.hasPermission(ADMIN_PERM) || sender.hasPermission("armortrim.*") || sender.hasPermission("*");
  }

  @Override
  public void onDisable() {
    if (killEffectManager != null) {
      killEffectManager.cleanupAllActiveKillEffectEntities();
    }
    if (hatManager != null) {
      for (Player player : Bukkit.getOnlinePlayers()) {
        hatManager.restoreAllCustomHatsInInventory(player);
      }
    }
    getLogger().info("AntiArmorTrim disabled.");
  }

  public static AntiArmorTrim getInstance() { return instance; }
  public CombatTracker getCombatTracker() { return combatTracker; }
  public HatManager getHatManager() { return hatManager; }
  public KillEffectManager getKillEffectManager() { return killEffectManager; }
  public KillSoundManager getKillSoundManager() { return killSoundManager; }
  public RenameManager getRenameManager() { return renameManager; }

  private boolean initNexoReflection() {
    Plugin nexo = Bukkit.getPluginManager().getPlugin("Nexo");
    if (nexo == null || !nexo.isEnabled()) return false;
    try {
      if (nexoItemsClass == null) {
        ClassLoader nexoLoader = nexo.getClass().getClassLoader();
        nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems", true, nexoLoader);
        nexoItemFromIdMethod = nexoItemsClass.getMethod("itemFromId", String.class);
        try {
          nexoItemNamesMethod = nexoItemsClass.getMethod("itemNames");
        } catch (Throwable ignored) {}
      }
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  @Nullable
  public ItemStack getNexoItem(String itemId) {
    if (itemId == null || itemId.isBlank() || !initNexoReflection()) return null;
    try {
      Object itemBuilder = nexoItemFromIdMethod.invoke(null, itemId.trim());
      if (itemBuilder == null) {
        itemBuilder = nexoItemFromIdMethod.invoke(null, itemId.trim().toLowerCase());
      }
      if (itemBuilder == null) return null;
      if (nexoBuilderBuildMethod == null) {
        nexoBuilderBuildMethod = itemBuilder.getClass().getMethod("build");
        nexoBuilderBuildMethod.setAccessible(true);
      }
      Object built = nexoBuilderBuildMethod.invoke(itemBuilder);
      if (built instanceof ItemStack stack) return stack.clone();
    } catch (Throwable ignored) {}
    return null;
  }

  public List<String> getAllNexoItemIds() {
    if (!initNexoReflection() || nexoItemNamesMethod == null) return Collections.emptyList();
    try {
      Object res = nexoItemNamesMethod.invoke(null);
      if (res instanceof Collection<?> col) {
        List<String> list = new ArrayList<>(col.size());
        for (Object o : col) if (o != null) list.add(o.toString());
        return list;
      }
    } catch (Throwable ignored) {}
    return Collections.emptyList();
  }

  public String getItemSpriteTag(Material material) {
    return "<white><sprite:items:item/" + material.getKey().getKey() + "></white>";
  }

  public Component parseLiteral(String input) {
    if (input == null || input.isEmpty()) return Component.empty();
    return miniMessage.deserialize(input).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
  }

  // Chuyển đổi tên vật phẩm: &c -> <red>, bỏ italic mặc định của Minecraft Custom Name
  public Component parseItemName(String input) {
    if (input == null || input.isEmpty()) return Component.empty();
    String converted = legacyToMiniMessage(input);
    return miniMessage.deserialize(converted).decoration(TextDecoration.ITALIC, false);
  }

  public Component parseMinecraftFormat(String input) {
    return parseItemName(input);
  }

  public Component parse(String input, TagResolver... resolvers) {
    return parseWithPerm(input, BYPASS_PERM, resolvers);
  }

  public Component parseWithPerm(String input, String permissionNode, TagResolver... resolvers) {
    if (input == null || input.isEmpty()) return Component.empty();
    TagResolver combined = TagResolver.builder()
        .resolver(Placeholder.parsed("rank", getRequiredRankRaw(permissionNode)))
        .resolvers(resolvers)
        .build();
    return miniMessage.deserialize(legacyToMiniMessage(input), combined).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
  }

  public Component getMessage(String key, TagResolver... resolvers) {
    return getMessageWithPerm(key, BYPASS_PERM, resolvers);
  }

  public Component getMessageWithPerm(String key, String permissionNode, TagResolver... resolvers) {
    String prefixRaw = getConfig().getString("messages.prefix", "");
    String msgRaw = getConfig().getString("messages." + key, "<#f38ba8>Missing message key: " + key);
    return parseWithPerm(prefixRaw + msgRaw, permissionNode, resolvers);
  }

  public Component getRawMessage(String key, TagResolver... resolvers) {
    return parse(getConfig().getString("messages." + key, "<#f38ba8>Action denied."), resolvers);
  }

  public String getRequiredRankRaw(String permissionNode) {
    String fallback = getConfig().getString("luckperms.fallback-rank", "<#f9e2af><bold>VIP</bold>");
    if (getConfig().getBoolean("luckperms.auto-detect-rank", true) && Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
      try {
        LuckPerms lp = LuckPermsProvider.get();
        List<Group> matchedGroups = new ArrayList<>();
        for (Group group : lp.getGroupManager().getLoadedGroups()) {
          for (Node node : group.getNodes()) {
            if ((node.getKey().equalsIgnoreCase(permissionNode) || node.getKey().equalsIgnoreCase(BYPASS_PERM)) && node.getValue()) {
              matchedGroups.add(group);
              break;
            }
          }
        }
        if (!matchedGroups.isEmpty()) {
          matchedGroups.sort(Comparator.comparingInt(g -> g.getWeight().orElse(999)));
          Group target = matchedGroups.get(0);
          String prefix = target.getCachedData().getMetaData().getPrefix();
          if (prefix != null && !prefix.isBlank()) return legacyToMiniMessage(prefix.trim());
          String displayName = target.getDisplayName();
          if (displayName != null && !displayName.isBlank()) return "<#f9e2af><bold>" + legacyToMiniMessage(displayName.trim()) + "</bold>";
          return "<#f9e2af><bold>" + target.getName().toUpperCase() + "</bold>";
        }
      } catch (Throwable ignored) {}
    }
    return legacyToMiniMessage(fallback);
  }

  public String legacyToMiniMessage(String text) {
    if (text == null || text.isEmpty()) return "";
    Matcher hexMatcher = HEX_LEGACY_PATTERN.matcher(text);
    StringBuilder hexSb = new StringBuilder();
    while (hexMatcher.find()) hexMatcher.appendReplacement(hexSb, "<#" + hexMatcher.group(1) + ">");
    hexMatcher.appendTail(hexSb);

    Matcher codeMatcher = LEGACY_CODE_PATTERN.matcher(hexSb.toString());
    StringBuilder finalSb = new StringBuilder();
    while (codeMatcher.find()) {
      char c = Character.toLowerCase(codeMatcher.group(1).charAt(0));
      String rep = switch (c) {
        case '0' -> "<black>"; case '1' -> "<dark_blue>"; case '2' -> "<dark_green>"; case '3' -> "<dark_aqua>";
        case '4' -> "<dark_red>"; case '5' -> "<dark_purple>"; case '6' -> "<gold>"; case '7' -> "<gray>";
        case '8' -> "<dark_gray>"; case '9' -> "<blue>"; case 'a' -> "<green>"; case 'b' -> "<aqua>";
        case 'c' -> "<red>"; case 'd' -> "<light_purple>"; case 'e' -> "<yellow>"; case 'f' -> "<white>";
        case 'k' -> "<obfuscated>"; case 'l' -> "<bold>"; case 'm' -> "<strikethrough>"; case 'n' -> "<underlined>";
        case 'o' -> "<italic>"; case 'r' -> "<reset>"; default -> "";
      };
      codeMatcher.appendReplacement(finalSb, rep);
    }
    codeMatcher.appendTail(finalSb);
    return finalSb.toString();
  }

  @Override
  public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
    String cmdName = command.getName().toLowerCase();
    if (cmdName.equals("decor")) {
      if (!(sender instanceof Player p)) { sender.sendMessage(getMessage("only-players")); return true; }
      com.server.antitrim.gui.DecorHubGui.open(this, p);
      return true;
    }
    if (cmdName.equals("trim")) {
      if (!(sender instanceof Player p)) { sender.sendMessage(getMessage("only-players")); return true; }
      com.server.antitrim.gui.TrimStudioGui.openMain(this, p);
      return true;
    }
    if (cmdName.equals("hat")) {
      if (!(sender instanceof Player p)) { sender.sendMessage(getMessage("only-players")); return true; }
      com.server.antitrim.gui.HatStudioGui.handleCommand(this, p, args);
      return true;
    }
    if (cmdName.equals("killeffect")) {
      if (!(sender instanceof Player p)) { sender.sendMessage(getMessage("only-players")); return true; }
      com.server.antitrim.gui.KillEffectStudioGui.handleCommand(this, p, args);
      return true;
    }
    if (cmdName.equals("killsound")) {
      if (!(sender instanceof Player p)) { sender.sendMessage(getMessage("only-players")); return true; }
      com.server.antitrim.gui.KillSoundStudioGui.handleCommand(this, p, args);
      return true;
    }
    if (cmdName.equals("rename")) {
      if (!(sender instanceof Player p)) { sender.sendMessage(getMessage("only-players")); return true; }
      if (args.length == 0) com.server.antitrim.gui.RenameStudioGui.open(this, p);
      else renameManager.applyItemRename(p, String.join(" ", args));
      return true;
    }
    if (args.length > 0) {
      if (args[0].equalsIgnoreCase("reload")) {
        if (!hasAdminPerm(sender)) { sender.sendMessage(getMessage("no-permission")); return true; }
        reloadConfig();
        loadDatabase();
        forceTakeoverCommands();
        if (trimListener != null) {
          for (World world : Bukkit.getWorlds()) trimListener.lockAlwaysDaytime(world);
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
          hatManager.forceRefreshEquippedHat(online);
          hatManager.applyAllSavedTrims(online);
        }
        sender.sendMessage(getMessage("reload-success"));
        return true;
      }
      if (args[0].equalsIgnoreCase("groups") && sender instanceof Player p) {
        if (!hasAdminPerm(p)) { p.sendMessage(getMessage("no-permission")); return true; }
        com.server.antitrim.gui.HatStudioGui.openGroupListMenu(this, p, 0);
        return true;
      }
      if ((args[0].equalsIgnoreCase("killeffects") || args[0].equalsIgnoreCase("ke") || args[0].equalsIgnoreCase("kfx")) && sender instanceof Player p) {
        if (!hasAdminPerm(p)) { p.sendMessage(getMessage("no-permission")); return true; }
        com.server.antitrim.gui.KillEffectStudioGui.openAdminMenu(this, p, 0);
        return true;
      }
      if ((args[0].equalsIgnoreCase("killsounds") || args[0].equalsIgnoreCase("ks")) && sender instanceof Player p) {
        if (!hasAdminPerm(p)) { p.sendMessage(getMessage("no-permission")); return true; }
        com.server.antitrim.gui.KillSoundStudioGui.openAdminMenu(this, p, 0);
        return true;
      }
      if ((args[0].equalsIgnoreCase("hats") || args[0].equalsIgnoreCase("hat") || args[0].equalsIgnoreCase("admin")) && sender instanceof Player p) {
        if (!hasAdminPerm(p)) { p.sendMessage(getMessage("no-permission")); return true; }
        com.server.antitrim.gui.HatStudioGui.openAdminMenu(this, p, 0);
        return true;
      }
    }
    sender.sendMessage(getMessage("usage", Placeholder.unparsed("label", label)));
    return true;
  }

  @Override
  public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
    String cmd = command.getName().toLowerCase();
    if (cmd.equals("antitrim") && hasAdminPerm(sender)) {
      if (args.length == 1) return List.of("reload", "hats", "killeffects", "killsounds", "groups");
    }
    if (cmd.equals("hat") && hasAdminPerm(sender)) {
      if (args.length == 1) return List.of("admin", "groups", "add", "edit", "remove", "default").stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
      if (args.length == 2) {
        String sub = args[0].toLowerCase();
        String prefix = args[1].toLowerCase();
        if (sub.equals("admin")) return List.of("groups", "add", "edit", "remove", "default").stream().filter(s -> s.startsWith(prefix)).toList();
        if (sub.equals("add") || sub.equals("default")) {
          List<String> opts = new ArrayList<>();
          if (sub.equals("default")) opts.addAll(List.of("off", "none"));
          opts.addAll(getAllNexoItemIds());
          return opts.stream().distinct().filter(id -> id.toLowerCase().startsWith(prefix)).limit(50).toList();
        }
        if (sub.equals("edit") || sub.equals("remove")) return getDbHatItems().stream().filter(id -> id.toLowerCase().startsWith(prefix)).toList();
      }
    }
    if (cmd.equals("killeffect") && hasAdminPerm(sender)) {
      if (args.length == 1) return List.of("admin", "groups", "add", "edit", "remove", "default").stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
      if (args.length == 2) {
        String sub = args[0].toLowerCase();
        String prefix = args[1].toLowerCase();
        if (sub.equals("admin")) return List.of("groups", "add", "edit", "remove", "default").stream().filter(s -> s.startsWith(prefix)).toList();
        if (sub.equals("add") || sub.equals("default")) {
          List<String> opts = new ArrayList<>();
          if (sub.equals("default")) opts.addAll(List.of("off", "none"));
          opts.addAll(getAllModelEngineBlueprintIds());
          return opts.stream().distinct().filter(id -> id.toLowerCase().startsWith(prefix)).limit(50).toList();
        }
        if (sub.equals("edit") || sub.equals("remove")) return getDbKillEffectItems().stream().filter(id -> id.toLowerCase().startsWith(prefix)).toList();
      }
    }
    if (cmd.equals("killsound") && hasAdminPerm(sender)) {
      if (args.length == 1) return List.of("admin", "groups", "add", "edit", "remove", "default").stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
      if (args.length == 2) {
        String sub = args[0].toLowerCase();
        String prefix = args[1].toLowerCase();
        if (sub.equals("admin")) return List.of("groups", "add", "edit", "remove", "default").stream().filter(s -> s.startsWith(prefix)).toList();
        if (sub.equals("add") || sub.equals("default")) {
          List<String> opts = new ArrayList<>();
          if (sub.equals("default")) opts.addAll(List.of("off", "none"));
          opts.addAll(getAllNexoSoundIds());
          opts.addAll(getDbKillSoundItems());
          return opts.stream().distinct().filter(id -> id.toLowerCase().startsWith(prefix)).limit(50).toList();
        }
        if (sub.equals("edit") || sub.equals("remove")) return getDbKillSoundItems().stream().filter(id -> id.toLowerCase().startsWith(prefix)).toList();
      }
    }
    return Collections.emptyList();
  }
}
