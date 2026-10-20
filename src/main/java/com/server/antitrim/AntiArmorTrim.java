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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

  @Override
  public void onEnable() {
    instance = this;
    saveDefaultConfig();
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
    getLogger().info("AntiArmorTrim v2.5.0 (Catppuccin + Nexo Hat Replacer) enabled!");
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
        ItemStack helmet = player.getInventory().getHelmet();
        if (guiManager.isCustomHatItem(helmet)) {
          player.getInventory().setHelmet(guiManager.restoreOriginalHelmet(helmet));
        }
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
      guiManager.openHatMenu(p);
      return;
    }
    String sub = args[0].toLowerCase();
    if (sub.equals("admin") || sub.equals("add") || sub.equals("edit") || sub.equals("remove") || sub.equals("delete")) {
      if (!hasAdminPerm(p)) {
        p.sendMessage(getMessage("no-permission"));
        return;
      }
      if (sub.equals("admin")) {
        if (args.length == 1) {
          guiManager.openHatAdminMenu(p);
          return;
        }
        sub = args[1].toLowerCase();
        args = java.util.Arrays.copyOfRange(args, 1, args.length);
      }
      if (sub.equals("add") && args.length >= 2) {
        String rawId = args[1].trim();
        List<String> hats = new ArrayList<>(getConfig().getStringList("hats.items"));
        if (hats.stream().anyMatch(h -> h.equalsIgnoreCase(rawId))) {
          p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
          p.sendMessage(getMessage("hat-admin-exists", Placeholder.unparsed("id", rawId)));
          guiManager.openHatEditorMenu(p, rawId);
          return;
        }
        hats.add(rawId);
        getConfig().set("hats.items", hats);
        saveConfig();
        if (getNexoItem(rawId) != null) {
          p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
          p.sendMessage(getMessage("hat-admin-added", Placeholder.unparsed("id", rawId)));
          guiManager.openHatEditorMenu(p, rawId);
        } else {
          p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
          p.sendMessage(getMessage("hat-admin-added-invalid", Placeholder.unparsed("id", rawId)));
          guiManager.openHatAdminMenu(p);
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
      guiManager.openHatAdminMenu(p);
      return;
    }
    guiManager.openHatMenu(p);
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
      if ((args[0].equalsIgnoreCase("hats") || args[0].equalsIgnoreCase("hat") || args[0].equalsIgnoreCase("admin")) && sender instanceof Player p) {
        if (!hasAdminPerm(p)) { p.sendMessage(getMessage("no-permission")); return true; }
        guiManager.openHatAdminMenu(p);
        return true;
      }
    }
    sender.sendMessage(getMessage("usage", Placeholder.unparsed("label", label)));
    return true;
  }

  @Override
  public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
    String cmd = command.getName().toLowerCase();
    if (cmd.equals("antitrim") && args.length == 1 && hasAdminPerm(sender)) {
      return List.of("reload", "hats", "admin");
    }
    if (cmd.equals("hat") && hasAdminPerm(sender)) {
      if (args.length == 1) {
        String prefix = args[0].toLowerCase();
        return List.of("admin", "add", "edit", "remove").stream().filter(s -> s.startsWith(prefix)).toList();
      }
      if (args.length == 2) {
        String sub = args[0].toLowerCase();
        String prefix = args[1].toLowerCase();
        if (sub.equals("admin")) {
          return List.of("add", "edit", "remove").stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (sub.equals("add")) {
          return getAllNexoItemIds().stream().filter(id -> id.toLowerCase().startsWith(prefix)).limit(50).toList();
        }
        if (sub.equals("edit") || sub.equals("remove")) {
          return getConfig().getStringList("hats.items").stream().filter(id -> id.toLowerCase().startsWith(prefix)).toList();
        }
      }
      if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
        String sub = args[1].toLowerCase();
        String prefix = args[2].toLowerCase();
        if (sub.equals("add")) {
          return getAllNexoItemIds().stream().filter(id -> id.toLowerCase().startsWith(prefix)).limit(50).toList();
        }
        if (sub.equals("edit") || sub.equals("remove")) {
          return getConfig().getStringList("hats.items").stream().filter(id -> id.toLowerCase().startsWith(prefix)).toList();
        }
      }
    }
    return Collections.emptyList();
  }
}
