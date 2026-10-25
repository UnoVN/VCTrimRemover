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

  /**
   * Ánh xạ mọi Material (kể cả Block 3D hoặc Item động) sang 2D Item Sprite chuẩn trong atlas 'items',
   * đảm bảo 100% không bao giờ xuất hiện khối đen/tím (Error Block).
   */
  public String getItemSpriteTag(Material material) {
    if (material == null) return "";
    String spriteName = switch (material) {
      case COMMAND_BLOCK, CHAIN_COMMAND_BLOCK, REPEATING_COMMAND_BLOCK -> "comparator";
      case BOOKSHELF, CHISELED_BOOKSHELF -> "book";
      case ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL -> "iron_ingot";
      case JUKEBOX, NOTE_BLOCK -> "music_disc_otherside";
      case SMITHING_TABLE -> "silence_armor_trim_smithing_template";
      case GRINDSTONE -> "flint";
      case CLOCK -> "clock_00";
      case COMPASS -> "compass_00";
      case RECOVERY_COMPASS -> "recovery_compass_00";
      case BLUE_ICE, PACKED_ICE, ICE -> "snowball";
      case LODESTONE -> "netherite_ingot";
      default -> material.getKey().getKey();
    };
    return "<white><sprite:items:item/" + spriteName + "></white>";
  }

  private TagResolver createGlyphTagResolver() {
    return TagResolver.resolver("glyph", (args, ctx) -> {
      String id = args.popOr("glyph id").value();
      Component nexoComp = deserializeNexoGlyph("<glyph:" + id + ">");
      if (nexoComp != null) {
        return Tag.selfClosingInserting(nexoComp.colorIfAbsent(NamedTextColor.WHITE));
      }
      return Tag.selfClosingInserting(Component.text("<glyph:" + id + ">", NamedTextColor.WHITE));
    });
  }

  @Nullable
  private Component deserializeNexoGlyph(String rawTag) {
    Plugin nexo = Bukkit.getPluginManager().getPlugin("Nexo");
    if (nexo == null || !nexo.isEnabled()) return null;
    try {
      ClassLoader loader = nexo.getClass().getClassLoader();
      Class<?> advClass = Class.forName("com.nexomc.nexo.utils.AdventureUtils", true, loader);
      Object advInstance = advClass.getField("INSTANCE").get(null);
      for (String methodName : new String[]{"getNEXO_SERIALIZER", "getMINI_MESSAGE"}) {
        try {
          Method m = advClass.getMethod(methodName);
          Object mmObj = m.invoke(advInstance);
          if (mmObj instanceof MiniMessage nexoMm) {
            Component c = nexoMm.deserialize(rawTag);
            if (c != null) return c;
          }
        } catch (Throwable ignored) {}
      }
    } catch (Throwable ignored) {}
    return null;
  }

  public Component parseLiteral(String input) {
    if (input == null || input.isEmpty()) return Component.empty();
    return miniMessage.deserialize(input, createGlyphTagResolver())
        .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
  }

  public Component parseItemName(String input) {
    if (input == null || input.isEmpty()) return Component.empty();

    if (input.contains("<") && input.contains(">")) {
      String converted = legacyToMiniMessage(input);
      try {
        return miniMessage.deserialize(converted, createGlyphTagResolver()).decoration(TextDecoration.ITALIC, false);
      } catch (Throwable ignored) {}
    }

    String normalized = input.replace('§', '&');
    return legacySerializer.deserialize(normalized).decoration(TextDecoration.ITALIC, false);
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
        .resolver(createGlyphTagResolver())
        .resolvers(resolvers)
        .build();
    return miniMessage.deserialize(legacyToMiniMessage(input), combined)
        .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
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
        syncConfigWithDefaults();
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
