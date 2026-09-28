/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.skstudios.core.SKPlugin
 *  net.skstudios.core.config.ConfigFile
 *  net.skstudios.core.data.PlayerStore
 *  net.skstudios.core.economy.EconomyHook
 *  net.skstudios.core.gui.Menu
 *  net.skstudios.core.text.Text
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.entity.Player
 *  org.bukkit.event.Listener
 *  org.bukkit.inventory.InventoryHolder
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 *  org.bukkit.scheduler.BukkitTask
 */
package net.skstudios.orders;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.skstudios.core.SKPlugin;
import net.skstudios.core.config.ConfigFile;
import net.skstudios.core.data.PlayerStore;
import net.skstudios.core.economy.EconomyHook;
import net.skstudios.core.gui.Menu;
import net.skstudios.core.text.Text;
import net.skstudios.orders.Categories;
import net.skstudios.orders.ChatPrompt;
import net.skstudios.orders.Decoration;
import net.skstudios.orders.ItemCatalogue;
import net.skstudios.orders.MenuFile;
import net.skstudios.orders.Order;
import net.skstudios.orders.OrderStore;
import net.skstudios.orders.OrdersCommand;
import net.skstudios.orders.OrdersMenu;
import net.skstudios.orders.SortMode;
import net.skstudios.orders.Sounds;
import net.skstudios.orders.ViewState;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class OrdersPlugin
extends SKPlugin {
    private OrderStore store;
    private Categories categories;
    private ItemCatalogue catalogue;
    private Sounds sounds;
    private ChatPrompt prompt;
    private MenuFile overviewMenu;
    private MenuFile yourOrdersMenu;
    private MenuFile newOrderMenu;
    private MenuFile selectItemMenu;
    private MenuFile deliverMenu;
    private final Map<UUID, ViewState> states = new HashMap<UUID, ViewState>();
    private BukkitTask saveTask;
    private boolean shortMoney;
    private String optionSelected;
    private String optionUnselected;
    private int maxOrdersPerPlayer;
    private int minAmount;
    private int maxAmount;
    private double minPrice;
    private double maxPrice;
    private double maxTotalCost;
    private long creationCooldownMillis;
    private boolean allowDecimalPrice;
    private boolean broadcastNewOrders;
    private Material defaultItem;
    private int defaultAmount;
    private double defaultPrice;
    private Set<Material> blacklist;
    private boolean hideFullOrders;
    private boolean searchIncludesOwner;
    private boolean showRemainingAsStackSize;
    private boolean cancelRequiresShift;
    private boolean submitOnClose;
    private boolean allowOwnOrders;
    private static final DecimalFormatSymbols MONEY_SYMBOLS = DecimalFormatSymbols.getInstance(Locale.ROOT);

    private static DecimalFormat plainMoney() {
        return new DecimalFormat("#,##0", MONEY_SYMBOLS);
    }

    protected void start() {
        this.readSettings();
        this.loadMenus();
        this.store = new OrderStore((JavaPlugin)this);
        this.store.load();
        int n = Math.max(30, this.config().yaml().getInt("data.save-interval-seconds", 300));
        this.saveTask = Bukkit.getScheduler().runTaskTimer((Plugin)this, () -> this.store.save(false, false), (long)n * 20L, (long)n * 20L);
        this.prompt = new ChatPrompt((JavaPlugin)this, this.states::remove);
        this.prompt.configure(this.config().yaml().getStringList("input.cancel-words"), this.config().yaml().getInt("input.timeout-seconds", 60));
        Bukkit.getPluginManager().registerEvents((Listener)this.prompt, (Plugin)this);
        OrdersCommand ordersCommand = new OrdersCommand(this);
        if (this.getCommand("orders") != null) {
            this.getCommand("orders").setExecutor((CommandExecutor)ordersCommand);
            this.getCommand("orders").setTabCompleter((TabCompleter)ordersCommand);
        }
        this.getLogger().info("Orders ready: " + this.store.size() + " open, " + this.catalogue.size() + " orderable items, " + this.categories.real().size() + " categories.");
    }

    protected void stop() {
        this.closeOpenMenus();
        if (this.saveTask != null) {
            this.saveTask.cancel();
            this.saveTask = null;
        }
        if (this.store != null) {
            this.store.shutdown();
        }
        this.states.clear();
    }

    public void reload() {
        this.config().load(true);
        this.readSettings();
        this.loadMenus();
        this.prompt.configure(this.config().yaml().getStringList("input.cancel-words"), this.config().yaml().getInt("input.timeout-seconds", 60));
        this.closeOpenMenus();
        this.states.clear();
    }

    private void loadMenus() {
        this.overviewMenu = new MenuFile((JavaPlugin)this, "menu-orders.yml");
        this.yourOrdersMenu = new MenuFile((JavaPlugin)this, "menu-your-orders.yml");
        this.newOrderMenu = new MenuFile((JavaPlugin)this, "menu-new-order.yml");
        this.selectItemMenu = new MenuFile((JavaPlugin)this, "menu-select-item.yml");
        this.deliverMenu = new MenuFile((JavaPlugin)this, "menu-deliver.yml");
        this.categories = new Categories((ConfigurationSection)this.config().yaml(), this.getLogger());
        this.catalogue = new ItemCatalogue(this.categories, this.blacklist, this.nameStyle());
        this.sounds = new Sounds(this.config().yaml().getConfigurationSection("sounds"));
    }

    private void closeOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Menu menu;
            InventoryHolder inventoryHolder = player.getOpenInventory().getTopInventory().getHolder();
            if (!(inventoryHolder instanceof Menu) || !((menu = (Menu)inventoryHolder) instanceof OrdersOwned)) continue;
            player.closeInventory();
        }
    }

    private void readSettings() {
        YamlConfiguration yamlConfiguration = this.config().yaml();
        this.shortMoney = yamlConfiguration.getBoolean("display.short-money", true);
        this.optionSelected = yamlConfiguration.getString("display.option-selected", "&#00FFD6\u25b6 %name%");
        this.optionUnselected = yamlConfiguration.getString("display.option-unselected", "&8\u25b6 &f%name%");
        Decoration.configure(yamlConfiguration.getStringList("delivery.ignore-decoration-from"));
        this.maxOrdersPerPlayer = yamlConfiguration.getInt("limits.max-orders-per-player", 5);
        this.minAmount = Math.max(1, yamlConfiguration.getInt("limits.min-amount", 1));
        this.maxAmount = Math.max(this.minAmount, yamlConfiguration.getInt("limits.max-amount", 100000));
        this.minPrice = Math.max(0.0, yamlConfiguration.getDouble("limits.min-price", 1.0));
        this.maxPrice = Math.max(this.minPrice, yamlConfiguration.getDouble("limits.max-price", 1000000.0));
        this.maxTotalCost = yamlConfiguration.getDouble("limits.max-total-cost", 0.0);
        this.creationCooldownMillis = (long)Math.max(0, yamlConfiguration.getInt("creation.cooldown-seconds", 0)) * 1000L;
        this.allowDecimalPrice = yamlConfiguration.getBoolean("creation.allow-decimal-price", false);
        this.broadcastNewOrders = yamlConfiguration.getBoolean("creation.broadcast", true);
        this.defaultItem = this.material(yamlConfiguration.getString("creation.default-item", "STONE"), Material.STONE);
        this.defaultAmount = Math.max(1, yamlConfiguration.getInt("creation.default-amount", 1));
        this.defaultPrice = Math.max(0.0, yamlConfiguration.getDouble("creation.default-price", 0.0));
        this.blacklist = EnumSet.noneOf(Material.class);
        for (String string : yamlConfiguration.getStringList("creation.blacklist")) {
            Material material = Material.matchMaterial((String)string.toUpperCase(Locale.ROOT));
            if (material != null) {
                this.blacklist.add(material);
                continue;
            }
            String string2 = string.toLowerCase(Locale.ROOT).replace('_', ' ').trim();
            if (string2.isEmpty()) continue;
            int n = 0;
            for (Material material2 : Material.values()) {
                if (material2.isLegacy() || !material2.name().toLowerCase(Locale.ROOT).replace('_', ' ').contains(string2)) continue;
                this.blacklist.add(material2);
                ++n;
            }
            if (n != 0) continue;
            this.getLogger().warning("creation.blacklist entry '" + string + "' is neither a material nor part of any material name - ignored.");
        }
        this.hideFullOrders = yamlConfiguration.getBoolean("overview.hide-full-orders", true);
        this.searchIncludesOwner = yamlConfiguration.getBoolean("overview.search-includes-owner", true);
        this.showRemainingAsStackSize = yamlConfiguration.getBoolean("overview.show-remaining-as-stack-size", true);
        this.cancelRequiresShift = yamlConfiguration.getBoolean("your-orders.cancel-requires-shift", true);
        this.submitOnClose = yamlConfiguration.getBoolean("delivery.submit-on-close", true);
        this.allowOwnOrders = yamlConfiguration.getBoolean("delivery.allow-own-orders", false);
    }

    private ItemCatalogue.NameStyle nameStyle() {
        String string = this.config().yaml().getString("display.item-name-style", "TITLE");
        try {
            return ItemCatalogue.NameStyle.valueOf(string.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException illegalArgumentException) {
            this.getLogger().warning("Unknown display.item-name-style '" + string + "' - using TITLE. Valid: title, lower, upper, raw.");
            return ItemCatalogue.NameStyle.TITLE;
        }
    }

    private Material material(String string, Material material) {
        Material material2 = Material.matchMaterial((String)(string == null ? "" : string.toUpperCase(Locale.ROOT)));
        return material2 == null ? material : material2;
    }

    public ConfigFile config() {
        return super.config();
    }

    public EconomyHook economy() {
        return super.economy();
    }

    public PlayerStore players() {
        return super.players();
    }

    public void message(CommandSender commandSender, String string, Map<String, String> map) {
        super.message(commandSender, string, map);
    }

    public void message(CommandSender commandSender, String string) {
        super.message(commandSender, string);
    }

    public OrderStore store() {
        return this.store;
    }

    public Categories categories() {
        return this.categories;
    }

    public ItemCatalogue catalogue() {
        return this.catalogue;
    }

    public Sounds sounds() {
        return this.sounds;
    }

    public ChatPrompt prompt() {
        return this.prompt;
    }

    public MenuFile overviewMenuFile() {
        return this.overviewMenu;
    }

    public MenuFile yourOrdersMenuFile() {
        return this.yourOrdersMenu;
    }

    public MenuFile newOrderMenuFile() {
        return this.newOrderMenu;
    }

    public MenuFile selectItemMenuFile() {
        return this.selectItemMenu;
    }

    public MenuFile deliverMenuFile() {
        return this.deliverMenu;
    }

    public boolean hideFullOrders() {
        return this.hideFullOrders;
    }

    public boolean searchIncludesOwner() {
        return this.searchIncludesOwner;
    }

    public boolean showRemainingAsStackSize() {
        return this.showRemainingAsStackSize;
    }

    public boolean cancelRequiresShift() {
        return this.cancelRequiresShift;
    }

    public boolean submitOnClose() {
        return this.submitOnClose;
    }

    public boolean allowOwnOrders() {
        return this.allowOwnOrders;
    }

    public int minAmount() {
        return this.minAmount;
    }

    public int maxAmount() {
        return this.maxAmount;
    }

    public double minPrice() {
        return this.minPrice;
    }

    public double maxPrice() {
        return this.maxPrice;
    }

    public boolean allowDecimalPrice() {
        return this.allowDecimalPrice;
    }

    public ViewState state(Player player) {
        return this.states.computeIfAbsent(player.getUniqueId(), uUID -> new ViewState(this.categories.all(), this.defaultItem, this.defaultAmount, this.defaultPrice));
    }

    public void resetDraft(ViewState viewState) {
        viewState.resetDraft(this.defaultItem, this.defaultAmount, this.defaultPrice);
    }

    public String money(double d) {
        return this.shortMoney ? EconomyHook.format((double)d) : OrdersPlugin.plainMoney().format(d);
    }

    public String itemName(Material material) {
        return this.catalogue.displayName(material);
    }

    public String itemName(ItemStack itemStack) {
        return this.itemName(itemStack.getType());
    }

    public Map<String, String> placeholders(Order order) {
        HashMap<String, String> hashMap = new HashMap<String, String>();
        hashMap.put("id", String.valueOf(order.id()));
        hashMap.put("owner", order.ownerName());
        hashMap.put("item", this.itemName(order.item()));
        hashMap.put("amount", String.valueOf(order.amount()));
        hashMap.put("delivered", String.valueOf(order.delivered()));
        hashMap.put("remaining", String.valueOf(order.remaining()));
        hashMap.put("collectable", String.valueOf(order.collectable()));
        hashMap.put("progress", order.delivered() + "/" + order.amount());
        hashMap.put("price", this.money(order.price()));
        hashMap.put("total", this.money(order.totalValue()));
        hashMap.put("escrow", this.money(order.escrow()));
        return hashMap;
    }

    public String option(String string, boolean bl) {
        return Text.apply((String)(bl ? this.optionSelected : this.optionUnselected), Map.of("name", string));
    }

    public void addSortPlaceholders(Map<String, String> map, SortMode sortMode) {
        for (SortMode sortMode2 : SortMode.values()) {
            String string = this.config().yaml().getString("display.sort-names." + sortMode2.id(), sortMode2.id());
            map.put("sort_" + sortMode2.id(), this.option(string, sortMode2 == sortMode));
        }
        map.put("sort", this.config().yaml().getString("display.sort-names." + sortMode.id(), sortMode.id()));
    }

    public void addCategoryPlaceholders(Map<String, String> map, Categories.Category category) {
        for (Categories.Category category2 : this.categories.cycle()) {
            map.put("filter_" + category2.id(), this.option(category2.display(), category2 == category));
        }
        map.put("filter", category.display());
    }

    public String searchOrEmpty(String string) {
        return string == null || string.isEmpty() ? this.config().yaml().getString("display.search-empty", "none") : string;
    }

    public void openOverview(Player player) {
        new OrdersMenu(player, this).open();
        this.sounds.play(player, "menu-open");
    }

    public void ask(Player player, String string, Map<String, String> map, Consumer<String> consumer) {
        player.closeInventory();
        this.message((CommandSender)player, string, map);
        this.sounds.play(player, "prompt");
        this.prompt.ask(player, consumer);
    }

    public Order createOrder(Player player, Material material, int n, double d) {
        if (!player.hasPermission("skorders.create")) {
            this.message((CommandSender)player, "no-permission");
            this.sounds.play(player, "error");
            return null;
        }
        if (this.blacklist.contains(material)) {
            this.message((CommandSender)player, "order.blacklisted", Map.of("item", this.itemName(material)));
            this.sounds.play(player, "error");
            return null;
        }
        if (n < this.minAmount) {
            this.message((CommandSender)player, "order.amount-too-low", Map.of("min", String.valueOf(this.minAmount)));
            this.sounds.play(player, "error");
            return null;
        }
        if (n > this.maxAmount) {
            this.message((CommandSender)player, "order.amount-too-high", Map.of("max", String.valueOf(this.maxAmount)));
            this.sounds.play(player, "error");
            return null;
        }
        if (d < this.minPrice) {
            this.message((CommandSender)player, "order.price-too-low", Map.of("min", this.money(this.minPrice)));
            this.sounds.play(player, "error");
            return null;
        }
        if (d > this.maxPrice) {
            this.message((CommandSender)player, "order.price-too-high", Map.of("max", this.money(this.maxPrice)));
            this.sounds.play(player, "error");
            return null;
        }
        double d2 = (double)n * d;
        if (this.maxTotalCost > 0.0 && d2 > this.maxTotalCost) {
            this.message((CommandSender)player, "order.total-too-high", Map.of("max", this.money(this.maxTotalCost)));
            this.sounds.play(player, "error");
            return null;
        }
        ViewState viewState = this.state(player);
        boolean bl = player.hasPermission("skorders.bypass-limit");
        if (!bl && this.maxOrdersPerPlayer > 0 && this.store.countOf(player.getUniqueId()) >= this.maxOrdersPerPlayer) {
            this.message((CommandSender)player, "order.limit-reached", Map.of("limit", String.valueOf(this.maxOrdersPerPlayer)));
            this.sounds.play(player, "error");
            return null;
        }
        long l = System.currentTimeMillis() - viewState.lastCreated();
        if (!bl && this.creationCooldownMillis > 0L && l < this.creationCooldownMillis) {
            this.message((CommandSender)player, "order.cooldown", Map.of("seconds", String.valueOf((this.creationCooldownMillis - l + 999L) / 1000L)));
            this.sounds.play(player, "error");
            return null;
        }
        if (!this.economy().available()) {
            this.message((CommandSender)player, "economy-missing");
            this.sounds.play(player, "error");
            return null;
        }
        if (!this.economy().withdraw((OfflinePlayer)player, d2)) {
            this.message((CommandSender)player, "order.not-enough-money", Map.of("total", this.money(d2), "balance", this.money(this.economy().balance((OfflinePlayer)player))));
            this.sounds.play(player, "error");
            return null;
        }
        Order order = this.store.add(player.getUniqueId(), player.getName(), new ItemStack(material), n, d);
        viewState.markCreated();
        this.store.save(false, false);
        Map<String, String> map = this.placeholders(order);
        this.message((CommandSender)player, "order.created", map);
        this.sounds.play(player, "create");
        if (this.broadcastNewOrders) {
            map.put("player", player.getName());
            for (Player player2 : Bukkit.getOnlinePlayers()) {
                if (player2.equals((Object)player) || !player2.hasPermission("skorders.broadcast")) continue;
                this.message((CommandSender)player2, "order.broadcast", map);
            }
        }
        return order;
    }

    public void cancelOrder(Player player, Order order) {
        double d;
        Order order2 = this.store.remove(order.id());
        if (order2 == null) {
            this.message((CommandSender)player, "order.gone");
            this.sounds.play(player, "error");
            return;
        }
        int n = order2.takeCollectable(Integer.MAX_VALUE);
        if (n > 0) {
            this.giveOrDrop(player, order2.item(), n);
        }
        if ((d = order2.refund()) > 0.0) {
            this.economy().deposit((OfflinePlayer)player, d);
        }
        this.store.save(false, false);
        Map<String, String> map = this.placeholders(order2);
        map.put("refund", this.money(d));
        map.put("returned", String.valueOf(n));
        this.message((CommandSender)player, "order.cancelled", map);
        this.sounds.play(player, "cancel");
    }

    public void collect(Player player, Order order) {
        if (order.collectable() <= 0) {
            this.message((CommandSender)player, "order.nothing-to-collect");
            this.sounds.play(player, "error");
            return;
        }
        int n = this.freeSpaceFor(player, order.item());
        int n2 = order.takeCollectable(n);
        if (n2 <= 0) {
            this.message((CommandSender)player, "order.inventory-full");
            this.sounds.play(player, "error");
            return;
        }
        this.giveOrDrop(player, order.item(), n2);
        this.store.touch();
        Map<String, String> map = this.placeholders(order);
        map.put("collected", String.valueOf(n2));
        this.message((CommandSender)player, "order.collected", map);
        this.sounds.play(player, "collect");
        if (order.finished()) {
            this.store.remove(order.id());
            this.message((CommandSender)player, "order.completed", map);
        }
        this.store.save(false, false);
    }

    public Settlement deliver(Player player, Order order, List<ItemStack> list) {
        ArrayList<ItemStack> arrayList = new ArrayList<ItemStack>();
        int n = 0;
        double d = 0.0;
        for (ItemStack itemStack : list) {
            if (itemStack == null || itemStack.getType().isAir()) continue;
            if (order == null || !order.matches(itemStack)) {
                arrayList.add(itemStack);
                continue;
            }
            Order.Delivery delivery = order.book(itemStack.getAmount());
            if (delivery.isEmpty()) {
                arrayList.add(itemStack);
                continue;
            }
            if (delivery.pay() > 0.0 && !this.economy().deposit((OfflinePlayer)player, delivery.pay())) {
                order.rollback(delivery);
                arrayList.add(itemStack);
                continue;
            }
            n += delivery.count();
            d += delivery.pay();
            int n2 = itemStack.getAmount() - delivery.count();
            if (n2 <= 0) continue;
            ItemStack itemStack2 = itemStack.clone();
            itemStack2.setAmount(n2);
            arrayList.add(itemStack2);
        }
        if (n > 0) {
            this.store.touch();
            this.store.save(false, false);
            Map<String, String> map = this.placeholders(order);
            map.put("count", String.valueOf(n));
            map.put("pay", this.money(d));
            this.message((CommandSender)player, "delivery.delivered", map);
            this.sounds.play(player, "deliver");
            this.notifyOwner(order, player, n, d);
        }
        return new Settlement(n, d, arrayList);
    }

    public void notifyOwner(Order order, Player player, int n, double d) {
        Player player2 = Bukkit.getPlayer((UUID)order.owner());
        if (player2 == null || player2.equals((Object)player)) {
            return;
        }
        Map<String, String> map = this.placeholders(order);
        map.put("player", player.getName());
        map.put("count", String.valueOf(n));
        map.put("pay", this.money(d));
        this.message((CommandSender)player2, "delivery.owner-notified", map);
        this.sounds.play(player2, "notify");
    }

    public boolean forceRemove(CommandSender commandSender, Order order) {
        Object object;
        double d;
        Player player = Bukkit.getPlayer((UUID)order.owner());
        if (order.collectable() > 0 && player == null) {
            this.message(commandSender, "admin.owner-offline", this.placeholders(order));
            return false;
        }
        Order order2 = this.store.remove(order.id());
        if (order2 == null) {
            this.message(commandSender, "order.gone");
            return false;
        }
        int n = order2.takeCollectable(Integer.MAX_VALUE);
        if (n > 0) {
            this.giveOrDrop(player, order2.item(), n);
        }
        if ((d = order2.refund()) > 0.0) {
            object = player != null ? player : Bukkit.getOfflinePlayer((UUID)order2.owner());
            this.economy().deposit((OfflinePlayer)object, d);
        }
        this.store.save(false, false);
        object = this.placeholders(order2);
        object.put("refund", this.money(d));
        object.put("returned", String.valueOf(n));
        this.message(commandSender, "admin.removed", (Map<String, String>)object);
        if (player != null && !player.equals((Object)commandSender)) {
            this.message((CommandSender)player, "order.cancelled", (Map<String, String>)object);
        }
        return true;
    }

    public int freeSpaceFor(Player player, ItemStack itemStack) {
        int n = Math.max(1, itemStack.getMaxStackSize());
        int n2 = 0;
        for (ItemStack itemStack2 : player.getInventory().getStorageContents()) {
            if (itemStack2 == null || itemStack2.getType().isAir()) {
                n2 += n;
                continue;
            }
            if (!itemStack2.isSimilar(itemStack) || itemStack2.getAmount() >= n) continue;
            n2 += n - itemStack2.getAmount();
        }
        return n2;
    }

    public void giveOrDrop(Player player, ItemStack itemStack, int n) {
        ItemStack itemStack2;
        int n2 = Math.max(1, itemStack.getMaxStackSize());
        for (int i = n; i > 0; i -= itemStack2.getAmount()) {
            itemStack2 = itemStack.clone();
            itemStack2.setAmount(Math.min(i, n2));
            this.giveOrDrop(player, itemStack2);
        }
    }

    public void giveOrDrop(Player player, ItemStack itemStack) {
        for (ItemStack itemStack2 : player.getInventory().addItem(new ItemStack[]{itemStack}).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), itemStack2);
        }
    }

    public static interface OrdersOwned {
    }

    public record Settlement(int accepted, double paid, List<ItemStack> leftovers) {
    }
}

