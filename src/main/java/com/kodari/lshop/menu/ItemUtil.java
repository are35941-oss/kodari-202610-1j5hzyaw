package com.kodari.lshop.menu;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class ItemUtil {
    private ItemUtil() {
    }

    public static ItemStack create(String material, int amount, String name, List<String> lore) {
        ItemStack item = XMaterial.matchXMaterial(material).map(XMaterial::parseItem).orElse(null);
        if (item == null) {
            return null;
        }
        item.setAmount(Math.max(1, Math.min(amount, item.getMaxStackSize())));
        ItemMeta meta = item.getItemMeta();
        if (name != null && !name.isEmpty()) {
            meta.setDisplayName(color(name));
        }
        if (lore != null && !lore.isEmpty()) {
            List<String> coloredLore = new ArrayList<>(lore.size());
            for (String line : lore) {
                coloredLore.add(color(line));
            }
            meta.setLore(coloredLore);
        }
        item.setItemMeta(meta);
        return item;
    }

    public static String materialName(ItemStack item) {
        return XMaterial.matchXMaterial(item.getType()).name();
    }

    public static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}