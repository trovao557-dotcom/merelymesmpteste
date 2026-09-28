package com.merelyme.afk;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class MerelyAfk extends JavaPlugin implements Listener, CommandExecutor {
    private static final String PREFIX = "§8[§b§lAFK§8] §r";
    private static final String MENU_TITLE = "§f\uF804\uE201";
    private static final String FIXED_POINTS_META = "merely_afk_fixed_points";

    private final Map<UUID, Progress> progress = new HashMap<>();
    private final Map<UUID, Boolean> pendingCrates = new HashMap<>();
    private final Map<Integer, Reward> rewards = Map.of(
            11, Reward.MONEY,
            12, Reward.STRENGTH,
            13, Reward.SPEED,
            14, Reward.JUMP,
            15, Reward.FIRE_RESISTANCE
    );

    private File dataFile;
    private YamlConfiguration data;
    private Object economy;
    private Method depositPlayer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        hookVault();
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("afkzone")).setExecutor(this);
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new AfkPlaceholder().register();
            getLogger().info("Registered PlaceholderAPI placeholder: %merelyafk_crates%");
        }
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        getLogger().info("MerelyAFK enabled: 2 Points/minute and 1 AFK Crate/10 minutes.");
    }

    @Override
    public void onDisable() {
        pendingCrates.clear();
        for (Player player : Bukkit.getOnlinePlayers()) player.removeMetadata(FIXED_POINTS_META, this);
        saveData();
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (!insideZone(player)) {
                if (progress.remove(id) != null) player.sendActionBar(Component.empty());
                continue;
            }
            Progress state = progress.get(id);
            if (state == null) {
                state = new Progress();
                progress.put(id, state);
                player.sendTitle("§b§lAFK ZONE", "§fYOU JOINED THE AFK ZONE", 5, 30, 5);
            }
            state.seconds++;
            if (state.seconds % 60 == 0) {
                if (giveFixedPoints(player, getConfig().getInt("rewards.points-per-minute", 2))) {
                    player.sendMessage(PREFIX + "§a+2 Points §7for staying in the AFK Zone.");
                } else {
                    player.sendMessage(PREFIX + "§cPoints could not be awarded. Please tell an admin.");
                }
            }
            if (state.seconds % 600 == 0) {
                addCrates(player.getUniqueId(), 1);
                player.sendMessage(PREFIX + "§6§l+1 AFK CRATE! §aOpen it at the shulker on §f200 73 117§a.");
                player.sendTitle("§a§lAFK CRATE", "§fYou stayed for 10 minutes", 5, 45, 10);
            }
            int pointRemaining = 60 - state.seconds % 60;
            int crateRemaining = 600 - state.seconds % 600;
            player.sendActionBar(Component.text("§b§lAFK ZONE §8• §f+2 Points in §b" + time(pointRemaining) + " §8• §fCrate in §6" + time(crateRemaining)));
        }
    }

    private boolean insideZone(Player player) {
        FileConfiguration config = getConfig();
        if (!player.getWorld().getName().equalsIgnoreCase(config.getString("zone.world", "world"))) return false;
        if (player.getGameMode() == GameMode.SPECTATOR) return false;
        int x = player.getLocation().getBlockX();
        int y = player.getLocation().getBlockY();
        int z = player.getLocation().getBlockZ();
        int x1 = config.getInt("zone.corner-one.x", 190), x2 = config.getInt("zone.corner-two.x", 198);
        int y1 = config.getInt("zone.corner-one.y", 68), y2 = config.getInt("zone.corner-two.y", 82);
        int z1 = config.getInt("zone.corner-one.z", 126), z2 = config.getInt("zone.corner-two.z", 118);
        return x >= Math.min(x1, x2) && x <= Math.max(x1, x2)
                && y >= Math.min(y1, y2) && y <= Math.max(y1, y2)
                && z >= Math.min(z1, z2) && z <= Math.max(z1, z2);
    }

    private String time(int seconds) {
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private boolean giveFixedPoints(Player player, int amount) {
        long validUntil = System.currentTimeMillis() + 2_500L;
        player.setMetadata(FIXED_POINTS_META, new FixedMetadataValue(this, validUntil));
        boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "points give " + player.getName() + " " + amount);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (MetadataValue value : player.getMetadata(FIXED_POINTS_META)) {
                if (value.getOwningPlugin() == this && value.asLong() == validUntil) {
                    player.removeMetadata(FIXED_POINTS_META, this);
                    break;
                }
            }
        }, 50L);
        return success;
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if ((event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK)
                || event.getClickedBlock() == null) return;
        if (!isCrateBlock(event.getClickedBlock())) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (pendingCrates.containsKey(player.getUniqueId())) return;
        int available = crates(player.getUniqueId());
        pendingCrates.put(player.getUniqueId(), available > 0);
        openRewardMenu(player);
    }

    private boolean isCrateBlock(Block block) {
        FileConfiguration config = getConfig();
        return block.getWorld().getName().equalsIgnoreCase(config.getString("crate-block.world", "world"))
                && block.getX() == config.getInt("crate-block.x", 200)
                && block.getY() == config.getInt("crate-block.y", 73)
                && block.getZ() == config.getInt("crate-block.z", 117)
                && block.getType().name().endsWith("SHULKER_BOX");
    }

    private void openRewardMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, MENU_TITLE);
        inventory.setItem(11, menuItem(Material.LIME_DYE, "§a§l$5,000", "§7Click to lock in this reward."));
        inventory.setItem(12, potion(Reward.STRENGTH));
        inventory.setItem(13, potion(Reward.SPEED));
        inventory.setItem(14, potion(Reward.JUMP));
        inventory.setItem(15, potion(Reward.FIRE_RESISTANCE));
        ItemStack fill = menuItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) if (inventory.getItem(slot) == null) inventory.setItem(slot, fill);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onMenu(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(MENU_TITLE)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Boolean canClaim = pendingCrates.get(player.getUniqueId());
        if (canClaim == null) return;
        Reward reward = rewards.get(event.getRawSlot());
        if (reward == null) return;
        if (!canClaim) {
            player.sendMessage(PREFIX + "§cYou need an AFK Crate to select a reward. §7Stay in the AFK Zone for 10 minutes.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.7f, 1.2f);
            return;
        }
        if (reward == Reward.MONEY && (economy == null || depositPlayer == null)) {
            player.sendMessage(PREFIX + "§cThe economy is unavailable. Choose another reward or tell an admin.");
            return;
        }
        if (!consumeCrate(player.getUniqueId())) {
            pendingCrates.remove(player.getUniqueId());
            player.closeInventory();
            player.sendMessage(PREFIX + "§cYour AFK Crate balance could not be saved. No reward was consumed.");
            return;
        }
        if (!grant(player, reward)) {
            addCrates(player.getUniqueId(), 1);
            player.sendMessage(PREFIX + "§cThe reward failed, so your AFK Crate was returned.");
            return;
        }
        pendingCrates.remove(player.getUniqueId());
        player.closeInventory();
        player.sendTitle("§a§lREWARD LOCKED IN", reward.title, 5, 35, 10);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!event.getView().getTitle().equals(MENU_TITLE)) return;
        UUID id = event.getPlayer().getUniqueId();
        pendingCrates.remove(id);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        progress.remove(id);
        pendingCrates.remove(id);
    }

    private boolean grant(Player player, Reward reward) {
        switch (reward) {
            case MONEY -> {
                if (!depositMoney(player, getConfig().getDouble("crate.money", 5000))) {
                    player.sendMessage(PREFIX + "§cThe economy is unavailable. Choose another reward or tell an admin.");
                    return false;
                }
            }
            default -> giveItem(player, potion(reward));
        }
        player.sendMessage(PREFIX + "§aYou selected " + reward.title + "§a.");
        return true;
    }

    private ItemStack potion(Reward reward) {
        ItemStack item = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        switch (reward) {
            case STRENGTH -> meta.setBasePotionType(PotionType.STRONG_STRENGTH);
            case SPEED -> meta.setBasePotionType(PotionType.STRONG_SWIFTNESS);
            case JUMP -> meta.setBasePotionType(PotionType.STRONG_LEAPING);
            case FIRE_RESISTANCE -> meta.setBasePotionType(PotionType.LONG_FIRE_RESISTANCE);
            default -> throw new IllegalArgumentException("Not a potion reward: " + reward);
        }
        meta.setDisplayName(reward.title);
        meta.setLore(List.of("§7Splash potion", "§aClick to lock in this reward."));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack menuItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    private void giveItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> left = player.getInventory().addItem(item);
        left.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
    }

    private int crates(UUID id) {
        return Math.max(0, data.getInt("players." + id + ".crates", 0));
    }

    private boolean addCrates(UUID id, int amount) {
        int before = crates(id);
        data.set("players." + id + ".crates", Math.max(0, before + amount));
        if (saveData()) return true;
        data.set("players." + id + ".crates", before);
        return false;
    }

    private boolean consumeCrate(UUID id) {
        int before = crates(id);
        if (before <= 0) return false;
        data.set("players." + id + ".crates", before - 1);
        if (saveData()) return true;
        data.set("players." + id + ".crates", before);
        return false;
    }

    private boolean saveData() {
        try {
            data.save(dataFile);
            return true;
        } catch (IOException exception) {
            getLogger().severe("Could not save AFK Crate balances: " + exception.getMessage());
            return false;
        }
    }

    private void hookVault() {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            @SuppressWarnings({"rawtypes", "unchecked"})
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) economyClass);
            if (registration == null) {
                getLogger().warning("Vault economy provider not found. The $5,000 reward will remain unavailable.");
                return;
            }
            economy = registration.getProvider();
            depositPlayer = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
        } catch (ReflectiveOperationException exception) {
            getLogger().warning("Vault economy hook unavailable: " + exception.getMessage());
        }
    }

    private boolean depositMoney(Player player, double amount) {
        if (economy == null || depositPlayer == null) return false;
        try {
            depositPlayer.invoke(economy, player, amount);
            return true;
        } catch (ReflectiveOperationException exception) {
            getLogger().warning("Could not deposit money for " + player.getName() + ": " + exception.getMessage());
            return false;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                Progress state = progress.get(player.getUniqueId());
                player.sendMessage(PREFIX + "§7AFK Crates: §f" + crates(player.getUniqueId()) + " §8• §7Open at §f200 73 117§7.");
                if (state == null) player.sendMessage(PREFIX + "§7AFK Zone: §fworld, 190 68 126 §7to §f198 82 118§7.");
                else player.sendMessage(PREFIX + "§aYou are earning rewards. §7Next Points: §f" + time(60 - state.seconds % 60) + "§7, next crate: §f" + time(600 - state.seconds % 600) + "§7.");
            } else sender.sendMessage("Use /afkzone givecrate <player> [amount], /afkzone verify or /afkzone reload.");
            return true;
        }
        if (!sender.hasPermission("merelyafk.admin")) {
            sender.sendMessage(PREFIX + "§cYou do not have permission.");
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage(PREFIX + "§aConfiguration reloaded.");
            return true;
        }
        if (args[0].equalsIgnoreCase("verify")) {
            World world = Bukkit.getWorld(getConfig().getString("crate-block.world", "world"));
            Block block = world == null ? null : world.getBlockAt(getConfig().getInt("crate-block.x", 200), getConfig().getInt("crate-block.y", 73), getConfig().getInt("crate-block.z", 117));
            boolean shulker = block != null && block.getType().name().endsWith("SHULKER_BOX");
            boolean points = Bukkit.getPluginCommand("points") != null;
            sender.sendMessage(PREFIX + (world != null ? "§a✔" : "§c✘") + " §7World §f" + getConfig().getString("zone.world", "world")
                    + " §8• " + (shulker ? "§a✔" : "§c✘") + " §7AFK shulker §f200 73 117"
                    + " §8• " + (points ? "§a✔" : "§c✘") + " §7Points"
                    + " §8• " + (economy != null ? "§a✔" : "§c✘") + " §7Money");
            return true;
        }
        if (args[0].equalsIgnoreCase("givecrate") && args.length >= 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { sender.sendMessage(PREFIX + "§cThat player must be online."); return true; }
            int amount = 1;
            if (args.length >= 3) try { amount = Math.max(1, Math.min(64, Integer.parseInt(args[2]))); }
            catch (NumberFormatException ignored) { sender.sendMessage(PREFIX + "§cThe amount must be a number from 1 to 64."); return true; }
            if (!addCrates(target.getUniqueId(), amount)) { sender.sendMessage(PREFIX + "§cCould not save the AFK Crates."); return true; }
            sender.sendMessage(PREFIX + "§aGave §f" + amount + " AFK Crate(s) §ato §f" + target.getName() + "§a. Balance: §f" + crates(target.getUniqueId()));
            return true;
        }
        sender.sendMessage(PREFIX + "§e/afkzone givecrate <player> [amount] §8• §e/afkzone verify §8• §e/afkzone reload");
        return true;
    }

    private enum Reward {
        MONEY("§a§l$5,000"),
        STRENGTH("§c§lSplash Strength II"),
        SPEED("§b§lSplash Speed II"),
        JUMP("§a§lSplash Jump Boost II"),
        FIRE_RESISTANCE("§6§lSplash Fire Resistance");

        private final String title;
        Reward(String title) { this.title = title; }
    }

    private final class AfkPlaceholder extends PlaceholderExpansion {
        @Override public String getIdentifier() { return "merelyafk"; }
        @Override public String getAuthor() { return "MerelyMeSMP"; }
        @Override public String getVersion() { return MerelyAfk.this.getDescription().getVersion(); }
        @Override public boolean persist() { return true; }
        @Override public String onRequest(OfflinePlayer player, String parameters) {
            if (player == null) return "0";
            if (parameters.equalsIgnoreCase("crates") || parameters.equalsIgnoreCase("keys")) {
                return String.valueOf(crates(player.getUniqueId()));
            }
            return null;
        }
    }

    private static final class Progress { private int seconds; }
}
