package me.merelyme.orderenchants;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class MerelyOrderEnchants extends org.bukkit.plugin.java.JavaPlugin {
    private static MerelyOrderEnchants instance;
    private final Map<UUID, ItemStack> pendingBooks = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        if (getServer().getPluginManager().getPlugin("SKOrders") == null) {
            getLogger().warning("SKOrders not found.");
            return;
        }
        getServer().getPluginManager().registerEvents(new OrderSelectListener(this), this);
        getServer().getPluginManager().registerEvents(new OrderCreateListener(this), this);
        getLogger().info("Order enchant picker enabled.");
    }

    @Override
    public void onDisable() {
        pendingBooks.clear();
        instance = null;
    }

    public static MerelyOrderEnchants get() {
        return instance;
    }

    public void setPendingBook(Player player, ItemStack book) {
        pendingBooks.put(player.getUniqueId(), book.clone());
    }

    public ItemStack takePendingBook(Player player) {
        return pendingBooks.remove(player.getUniqueId());
    }

    public ItemStack peekPendingBook(Player player) {
        ItemStack stack = pendingBooks.get(player.getUniqueId());
        return stack == null ? null : stack.clone();
    }

    public void clearPending(Player player) {
        pendingBooks.remove(player.getUniqueId());
    }

    /** Called from patched SKOrders or post-create fix. */
    public ItemStack draftItemStack(Player player, Material material) {
        if (material != Material.ENCHANTED_BOOK) {
            return new ItemStack(material);
        }
        ItemStack pending = peekPendingBook(player);
        return pending != null ? pending.clone() : new ItemStack(material);
    }
}
