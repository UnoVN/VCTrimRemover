package com.server.antitrim.gui;

import com.server.antitrim.AntiArmorTrim;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class KillEffectHubGui implements Listener {

  private final AntiArmorTrim plugin;
  private static boolean registered = false;

  public KillEffectHubGui(AntiArmorTrim plugin) {
    this.plugin = plugin;
  }

  public static synchronized void ensureRegistered(AntiArmorTrim plugin) {
    if (!registered) {
      Bukkit.getPluginManager().registerEvents(new KillEffectHubGui(plugin), plugin);
      registered = true;
    }
  }

  public static void open(AntiArmorTrim plugin, Player player) {
    ensureRegistered(plugin);
    String rawTitle = plugin.getConfig().getString("gui.killeffect-hub-title", "<white><sprite:items:item/totem_of_undying></white> <#cba6f7><bold>KillEffect Studio</bold> <#6c7086>▪ <#a6adc8>Hub");
    Component title = plugin.parse(rawTitle);

    TrimMenuHolder holder = new TrimMenuHolder(TrimMenuHolder.MenuType.KILLEFFECT_HUB, null, null, 0);
    Inventory inv = Bukkit.createInventory(holder, 27, title);
    holder.setInventory(inv);

    ItemStack border = TrimMenuHolder.createFiller(plugin, Material.GRAY_STAINED_GLASS_PANE);
    for (int i = 0; i < 27; i++) inv.setItem(i, border);

    // Slot 12: Classic KillEffect (Particle Effects - calls /killeffects)
    List<Component> classicLore = new ArrayList<>(List.of(
        plugin.parse("<#a6adc8>ѕᴇʟᴇᴄᴛ ᴄʟᴀѕѕɪᴄ <#fab387>Particle Effects"),
        plugin.parse("<#a6adc8>ꜰʀᴏᴍ ᴛʜᴇ ѕᴇʀᴠᴇʀ'ѕ ᴄʟᴀѕѕɪᴄ ᴍᴇɴᴜ."),
        Component.empty(),
        plugin.parse("<#89dceb>▸ ᴄʟɪᴄᴋ <#6c7086>▪ <#cdd6f4>ʀᴜɴ /killeffects")));
    inv.setItem(12, TrimMenuHolder.createStyledButton(plugin, Material.FIREWORK_ROCKET, "<#fab387><bold>Classic KillEffect</bold>", classicLore));

    // Slot 14: Modern KillEffect (3D Model Effects via ModelEngine)
    // Rule: Any line containing numbers must use Normal MC Text (no MiniText)
    List<Component> modernLore = new ArrayList<>(List.of(
        plugin.parse("<#a6adc8>Choose an animated <#eba0ac>3D Model Effect"),
        plugin.parse("<#a6adc8>with <#cdd6f4>Victim Skin <#a6adc8>& custom particles."),
        Component.empty(),
        plugin.parse("<#89dceb>▸ Click <#6c7086>▪ <#cdd6f4>Open 3D KillEffect Studio")));
    TrimMenuHolder.appendLockedLoreIfNeeded(plugin, player, modernLore, AntiArmorTrim.KILLEFFECT_PERM);
    inv.setItem(14, TrimMenuHolder.createStyledButton(plugin, Material.TOTEM_OF_UNDYING, "<#eba0ac><bold>Modern KillEffect (3D)</bold>", modernLore));

    // Slot 18: Back to Decor Hub
    inv.setItem(18, TrimMenuHolder.createBackButton(plugin, "<#f9e2af><bold>Back</bold> <#6c7086>▪ <#cdd6f4>Decor Hub"));

    // Slot 26: Admin 3D Manager (if player has admin permission)
    if (plugin.hasAdminPerm(player)) {
      inv.setItem(26, TrimMenuHolder.createAdminButton(plugin, "<#f38ba8><bold>Admin 3D Manager</bold>", List.of(
          plugin.parse("<#a6adc8>Manage <#cdd6f4>ModelEngine Models<#a6adc8>,"),
          plugin.parse("<#a6adc8>permission groups, or default effect."))));
    }

    player.openInventory(inv);
  }

  @EventHandler
  public void onClick(InventoryClickEvent event) {
    if (!(event.getInventory().getHolder() instanceof TrimMenuHolder holder)) return;
    if (holder.getMenuType() != TrimMenuHolder.MenuType.KILLEFFECT_HUB) return;
    event.setCancelled(true);
    if (!(event.getWhoClicked() instanceof Player player)) return;

    int slot = event.getRawSlot();
    if (slot == 12) {
      player.closeInventory();
      player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
      player.performCommand("killeffects");
    } else if (slot == 14) {
      player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
      KillEffectStudioGui.openPlayerMenu(plugin, player, 0);
    } else if (slot == 18) {
      player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
      DecorHubGui.open(plugin, player);
    } else if (slot == 26 && plugin.hasAdminPerm(player)) {
      player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
      KillEffectStudioGui.openAdminMenu(plugin, player, 0);
    }
  }
}
