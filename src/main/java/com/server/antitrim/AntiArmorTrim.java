package com.server.antitrim;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AntiArmorTrim extends JavaPlugin implements CommandExecutor, TabCompleter {

    private static AntiArmorTrim instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(new TrimListener(this), this);

        if (getCommand("antitrim") != null) {
            getCommand("antitrim").setExecutor(this);
            getCommand("antitrim").setTabCompleter(this);
        }

        getLogger().info("AntiArmorTrim da kich hoat thanh cong tren Paper 1.21!");
    }

    @Override
    public void onDisable() {
        getLogger().info("AntiArmorTrim da tat!");
    }

    public static AntiArmorTrim getInstance() {
        return instance;
    }

    public String getMessage(String path) {
        // Da sua loi thieu dau ngoac kep o "messages."
        String msg = getConfig().getString("messages." + path, "&cThao tac bi tu choi do thieu quyen han!");
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("armortrim.admin")) {
                sender.sendMessage(getMessage("no-permission"));
                return true;
            }
            reloadConfig();
            sender.sendMessage(getMessage("reload-success"));
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Cú pháp: /" + label + " reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>();
            if (sender.hasPermission("armortrim.admin")) {
                subCommands.add("reload");
            }
            return subCommands;
        }
        return Collections.emptyList();
    }
}
