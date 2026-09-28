/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.plugin.java.JavaPlugin
 */
package net.skstudios.orders;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import net.skstudios.orders.Order;
import net.skstudios.orders.SortMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class OrderStore {
    private final JavaPlugin plugin;
    private final File dataFile;
    private final Map<Long, Order> byId = new HashMap<Long, Order>();
    private final Map<UUID, Integer> countByOwner = new HashMap<UUID, Integer>();
    private final Map<SortMode, List<Order>> sorted = new EnumMap<SortMode, List<Order>>(SortMode.class);
    private long nextId = 1L;
    private long version;
    private boolean dirty;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "SKOrders-Save"));

    public OrderStore(JavaPlugin javaPlugin) {
        this.plugin = javaPlugin;
        this.dataFile = new File(javaPlugin.getDataFolder(), "data/orders.yml");
    }

    public List<Order> view(SortMode sortMode) {
        List<Order> list = this.sorted.get((Object)sortMode);
        if (list != null) {
            return list;
        }
        ArrayList<Order> arrayList = new ArrayList<Order>(this.byId.values());
        arrayList.sort(sortMode.comparator());
        this.sorted.put(sortMode, arrayList);
        return arrayList;
    }

    public Order byId(long l) {
        return this.byId.get(l);
    }

    public int countOf(UUID uUID) {
        return this.countByOwner.getOrDefault(uUID, 0);
    }

    public List<Order> ordersOf(UUID uUID) {
        ArrayList<Order> arrayList = new ArrayList<Order>();
        for (Order order : this.byId.values()) {
            if (!order.owner().equals(uUID)) continue;
            arrayList.add(order);
        }
        arrayList.sort(SortMode.RECENT.comparator());
        return arrayList;
    }

    public Collection<Order> all() {
        return this.byId.values();
    }

    public int size() {
        return this.byId.size();
    }

    public long version() {
        return this.version;
    }

    public Order add(UUID uUID, String string, ItemStack itemStack, int n, double d) {
        Order order = new Order(this.nextId++, uUID, string, itemStack, n, d, System.currentTimeMillis());
        this.byId.put(order.id(), order);
        this.countByOwner.merge(uUID, 1, Integer::sum);
        this.invalidate();
        return order;
    }

    public Order remove(long l) {
        Order order = this.byId.remove(l);
        if (order == null) {
            return null;
        }
        this.countByOwner.computeIfPresent(order.owner(), (uUID, n) -> n <= 1 ? null : Integer.valueOf(n - 1));
        this.invalidate();
        return order;
    }

    public void touch() {
        this.invalidate();
    }

    private void invalidate() {
        this.sorted.clear();
        ++this.version;
        this.dirty = true;
    }

    public void load() {
        this.byId.clear();
        this.countByOwner.clear();
        this.sorted.clear();
        if (!this.dataFile.exists()) {
            return;
        }
        YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration((File)this.dataFile);
        this.nextId = Math.max(1L, yamlConfiguration.getLong("next-id", 1L));
        int n = 0;
        ConfigurationSection configurationSection = yamlConfiguration.getConfigurationSection("orders");
        if (configurationSection != null) {
            for (String string : configurationSection.getKeys(false)) {
                ConfigurationSection configurationSection2 = configurationSection.getConfigurationSection(string);
                if (configurationSection2 == null) continue;
                try {
                    ItemStack itemStack = ItemStack.deserializeBytes((byte[])Base64.getDecoder().decode(configurationSection2.getString("item", "")));
                    Order order = new Order(Long.parseLong(string), UUID.fromString(configurationSection2.getString("owner", "")), configurationSection2.getString("owner-name", "?"), itemStack, configurationSection2.getInt("amount"), configurationSection2.getDouble("price"), configurationSection2.getLong("created-at"), configurationSection2.getInt("delivered"), configurationSection2.getInt("collectable"), configurationSection2.getDouble("escrow"));
                    this.byId.put(order.id(), order);
                    this.countByOwner.merge(order.owner(), 1, Integer::sum);
                    this.nextId = Math.max(this.nextId, order.id() + 1L);
                }
                catch (Exception exception) {
                    ++n;
                }
            }
        }
        ++this.version;
        this.plugin.getLogger().info("Loaded " + this.byId.size() + " orders" + (String)(n > 0 ? " (" + n + " unreadable and skipped)" : "") + ".");
    }

    public void save(boolean bl, boolean bl2) {
        if (!bl && !this.dirty) {
            return;
        }
        YamlConfiguration yamlConfiguration = new YamlConfiguration();
        yamlConfiguration.set("next-id", (Object)this.nextId);
        for (Order order : this.byId.values()) {
            String string = "orders." + order.id();
            yamlConfiguration.set(string + ".item", (Object)Base64.getEncoder().encodeToString(order.item().serializeAsBytes()));
            yamlConfiguration.set(string + ".owner", (Object)order.owner().toString());
            yamlConfiguration.set(string + ".owner-name", (Object)order.ownerName());
            yamlConfiguration.set(string + ".amount", (Object)order.amount());
            yamlConfiguration.set(string + ".price", (Object)order.price());
            yamlConfiguration.set(string + ".delivered", (Object)order.delivered());
            yamlConfiguration.set(string + ".collectable", (Object)order.collectable());
            yamlConfiguration.set(string + ".escrow", (Object)order.escrow());
            yamlConfiguration.set(string + ".created-at", (Object)order.createdAt());
        }
        this.dirty = false;
        String string = yamlConfiguration.saveToString();
        if (bl2) {
            this.writeToDisk(string);
        } else {
            this.writer.execute(() -> this.writeToDisk(string));
        }
    }

    public void shutdown() {
        this.save(true, true);
        this.writer.shutdown();
        try {
            if (!this.writer.awaitTermination(10L, TimeUnit.SECONDS)) {
                this.plugin.getLogger().warning("Order save did not finish within 10s.");
            }
        }
        catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private void writeToDisk(String string) {
        try {
            this.dataFile.getParentFile().mkdirs();
            File file = new File(this.dataFile.getParentFile(), this.dataFile.getName() + ".tmp");
            Files.writeString(file.toPath(), (CharSequence)string, new OpenOption[0]);
            Files.move(file.toPath(), this.dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (IOException iOException) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not save orders", iOException);
        }
    }
}

