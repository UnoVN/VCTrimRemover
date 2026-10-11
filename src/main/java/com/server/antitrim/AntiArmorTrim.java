package com.server.antitrim;

import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public class AntiArmorTrim extends JavaPlugin {

    private static AntiArmorTrim instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(new TrimListener(this), this);
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
        String msg = getConfig().getString(messages. + path, "&cThao tac bi tu choi do thieu quyen han!");
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}
