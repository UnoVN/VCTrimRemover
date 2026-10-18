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
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
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
    private final LegacyComponentSerializer minecraftLegacySerializer = LegacyComponentSerializer.builder()
            .character('&')
            .build();

    public static final String BYPASS_PERM = "armortrim.bypass";
    public static final String HAT_PERM = "armortrim.hat";
    public static final String RENAME_PERM = "armortrim.rename";
    public static final String ADMIN_PERM = "armortrim.admin";

    private static final Pattern HEX_LEGACY_PATTERN = Pattern.compile("(?i)[&§]#([A-Fa-f0-9]{6})");
    private static final Pattern LEGACY_CODE_PATTERN = Pattern.compile("(?i)[&§]([0-9a-fk-or])");

    private Method nexoItemFromIdMethod;
    private Method nexoBuilderBuildMethod;

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
        registerCmd("hat");
        registerCmd("rename");

        getLogger().info("AntiArmorTrim v2.5.0 (Catppuccin + Nexo Hat Replacer) enabled!");
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
        // Restore all online players' helmets before shutdown so no cosmetic state is left in inventory
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

    public static AntiArmorTrim getInstance() {
        return instance;
    }

    public TrimGuiManager getGuiManager() {
        return guiManager;
    }

    @Nullable
    public ItemStack getNexoItem(String itemId) {
        if (itemId == null || itemId.isBlank()) return null;
        if (!Bukkit.getPluginManager().isPluginEnabled("Nexo")) return null;

        try {
            if (nexoItemFromIdMethod == null) {
                Class<?> nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems");
                nexoItemFromIdMethod = nexoItemsClass.getMethod("itemFromId", String.class);
            }
            Object itemBuilder = nexoItemFromIdMethod.invoke(null, itemId.trim());
            if (itemBuilder == null) {
                return null;
            }
            if (nexoBuilderBuildMethod == null) {
                nexoBuilderBuildMethod = itemBuilder.getClass().getMethod("build");
            }
            Object built = nexoBuilderBuildMethod.invoke(itemBuilder);
            if (built instanceof ItemStack stack) {
                return stack.clone();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public String getItemSpriteTag(Material material) {
        String key = material.getKey().getKey();
        return "<white><sprite:items:item/" + key + "></white>";
    }

    public Component parseLiteral(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        return miniMessage.deserialize(input)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public Component parseMinecraftFormat(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        String normalized = input.replace('§', '&');
        return minecraftLegacySerializer.deserialize(normalized)
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
        String msgRaw = getConfig().getString("messages." + key, "<#f38ba8>Missing message key: " + key);
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
                case '7' -> "<gray>";
                case '8' -> "<dark_gray>";
                case '9' -> "<blue>";
                case 'a' -> "<green>";
                case 'b' -> "<aqua>";
                case 'c' -> "<red>";
                case 'd' -> "<light_purple>";
                case 'e' -> "<yellow>";
                case 'f' -> "<white>";
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

        if (cmdName.equals("hat")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(getMessage("only-players"));
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
                if (!player.hasPermission(ADMIN_PERM)) {
                    player.sendMessage(getMessage("no-permission"));
                    return true;
                }
                guiManager.openHatAdminMenu(player);
                return true;
            }
            guiManager.openHatMenu(player);
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

        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission(ADMIN_PERM)) {
                    sender.sendMessage(getMessage("no-permission"));
                    return true;
                }
                reloadConfig();
                for (Player online : Bukkit.getOnlinePlayers()) {
                    guiManager.applyAllSavedTrims(online);
                }
                sender.sendMessage(getMessage("reload-success"));
                return true;
            }
            if (args[0].equalsIgnoreCase("hats") && sender instanceof Player player) {
                if (!player.hasPermission(ADMIN_PERM)) {
                    player.sendMessage(getMessage("no-permission"));
                    return true;
                }
                guiManager.openHatAdminMenu(player);
                return true;
            }
        }

        sender.sendMessage(getMessage("usage", Placeholder.unparsed("label", label)));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("antitrim") && args.length == 1) {
            if (sender.hasPermission(ADMIN_PERM)) {
                return List.of("reload", "hats");
            }
        }
        if (command.getName().equalsIgnoreCase("hat") && args.length == 1) {
            if (sender.hasPermission(ADMIN_PERM)) {
                return Collections.singletonList("admin");
            }
        }
        return Collections.emptyList();
    }
}
