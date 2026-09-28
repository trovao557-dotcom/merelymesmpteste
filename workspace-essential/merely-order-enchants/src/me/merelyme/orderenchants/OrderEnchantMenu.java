package me.merelyme.orderenchants;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class OrderEnchantMenu implements InventoryHolder, Listener {
    private static final String TITLE = "§b§lEscolhe encantamento";
    private final MerelyOrderEnchants plugin;
    private final Player player;
    private final Inventory inventory;
    private boolean registered;

    OrderEnchantMenu(MerelyOrderEnchants plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, 54, TITLE);
        draw();
    }

    void open() {
        if (!this.registered) {
            Bukkit.getPluginManager().registerEvents(this, this.plugin);
            this.registered = true;
        }
        this.player.openInventory(this.inventory);
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    private void draw() {
        this.inventory.clear();
        for (EnchantOption option : EnchantOption.ALL) {
            this.inventory.setItem(option.slot(), option.icon(this.plugin));
        }
        this.inventory.setItem(45, button(Material.BARRIER, "§e§lVoltar", "§7Fecha sem escolher"));
        this.inventory.setItem(49, button(Material.ENCHANTED_BOOK, "§e§lLivro genérico", "§7Qualquer enchant book"));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player clicker) || !clicker.getUniqueId().equals(this.player.getUniqueId())) {
            return;
        }
        if (event.getView().getTopInventory().getHolder() != this) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() >= this.inventory.getSize()) {
            return;
        }

        if (event.getRawSlot() == 45) {
            close();
            return;
        }
        if (event.getRawSlot() == 49) {
            this.plugin.clearPending(clicker);
            try {
                Object ordersPlugin = Bukkit.getPluginManager().getPlugin("SKOrders");
                Object state = ordersPlugin.getClass().getMethod("state", Player.class).invoke(ordersPlugin, clicker);
                state.getClass().getMethod("setDraftItem", Material.class).invoke(state, Material.ENCHANTED_BOOK);
            } catch (ReflectiveOperationException ignored) {
            }
            close();
            OrderSelectListener.openNewOrderForm(this.plugin, clicker, new ItemStack(Material.ENCHANTED_BOOK));
            return;
        }

        ItemStack clicked = this.inventory.getItem(event.getRawSlot());
        if (clicked == null) {
            return;
        }
        String key = clicked.getPersistentDataContainer().get(enchantKey(this.plugin), PersistentDataType.STRING);
        if (key == null) {
            return;
        }
        ItemStack book = enchantBook(key);
        close();
        OrderSelectListener.openNewOrderForm(this.plugin, clicker, book);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() != this) {
            return;
        }
        shutdown();
    }

    private void close() {
        this.player.closeInventory();
    }

    private void shutdown() {
        if (this.registered) {
            HandlerList.unregisterAll(this);
            this.registered = false;
        }
    }

    private static NamespacedKey enchantKey(MerelyOrderEnchants plugin) {
        return new NamespacedKey(plugin, "enchant");
    }

    private static ItemStack enchantBook(String key) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(key));
        if (enchantment != null) {
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
            meta.addStoredEnchant(enchantment, Math.max(1, enchantment.getMaxLevel()), true);
            meta.setDisplayName("§b§l" + pretty(key));
            book.setItemMeta(meta);
        }
        return book;
    }

    private static ItemStack button(Material material, String name, String line) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(line));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String pretty(String key) {
        return key.replace('_', ' ');
    }

    private record EnchantOption(String key, String label, int slot) {
        private static final List<EnchantOption> ALL = List.of(
                new EnchantOption("sharpness", "Sharpness", 10),
                new EnchantOption("smite", "Smite", 11),
                new EnchantOption("bane_of_arthropods", "Bane of Arthropods", 12),
                new EnchantOption("looting", "Looting", 13),
                new EnchantOption("fire_aspect", "Fire Aspect", 14),
                new EnchantOption("sweeping_edge", "Sweeping Edge", 15),
                new EnchantOption("efficiency", "Efficiency", 19),
                new EnchantOption("fortune", "Fortune", 20),
                new EnchantOption("silk_touch", "Silk Touch", 21),
                new EnchantOption("unbreaking", "Unbreaking", 22),
                new EnchantOption("mending", "Mending", 23),
                new EnchantOption("protection", "Protection", 28),
                new EnchantOption("fire_protection", "Fire Protection", 29),
                new EnchantOption("blast_protection", "Blast Protection", 30),
                new EnchantOption("projectile_protection", "Projectile Protection", 31),
                new EnchantOption("feather_falling", "Feather Falling", 32),
                new EnchantOption("respiration", "Respiration", 33),
                new EnchantOption("depth_strider", "Depth Strider", 34),
                new EnchantOption("power", "Power", 37),
                new EnchantOption("punch", "Punch", 38),
                new EnchantOption("flame", "Flame", 39),
                new EnchantOption("infinity", "Infinity", 40),
                new EnchantOption("loyalty", "Loyalty", 41),
                new EnchantOption("channeling", "Channeling", 42),
                new EnchantOption("impaling", "Impaling", 43),
                new EnchantOption("riptide", "Riptide", 44)
        );

        private ItemStack icon(MerelyOrderEnchants plugin) {
            ItemStack book = enchantBook(this.key);
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add("§7Order: " + this.label);
            lore.add("");
            lore.add("§6▶ Clica para escolher");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(enchantKey(plugin), PersistentDataType.STRING, this.key);
            book.setItemMeta(meta);
            return book;
        }
    }
}
