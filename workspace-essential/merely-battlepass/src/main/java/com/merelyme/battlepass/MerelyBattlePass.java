package com.merelyme.battlepass;

import io.papermc.paper.event.player.PlayerTradeEvent;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class MerelyBattlePass extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final String PREFIX = "§8[§6§lBATTLE PASS§8] §r";
    private static final String TITLE_PREFIX = "§f\uF804\uE211";
    private static final int MAX_LEVEL = 60;
    private static final int LEVELS_PER_PAGE = 7;
    private static final int PAGE_COUNT = 9;
    private static final int[] LEVEL_SLOTS = {1,2,3,4,5,6,7};
    private static final int[] FREE_SLOTS = {10,11,12,13,14,15,16};
    private static final int[] PREMIUM_SLOTS = {28,29,30,31,32,33,34};

    private final Map<UUID, PassData> players = new HashMap<>();
    private final Map<UUID, Long> lastPoints = new HashMap<>();
    private final Map<UUID, Integer> openPages = new HashMap<>();
    private final Map<String, ClaimTarget> clickTargets = new HashMap<>();
    private File playersFile;
    private YamlConfiguration playerStore;
    private int season;
    private boolean dirty;

    @Override public void onEnable() {
        saveDefaultConfig();
        season = Math.max(1, getConfig().getInt("season", 1));
        playersFile = new File(getDataFolder(), "players.yml");
        playerStore = YamlConfiguration.loadConfiguration(playersFile);
        loadPlayers();
        Objects.requireNonNull(getCommand("battlepass")).setExecutor(this);
        Objects.requireNonNull(getCommand("battlepass")).setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::playMinuteTick, 1200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(this, this::syncPointXp, 40L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, () -> { if (dirty) savePlayers(); }, 1200L, 1200L);
        getLogger().info("Season " + season + " Battle Pass enabled with 60 levels.");
    }

    @Override public void onDisable() { savePlayers(); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) { sender.sendMessage(PREFIX + "§e/battlepass give <player>"); return true; }
            if (!player.hasPermission("merelybattlepass.use")) { player.sendMessage(PREFIX + "§cYou do not have permission."); return true; }
            openPass(player, openPages.getOrDefault(player.getUniqueId(), 0));
            return true;
        }
        if (!sender.hasPermission("merelybattlepass.admin")) { sender.sendMessage(PREFIX + "§cYou do not have permission."); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ((sub.equals("give") || sub.equals("take")) && args.length == 2) {
            OfflinePlayer target = knownPlayer(args[1]);
            if (target == null) { sender.sendMessage(PREFIX + "§cPlayer not found."); return true; }
            PassData data = state(target.getUniqueId(), target.getName());
            data.premium = sub.equals("give"); dirty = true; savePlayers();
            sender.sendMessage(PREFIX + (data.premium ? "§aPremium Pass granted to §f" : "§ePremium Pass removed from §f") + displayName(target) + "§a.");
            Player online = target.getPlayer();
            if (online != null) online.sendTitle(data.premium ? "§6§lPREMIUM PASS UNLOCKED" : "§c§lPREMIUM PASS REMOVED", "§fSeason " + season, 10, 60, 15);
            return true;
        }
        if (sub.equals("xp") && args.length == 4) {
            OfflinePlayer target = knownPlayer(args[2]);
            if (target == null) { sender.sendMessage(PREFIX + "§cPlayer not found."); return true; }
            int amount;
            try { amount = Integer.parseInt(args[3]); } catch (NumberFormatException ex) { sender.sendMessage(PREFIX + "§cInvalid XP amount."); return true; }
            PassData data = state(target.getUniqueId(), target.getName());
            int before = level(data);
            if (args[1].equalsIgnoreCase("give")) data.xp = Math.max(0, Math.min(maxXp(), data.xp + amount));
            else if (args[1].equalsIgnoreCase("set")) data.xp = Math.max(0, Math.min(maxXp(), amount));
            else { sender.sendMessage(PREFIX + "§e/battlepass xp <give|set> <player> <amount>"); return true; }
            dirty = true; savePlayers();
            Player online = target.getPlayer(); if (online != null) announceLevels(online, before, level(data));
            sender.sendMessage(PREFIX + "§a" + displayName(target) + " now has §f" + format(data.xp) + " XP §8(§fLevel " + level(data) + "§8)§a.");
            return true;
        }
        sender.sendMessage(PREFIX + "§e/battlepass give <player> §8• §e/battlepass take <player> §8• §e/battlepass xp <give|set> <player> <amount>");
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("merelybattlepass.admin")) return List.of();
        if (args.length == 1) return match(args[0], List.of("give", "take", "xp"));
        if (args.length == 2 && args[0].equalsIgnoreCase("xp")) return match(args[1], List.of("give", "set"));
        if ((args.length == 2 && !args[0].equalsIgnoreCase("xp")) || (args.length == 3 && args[0].equalsIgnoreCase("xp")))
            return match(args[args.length - 1], Arrays.stream(Bukkit.getOfflinePlayers()).map(this::displayName).filter(Objects::nonNull).toList());
        if (args.length == 4 && args[0].equalsIgnoreCase("xp")) return match(args[3], List.of("1000", "5000", "10000", "60000"));
        return List.of();
    }

    private void openPass(Player player, int requestedPage) {
        int page = Math.max(0, Math.min(PAGE_COUNT - 1, requestedPage));
        openPages.put(player.getUniqueId(), page);
        clickTargets.keySet().removeIf(key -> key.startsWith(player.getUniqueId() + ":"));
        PassData data = state(player.getUniqueId(), player.getName());
        int currentLevel = level(data);
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_PREFIX + (page + 1) + "/" + PAGE_COUNT);
        ItemStack background = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, background);
        inv.setItem(9, item(Material.LIME_DYE, "§a§lFREE TRACK", "§7Available to every player."));
        inv.setItem(27, item(Material.GOLD_INGOT, "§6§lPREMIUM TRACK", data.premium ? "§aPremium Pass active" : "§cPremium Pass required"));
        int first = page * LEVELS_PER_PAGE + 1;
        String title = TITLE_PREFIX + (page + 1) + "/" + PAGE_COUNT;
        for (int column = 0; column < LEVELS_PER_PAGE; column++) {
            int level = first + column;
            if (level > MAX_LEVEL) continue;
            boolean reached = currentLevel >= level;
            inv.setItem(LEVEL_SLOTS[column], item(reached ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                    (reached ? "§a§l" : "§7§l") + "LEVEL " + level, "§7Required XP: §f" + format(requiredXp(level)), reached ? "§aUnlocked" : "§cLocked"));
            Reward free = freeReward(level), premium = premiumReward(level);
            inv.setItem(FREE_SLOTS[column], rewardItem(free, level, false, reached, data.freeClaims.contains(level), true));
            inv.setItem(PREMIUM_SLOTS[column], rewardItem(premium, level, true, reached, data.premiumClaims.contains(level), data.premium));
            clickTargets.put(player.getUniqueId() + ":" + title + ":" + FREE_SLOTS[column], new ClaimTarget(level, false));
            clickTargets.put(player.getUniqueId() + ":" + title + ":" + PREMIUM_SLOTS[column], new ClaimTarget(level, true));
        }
        if (page > 0) inv.setItem(45, item(Material.ARROW, "§e§lPREVIOUS PAGE", "§7Page " + page));
        inv.setItem(48, item(Material.CHEST, "§a§lCLAIM ALL", "§7Claim every available reward", data.premium ? "§6Free + Premium tracks" : "§aFree track"));
        int nextXp = currentLevel >= MAX_LEVEL ? maxXp() : requiredXp(currentLevel + 1);
        inv.setItem(49, item(Material.EXPERIENCE_BOTTLE, "§b§lSEASON " + season + " PROGRESS", "§fLevel: §b" + currentLevel + "§7/§f60", "§fXP: §b" + format(data.xp) + "§7/§f" + format(maxXp()), currentLevel >= MAX_LEVEL ? "§aPASS COMPLETED" : progressBar(data.xp - requiredXp(currentLevel), nextXp - requiredXp(currentLevel))));
        inv.setItem(50, item(data.premium ? Material.NETHER_STAR : Material.BARRIER, data.premium ? "§6§lPREMIUM ACTIVE" : "§c§lPREMIUM LOCKED", data.premium ? "§7Both reward tracks are available." : "§7An admin or the store can grant your pass."));
        if (page < PAGE_COUNT - 1) inv.setItem(53, item(Material.ARROW, "§e§lNEXT PAGE", "§7Page " + (page + 2)));
        player.openInventory(inv);
    }

    private ItemStack rewardItem(Reward reward, int level, boolean premium, boolean reached, boolean claimed, boolean ownsTrack) {
        Material material = claimed ? Material.LIME_STAINED_GLASS_PANE : reward.icon;
        List<String> lore = new ArrayList<>(reward.lore);
        lore.add("");
        if (claimed) lore.add("§a✔ Claimed");
        else if (!ownsTrack) lore.add("§cPremium Pass required");
        else if (!reached) lore.add("§cReach Level " + level + " to unlock");
        else lore.add("§eClick to claim");
        ItemStack item = item(material, (premium ? "§6" : "§a") + "§l" + reward.name, lore.toArray(String[]::new));
        if (!claimed && reached && ownsTrack) {
            ItemMeta meta = item.getItemMeta(); meta.addEnchant(Enchantment.UNBREAKING, 1, true); meta.addItemFlags(ItemFlag.HIDE_ENCHANTS); item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler public void onMenu(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.startsWith(TITLE_PREFIX) || !(event.getWhoClicked() instanceof Player player)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot(), page = openPages.getOrDefault(player.getUniqueId(), 0);
        if (slot == 45 && page > 0) { openPass(player, page - 1); return; }
        if (slot == 53 && page < PAGE_COUNT - 1) { openPass(player, page + 1); return; }
        if (slot == 48) { claimAll(player); openPass(player, page); return; }
        ClaimTarget target = clickTargets.get(player.getUniqueId() + ":" + title + ":" + slot);
        if (target != null && claim(player, target.level, target.premium)) openPass(player, page);
    }

    private boolean claim(Player player, int rewardLevel, boolean premium) {
        PassData data = state(player.getUniqueId(), player.getName());
        if (level(data) < rewardLevel) { player.sendMessage(PREFIX + "§cReach Level " + rewardLevel + " first."); return false; }
        if (premium && !data.premium) { player.sendMessage(PREFIX + "§cYou need the Premium Pass for this reward."); return false; }
        Set<Integer> claims = premium ? data.premiumClaims : data.freeClaims;
        if (!claims.add(rewardLevel)) { player.sendMessage(PREFIX + "§eYou already claimed this reward."); return false; }
        dirty = true; savePlayers();
        Reward reward = premium ? premiumReward(rewardLevel) : freeReward(rewardLevel);
        grant(player, reward);
        player.sendMessage(PREFIX + "§aClaimed §fLevel " + rewardLevel + " " + (premium ? "Premium" : "Free") + "§a: " + (premium ? "§6" : "§a") + reward.name + "§a.");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, .8f, 1.35f);
        return true;
    }

    private void claimAll(Player player) {
        PassData data = state(player.getUniqueId(), player.getName());
        int current = level(data); List<Reward> rewards = new ArrayList<>(); int count = 0;
        for (int level = 1; level <= current; level++) {
            if (data.freeClaims.add(level)) { rewards.add(freeReward(level)); count++; }
            if (data.premium && data.premiumClaims.add(level)) { rewards.add(premiumReward(level)); count++; }
        }
        if (count == 0) { player.sendMessage(PREFIX + "§eThere are no available rewards to claim."); return; }
        dirty = true; savePlayers(); rewards.forEach(reward -> grant(player, reward));
        player.sendMessage(PREFIX + "§aClaimed §f" + count + " §aBattle Pass rewards.");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
    }

    private Reward freeReward(int level) {
        return switch (level) {
            case 1 -> reward(Material.IRON_INGOT, "SEASON KICKOFF", "§e500 Points", "§f1 Iron Key", "points:500", "key:iron:1");
            case 10 -> reward(Material.IRON_BLOCK, "IRON MILESTONE", "§e500 Points", "§f1 Iron Key", "points:500", "key:iron:1");
            case 20 -> reward(Material.GOLD_BLOCK, "GOLD MILESTONE", "§e750 Points", "§61 Gold Key", "points:750", "key:gold:1");
            case 30 -> reward(Material.NETHER_STAR, "HALFWAY JACKPOT", "§e1,500 Points", "§61 Gold Key", "§d1 Daily Wheel Spin", "points:1500", "key:gold:1", "wheel:1");
            case 40 -> reward(Material.EXPERIENCE_BOTTLE, "WHEEL PACK", "§e1,000 Points", "§d2 Daily Wheel Spins", "points:1000", "wheel:2");
            case 50 -> reward(Material.DIAMOND, "DIAMOND MILESTONE", "§e2,000 Points", "§61 Gold Key", "§d1 Daily Wheel Spin", "points:2000", "key:gold:1", "wheel:1");
            case 55 -> reward(Material.EMERALD, "EMERALD PREVIEW", "§a1 Emerald Key", "key:emerald:1");
            case 60 -> reward(Material.EMERALD_BLOCK, "FREE PASS FINALE", "§e3,000 Points", "§a1 Emerald Key", "§dPASS VETERAN Tag", "points:3000", "key:emerald:1", "tag:pass_veteran");
            default -> {
                if (level % 12 == 0) yield reward(Material.EXPERIENCE_BOTTLE, "DAILY WHEEL SPIN", "§d1 Extra Daily Wheel Spin", "wheel:1");
                if (level % 8 == 0) yield reward(Material.IRON_INGOT, "IRON KEY", "§f1 Iron Key", "key:iron:1");
                if (level % 2 == 0) yield reward(Material.GOLD_INGOT, "$25,000", "§a$25,000 Money", "money:25000");
                yield reward(Material.LIGHT_BLUE_DYE, "200 POINTS", "§b200 Points", "points:200");
            }
        };
    }

    private Reward premiumReward(int level) {
        return switch (level) {
            case 1 -> reward(Material.NETHER_STAR, "PREMIUM KICKOFF", "§e750 Points", "§f1 Iron Key", "§d1 Daily Wheel Spin", "points:750", "key:iron:1", "wheel:1");
            case 10 -> reward(Material.NAME_TAG, "SEASON SCOUT", "§dExclusive two-colour Tag", "§e500 Points", "tag:season_scout", "points:500");
            case 20 -> reward(Material.NAME_TAG, "SEASON CHALLENGER", "§dExclusive two-colour Tag", "§a1 Emerald Key", "tag:season_challenger", "key:emerald:1");
            case 25 -> reward(Material.END_CRYSTAL, "SPECIAL DROP", "§d1 Special Key", "§e1,000 Points", "key:special:1", "points:1000");
            case 30 -> reward(Material.SOUL_LANTERN, "SOUL TRAIL", "§bPermanent Soul Trail", "§d2 Daily Wheel Spins", "§d1 Special Key", "particle:soul", "wheel:2", "key:special:1");
            case 40 -> reward(Material.NAME_TAG, "SEASON ELITE", "§dExclusive two-colour Tag", "§d1 Special Key", "§e1,500 Points", "tag:season_elite", "key:special:1", "points:1500");
            case 45 -> reward(Material.END_CRYSTAL, "SPECIAL CACHE", "§d1 Special Key", "§e2,000 Points", "key:special:1", "points:2000");
            case 50 -> reward(Material.MAGMA_CREAM, "METEOR STRIKE", "§6Permanent Kill Effect", "§a1 Emerald Key", "§d2 Daily Wheel Spins", "kill:meteor", "key:emerald:1", "wheel:2");
            case 55 -> reward(Material.END_CRYSTAL, "FINAL APPROACH", "§d1 Special Key", "§e2,500 Points", "key:special:1", "points:2500");
            case 60 -> reward(Material.NETHER_STAR, "SEASON 1 CHAMPION", "§e5,000 Points", "§dSEASON 1 Tag", "§d1 Special Key", "§d3 Daily Wheel Spins", "points:5000", "tag:season1", "key:special:1", "wheel:3");
            default -> {
                if (level % 10 == 0) yield reward(Material.EMERALD, "EMERALD KEY", "§a1 Emerald Key", "key:emerald:1");
                if (level % 6 == 0) yield reward(Material.EXPERIENCE_BOTTLE, "WHEEL BUNDLE", "§d2 Daily Wheel Spins", "wheel:2");
                if (level % 4 == 0) yield reward(Material.GOLD_INGOT, "GOLD KEY", "§61 Gold Key", "key:gold:1");
                if (level % 2 == 0) { int money = 40000 + (level % 3) * 10000; yield reward(Material.GOLD_BLOCK, "$" + format(money), "§a$" + format(money) + " Money", "money:" + money); }
                int points = 300 + (level % 3) * 100; yield reward(Material.CYAN_DYE, format(points) + " POINTS", "§b" + format(points) + " Points", "points:" + points);
            }
        };
    }

    private Reward reward(Material icon, String name, String... values) {
        List<String> lore = new ArrayList<>(), actions = new ArrayList<>();
        for (String value : values) { if (value.contains(":")) actions.add(value); else lore.add(value); }
        return new Reward(icon, name, List.copyOf(lore), List.copyOf(actions));
    }

    private void grant(Player player, Reward reward) {
        for (String action : reward.actions) {
            String[] parts = action.split(":");
            switch (parts[0]) {
                case "points" -> dispatch("points give " + player.getName() + " " + parts[1]);
                case "money" -> dispatch("eco give " + player.getName() + " " + parts[1]);
                case "key" -> dispatch("keys give " + player.getName() + " " + parts[1] + " " + parts[2]);
                case "wheel" -> dispatch("dailytoken give " + player.getName() + " " + parts[1]);
                case "tag" -> dispatch("lp user " + player.getName() + " permission set sksmp.tag." + parts[1] + " true");
                case "particle" -> dispatch("tournament unlock " + player.getName() + " particle " + parts[1]);
                case "kill" -> dispatch("tournament unlock " + player.getName() + " kill " + parts[1]);
            }
        }
    }

    private void dispatch(String command) {
        if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) getLogger().warning("Reward command was not accepted: " + command);
    }

    private void playMinuteTick() {
        int amount = getConfig().getInt("xp.play-minute", 10);
        for (Player player : Bukkit.getOnlinePlayers()) if (player.getGameMode() != GameMode.SPECTATOR) addXp(player, amount);
    }

    @EventHandler(priority = EventPriority.LOWEST) public void onJoin(PlayerJoinEvent event) {
        PassData data = state(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        capturePoints(event.getPlayer());
        Bukkit.getScheduler().runTaskLater(this, () -> capturePoints(event.getPlayer()), 20L);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!event.getPlayer().isOnline()) return;
            event.getPlayer().sendMessage(PREFIX + "§fSeason " + season + " §8• §bLevel " + level(data) + "§7/§f60 §8• " + (data.premium ? "§6Premium Active" : "§aFree Track") + " §8• §f/battlepass");
        }, 60L);
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { if (dirty) savePlayers(); openPages.remove(event.getPlayer().getUniqueId()); lastPoints.remove(event.getPlayer().getUniqueId()); }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR) public void onPlayerDeath(PlayerDeathEvent event) { Player killer = event.getEntity().getKiller(); if (killer != null && killer != event.getEntity()) addXp(killer, getConfig().getInt("xp.player-kill", 75)); }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR) public void onMobDeath(EntityDeathEvent event) { if (event.getEntity() instanceof Player) return; Player killer = event.getEntity().getKiller(); if (killer != null) addXp(killer, getConfig().getInt("xp.mob-kill", 3)); }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR) public void onBreak(BlockBreakEvent event) {
        PassData data = state(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        data.blocks++;
        int every = Math.max(1, getConfig().getInt("xp.blocks-per-reward", 25));
        if (data.blocks >= every) { data.blocks %= every; addXp(event.getPlayer(), getConfig().getInt("xp.block-reward", 2)); }
        if (isOre(event.getBlock())) addXp(event.getPlayer(), getConfig().getInt("xp.ore-mined", 5));
        dirty = true;
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR) public void onFish(PlayerFishEvent event) { if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) addXp(event.getPlayer(), getConfig().getInt("xp.fish", 15)); }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR) public void onEnchant(EnchantItemEvent event) { addXp(event.getEnchanter(), getConfig().getInt("xp.enchant", 20)); }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR) public void onBreed(EntityBreedEvent event) { if (event.getBreeder() instanceof Player player) addXp(player, getConfig().getInt("xp.breed", 10)); }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR) public void onTrade(PlayerTradeEvent event) { addXp(event.getPlayer(), getConfig().getInt("xp.villager-trade", 5)); }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR) public void onCraft(CraftItemEvent event) { if (event.getWhoClicked() instanceof Player player && event.getCurrentItem() != null) addXp(player, Math.max(1, event.getCurrentItem().getAmount() / Math.max(1, getConfig().getInt("xp.craft-batch", 5)))); }

    private void addXp(Player player, int amount) {
        if (amount <= 0) return;
        PassData data = state(player.getUniqueId(), player.getName()); int before = level(data);
        data.xp = Math.min(maxXp(), data.xp + amount); dirty = true;
        int after = level(data); if (after > before) announceLevels(player, before, after);
    }

    private void announceLevels(Player player, int before, int after) {
        if (after <= before) return;
        player.sendTitle("§6§lBATTLE PASS LEVEL " + after, "§fNew rewards available in §e/battlepass", 10, 55, 15);
        player.sendMessage(PREFIX + "§aYou reached Level §f" + after + "§a! New rewards are ready to claim.");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, .8f, 1.15f);
    }

    private boolean isOre(Block block) {
        return switch (block.getType()) {
            case COAL_ORE, DEEPSLATE_COAL_ORE, COPPER_ORE, DEEPSLATE_COPPER_ORE, IRON_ORE, DEEPSLATE_IRON_ORE,
                 GOLD_ORE, DEEPSLATE_GOLD_ORE, REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE, LAPIS_ORE, DEEPSLATE_LAPIS_ORE,
                 DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE, EMERALD_ORE, DEEPSLATE_EMERALD_ORE, NETHER_GOLD_ORE, NETHER_QUARTZ_ORE,
                 ANCIENT_DEBRIS -> true;
            default -> false;
        };
    }

    private int level(PassData data) {
        int level = 1;
        while (level < MAX_LEVEL && data.xp >= requiredXp(level + 1)) level++;
        return level;
    }

    private int levelCost(int level) {
        if (level >= 50) return 1500;
        if (level <= 12) return 500 + (int)Math.round((level - 1) * (200.0 / 11.0));
        return 700 + (int)Math.round((level - 12) * (800.0 / 38.0));
    }

    private int requiredXp(int level) {
        int total = 0;
        for (int current = 1; current < Math.max(1, level); current++) total += levelCost(current);
        return total;
    }

    private int maxXp() { return requiredXp(MAX_LEVEL); }

    private void syncPointXp() {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            Long current = readPoints(player);
            if (current == null) continue;
            Long previous = lastPoints.putIfAbsent(player.getUniqueId(), current);
            if (previous == null) continue;
            long gained = current - previous;
            lastPoints.put(player.getUniqueId(), current);
            if (gained > 0) addXp(player, (int)Math.min(Integer.MAX_VALUE, gained));
        }
    }

    private void capturePoints(Player player) {
        if (!player.isOnline()) return;
        Long points = readPoints(player);
        if (points != null) lastPoints.putIfAbsent(player.getUniqueId(), points);
    }

    private Long readPoints(Player player) {
        try {
            String raw = PlaceholderAPI.setPlaceholders(player, "%skcore_points%");
            return Long.parseLong(ChatColor.stripColor(raw).replace(",", "").trim());
        } catch (Exception ignored) { return null; }
    }
    private String progressBar(int current, int needed) { int lit = needed <= 0 ? 10 : Math.max(0, Math.min(10, (int)Math.floor(current * 10.0 / needed))); return "§a" + "■".repeat(lit) + "§8" + "■".repeat(10-lit) + " §7" + Math.max(0,current) + "/" + needed + " XP"; }

    private ItemStack item(Material material, String name, String... lore) { ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta(); meta.setDisplayName(name); meta.setLore(Arrays.asList(lore)); item.setItemMeta(meta); return item; }
    private List<String> match(String typed, List<String> choices) { String lower = typed.toLowerCase(Locale.ROOT); return choices.stream().filter(v -> v != null && v.toLowerCase(Locale.ROOT).startsWith(lower)).toList(); }
    private static String format(long value) { return String.format(Locale.US, "%,d", value); }
    private String displayName(OfflinePlayer player) { return player.getName() == null ? player.getUniqueId().toString() : player.getName(); }
    private OfflinePlayer knownPlayer(String name) { Player online = Bukkit.getPlayerExact(name); if (online != null) return online; for (OfflinePlayer player : Bukkit.getOfflinePlayers()) if (player.getName() != null && player.getName().equalsIgnoreCase(name)) return player; return null; }
    private PassData state(UUID id, String name) { PassData data = players.computeIfAbsent(id, ignored -> new PassData(name == null ? id.toString() : name)); if (name != null) data.name = name; return data; }

    private void loadPlayers() {
        ConfigurationSection root = playerStore.getConfigurationSection("players"); if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key); String base = "players." + key;
                PassData data = new PassData(playerStore.getString(base + ".name", key));
                int savedSeason = playerStore.getInt(base + ".season", season);
                if (savedSeason == season) {
                    data.xp = Math.max(0, Math.min(maxXp(), playerStore.getInt(base + ".xp", 0)));
                    data.premium = playerStore.getBoolean(base + ".premium", false);
                    data.blocks = playerStore.getInt(base + ".blocks", 0);
                    data.freeClaims.addAll(playerStore.getIntegerList(base + ".claimed-free"));
                    data.premiumClaims.addAll(playerStore.getIntegerList(base + ".claimed-premium"));
                }
                players.put(id, data);
            } catch (Exception ex) { getLogger().warning("Ignored invalid Battle Pass player record: " + key); }
        }
    }

    private void savePlayers() {
        if (playerStore == null || playersFile == null) return;
        for (Map.Entry<UUID, PassData> entry : players.entrySet()) {
            String base = "players." + entry.getKey(); PassData data = entry.getValue();
            playerStore.set(base + ".name", data.name); playerStore.set(base + ".season", season); playerStore.set(base + ".xp", data.xp);
            playerStore.set(base + ".premium", data.premium); playerStore.set(base + ".blocks", data.blocks);
            playerStore.set(base + ".claimed-free", new ArrayList<>(data.freeClaims)); playerStore.set(base + ".claimed-premium", new ArrayList<>(data.premiumClaims));
        }
        try { playerStore.save(playersFile); dirty = false; } catch (IOException ex) { getLogger().severe("Could not save Battle Pass data: " + ex.getMessage()); }
    }

    private record Reward(Material icon, String name, List<String> lore, List<String> actions) {}
    private record ClaimTarget(int level, boolean premium) {}
    private static final class PassData { String name; int xp, blocks; boolean premium; final Set<Integer> freeClaims = new TreeSet<>(), premiumClaims = new TreeSet<>(); PassData(String name) { this.name = name; } }
}
