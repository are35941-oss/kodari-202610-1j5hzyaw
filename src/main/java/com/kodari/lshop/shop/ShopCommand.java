package com.kodari.lshop.shop;

import com.kodari.lshop.LShopPlugin;
import com.kodari.lshop.menu.ItemUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import net.milkbowl.vault.economy.EconomyResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ShopCommand implements CommandExecutor {
    private static final int PAGE_SIZE = 45;
    private final LShopPlugin plugin;

    public ShopCommand(LShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("lshop.admin")) {
                sender.sendMessage(ItemUtil.color("&cYou don't have permission to reload the shop."));
                return true;
            }
            plugin.reloadConfig();
            sender.sendMessage(ItemUtil.color("&aShop configuration reloaded."));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        if (!player.hasPermission("lshop.use")) {
            player.sendMessage(ItemUtil.color("&cYou don't have permission to use the shop."));
            return true;
        }
        open(player, 0);
        return true;
    }

    private void open(Player player, int page) {
        ConfigurationSection shop = plugin.getConfig().getConfigurationSection("shop");
        if (shop == null) {
            player.sendMessage(ItemUtil.color("&cThe shop has no items configured."));
            return;
        }

        List<String> keys = new ArrayList<>(shop.getKeys(false));
        int pages = Math.max(1, (keys.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, pages - 1));
        Map<Integer, ItemStack> contents = new HashMap<>();
        Map<Integer, java.util.function.Consumer<Player>> actions = new HashMap<>();
        int start = currentPage * PAGE_SIZE;
        int end = Math.min(keys.size(), start + PAGE_SIZE);

        for (int index = start; index < end; index++) {
            String key = keys.get(index);
            ConfigurationSection itemConfig = shop.getConfigurationSection(key);
            if (itemConfig == null) {
                continue;
            }
            double price = itemConfig.getDouble("buy", -1.0);
            int amount = itemConfig.getInt("amount", 1);
            String name = itemConfig.getString("name", key);
            List<String> lore = new ArrayList<>();
            lore.add("&7Price: &e" + plugin.getEconomy().format(price));
            lore.add("&aClick to buy");
            ItemStack icon = ItemUtil.create(key, amount, name, lore);
            if (icon == null) {
                continue;
            }
            int slot = index - start;
            contents.put(slot, icon);
            if (price > 0 && Double.isFinite(price) && amount > 0) {
                actions.put(slot, clicker -> buy(clicker, key, amount, price));
            }
        }

        if (currentPage > 0) {
            contents.put(45, ItemUtil.create("ARROW", 1, "&aPrevious page", List.of()));
            actions.put(45, clicker -> open(clicker, currentPage - 1));
        }
        contents.put(49, ItemUtil.create("PAPER", 1, "&ePage " + (currentPage + 1) + "/" + pages,
                List.of("&7Click an item to purchase it.")));
        if (currentPage + 1 < pages) {
            contents.put(53, ItemUtil.create("ARROW", 1, "&aNext page", List.of()));
            actions.put(53, clicker -> open(clicker, currentPage + 1));
        }
        plugin.getMenuManager().open(player, "&8LShop", 54, contents, actions);
    }

    private void buy(Player player, String material, int amount, double price) {
        ItemStack item = ItemUtil.create(material, amount, "", List.of());
        if (item == null) {
            player.sendMessage(ItemUtil.color("&cThat shop item is not available on this server."));
            return;
        }
        double total = price * amount;
        if (!Double.isFinite(total) || total <= 0) {
            player.sendMessage(ItemUtil.color("&cThis item has an invalid price."));
            return;
        }
        if (!hasSpace(player, item)) {
            player.sendMessage(ItemUtil.color("&cYou don't have enough inventory space."));
            return;
        }
        if (!plugin.getEconomy().has(player, total)) {
            player.sendMessage(ItemUtil.color("&cYou can't afford this item."));
            return;
        }
        EconomyResponse response = plugin.getEconomy().withdrawPlayer(player, total);
        if (!response.transactionSuccess()) {
            player.sendMessage(ItemUtil.color("&cPayment failed: " + response.errorMessage));
            return;
        }
        player.getInventory().addItem(item);
        player.sendMessage(ItemUtil.color("&aPurchased &f" + amount + "x " + material + " &afor &e"
                + plugin.getEconomy().format(total) + "&a."));
    }

    public static boolean hasSpace(Player player, ItemStack item) {
        int remaining = item.getAmount();
        int maxStack = item.getMaxStackSize();
        for (ItemStack content : player.getInventory().getStorageContents()) {
            if (content == null) {
                remaining -= maxStack;
            } else if (content.isSimilar(item)) {
                remaining -= Math.max(0, content.getMaxStackSize() - content.getAmount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }
}