package com.server.antitrim;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
  private TrimGuiManager guiManager;
  private TrimListener trimListener;
  private final MiniMessage miniMessage = MiniMessage.miniMessage();
  private final LegacyComponentSerializer minecraftLegacySerializer = LegacyComponentSerializer.builder().character('&').build();

  public static final String BYPASS_PERM = "armortrim.bypass";
  public static final String HAT_PERM = "armortrim.hat";
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

  @Override
  public void onEnable() {
    instance = this;
    saveDefaultConfig();
    initHatDatabase();
    this.guiManager = new TrimGuiManager(this);
    this.trimListener = new TrimListener(this, guiManager);
    getServer().getPluginManager().registerEvents(trimListener, this);
    getServer().getPluginManager().registerEvents(guiManager, this);
    registerCmd("antitrim");
    registerCmd("decor");
    registerCmd("trim");
    registerCmd("hat");
    registerCmd("rename");
    Bukkit.getScheduler().runTaskLater(this, this::forceTakeoverCommands, 20L);
    getLogger().info("AntiArmorTrim v2.5.0 (Catppuccin + SQLite Nexo Hat Replacer) enabled!");
  }

  public void initHatDatabase() {
    try {
      if (!getDataFolder().exists()) getDataFolder().mkdirs();
      File dbFile = new File(getDataFolder(), "database.db");
      this.jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
      try (Connection conn = DriverManager.getConnection(jdbcUrl);
           Statement st = conn.createStatement()) {
        st.executeUpdate("CREATE TABLE IF NOT EXISTS hat_items (hat_id TEXT PRIMARY KEY, display_name TEXT, lore TEXT, sort_order INTEGER)");
        st.executeUpdate("CREATE TABLE IF NOT EXISTS hat_settings (setting_key TEXT PRIMARY KEY, setting_value TEXT)");
        st.executeUpdate("CREATE TABLE IF NOT EXISTS hat_groups (perm_node TEXT PRIMARY KEY, hat_ids TEXT)");
      }
      loadHatsFromDatabase();

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
    } catch (Throwable e) {
      getLogger().warning("Failed to initialize SQLite hat database: " + e.getMessage());
    }
  }

  public void loadHatsFromDatabase() {
    dbHatItems.clear();
    dbHatNames.clear();
    dbHatLores.clear();
    dbPermGroups.clear();
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
            for (String s : rawIds.split(",")) {
              if (!s.isBlank()) set.add(s.trim());
            }
          }
          if (perm != null && !perm.isBlank()) dbPermGroups.put(perm.trim().toLowerCase(), set);
        }
      }
      try (ResultSet rs = st.executeQuery("SELECT setting_key, setting_value FROM hat_settings")) {
        while (rs.next()) {
          String k = rs.getString("setting_key");
          String v = rs.getString("setting_value");
          if ("force_default_hat".equalsIgnoreCase(k)) dbForceDefaultHat = Boolean.parseBoolean(v);
          else if ("default_hat".equalsIgnoreCase(k)) dbDefaultHat = v != null ? v : "";
        }
      }
    } catch (Throwable ignored) {}
  }

  public List<String> getDbHatItems() {
    return Collections.unmodifiableList(dbHatItems);
  }

  public Map<String, Set<String>> getDbPermGroups() {
    return Collections.unmodifiableMap(dbPermGroups);
  }

  public String normalizePermNode(String rawInput) {
    String clean = rawInput.trim().toLowerCase().replace(" ", "_");
    return clean.contains(".") ? clean : "armortrim." + clean;
  }

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
    if (set == null) return false;
    return set.stream().anyMatch(h -> h.equalsIgnoreCase(hatId));
  }

  public boolean toggleHatInDbPermGroup(String permNode, String hatId) {
    String perm = permNode.trim().toLowerCase();
    Set<String> set = dbPermGroups.computeIfAbsent(perm, k -> new LinkedHashSet<>());
    String existing = set.stream().filter(h -> h.equalsIgnoreCase(hatId)).findFirst().orElse(null);
    boolean added;
    if (existing != null) {
      set.remove(existing);
      added = false;
    } else {
      set.add(hatId.trim());
      added = true;
    }
    saveDbPermGroup(perm);
    return added;
  }

  private void saveDbPermGroup(String perm) {
    Set<String> set = dbPermGroups.getOrDefault(perm, Collections.emptySet());
    String joined = String.join(",", set);
    try (Connection conn = DriverManager.getConnection(jdbcUrl);
         PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO hat_groups (perm_node, hat_ids) VALUES (?, ?)")) {
      ps.setString(1, perm);
      ps.setString(2, joined);
      ps.executeUpdate();
    } catch (Throwable ignored) {}
  }

  @Nullable
  public String getDbHatDisplayName(String hatId) {
    return dbHatNames.get(hatId);
  }

  @Nullable
  public List<String> getDbHatLore(String hatId) {
    return dbHatLores.get(hatId);
  }

  public boolean isDbForceDefaultHat() {
    return dbForceDefaultHat;
  }

  @Nullable
  public String getDbDefaultHat() {
    return (dbDefaultHat == null || dbDefaultHat.isBlank()) ? null : dbDefaultHat.trim();
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
      if (entry.getValue().removeIf(h -> h.equalsIgnoreCase(matched))) {
        saveDbPermGroup(entry.getKey());
      }
    }
    try (Connection conn = DriverManager.getConnection(jdbcUrl);
         PreparedStatement ps = conn.prepareStatement("DELETE FROM hat_items WHERE hat_id = ?")) {
      ps.setString(1, matched);
      ps.executeUpdate();
    } catch (Throwable ignored) {}
    if (matched.equalsIgnoreCase(dbDefaultHat)) {
      setDefaultHatInDb("", false);
    }
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
      ps.setString(1, "default_hat");
      ps.setString(2, this.dbDefaultHat);
      ps.executeUpdate();
      ps.setString(1, "force_default_hat");
      ps.setString(2, String.valueOf(this.dbForceDefaultHat));
      ps.executeUpdate();
    } catch (Throwable ignored) {}
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
      String[] cmds = {"antitrim", "decor", "trim", "hat", "rename"};
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
    if (guiManager != null) {
      for (Player player : Bukkit.getOnlinePlayers()) {
        guiManager.restoreAllCustomHatsInInventory(player);
      }
    }
    getLogger().info("AntiArmorTrim disabled.");
  }

  public static AntiArmorTrim getInstance() { return instance; }
  public TrimGuiManager getGuiManager() { return guiManager; }

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

  public Component parseMinecraftFormat(String input) {
    if (input == null || input.isEmpty()) return Component.empty();
    return minecraftLegacySerializer.deserialize(input.replace('§', '&')).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
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

  public void handleHatCommand(Player p, String[] args) {
    if (args.length == 0) {
      guiManager.openHatMenu(p, 0);
      return;
    }
    String sub = args[0].toLowerCase();
    if (sub.equals("admin") || sub.equals("add") || sub.equals("edit") || sub.equals("remove")
        || sub.equals("delete") || sub.equals("default") || sub.equals("groups") || sub.equals("group")) {
      if (!hasAdminPerm(p)) {
        p.sendMessage(getMessage("no-permission"));
        return;
      }
      if (sub.equals("admin")) {
        if (args.length == 1) {
          guiManager.openHatAdminMenu(p, 0);
          return;
        }
        sub = args[1].toLowerCase();
        args = Arrays.copyOfRange(args, 1, args.length);
      }
      if (sub.equals("groups") || sub.equals("group")) {
        guiManager.openHatGroupListMenu(p, 0);
        return;
      }
      if (sub.equals("default") && args.length >= 2) {
        String rawId = args[1].trim();
        if (rawId.equalsIgnoreCase("off") || rawId.equalsIgnoreCase("none") || rawId.equalsIgnoreCase("clear") || rawId.equalsIgnoreCase("disable")) {
          guiManager.setForceDefaultHat(p, null, false);
        } else {
          guiManager.setForceDefaultHat(p, rawId, true);
        }
        return;
      }
      if (sub.equals("add") && args.length >= 2) {
        String rawId = args[1].trim();
        if (getDbHatItems().stream().anyMatch(h -> h.equalsIgnoreCase(rawId))) {
          p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
          p.sendMessage(getMessage("hat-admin-exists", Placeholder.unparsed("id", rawId)));
          guiManager.openHatEditorMenu(p, rawId);
          return;
        }
        addHatToDb(rawId);
        if (getNexoItem(rawId) != null) {
          p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
          p.sendMessage(getMessage("hat-admin-added", Placeholder.unparsed("id", rawId)));
          guiManager.openHatEditorMenu(p, rawId);
        } else {
          p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
          p.sendMessage(getMessage("hat-admin-added-invalid", Placeholder.unparsed("id", rawId)));
          guiManager.openHatAdminMenu(p, 0);
        }
        return;
      }
      if (sub.equals("edit") && args.length >= 2) {
        guiManager.openHatEditorMenu(p, args[1].trim());
        return;
      }
      if ((sub.equals("remove") || sub.equals("delete")) && args.length >= 2) {
        guiManager.deleteHatByCommand(p, args[1].trim());
        return;
      }
      guiManager.openHatAdminMenu(p, 0);
      return;
    }
    guiManager.openHatMenu(p, 0);
  }

  @Override
  public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
    String cmdName = command.getName().toLowerCase();
    if (cmdName.equals("decor")) {
      if (!(sender instanceof Player p)) { sender.sendMessage(getMessage("only-players")); return true; }
      guiManager.openDecorHub(p);
      return true;
    }
    if (cmdName.equals("trim")) {
      if (!(sender instanceof Player p)) { sender.sendMessage(getMessage("only-players")); return true; }
      guiManager.openMainMenu(p);
      return true;
    }
    if (cmdName.equals("hat")) {
      if (!(sender instanceof Player p)) { sender.sendMessage(getMessage("only-players")); return true; }
      handleHatCommand(p, args);
      return true;
    }
    if (cmdName.equals("rename")) {
      if (!(sender instanceof Player p)) { sender.sendMessage(getMessage("only-players")); return true; }
      if (args.length == 0) guiManager.openRenameMenu(p);
      else guiManager.applyItemRename(p, String.join(" ", args));
      return true;
    }
    if (args.length > 0) {
      if (args[0].equalsIgnoreCase("reload")) {
        if (!hasAdminPerm(sender)) { sender.sendMessage(getMessage("no-permission")); return true; }
        reloadConfig();
        loadHatsFromDatabase();
        forceTakeoverCommands();
        if (trimListener != null) {
          for (World world : Bukkit.getWorlds()) trimListener.lockAlwaysDaytime(world);
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
          guiManager.forceRefreshEquippedHat(online);
          guiManager.applyAllSavedTrims(online);
        }
        sender.sendMessage(getMessage("reload-success"));
        return true;
      }
      if (args[0].equalsIgnoreCase("groups") && sender instanceof Player p) {
        if (!hasAdminPerm(p)) { p.sendMessage(getMessage("no-permission")); return true; }
        guiManager.openHatGroupListMenu(p, 0);
        return true;
      }
      if (args[0].equalsIgnoreCase("default") && args.length >= 2 && sender instanceof Player p) {
        if (!hasAdminPerm(p)) { p.sendMessage(getMessage("no-permission")); return true; }
        String rawId = args[1].trim();
        if (rawId.equalsIgnoreCase("off") || rawId.equalsIgnoreCase("none") || rawId.equalsIgnoreCase("clear")) {
          guiManager.setForceDefaultHat(p, null, false);
        } else {
          guiManager.setForceDefaultHat(p, rawId, true);
        }
        return true;
      }
      if ((args[0].equalsIgnoreCase("hats") || args[0].equalsIgnoreCase("hat") || args[0].equalsIgnoreCase("admin")) && sender instanceof Player p) {
        if (!hasAdminPerm(p)) { p.sendMessage(getMessage("no-permission")); return true; }
        guiManager.openHatAdminMenu(p, 0);
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
      if (args.length == 1) return List.of("reload", "hats", "groups", "default");
      if (args.length == 2 && args[0].equalsIgnoreCase("default")) {
        String prefix = args[1].toLowerCase();
        List<String> opts = new ArrayList<>(List.of("off", "none"));
        opts.addAll(getDbHatItems());
        opts.addAll(getAllNexoItemIds());
        return opts.stream().distinct().filter(id -> id.toLowerCase().startsWith(prefix)).limit(50).toList();
      }
    }
    if (cmd.equals("hat") && hasAdminPerm(sender)) {
      if (args.length == 1) {
        String prefix = args[0].toLowerCase();
        return List.of("admin", "groups", "add", "edit", "remove", "default").stream().filter(s -> s.startsWith(prefix)).toList();
      }
      if (args.length == 2) {
        String sub = args[0].toLowerCase();
        String prefix = args[1].toLowerCase();
        if (sub.equals("admin")) {
          return List.of("groups", "add", "edit", "remove", "default").stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (sub.equals("add") || sub.equals("default")) {
          List<String> opts = new ArrayList<>();
          if (sub.equals("default")) opts.addAll(List.of("off", "none"));
          opts.addAll(getAllNexoItemIds());
          return opts.stream().distinct().filter(id -> id.toLowerCase().startsWith(prefix)).limit(50).toList();
        }
        if (sub.equals("edit") || sub.equals("remove")) {
          return getDbHatItems().stream().filter(id -> id.toLowerCase().startsWith(prefix)).toList();
        }
      }
    }
    return Collections.emptyList();
  }
}
