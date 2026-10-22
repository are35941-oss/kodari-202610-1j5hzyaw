package com.kodari.lshop;

import com.kodari.lshop.auction.AuctionCommand;
import com.kodari.lshop.auction.AuctionHouse;
import com.kodari.lshop.menu.MenuManager;
import com.kodari.lshop.shop.SellCommand;
import com.kodari.lshop.shop.ShopCommand;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class LShopPlugin extends JavaPlugin {
    private Economy economy;
    private MenuManager menuManager;
    private AuctionHouse auctionHouse;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        RegisteredServiceProvider<Economy> registration = getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null || registration.getProvider() == null) {
            getLogger().severe("Vault and a Vault-compatible economy provider are required.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        economy = registration.getProvider();
        menuManager = new MenuManager();
        auctionHouse = new AuctionHouse(this);
        Bukkit.getPluginManager().registerEvents(menuManager, this);

        getCommand("lshop").setExecutor(new ShopCommand(this));
        getCommand("lsell").setExecutor(new SellCommand(this));
        AuctionCommand auctionCommand = new AuctionCommand(this);
        getCommand("lah").setExecutor(auctionCommand);
        getCommand("lah").setTabCompleter(auctionCommand);
    }

    public Economy getEconomy() {
        return economy;
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }

    public AuctionHouse getAuctionHouse() {
        return auctionHouse;
    }
}