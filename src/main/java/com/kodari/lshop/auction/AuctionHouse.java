package com.kodari.lshop.auction;

import com.kodari.lshop.LShopPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AuctionHouse {
    private final File file;
    private final Map<String, Listing> listings = new LinkedHashMap<>();

    public AuctionHouse(LShopPlugin plugin) {
        file = new File(plugin.getDataFolder(), "auctions.yml");
        load(plugin);
    }

    public List<Listing> getListings() {
        return new ArrayList<>(listings.values());
    }

    public Listing find(String id) {
        if (id == null) {
            return null;
        }
        Listing exact = listings.get(id.toLowerCase());
        if (exact != null) {
            return exact;
        }
        Listing match = null;
        for (Listing listing : listings.values()) {
            if (listing.id.toLowerCase().startsWith(id.toLowerCase())) {
                if (match != null) {
                    return null;
                }
                match = listing;
            }
        }
        return match;
    }

    public boolean create(Listing listing) {
        listings.put(listing.id, listing);
        if (save()) {
            return true;
        }
        listings.remove(listing.id);
        return false;
    }

    public boolean remove(String id) {
        Listing listing = listings.remove(id);
        if (listing == null) {
            return false;
        }
        if (save()) {
            return true;
        }
        listings.put(listing.id, listing);
        return false;
    }

    public boolean restore(Listing listing) {
        listings.put(listing.id, listing);
        if (save()) {
            return true;
        }
        listings.remove(listing.id);
        return false;
    }

    private void load(LShopPlugin plugin) {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = data.getConfigurationSection("listings");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null || entry.getItemStack("item") == null) {
                continue;
            }
            try {
                Listing listing = new Listing(id, UUID.fromString(entry.getString("seller")),
                        entry.getString("seller-name", "Unknown"), entry.getDouble("price"),
                        entry.getItemStack("item"), entry.getLong("created"));
                if (listing.price > 0 && Double.isFinite(listing.price)) {
                    listings.put(id, listing);
                }
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Skipping invalid auction listing " + id + ".");
            }
        }
    }

    private boolean save() {
        YamlConfiguration data = new YamlConfiguration();
        for (Listing listing : listings.values()) {
            String path = "listings." + listing.id;
            data.set(path + ".seller", listing.seller.toString());
            data.set(path + ".seller-name", listing.sellerName);
            data.set(path + ".price", listing.price);
            data.set(path + ".item", listing.item);
            data.set(path + ".created", listing.created);
        }
        try {
            data.save(file);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    public static final class Listing {
        private final String id;
        private final UUID seller;
        private final String sellerName;
        private final double price;
        private final ItemStack item;
        private final long created;

        public Listing(String id, UUID seller, String sellerName, double price, ItemStack item, long created) {
            this.id = id;
            this.seller = seller;
            this.sellerName = sellerName;
            this.price = price;
            this.item = item.clone();
            this.created = created;
        }

        public String getId() {
            return id;
        }

        public UUID getSeller() {
            return seller;
        }

        public String getSellerName() {
            return sellerName;
        }

        public double getPrice() {
            return price;
        }

        public ItemStack getItem() {
            return item.clone();
        }

        public long getCreated() {
            return created;
        }
    }
}