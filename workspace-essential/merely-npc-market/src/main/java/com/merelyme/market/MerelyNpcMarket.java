package com.merelyme.market;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.events.NpcInteractEvent;
import net.skstudios.core.SKCorePlugin;
import net.skstudios.core.data.PlayerRecord;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class MerelyNpcMarket extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final String PREFIX = "§b§lPLAYER SHOPS §8» §f";
    private static final List<String> NPC_IDS = List.of("trade1", "trade2", "trade3", "trade4", "trade5", "trade6", "trade7");
    private static final int[] NPC_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] LISTING_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17};
    private static final long MAX_PRICE = 1_000_000_000L;

    private final Map<String, Rental> rentals = new LinkedHashMap<>();
    private final Map<UUID, List<ItemStack>> returns = new HashMap<>();
    private final Map<UUID, PricePrompt> prompts = new HashMap<>();
    private final Map<UUID, PermissionAttachment> voiceBlocks = new HashMap<>();

    private File dataFile;
    private SKCorePlugin core;
    private Object punishmentStore;
    private Method muteUntil;
    private boolean muteHookFailed;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        Plugin corePlugin = getServer().getPluginManager().getPlugin("SKCore");
        if (!(corePlugin instanceof SKCorePlugin skCore)) {
            getLogger().severe("SKCore was not found; disabling player shops.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        core = skCore;
        loadData();
        hookMutes();
        Objects.requireNonNull(getCommand("npcmarket")).setExecutor(this);
        Objects.requireNonNull(getCommand("npcmarket")).setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);

        Bukkit.getScheduler().runTask(this, () -> {
            expireRentals();
            for (String id : NPC_IDS) applyNpcState(id);
        });
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        getLogger().info("Player shops ready: 7 rentable NPCs, 9 listings each, voice mute bridge enabled.");
    }

    @Override
    public void onDisable() {
        for (PermissionAttachment attachment : voiceBlocks.values()) {
            try { attachment.remove(); } catch (Throwable ignored) {}
        }
        voiceBlocks.clear();
        prompts.clear();
        saveData();
    }

    private void tick() {
        expireRentals();
        for (Player player : Bukkit.getOnlinePlayers()) syncVoiceMute(player);
    }

    private void hookMutes() {
        try {
            Plugin mod = getServer().getPluginManager().getPlugin("SKMod");
            if (mod == null) throw new IllegalStateException("SKMod is missing");
            Method storeMethod = mod.getClass().getDeclaredMethod("store");
            storeMethod.setAccessible(true);
            punishmentStore = storeMethod.invoke(mod);
            muteUntil = punishmentStore.getClass().getDeclaredMethod("muteUntil", UUID.class);
            muteUntil.setAccessible(true);
        } catch (Throwable error) {
            muteHookFailed = true;
            getLogger().log(Level.SEVERE, "Could not connect server mutes to voice chat.", error);
        }
    }

    private void syncVoiceMute(Player player) {
        if (muteHookFailed || muteUntil == null || punishmentStore == null) return;
        try {
            long until = ((Number) muteUntil.invoke(punishmentStore, player.getUniqueId())).longValue();
            boolean muted = until != 0L;
            PermissionAttachment current = voiceBlocks.get(player.getUniqueId());
            if (muted && current == null) {
                PermissionAttachment attachment = player.addAttachment(this);
                attachment.setPermission("voicechat.speak", false);
                player.recalculatePermissions();
                voiceBlocks.put(player.getUniqueId(), attachment);
                player.sendMessage("§c§lMUTED §8» §7You cannot speak in voice chat while muted.");
            } else if (!muted && current != null) {
                current.remove();
                player.recalculatePermissions();
                voiceBlocks.remove(player.getUniqueId());
                player.sendMessage("§a§lVOICE §8» §7You can speak in voice chat again.");
            }
        } catch (Throwable error) {
            muteHookFailed = true;
            getLogger().log(Level.SEVERE, "Voice mute synchronisation stopped after an SKMod API error.", error);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) return;
            syncVoiceMute(player);
            deliverReturns(player, false);
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PermissionAttachment attachment = voiceBlocks.remove(event.getPlayer().getUniqueId());
        if (attachment != null) {
            try { attachment.remove(); } catch (Throwable ignored) {}
        }
        PricePrompt prompt = prompts.remove(event.getPlayer().getUniqueId());
        if (prompt != null && !prompt.edit && prompt.preview != null) {
            // The preview was never removed from the inventory, so no item is returned here.
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNpcInteract(NpcInteractEvent event) {
        String id = event.getNpc().getData().getName().toLowerCase(Locale.ROOT);
        if (!NPC_IDS.contains(id)) return;
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(this, () -> openNpc(event.getPlayer(), id));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + "Players use this command in game.");
            return true;
        }
        if (args.length == 0) {
            openDirectory(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("open") && args.length >= 2 && NPC_IDS.contains(args[1].toLowerCase(Locale.ROOT))) {
            openNpc(player, args[1].toLowerCase(Locale.ROOT));
            return true;
        }
        if (args[0].equalsIgnoreCase("manage")) {
            Rental rental = rentalOwnedBy(player.getUniqueId());
            if (rental == null) player.sendMessage(PREFIX + "§cYou do not rent a shop NPC.");
            else openOwner(player, rental);
            return true;
        }
        if (args[0].equalsIgnoreCase("returns")) {
            deliverReturns(player, true);
            return true;
        }
        if (args[0].equalsIgnoreCase("admin") && args.length >= 3 && args[1].equalsIgnoreCase("reset")) {
            if (!player.hasPermission("merelynpcmarket.admin")) {
                player.sendMessage(PREFIX + "§cNo permission.");
                return true;
            }
            String id = args[2].toLowerCase(Locale.ROOT);
            if (!NPC_IDS.contains(id)) {
                player.sendMessage(PREFIX + "§cUse trade1 to trade7.");
                return true;
            }
            release(id, true);
            player.sendMessage(PREFIX + "§a" + id + " was reset and its stock was returned.");
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission("merelynpcmarket.admin")) {
                player.sendMessage(PREFIX + "§cNo permission.");
                return true;
            }
            reloadConfig();
            loadData();
            expireRentals();
            for (String id : NPC_IDS) applyNpcState(id);
            player.sendMessage(PREFIX + "§aConfiguration and shops reloaded.");
            return true;
        }
        player.sendMessage(PREFIX + "§7/npcmarket §8• §7/npcmarket manage §8• §7/npcmarket returns");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>(List.of("manage", "returns", "open"));
            if (sender.hasPermission("merelynpcmarket.admin")) values.addAll(List.of("admin", "reload"));
            return partial(values, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("open")) return partial(NPC_IDS, args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) return partial(List.of("reset"), args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("reset")) return partial(NPC_IDS, args[2]);
        return Collections.emptyList();
    }

    private List<String> partial(Collection<String> values, String input) {
        String needle = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(needle)).toList();
    }

    private void openNpc(Player player, String npcId) {
        Rental rental = currentRental(npcId);
        if (rental == null) openConfirm(player, npcId);
        else if (rental.owner.equals(player.getUniqueId())) openOwner(player, rental);
        else openShop(player, rental);
    }

    private void openDirectory(Player player) {
        DirectoryHolder holder = new DirectoryHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, "§8Player Shops");
        holder.inventory = inv;
        fill(inv);
        for (int i = 0; i < NPC_IDS.size(); i++) {
            String id = NPC_IDS.get(i);
            Rental rental = currentRental(id);
            ItemStack icon = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) icon.getItemMeta();
            if (rental != null) meta.setOwningPlayer(Bukkit.getOfflinePlayer(rental.owner));
            meta.setDisplayName(rental == null ? "§a§l" + id.toUpperCase(Locale.ROOT) + " §8• §fAVAILABLE" : "§b§l" + rental.ownerName + "'S SHOP");
            List<String> lore = new ArrayList<>();
            lore.add("§7NPC §f" + id);
            lore.add("");
            if (rental == null) {
                lore.add("§fRent: §b" + number(rentPrice()) + " Points");
                lore.add("§fDuration: §b" + rentDays() + " days");
                lore.add("");
                lore.add("§e§l▶ CLICK TO RENT");
            } else {
                lore.add("§fOwner: §b" + rental.ownerName);
                lore.add("§fTime left: §b" + remaining(rental.expiresAt));
                lore.add("§fListings: §b" + rental.listingCount() + "§7/§b9");
                lore.add("");
                lore.add(rental.owner.equals(player.getUniqueId()) ? "§e§l▶ CLICK TO MANAGE" : "§e§l▶ CLICK TO SHOP");
            }
            meta.setLore(lore);
            icon.setItemMeta(meta);
            inv.setItem(NPC_SLOTS[i], icon);
        }
        inv.setItem(22, item(Material.CHEST, "§b§lHOW IT WORKS", List.of(
                "§7Rent one NPC for §f" + number(rentPrice()) + " Points§7.",
                "§7The rental lasts §f" + rentDays() + " days§7.",
                "§7List up to §f9 different items§7.",
                "§7Every sale pays Points directly to you.",
                "§7Unsold stock is returned when time ends."
        )));
        player.openInventory(inv);
    }

    private void openConfirm(Player player, String npcId) {
        if (currentRental(npcId) != null) {
            openNpc(player, npcId);
            return;
        }
        ConfirmHolder holder = new ConfirmHolder(npcId);
        Inventory inv = Bukkit.createInventory(holder, 27, "§8Rent " + npcId);
        holder.inventory = inv;
        fill(inv);
        inv.setItem(11, item(Material.LIME_CONCRETE, "§a§lCONFIRM RENTAL", List.of(
                "§7NPC: §f" + npcId,
                "§7Price: §b" + number(rentPrice()) + " Points",
                "§7Duration: §f" + rentDays() + " days",
                "",
                "§aYour skin will appear on this NPC.",
                "§e§l▶ CLICK TO CONFIRM"
        )));
        inv.setItem(15, item(Material.RED_CONCRETE, "§c§lCANCEL", List.of("§7Return to the shop list.")));
        player.openInventory(inv);
    }

    private void openOwner(Player player, Rental rental) {
        if (!isCurrent(rental) || !rental.owner.equals(player.getUniqueId())) {
            openDirectory(player);
            return;
        }
        OwnerHolder holder = new OwnerHolder(rental.npcId);
        Inventory inv = Bukkit.createInventory(holder, 36, "§8Manage " + rental.npcId);
        holder.inventory = inv;
        fill(inv);
        for (int i = 0; i < 9; i++) {
            Listing listing = rental.listings[i];
            if (listing == null) {
                inv.setItem(LISTING_SLOTS[i], item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§7Empty Listing §8#" + (i + 1), List.of("§7Use the Add Listing button below.")));
            } else {
                inv.setItem(LISTING_SLOTS[i], listingIcon(listing, true));
            }
        }
        inv.setItem(27, item(Material.ARROW, "§fBack", List.of("§7Open all player shops.")));
        inv.setItem(30, item(Material.CLOCK, "§b§lYOUR RENTAL", List.of(
                "§7NPC: §f" + rental.npcId,
                "§7Time left: §f" + remaining(rental.expiresAt),
                "§7Listings: §f" + rental.listingCount() + "§7/§f9"
        )));
        inv.setItem(31, item(Material.LIME_DYE, "§a§lADD LISTING", List.of(
                "§7Hold the stock in your main hand,",
                "§7then click this button.",
                "",
                "§eYou will type the price in chat."
        )));
        inv.setItem(32, item(Material.CHEST, "§e§lOWNER CONTROLS", List.of(
                "§7Click a listing with matching items",
                "§7in your hand to add more stock.",
                "§7Click without an item to change price.",
                "§7Shift-right-click to remove the listing."
        )));
        inv.setItem(35, item(Material.BARRIER, "§cClose", List.of()));
        player.openInventory(inv);
    }

    private void openShop(Player player, Rental rental) {
        if (!isCurrent(rental)) {
            openDirectory(player);
            return;
        }
        ShopHolder holder = new ShopHolder(rental.npcId);
        Inventory inv = Bukkit.createInventory(holder, 36, "§8" + rental.ownerName + "'s Shop");
        holder.inventory = inv;
        fill(inv);
        for (int i = 0; i < 9; i++) {
            Listing listing = rental.listings[i];
            if (listing != null && listing.stock > 0) inv.setItem(LISTING_SLOTS[i], listingIcon(listing, false));
        }
        inv.setItem(27, item(Material.ARROW, "§fBack", List.of("§7Open all player shops.")));
        inv.setItem(31, playerHead(rental.owner, rental.ownerName, "§b§l" + rental.ownerName + "'S SHOP", List.of(
                "§7NPC: §f" + rental.npcId,
                "§7Time left: §f" + remaining(rental.expiresAt),
                "§7All prices use §bPoints§7."
        )));
        inv.setItem(35, item(Material.BARRIER, "§cClose", List.of()));
        player.openInventory(inv);
    }

    private ItemStack listingIcon(Listing listing, boolean owner) {
        ItemStack icon = listing.item.clone();
        icon.setAmount(Math.max(1, Math.min(icon.getMaxStackSize(), listing.stock)));
        ItemMeta meta = icon.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(Objects.requireNonNull(meta.getLore())) : new ArrayList<>();
        if (!lore.isEmpty()) lore.add("");
        lore.add("§b§lPLAYER SHOP");
        lore.add("§7Price each: §b" + number(listing.price) + " Points");
        lore.add("§7Stock: §f" + number(listing.stock));
        lore.add("");
        if (owner) {
            lore.add("§eClick with matching items: §fadd stock");
            lore.add("§eClick empty-handed: §fchange price");
            lore.add("§cShift-right-click: §fremove listing");
        } else {
            lore.add("§eLeft-click: §fbuy 1");
            lore.add("§eRight-click: §fbuy one full stack");
            lore.add("§eShift-left-click: §fbuy 16");
            lore.add("§eShift-right-click: §fbuy 64");
        }
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof MarketHolder)) return;
        event.setCancelled(true);
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= event.getInventory().getSize()) return;

        if (holder instanceof DirectoryHolder) {
            for (int i = 0; i < NPC_SLOTS.length; i++) if (raw == NPC_SLOTS[i]) {
                openNpc(player, NPC_IDS.get(i));
                return;
            }
            return;
        }
        if (holder instanceof ConfirmHolder confirm) {
            if (raw == 11) rent(player, confirm.npcId);
            else if (raw == 15) openDirectory(player);
            return;
        }
        if (raw == 27) { openDirectory(player); return; }
        if (raw == 35) { player.closeInventory(); return; }

        if (holder instanceof OwnerHolder ownerHolder) {
            Rental rental = currentRental(ownerHolder.npcId);
            if (rental == null || !rental.owner.equals(player.getUniqueId())) { openDirectory(player); return; }
            if (raw == 31) { beginAddListing(player, rental); return; }
            int index = listingIndex(raw);
            if (index >= 0 && rental.listings[index] != null) manageListing(player, rental, index, event.getClick());
            return;
        }
        if (holder instanceof ShopHolder shopHolder) {
            Rental rental = currentRental(shopHolder.npcId);
            if (rental == null) { openDirectory(player); return; }
            int index = listingIndex(raw);
            if (index < 0 || rental.listings[index] == null) return;
            int amount;
            ClickType click = event.getClick();
            if (click == ClickType.RIGHT) amount = rental.listings[index].item.getMaxStackSize();
            else if (click == ClickType.SHIFT_LEFT) amount = 16;
            else if (click == ClickType.SHIFT_RIGHT) amount = 64;
            else amount = 1;
            buy(player, rental, index, amount);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MarketHolder) event.setCancelled(true);
    }

    private int listingIndex(int raw) {
        for (int i = 0; i < LISTING_SLOTS.length; i++) if (LISTING_SLOTS[i] == raw) return i;
        return -1;
    }

    private void rent(Player player, String npcId) {
        if (currentRental(npcId) != null) {
            player.sendMessage(PREFIX + "§cThat NPC has just been rented.");
            openDirectory(player);
            return;
        }
        if (rentalOwnedBy(player.getUniqueId()) != null) {
            player.sendMessage(PREFIX + "§cYou can rent only one NPC at a time.");
            openDirectory(player);
            return;
        }
        PlayerRecord record = core.players().get(player.getUniqueId(), player.getName());
        long cost = rentPrice();
        if (!record.takePoints(cost)) {
            player.sendMessage(PREFIX + "§cYou need §f" + number(cost) + " Points§c. You have §f" + number(record.points()) + "§c.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            return;
        }
        Rental rental = new Rental(npcId, player.getUniqueId(), player.getName(), System.currentTimeMillis() + rentDays() * 86_400_000L);
        rentals.put(npcId, rental);
        if (!saveData()) {
            rentals.remove(npcId);
            record.addPoints(cost);
            core.players().save(false, true);
            player.sendMessage(PREFIX + "§cThe rental could not be saved. Your Points were returned.");
            return;
        }
        core.players().save(false, true);
        applyNpcState(npcId);
        player.sendMessage(PREFIX + "§aYou rented §f" + npcId + " §afor §f" + rentDays() + " days§a.");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.15f);
        openOwner(player, rental);
    }

    private void beginAddListing(Player player, Rental rental) {
        int empty = rental.firstEmpty();
        if (empty < 0) {
            player.sendMessage(PREFIX + "§cThis shop already has 9 listings.");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir() || hand.getAmount() <= 0) {
            player.sendMessage(PREFIX + "§cHold the stock you want to sell in your main hand.");
            return;
        }
        PricePrompt prompt = new PricePrompt(rental.npcId, empty, hand.clone(), false);
        prompts.put(player.getUniqueId(), prompt);
        player.closeInventory();
        player.sendMessage("");
        player.sendMessage(PREFIX + "§eType the §bprice per item §ein chat.");
        player.sendMessage(PREFIX + "§7Stock: §f" + hand.getAmount() + "x " + pretty(hand) + " §8• §7Type §fcancel §7to stop.");
        player.sendMessage("");
    }

    private void manageListing(Player player, Rental rental, int index, ClickType click) {
        Listing listing = rental.listings[index];
        if (click == ClickType.SHIFT_RIGHT) {
            giveOrQueue(player, listing.item, listing.stock);
            rental.listings[index] = null;
            saveData();
            player.sendMessage(PREFIX + "§aListing removed. All remaining stock was returned.");
            openOwner(player, rental);
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!hand.getType().isAir()) {
            if (!hand.isSimilar(listing.item)) {
                player.sendMessage(PREFIX + "§cThat item does not match this listing.");
                return;
            }
            int room = Math.max(0, getConfig().getInt("market.max-stock-per-listing", 10_000) - listing.stock);
            int added = Math.min(room, hand.getAmount());
            if (added <= 0) {
                player.sendMessage(PREFIX + "§cThis listing reached the stock limit.");
                return;
            }
            listing.stock += added;
            hand.setAmount(hand.getAmount() - added);
            if (hand.getAmount() <= 0) player.getInventory().setItemInMainHand(null);
            if (!saveData()) {
                listing.stock -= added;
                giveOrQueue(player, listing.item, added);
                player.sendMessage(PREFIX + "§cStock was not saved and was returned.");
                return;
            }
            player.sendMessage(PREFIX + "§aAdded §f" + added + " §aitems. New stock: §f" + listing.stock + "§a.");
            openOwner(player, rental);
            return;
        }
        prompts.put(player.getUniqueId(), new PricePrompt(rental.npcId, index, null, true));
        player.closeInventory();
        player.sendMessage(PREFIX + "§eType the new §bprice per item §ein chat, or §fcancel§e.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChatPrice(AsyncPlayerChatEvent event) {
        PricePrompt prompt = prompts.remove(event.getPlayer().getUniqueId());
        if (prompt == null) return;
        event.setCancelled(true);
        String answer = event.getMessage().trim();
        Bukkit.getScheduler().runTask(this, () -> finishPrice(event.getPlayer(), prompt, answer));
    }

    private void finishPrice(Player player, PricePrompt prompt, String answer) {
        Rental rental = currentRental(prompt.npcId);
        if (answer.equalsIgnoreCase("cancel")) {
            player.sendMessage(PREFIX + "§7Listing change cancelled.");
            if (rental != null && rental.owner.equals(player.getUniqueId())) openOwner(player, rental);
            return;
        }
        long price;
        try { price = Long.parseLong(answer.replace(",", "")); }
        catch (NumberFormatException error) { price = -1; }
        if (price <= 0 || price > MAX_PRICE) {
            prompts.put(player.getUniqueId(), prompt);
            player.sendMessage(PREFIX + "§cEnter a whole number from 1 to " + number(MAX_PRICE) + ", or type cancel.");
            return;
        }
        if (rental == null || !rental.owner.equals(player.getUniqueId())) {
            player.sendMessage(PREFIX + "§cThat rental is no longer active.");
            return;
        }
        if (prompt.edit) {
            Listing listing = rental.listings[prompt.index];
            if (listing == null) { player.sendMessage(PREFIX + "§cThat listing no longer exists."); return; }
            long old = listing.price;
            listing.price = price;
            if (!saveData()) { listing.price = old; player.sendMessage(PREFIX + "§cThe new price could not be saved."); return; }
            player.sendMessage(PREFIX + "§aPrice changed to §b" + number(price) + " Points §aper item.");
            openOwner(player, rental);
            return;
        }
        if (rental.listings[prompt.index] != null) {
            player.sendMessage(PREFIX + "§cThat listing slot is no longer empty.");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (prompt.preview == null || hand.getType().isAir() || !hand.isSimilar(prompt.preview) || hand.getAmount() < prompt.preview.getAmount()) {
            player.sendMessage(PREFIX + "§cKeep the same stock in your main hand until the price is entered.");
            return;
        }
        int stock = prompt.preview.getAmount();
        ItemStack template = prompt.preview.clone();
        template.setAmount(1);
        hand.setAmount(hand.getAmount() - stock);
        if (hand.getAmount() <= 0) player.getInventory().setItemInMainHand(null);
        rental.listings[prompt.index] = new Listing(template, stock, price);
        if (!saveData()) {
            rental.listings[prompt.index] = null;
            giveOrQueue(player, template, stock);
            player.sendMessage(PREFIX + "§cThe listing could not be saved. Your stock was returned.");
            return;
        }
        player.sendMessage(PREFIX + "§aListing created: §f" + stock + "x " + pretty(template) + " §8• §b" + number(price) + " Points §aeach.");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.4f);
        openOwner(player, rental);
    }

    private void buy(Player buyer, Rental rental, int index, int requested) {
        if (!isCurrent(rental) || rental.owner.equals(buyer.getUniqueId())) {
            buyer.sendMessage(PREFIX + (rental.owner.equals(buyer.getUniqueId()) ? "§cYou cannot buy your own listing." : "§cThis shop expired."));
            return;
        }
        Listing listing = rental.listings[index];
        if (listing == null || listing.stock <= 0) { openShop(buyer, rental); return; }
        int amount = Math.max(1, Math.min(requested, listing.stock));
        long total;
        try { total = Math.multiplyExact(listing.price, (long) amount); }
        catch (ArithmeticException error) { buyer.sendMessage(PREFIX + "§cThat purchase is too large."); return; }
        PlayerRecord buyerRecord = core.players().get(buyer.getUniqueId(), buyer.getName());
        if (buyerRecord.points() < total) {
            buyer.sendMessage(PREFIX + "§cYou need §f" + number(total) + " Points§c. You have §f" + number(buyerRecord.points()) + "§c.");
            buyer.playSound(buyer.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            return;
        }
        if (!canFit(buyer, listing.item, amount)) {
            buyer.sendMessage(PREFIX + "§cYou do not have enough inventory space.");
            return;
        }
        PlayerRecord sellerRecord = core.players().get(rental.owner, rental.ownerName);
        if (!buyerRecord.takePoints(total)) return;
        sellerRecord.addPoints(total);
        listing.stock -= amount;
        ItemStack bought = listing.item.clone();
        if (!saveData()) {
            listing.stock += amount;
            sellerRecord.takePoints(total);
            buyerRecord.addPoints(total);
            core.players().save(false, true);
            buyer.sendMessage(PREFIX + "§cThe purchase could not be saved. Your Points were returned.");
            return;
        }
        giveExact(buyer, bought, amount);
        core.players().save(false, true);
        buyer.sendMessage(PREFIX + "§aBought §f" + amount + "x " + pretty(bought) + " §afor §b" + number(total) + " Points§a.");
        Player seller = Bukkit.getPlayer(rental.owner);
        if (seller != null) seller.sendMessage(PREFIX + "§b" + buyer.getName() + " §7bought §f" + amount + "x " + pretty(bought) + " §7for §b" + number(total) + " Points§7.");
        buyer.playSound(buyer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        if (listing.stock <= 0) rental.listings[index] = null;
        saveData();
        openShop(buyer, rental);
    }

    private boolean canFit(Player player, ItemStack item, int amount) {
        int remaining = amount;
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) remaining -= item.getMaxStackSize();
            else if (slot.isSimilar(item)) remaining -= Math.max(0, slot.getMaxStackSize() - slot.getAmount());
            if (remaining <= 0) return true;
        }
        return false;
    }

    private void giveExact(Player player, ItemStack template, int amount) {
        int left = amount;
        while (left > 0) {
            ItemStack part = template.clone();
            int batch = Math.min(part.getMaxStackSize(), left);
            part.setAmount(batch);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(part);
            if (!overflow.isEmpty()) overflow.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
            left -= batch;
        }
    }

    private void giveOrQueue(Player player, ItemStack template, int amount) {
        int left = amount;
        while (left > 0) {
            ItemStack part = template.clone();
            int batch = Math.min(part.getMaxStackSize(), left);
            part.setAmount(batch);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(part);
            overflow.values().forEach(stack -> queueReturn(player.getUniqueId(), stack));
            left -= batch;
        }
        saveData();
    }

    private void queueReturn(UUID owner, ItemStack stack) {
        returns.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(stack.clone());
    }

    private void deliverReturns(Player player, boolean notifyEmpty) {
        List<ItemStack> waiting = returns.get(player.getUniqueId());
        if (waiting == null || waiting.isEmpty()) {
            if (notifyEmpty) player.sendMessage(PREFIX + "§7You have no returned stock waiting.");
            return;
        }
        List<ItemStack> left = new ArrayList<>();
        int delivered = 0;
        for (ItemStack stack : waiting) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack.clone());
            int remaining = overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
            delivered += stack.getAmount() - remaining;
            left.addAll(overflow.values());
        }
        if (left.isEmpty()) returns.remove(player.getUniqueId());
        else returns.put(player.getUniqueId(), left);
        saveData();
        if (delivered > 0) player.sendMessage(PREFIX + "§aReturned §f" + number(delivered) + " §aunsold items to your inventory.");
        if (!left.isEmpty()) player.sendMessage(PREFIX + "§eYour inventory is full. Use §f/npcmarket returns §elater.");
    }

    private void expireRentals() {
        long now = System.currentTimeMillis();
        List<String> expired = rentals.values().stream().filter(r -> r.expiresAt <= now).map(r -> r.npcId).toList();
        for (String id : expired) release(id, true);
    }

    private void release(String npcId, boolean returnStock) {
        Rental rental = rentals.remove(npcId);
        if (rental != null && returnStock) {
            for (Listing listing : rental.listings) {
                if (listing == null || listing.stock <= 0) continue;
                int left = listing.stock;
                while (left > 0) {
                    ItemStack part = listing.item.clone();
                    int batch = Math.min(part.getMaxStackSize(), left);
                    part.setAmount(batch);
                    queueReturn(rental.owner, part);
                    left -= batch;
                }
            }
            Player owner = Bukkit.getPlayer(rental.owner);
            if (owner != null) owner.sendMessage(PREFIX + "§eYour rental ended. Unsold stock is available with §f/npcmarket returns§e.");
        }
        saveData();
        applyNpcState(npcId);
    }

    private Rental currentRental(String npcId) {
        Rental rental = rentals.get(npcId);
        if (rental != null && rental.expiresAt <= System.currentTimeMillis()) {
            release(npcId, true);
            return null;
        }
        return rental;
    }

    private boolean isCurrent(Rental rental) {
        return rental != null && rentals.get(rental.npcId) == rental && rental.expiresAt > System.currentTimeMillis();
    }

    private Rental rentalOwnedBy(UUID owner) {
        for (Rental rental : rentals.values()) if (rental.owner.equals(owner) && rental.expiresAt > System.currentTimeMillis()) return rental;
        return null;
    }

    private void applyNpcState(String npcId) {
        try {
            Npc npc = FancyNpcsPlugin.get().getNpcManager().getNpc(npcId);
            if (npc == null) {
                getLogger().warning("FancyNpcs NPC '" + npcId + "' was not found.");
                return;
            }
            Rental rental = rentals.get(npcId);
            String skin = rental == null ? getConfig().getString("npc.idle-skin", "fleurily") : rental.ownerName;
            String display = rental == null
                    ? getConfig().getString("npc.idle-display", "&#00E5FF&lPLAYER SHOP &8• &#71F521AVAILABLE")
                    : "&#00E5FF&l" + rental.ownerName.toUpperCase(Locale.ROOT) + "'S SHOP";
            npc.getData().setMirrorSkin(false);
            npc.getData().setSkin(skin);
            npc.getData().setDisplayName(display);
            npc.removeForAll();
            npc.create();
            npc.spawnForAll();
            FancyNpcsPlugin.get().getNpcManager().saveNpcs(false);
        } catch (Throwable error) {
            getLogger().log(Level.WARNING, "Could not update NPC " + npcId + ".", error);
        }
    }

    private void loadData() {
        rentals.clear();
        returns.clear();
        if (!dataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection rentalRoot = yaml.getConfigurationSection("rentals");
        if (rentalRoot != null) for (String npcId : rentalRoot.getKeys(false)) {
            if (!NPC_IDS.contains(npcId)) continue;
            try {
                UUID owner = UUID.fromString(yaml.getString("rentals." + npcId + ".owner", ""));
                String ownerName = yaml.getString("rentals." + npcId + ".owner-name", "Unknown");
                long expires = yaml.getLong("rentals." + npcId + ".expires-at");
                Rental rental = new Rental(npcId, owner, ownerName, expires);
                for (int i = 0; i < 9; i++) {
                    String path = "rentals." + npcId + ".listings." + i;
                    ItemStack stack = yaml.getItemStack(path + ".item");
                    int stock = yaml.getInt(path + ".stock");
                    long price = yaml.getLong(path + ".price");
                    if (stack != null && stock > 0 && price > 0) { stack.setAmount(1); rental.listings[i] = new Listing(stack, stock, price); }
                }
                rentals.put(npcId, rental);
            } catch (Exception error) {
                getLogger().warning("Skipped unreadable rental " + npcId + ": " + error.getMessage());
            }
        }
        ConfigurationSection returnRoot = yaml.getConfigurationSection("returns");
        if (returnRoot != null) for (String uuidText : returnRoot.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidText);
                List<?> raw = yaml.getList("returns." + uuidText, List.of());
                List<ItemStack> items = raw.stream().filter(ItemStack.class::isInstance).map(ItemStack.class::cast).map(ItemStack::clone).collect(Collectors.toCollection(ArrayList::new));
                if (!items.isEmpty()) returns.put(uuid, items);
            } catch (Exception error) {
                getLogger().warning("Skipped unreadable returned-stock row " + uuidText + ".");
            }
        }
    }

    private boolean saveData() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Rental rental : rentals.values()) {
            String base = "rentals." + rental.npcId;
            yaml.set(base + ".owner", rental.owner.toString());
            yaml.set(base + ".owner-name", rental.ownerName);
            yaml.set(base + ".expires-at", rental.expiresAt);
            for (int i = 0; i < 9; i++) {
                Listing listing = rental.listings[i];
                if (listing == null) continue;
                String path = base + ".listings." + i;
                yaml.set(path + ".item", listing.item);
                yaml.set(path + ".stock", listing.stock);
                yaml.set(path + ".price", listing.price);
            }
        }
        for (Map.Entry<UUID, List<ItemStack>> entry : returns.entrySet()) yaml.set("returns." + entry.getKey(), entry.getValue());
        try {
            File parent = dataFile.getParentFile();
            if (parent != null) parent.mkdirs();
            yaml.save(dataFile);
            return true;
        } catch (IOException error) {
            getLogger().log(Level.SEVERE, "Could not save player shops.", error);
            return false;
        }
    }

    private long rentPrice() { return Math.max(1L, getConfig().getLong("market.rent-price-points", 5000L)); }
    private long rentDays() { return Math.max(1L, getConfig().getLong("market.rent-days", 3L)); }

    private String remaining(long endsAt) {
        long seconds = Math.max(0L, (endsAt - System.currentTimeMillis() + 999L) / 1000L);
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    private String number(long value) { return NumberFormat.getIntegerInstance(Locale.US).format(value); }
    private String pretty(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta.hasDisplayName()) return meta.getDisplayName();
        return Arrays.stream(item.getType().name().toLowerCase(Locale.ROOT).split("_"))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    private ItemStack playerHead(UUID owner, String ownerName, String name, List<String> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(owner);
        meta.setOwningPlayer(offline);
        meta.setDisplayName(name);
        meta.setLore(lore);
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void fill(Inventory inventory) {
        ItemStack pane = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, pane);
    }

    private static final class Rental {
        final String npcId;
        final UUID owner;
        final String ownerName;
        final long expiresAt;
        final Listing[] listings = new Listing[9];
        Rental(String npcId, UUID owner, String ownerName, long expiresAt) { this.npcId = npcId; this.owner = owner; this.ownerName = ownerName; this.expiresAt = expiresAt; }
        int firstEmpty() { for (int i = 0; i < listings.length; i++) if (listings[i] == null) return i; return -1; }
        int listingCount() { int count = 0; for (Listing listing : listings) if (listing != null) count++; return count; }
    }

    private static final class Listing {
        final ItemStack item;
        int stock;
        long price;
        Listing(ItemStack item, int stock, long price) { this.item = item; this.stock = stock; this.price = price; }
    }

    private record PricePrompt(String npcId, int index, ItemStack preview, boolean edit) {}

    private abstract static class MarketHolder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final class DirectoryHolder extends MarketHolder {}
    private static final class ConfirmHolder extends MarketHolder { final String npcId; ConfirmHolder(String npcId) { this.npcId = npcId; } }
    private static final class OwnerHolder extends MarketHolder { final String npcId; OwnerHolder(String npcId) { this.npcId = npcId; } }
    private static final class ShopHolder extends MarketHolder { final String npcId; ShopHolder(String npcId) { this.npcId = npcId; } }
}
