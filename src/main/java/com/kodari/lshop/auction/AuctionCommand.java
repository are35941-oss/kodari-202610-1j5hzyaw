package com.kodari.lshop.auction;

import com.kodari.lshop.LShopPlugin;
import com.kodari.lshop.menu.ItemUtil;
import com.kodari.lshop.shop.ShopCommand;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class AuctionCommand implements CommandExecutor, TabCompleter {
    private static final int PAGE_SIZE = 45;
    private final LShopPlugin plugin;
    private final AuctionHouse house;

    public AuctionCommand(LShopPlugin plugin) {
        this.plugin = plugin;
        this.house = plugin.getAuctionHouse();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("lah.admin")) {
                sender.sendMessage(ItemUtil.color("&cYou don't have permission to reload the auction house."));
                return true;
            }
            plugin.reloadConfig();
            sender.sendMessage(ItemUtil.color("&aAuction settings reloaded."));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        if (!player.hasPermission("lah.use")) {
            player.sendMessage(ItemUtil.color("&cYou don't have permission to use the auction house."));
            return true;
        }
        if (args.length == 0) {
            open(player, 0);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "sell" -> createListing(player, args);
            case "buy" -> {
                if (args.length < 2) {
                    player.sendMessage(ItemUtil.color("&eUsage: /lah buy <listing-id>"));
                } else {
                    buy(player, args[1]);
                }
            }
            case "cancel", "remove" -> {
                if (args.length < 2) {
                    player.sendMessage(ItemUtil.color("&eUsage: /lah cancel <listing-id>"));
                } else {
                    cancel(player, args[1]);
                }
            }
            case "my" -> showOwnListings(player);
            case "expired", "claim" -> claimExpired(player);
            case "help" -> sendHelp(player);
            default -> {
                if (args[0].equalsIgnoreCase("page") && args.length > 1) {
                    try {
                        open(player, Integer.parseInt(args[1]) - 1);
                    } catch (NumberFormatException exception) {
                        player.sendMessage(ItemUtil.color("&cPage must be a number."));
                    }
                } else {
                    sendHelp(player);
                }
            }
        }
        return true;
    }

    private void open(Player player, int page) {
        List<AuctionHouse.Listing> available = house.getListings().stream()
                .filter(listing -> !isExpired(listing))
                .collect(Collectors.toList());
        int pages = Math.max(1, (available.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, pages - 1));
        Map<Integer, ItemStack> contents = new HashMap<>();
        Map<Integer, Consumer<Player>> actions = new HashMap<>();
        int start = currentPage * PAGE_SIZE;
        int end = Math.min(available.size(), start + PAGE_SIZE);

        for (int index = start; index < end; index++) {
            AuctionHouse.Listing listing = available.get(index);
            ItemStack icon = listing.getItem();
            ItemMeta meta = icon.getItemMeta();
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(ItemUtil.color("&7Seller: &f" + listing.getSellerName()));
            lore.add(ItemUtil.color("&7Price: &e" + plugin.getEconomy().format(listing.getPrice())));
            lore.add(ItemUtil.color("&7Listing: &f" + listing.getId()));
            lore.add(ItemUtil.color(listing.getSeller().equals(player.getUniqueId())
                    ? "&cYour listing" : "&aClick to buy"));
            meta.setLore(lore);
            icon.setItemMeta(meta);
            int slot = index - start;
            contents.put(slot, icon);
            if (!listing.getSeller().equals(player.getUniqueId())) {
                actions.put(slot, clicker -> buy(clicker, listing.getId()));
            }
        }

        if (currentPage > 0) {
            contents.put(45, ItemUtil.create("ARROW", 1, "&aPrevious page", List.of()));
            actions.put(45, clicker -> open(clicker, currentPage - 1));
        }
        contents.put(49, ItemUtil.create("PAPER", 1, "&eAuction House", List.of(
                "&7Page " + (currentPage + 1) + "/" + pages,
                "&7Use &f/lah sell <price> &7while holding an item.")));
        if (currentPage + 1 < pages) {
            contents.put(53, ItemUtil.create("ARROW", 1, "&aNext page", List.of()));
            actions.put(53, clicker -> open(clicker, currentPage + 1));
        }
        plugin.getMenuManager().open(player, "&8Auction House", 54, contents, actions);
    }

    private void createListing(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ItemUtil.color("&eUsage: /lah sell <price> [amount]"));
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            player.sendMessage(ItemUtil.color("&cHold the item you want to list."));
            return;
        }
        double price;
        int amount = held.getAmount();
        try {
            price = Double.parseDouble(args[1]);
            if (args.length > 2) {
                amount = Integer.parseInt(args[2]);
            }
        } catch (NumberFormatException exception) {
            player.sendMessage(ItemUtil.color("&cPrice and amount must be valid numbers."));
            return;
        }
        if (!Double.isFinite(price) || price <= 0 || amount <= 0 || amount > held.getAmount()) {
            player.sendMessage(ItemUtil.color("&cEnter a positive price and a valid amount."));
            return;
        }
        int activeListings = 0;
        for (AuctionHouse.Listing listing : house.getListings()) {
            if (listing.getSeller().equals(player.getUniqueId()) && !isExpired(listing)) {
                activeListings++;
            }
        }
        int maxListings = plugin.getConfig().getInt("auction.max-listings-per-player", 10);
        if (activeListings >= maxListings) {
            player.sendMessage(ItemUtil.color("&cYou've reached the maximum of " + maxListings + " active listings."));
            return;
        }

        String id = UUID.randomUUID().toString().substring(0, 8);
        ItemStack listedItem = held.clone();
        listedItem.setAmount(amount);
        AuctionHouse.Listing listing = new AuctionHouse.Listing(id, player.getUniqueId(), player.getName(),
                price, listedItem, System.currentTimeMillis());
        if (amount == held.getAmount()) {
            player.getInventory().setItemInMainHand(null);
        } else {
            held.setAmount(held.getAmount() - amount);
        }
        if (!house.create(listing)) {
            player.getInventory().addItem(listedItem);
            player.sendMessage(ItemUtil.color("&cCould not save the auction listing. Your item was returned."));
            return;
        }
        player.sendMessage(ItemUtil.color("&aListed &f" + amount + "x " + listedItem.getType().name()
                + " &afor &e" + plugin.getEconomy().format(price) + "&a. ID: &f" + id));
    }

    private void buy(Player buyer, String requestedId) {
        AuctionHouse.Listing listing = house.find(requestedId);
        if (listing == null) {
            buyer.sendMessage(ItemUtil.color("&cListing not found, or the ID is ambiguous."));
            return;
        }
        if (listing.getSeller().equals(buyer.getUniqueId())) {
            buyer.sendMessage(ItemUtil.color("&cYou can't buy your own listing."));
            return;
        }
        if (isExpired(listing)) {
            buyer.sendMessage(ItemUtil.color("&cThat listing has expired."));
            return;
        }
        ItemStack item = listing.getItem();
        if (!ShopCommand.hasSpace(buyer, item)) {
            buyer.sendMessage(ItemUtil.color("&cYou don't have enough inventory space."));
            return;
        }
        if (!plugin.getEconomy().has(buyer, listing.getPrice())) {
            buyer.sendMessage(ItemUtil.color("&cYou can't afford this listing."));
            return;
        }
        if (!house.remove(listing.getId())) {
            buyer.sendMessage(ItemUtil.color("&cCould not update the auction listing. Try again."));
            return;
        }
        EconomyResponse withdrawal = plugin.getEconomy().withdrawPlayer(buyer, listing.getPrice());
        if (!withdrawal.transactionSuccess()) {
            house.restore(listing);
            buyer.sendMessage(ItemUtil.color("&cPayment failed: " + withdrawal.errorMessage));
            return;
        }
        EconomyResponse payout = plugin.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(listing.getSeller()), listing.getPrice());
        if (!payout.transactionSuccess()) {
            plugin.getEconomy().depositPlayer(buyer, listing.getPrice());
            house.restore(listing);
            buyer.sendMessage(ItemUtil.color("&cSeller payout failed. Your payment was refunded."));
            return;
        }
        buyer.getInventory().addItem(item);
        buyer.sendMessage(ItemUtil.color("&aBought listing &f" + listing.getId() + " &afor &e"
                + plugin.getEconomy().format(listing.getPrice()) + "&a."));
        Player seller = Bukkit.getPlayer(listing.getSeller());
        if (seller != null) {
            seller.sendMessage(ItemUtil.color("&aYour auction &f" + listing.getId() + " &awas sold for &e"
                    + plugin.getEconomy().format(listing.getPrice()) + "&a."));
        }
    }

    private void cancel(Player player, String requestedId) {
        AuctionHouse.Listing listing = house.find(requestedId);
        if (listing == null || !listing.getSeller().equals(player.getUniqueId())) {
            player.sendMessage(ItemUtil.color("&cYou don't own that listing, or it doesn't exist."));
            return;
        }
        if (!house.remove(listing.getId())) {
            player.sendMessage(ItemUtil.color("&cCould not remove the listing. Try again."));
            return;
        }
        giveOrDrop(player, listing.getItem());
        player.sendMessage(ItemUtil.color("&aListing &f" + listing.getId() + " &acancelled; your item was returned."));
    }

    private void claimExpired(Player player) {
        List<AuctionHouse.Listing> expired = house.getListings().stream()
                .filter(listing -> listing.getSeller().equals(player.getUniqueId()) && isExpired(listing))
                .collect(Collectors.toList());
        int claimed = 0;
        for (AuctionHouse.Listing listing : expired) {
            if (house.remove(listing.getId())) {
                giveOrDrop(player, listing.getItem());
                claimed++;
            }
        }
        player.sendMessage(claimed == 0
                ? ItemUtil.color("&eYou have no expired items to claim.")
                : ItemUtil.color("&aReturned " + claimed + " expired auction item(s)."));
    }

    private void showOwnListings(Player player) {
        List<AuctionHouse.Listing> own = house.getListings().stream()
                .filter(listing -> listing.getSeller().equals(player.getUniqueId()))
                .collect(Collectors.toList());
        if (own.isEmpty()) {
            player.sendMessage(ItemUtil.color("&eYou have no auction listings."));
            return;
        }
        player.sendMessage(ItemUtil.color("&6Your auction listings:"));
        for (AuctionHouse.Listing listing : own) {
            String state = isExpired(listing) ? " &c(expired - /lah expired)" : "";
            player.sendMessage(ItemUtil.color("&e" + listing.getId() + " &7- " + listing.getItem().getAmount()
                    + "x " + listing.getItem().getType().name() + " for &e"
                    + plugin.getEconomy().format(listing.getPrice()) + state));
        }
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private boolean isExpired(AuctionHouse.Listing listing) {
        long hours = plugin.getConfig().getLong("auction.duration-hours", 168);
        if (hours <= 0) {
            return false;
        }
        long duration = hours > Long.MAX_VALUE / 3_600_000L ? Long.MAX_VALUE : hours * 3_600_000L;
        return System.currentTimeMillis() - listing.getCreated() >= duration;
    }

    private void sendHelp(Player player) {
        player.sendMessage(ItemUtil.color("&6Auction House commands:"));
        player.sendMessage(ItemUtil.color("&e/lah &7- Browse auctions"));
        player.sendMessage(ItemUtil.color("&e/lah sell <price> [amount] &7- List the held item"));
        player.sendMessage(ItemUtil.color("&e/lah buy <id> &7- Buy a listing"));
        player.sendMessage(ItemUtil.color("&e/lah cancel <id> &7- Cancel your listing"));
        player.sendMessage(ItemUtil.color("&e/lah my &7- View your listings"));
        player.sendMessage(ItemUtil.color("&e/lah expired &7- Claim expired listings"));
        player.sendMessage(ItemUtil.color("&e/lah page <number> &7- Open a specific page"));
        player.sendMessage(ItemUtil.color("&e/lah reload &7- Reload settings (admin)"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("sell", "buy", "cancel", "my", "expired", "page", "help", "reload").stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("buy") || args[0].equalsIgnoreCase("cancel"))
                && sender instanceof Player player) {
            return house.getListings().stream()
                    .filter(listing -> args[0].equalsIgnoreCase("buy") || listing.getSeller().equals(player.getUniqueId()))
                    .map(AuctionHouse.Listing::getId)
                    .filter(id -> id.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}