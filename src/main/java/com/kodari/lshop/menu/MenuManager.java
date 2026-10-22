package com.kodari.lshop.menu;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class MenuManager implements Listener {
    private final Map<Inventory, Map<Integer, Consumer<Player>>> menus = new HashMap<>();

    public void open(Player player, String title, int size, Map<Integer, ItemStack> contents,
                     Map<Integer, Consumer<Player>> actions) {
        Inventory inventory = Bukkit.createInventory(null, size, ChatColor.translateAlternateColorCodes('&', title));
        contents.forEach(inventory::setItem);
        menus.put(inventory, actions);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        Map<Integer, Consumer<Player>> actions = menus.get(top);
        if (actions == null) {
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() == top && event.getWhoClicked() instanceof Player player) {
            Consumer<Player> action = actions.get(event.getSlot());
            if (action != null) {
                action.accept(player);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (menus.containsKey(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        menus.remove(event.getInventory());
    }
}