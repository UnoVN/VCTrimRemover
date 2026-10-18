package com.server.antitrim;

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
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AntiArmorTrim extends JavaPlugin implements CommandExecutor, TabCompleter {

    private static AntiArmorTrim instance;
    private TrimGuiManager guiManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public static final String BYPASS_PERM = "armortrim.bypass";
    public static final String RENAME_PERM = "armortrim.rename";
    public static final String ADMIN_PERM = "armortrim.admin";

    private static final Pattern HEX_LEGACY_PATTERN = Pattern.compile("(?i)&?#([A-Fa-f0-9]{6})");
    private static final Pattern LEGACY_CODE_PATTERN = Pattern.compile("(?i)[&§]([0-9a-fk-or])");

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.guiManager = new TrimGuiManager(this);
        getServer().getPluginManager().registerEvents(new TrimListener(this, guiManager), this);
        getServer().getPluginManager().registerEvents(guiManager, this);

        registerCmd("antitrim");
        registerCmd("decor");
        registerCmd("trim");
        registerCmd("rename");

        getLogger().info("AntiArmorTrim v2.3.1 (Catppuccin Sprite Edition) enabled!");
    }

    private void registerCmd(String name) {
        var cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("AntiArmorTrim disabled.");
    }

    public static AntiArmorTrim getInstance() {
        return instance;
    }

    public TrimGuiManager getGuiManager() {
        return guiManager;
    }

    public String getItemSpriteTag(Material material) {
        String key = material.getKey().getKey();
        return "<white><sprite:items:item/" + key + "></white>";
    }

    /**
     * Parses pure MiniMessage without replacing & codes (used for the Format Guide so &c, &k stay visible).
     */
    public Component parseLiteral(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        return miniMessage.deserialize(input)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public Component parse(String input, TagResolver... resolvers) {
        return parseWithPerm(input, BYPASS_PERM, resolvers);
    }

    public Component parseWithPerm(String input, String permissionNode, TagResolver... resolvers) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        String converted = legacyToMiniMessage(input);
        TagResolver combined = TagResolver.builder()
                .resolver(Placeholder.parsed("rank", getRequiredRankRaw(permissionNode)))
                .resolvers(resolvers)
                .build();
        return miniMessage.deserialize(converted, combined)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public Component getMessage(String key, TagResolver... resolvers) {
        return getMessageWithPerm(key, BYPASS_PERM, resolvers);
    }

    public Component getMessageWithPerm(String key, String permissionNode, TagResolver... resolvers) {
        String prefixRaw = getConfig().getString("messages.prefix", "");
        String defaultMsg = key.equals("trim-cleared-all")
                ? "<#cdd6f4>Cleared active trims across all <#cba6f7>4 armor pieces<#cdd6f4>."
                : "<#f38ba8>Missing message key: " + key;
        String msgRaw = getConfig().getString("messages." + key, defaultMsg);
        return parseWithPerm(prefixRaw + msgRaw, permissionNode, resolvers);
    }

    public Component getRawMessage(String key, TagResolver... resolvers) {
        String msgRaw = getConfig().getString("messages." + key, "<#f38ba8>Action denied.");
        return parse(msgRaw, resolvers);
    }

    public String getRequiredRankRaw(String permissionNode) {
        String fallback = getConfig().getString("luckperms.fallback-rank", "<#f9e2af><bold>VIP</bold>");
        boolean autoDetect = getConfig().getBoolean("luckperms.auto-detect-rank", true);

        if (autoDetect && Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                LuckPerms lp = LuckPermsProvider.get();
                List<Group> matchedGroups = new ArrayList<>();

                for (Group group : lp.getGroupManager().getLoadedGroups()) {
                    for (Node node : group.getNodes()) {
                        if ((node.getKey().equalsIgnoreCase(permissionNode) || node.getKey().equalsIgnoreCase(BYPASS_PERM))
                                && node.getValue()) {
                            matchedGroups.add(group);
                            break;
                        }
                    }
                }

                if (!matchedGroups.isEmpty()) {
                    matchedGroups.sort(Comparator.comparingInt(g -> g.getWeight().orElse(999)));
                    Group target = matchedGroups.get(0);

                    String prefix = target.getCachedData().getMetaData().getPrefix();
                    if (prefix != null && !prefix.isBlank()) {
                        return legacyToMiniMessage(prefix.trim());
                    }
                    String displayName = target.getDisplayName();
                    if (displayName != null && !displayName.isBlank()) {
                        return "<#f9e2af><bold>" + legacyToMiniMessage(displayName.trim()) + "</bold>";
                    }
                    return "<#f9e2af><bold>" + target.getName().toUpperCase() + "</bold>";
                }
            } catch (Throwable ignored) {
            }
        }
        return legacyToMiniMessage(fallback);
    }

    /**
     * Case-insensitive converter for legacy & / § color codes and &#RRGGBB hex codes into Catppuccin MiniMessage tags.
     */
    public String legacyToMiniMessage(String text) {
        if (text == null || text.isEmpty()) return "";

        Matcher hexMatcher = HEX_LEGACY_PATTERN.matcher(text);
        StringBuilder hexSb = new StringBuilder();
        while (hexMatcher.find()) {
            hexMatcher.appendReplacement(hexSb, "<#" + hexMatcher.group(1) + ">");
        }
        hexMatcher.appendTail(hexSb);

        Matcher codeMatcher = LEGACY_CODE_PATTERN.matcher(hexSb.toString());
        StringBuilder finalSb = new StringBuilder();
        while (codeMatcher.find()) {
            char code = Character.toLowerCase(codeMatcher.group(1).charAt(0));
            String replacement = switch (code) {
                case '0' -> "<black>";
                case '1' -> "<dark_blue>";
                case '2' -> "<dark_green>";
                case '3' -> "<dark_aqua>";
                case '4' -> "<dark_red>";
                case '5' -> "<dark_purple>";
                case '6' -> "<gold>";
                case '7' -> "<#a6adc8>";
                case '8' -> "<#6c7086>";
                case '9' -> "<#89b4fa>";
                case 'a' -> "<#a6e3a1>";
                case 'b' -> "<#89dceb>";
                case 'c' -> "<#f38ba8>";
                case 'd' -> "<#f5c2e7>";
                case 'e' -> "<#f9e2af>";
                case 'f' -> "<#cdd6f4>";
                case 'k' -> "<obfuscated>";
                case 'l' -> "<bold>";
                case 'm' -> "<strikethrough>";
                case 'n' -> "<underlined>";
                case 'o' -> "<italic>";
                case 'r' -> "<reset>";
                default -> "";
            };
            codeMatcher.appendReplacement(finalSb, replacement);
        }
        codeMatcher.appendTail(finalSb);
        return finalSb.toString();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String cmdName = command.getName().toLowerCase();

        if (cmdName.equals("decor")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(getMessage("only-players"));
                return true;
            }
            guiManager.openDecorHub(player);
            return true;
        }

        if (cmdName.equals("trim")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(getMessage("only-players"));
                return true;
            }
            guiManager.openMainMenu(player);
            return true;
        }

        if (cmdName.equals("rename")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(getMessage("only-players"));
                return true;
            }
            if (args.length == 0) {
                guiManager.openRenameMenu(player);
            } else {
                String rawName = String.join(" ", args);
                guiManager.applyItemRename(player, rawName);
            }
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission(ADMIN_PERM)) {
                sender.sendMessage(getMessage("no-permission"));
                return true;
            }
            reloadConfig();
            sender.sendMessage(getMessage("reload-success"));
            return true;
        }

        sender.sendMessage(getMessage("usage", Placeholder.unparsed("label", label)));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("antitrim") && args.length == 1) {
            if (sender.hasPermission(ADMIN_PERM)) {
                return Collections.singletonList("reload");
            }
        }
        return Collections.emptyList();
    }
}
