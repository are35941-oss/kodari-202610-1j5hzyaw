package com.kodari.lshop.shop;

import com.kodari.lshop.LShopPlugin;
import com.kodari.lshop.menu.ItemUtil;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SellCommand implements CommandExecutor {
    private final LShopPlugin plugin;

    public SellCommand(LShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        if (!player.hasPermission("lsell.use")) {
            player.sendMessage(ItemUtil.color("&cYou don't have permission to sell items."));
            return true;
        }
        if (args.length == 0) {
            open(player);
        } else if (args[0].equalsIgnoreCase("all")) {
            sellAll(player);
        } else if (args[0].equalsIgnoreCase("hand")) {
            int slot = player.getInventory().getHeldItemSlot();
            ItemStack held = player.getInventory().getItem(slot);
            if (held == null || held.getType().isAir()) {
                player.sendMessage(ItemUtil.color("&cHold an item you can sell."));
                return true;
            }
            sellSlot(player, slot, held.getAmount(), ItemUtil.materialName(held));
        } else {
            player.sendMessage(ItemUtil.color("&eUsage: /lsell [hand|all]"));
        }
        return true;
    }

    private void open(Player player) {
        Map<Integer, ItemStack> contents = new HashMap<>();
        Map<Integer, java.util.function.Consumer<Player>> actions = new HashMap<>();
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack stack = storage[slot];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            String material = ItemUtil.materialName(stack);
            double price = sellPrice(material);
            if (price <= 0 || !Double.isFinite(price)) {
                continue;
            }
            ItemStack icon = stack.clone();
            ItemMeta meta = icon.getItemMeta();
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(ItemUtil.color("&7Sell value: &e" + plugin.getEconomy().format(price * stack.getAmount())));
            lore.add(ItemUtil.color("&aClick to sell this stack"));
            meta.setLore(lore);
            icon.setItemMeta(meta);
            final int inventorySlot = slot;
            final int amount = stack.getAmount();
            contents.put(slot, icon);
            actions.put(slot, clicker -> sellSlot(clicker, inventorySlot, amount, material));
        }
        contents.put(49, ItemUtil.create("GOLD_INGOT", 1, "&6Sell menu", List.of("&7Click an item to sell its stack.")));
        plugin.getMenuManager().open(player, "&8Sell Items", 54, contents, actions);
    }

    private void sellSlot(Player player, int slot, int expectedAmount, String material) {
        ItemStack current = player.getInventory().getItem(slot);
        if (current == null || current.getType().isAir() || current.getAmount() != expectedAmount
                || !ItemUtil.materialName(current).equals(material)) {
            player.sendMessage(ItemUtil.color("&cThat inventory item has changed. Try again."));
            return;
        }
        double price = sellPrice(material);
        double total = price * current.getAmount();
        if (price <= 0 || !Double.isFinite(total) || total <= 0) {
            player.sendMessage(ItemUtil.color("&cThat item cannot be sold."));
            return;
        }
        ItemStack backup = current.clone();
        player.getInventory().setItem(slot, null);
        EconomyResponse response = plugin.getEconomy().depositPlayer(player, total);
        if (!response.transactionSuccess()) {
            player.getInventory().setItem(slot, backup);
            player.sendMessage(ItemUtil.color("&cSale failed: " + response.errorMessage));
            return;
        }
        player.sendMessage(ItemUtil.color("&aSold &f" + backup.getAmount() + "x " + material + " &afor &e"
                + plugin.getEconomy().format(total) + "&a."));
        open(player);
    }

    private void sellAll(Player player) {
        ItemStack[] storage = player.getInventory().getStorageContents();
        Map<Integer, ItemStack> toRemove = new HashMap<>();
        double total = 0;
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack stack = storage[slot];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            double price = sellPrice(ItemUtil.materialName(stack));
            if (price > 0 && Double.isFinite(price)) {
                total += price * stack.getAmount();
                toRemove.put(slot, stack.clone());
            }
        }
        if (toRemove.isEmpty() || !Double.isFinite(total) || total <= 0) {
            player.sendMessage(ItemUtil.color("&cYou don't have any configured items to sell."));
            return;
        }
        toRemove.keySet().forEach(slot -> player.getInventory().setItem(slot, null));
        EconomyResponse response = plugin.getEconomy().depositPlayer(player, total);
        if (!response.transactionSuccess()) {
            toRemove.forEach((slot, item) -> player.getInventory().setItem(slot, item));
            player.sendMessage(ItemUtil.color("&cSale failed: " + response.errorMessage));
            return;
        }
        player.sendMessage(ItemUtil.color("&aSold all configured items for &e" + plugin.getEconomy().format(total) + "&a."));
    }

    private double sellPrice(String material) {
        ConfigurationSection item = plugin.getConfig().getConfigurationSection("shop." + material);
        return item == null ? -1.0 : item.getDouble("sell", -1.0);
    }
}