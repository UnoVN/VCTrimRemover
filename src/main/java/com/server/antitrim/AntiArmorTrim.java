package com.server.antitrim;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
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

    private static final Pattern HEX_LEGACY_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

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

        getLogger().info("AntiArmorTrim v2.2.0 (Catppuccin Decoration Studio) enabled successfully!");
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

    /**
     * Builds a clean Adventure TextComponent icon badge using Catppuccin hex colors.
     */
    public TextComponent createIconBadge(String symbol, String hexColor) {
        TextColor color = TextColor.fromHexString(hexColor);
        return Component.text(symbol + " ")
                .color(color != null ? color : TextColor.fromHexString("#cba6f7"))
                .decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Parses MiniMessage strings (and converts any legacy & codes) with non-italic default.
     */
    public Component parse(String input, TagResolver... resolvers) {
        return parseWithPerm(input, BYPASS_PERM, resolvers);
    }

    /**
     * Parses MiniMessage strings while resolving <rank> for a specific permission node.
     */
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
        String prefixRaw = getConfig().getString("messages.prefix", "<#6c7086>[<#cba6f7>✦ <bold>Decor</bold><#6c7086>] ");
        String msgRaw = getConfig().getString("messages." + key, "<#f38ba8>Missing message key: " + key);
        return parseWithPerm(prefixRaw + msgRaw, permissionNode, resolvers);
    }

    public Component getRawMessage(String key, TagResolver... resolvers) {
        String msgRaw = getConfig().getString("messages." + key, "<#f38ba8>Action denied.");
        return parse(msgRaw, resolvers);
    }

    /**
     * Automatically detects the lowest-weight LuckPerms group with the specified permission
     * or returns the configured fallback rank string.
     */
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
     * Converts legacy & / § and &#RRGGBB color codes into MiniMessage tags.
     */
    public String legacyToMiniMessage(String text) {
        if (text == null) return "";
        String result = text.replace('§', '&');

        Matcher matcher = HEX_LEGACY_PATTERN.matcher(result);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, "<#" + matcher.group(1) + ">");
        }
        matcher.appendTail(sb);
        result = sb.toString();

        return result
                .replace("&0", "<black>")
                .replace("&1", "<dark_blue>")
                .replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>")
                .replace("&4", "<dark_red>")
                .replace("&5", "<dark_purple>")
                .replace("&6", "<gold>")
                .replace("&7", "<#a6adc8>")
                .replace("&8", "<#6c7086>")
                .replace("&9", "<#89b4fa>")
                .replace("&a", "<#a6e3a1>")
                .replace("&b", "<#89dceb>")
                .replace("&c", "<#f38ba8>")
                .replace("&d", "<#f5c2e7>")
                .replace("&e", "<#f9e2af>")
                .replace("&f", "<#cdd6f4>")
                .replace("&l", "<bold>")
                .replace("&m", "<strikethrough>")
                .replace("&n", "<underlined>")
                .replace("&o", "<italic>")
                .replace("&r", "<reset>");
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
