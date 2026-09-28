package com.merelyme.daily;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.event.player.PlayerTradeEvent;

import java.io.File;
import java.lang.reflect.Method;
import java.time.*;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public final class MerelyDaily extends JavaPlugin implements Listener, CommandExecutor {
    private static final String PREFIX = "§b§lDAILY §8» §r";
    private static final String WHEEL_TITLE = "§f\uF804\uE213";
    private static final String LOGIN_TITLE = "§8Daily Login Rewards";
    private static final String VOTE_TITLE = "§f\uF804\uE212";
    private static final String VOTE_POLL = "update-1";
    private static final Map<Integer, String> VOTE_OPTIONS = Map.of(
            10, "free_battle_pass",
            12, "premium_battle_pass",
            14, "clan_wars",
            16, "lootable_structures");
    private final Map<UUID, PlayerData> players = new HashMap<>();
    private final Map<UUID, Double> lastMoney = new HashMap<>();
    private final Map<UUID, Location> lastWalk = new HashMap<>();
    private final Set<UUID> spinning = new HashSet<>();
    private static final List<WheelPrize> WHEEL_PRIZES = List.of(
            new WheelPrize("money_25k", 35, Material.GOLD_INGOT, "§a§l$25,000"),
            new WheelPrize("points_250", 30, Material.LIGHT_BLUE_DYE, "§b§l250 POINTS"),
            new WheelPrize("money_50k", 12, Material.GOLD_BLOCK, "§a§l$50,000"),
            new WheelPrize("diamond_block", 8, Material.DIAMOND_BLOCK, "§b§lDIAMOND BLOCK"),
            new WheelPrize("enchanted_gapple", 6, Material.ENCHANTED_GOLDEN_APPLE, "§6§lENCHANTED GOLDEN APPLE"),
            new WheelPrize("points_500", 4, Material.CYAN_DYE, "§b§l500 POINTS"),
            new WheelPrize("exclusive_tag", 2, Material.NAME_TAG, "§d§lEXCLUSIVE TAG"),
            new WheelPrize("skeleton_spawner", 1, Material.SPAWNER, "§f§lSKELETON SPAWNER"),
            new WheelPrize("points_1000", 1, Material.NETHER_STAR, "§d§l1,000 POINTS"),
            new WheelPrize("money_100k", 1, Material.EMERALD_BLOCK, "§a§l$100,000"));
    private File dataFile;
    private YamlConfiguration data;
    private ZoneId zone;
    private LocalDate activeDate;
    private List<Quest> dailyQuests = List.of();
    private String activeWeek = "";
    private List<Quest> weeklyQuests = List.of();
    private int dailyReward;
    private Object economy;
    private Method getBalance;

    @Override public void onEnable() {
        saveDefaultConfig();
        try { zone = ZoneId.of(getConfig().getString("timezone", "Europe/Lisbon")); }
        catch (Exception ignored) { zone = ZoneId.of("Europe/Lisbon"); }
        dataFile = new File(getDataFolder(), "players.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        loadPlayers();
        refreshDay();
        refreshWeek();
        hookVault();
        Objects.requireNonNull(getCommand("daily")).setExecutor(this);
        Objects.requireNonNull(getCommand("weekly")).setExecutor(this);
        Objects.requireNonNull(getCommand("dailylogin")).setExecutor(this);
        Objects.requireNonNull(getCommand("updatevote")).setExecutor(this);
        Objects.requireNonNull(getCommand("dailytoken")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tickMinute, 20L * 60L, 20L * 60L);
        Bukkit.getScheduler().runTaskTimer(this, this::tickMoney, 20L * 20L, 20L * 20L);
        for (Player player : Bukkit.getOnlinePlayers()) prepareJoin(player);
        getLogger().info("MerelyDaily enabled. Daily Wheel ready; weekly: " + weeklyQuests);
    }

    @Override public void onDisable() { savePlayers(); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("dailytoken")) return handleDailyToken(sender, args);
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        refreshDay(); refreshWeek();
        if (command.getName().equalsIgnoreCase("weekly")) showWeekly(player);
        else if (command.getName().equalsIgnoreCase("dailylogin")) openLogin(player);
        else if (command.getName().equalsIgnoreCase("updatevote")) openUpdateVote(player);
        else openDailyWheel(player);
        return true;
    }

    private boolean handleDailyToken(CommandSender sender, String[] args) {
        if (!sender.hasPermission("merelydaily.admin")) { sender.sendMessage(PREFIX + "§cYou do not have permission."); return true; }
        if (args.length != 3 || !args[0].equalsIgnoreCase("give")) { sender.sendMessage(PREFIX + "§e/dailytoken give <player> <amount>"); return true; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(PREFIX + "§cThat player must be online."); return true; }
        int amount;
        try { amount = Integer.parseInt(args[2]); } catch (NumberFormatException ex) { sender.sendMessage(PREFIX + "§cInvalid amount."); return true; }
        if (amount < 1 || amount > 100) { sender.sendMessage(PREFIX + "§cAmount must be between 1 and 100."); return true; }
        PlayerData state = state(target);
        state.wheelTokens += amount;
        savePlayers();
        target.sendMessage(PREFIX + "§aYou received §b" + amount + " Daily Wheel Spin" + (amount == 1 ? "" : "s") + "§a. Use §f/daily§a.");
        sender.sendMessage(PREFIX + "§aGave §b" + amount + " Daily Wheel Spin" + (amount == 1 ? "" : "s") + " §ato §f" + target.getName() + "§a.");
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR) public void onJoin(PlayerJoinEvent event) { prepareJoin(event.getPlayer()); }
    private void prepareJoin(Player player) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) return;
            refreshDay();
            refreshWeek();
            PlayerData state = state(player);
            if (!state.starter) grantStarter(player, state);
            processLoginReward(player, state);
            Double balance = readMoney(player);
            if (balance != null) lastMoney.put(player.getUniqueId(), balance);
            lastWalk.put(player.getUniqueId(), player.getLocation().clone());
            boolean wheelUsed = LocalDate.now(zone).toString().equals(state.wheelDate);
            player.sendMessage(PREFIX + (wheelUsed ? "§aYou already used today's Daily Wheel." : "§eYour Daily Wheel is ready! §fUse §b/daily §fto spin."));
            player.sendMessage(PREFIX + "§fDaily Login streak: §bDay " + state.loginStreak + "§7. §fUse §b/dailylogin §fto view all rewards.");
            showWeekly(player);
        }, 40L);
    }

    private void processLoginReward(Player player, PlayerData state) {
        LocalDate today = LocalDate.now(zone);
        if (today.toString().equals(state.loginDate)) return;
        LocalDate previous = null;
        try { if (!state.loginDate.isBlank()) previous = LocalDate.parse(state.loginDate); } catch (Exception ignored) {}
        state.loginStreak = previous != null && previous.plusDays(1).equals(today) ? state.loginStreak % 7 + 1 : 1;
        state.loginDate = today.toString();
        LoginReward reward = loginReward(state.loginStreak);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "points give " + player.getName() + " " + reward.points);
        if (reward.key != null) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "keys give " + player.getName() + " " + reward.key + " 1");
        savePlayers();
        player.sendTitle("§b§lDAILY LOGIN §8• §fDAY " + state.loginStreak, "§e+" + format(reward.points) + " Points" + (reward.key == null ? "" : " §8+ §a1 " + pretty(reward.key) + " Key"), 5, 55, 10);
        player.sendMessage(PREFIX + "§aDay " + state.loginStreak + " claimed: §e" + format(reward.points) + " Points" + (reward.key == null ? "" : " §7and §a1 " + pretty(reward.key) + " Key") + "§a.");
    }

    private LoginReward loginReward(int day) {
        return switch (day) {
            case 1 -> new LoginReward(150, null);
            case 2 -> new LoginReward(200, "coal");
            case 3 -> new LoginReward(300, "iron");
            case 4 -> new LoginReward(400, "gold");
            case 5 -> new LoginReward(500, "emerald");
            case 6 -> new LoginReward(750, "emerald");
            default -> new LoginReward(1000, "special");
        };
    }

    private void openLogin(Player player) {
        PlayerData state = state(player);
        Inventory inv = Bukkit.createInventory(null, 27, LOGIN_TITLE);
        Material[] icons = {Material.COAL, Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND, Material.EMERALD, Material.NETHERITE_INGOT, Material.NETHER_STAR};
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        for (int day = 1; day <= 7; day++) {
            LoginReward reward = loginReward(day);
            boolean claimed = day <= state.loginStreak && LocalDate.now(zone).toString().equals(state.loginDate);
            ItemStack item = new ItemStack(icons[day - 1]);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName((claimed ? "§a§l" : "§b§l") + "DAY " + day);
            List<String> lore = new ArrayList<>();
            lore.add("§e" + format(reward.points) + " Points");
            if (reward.key != null) lore.add("§a1 " + pretty(reward.key) + " Key");
            lore.add("");
            lore.add(claimed ? "§a✔ Claimed" : day == state.loginStreak + 1 ? "§eNext reward" : "§7Keep your streak to unlock");
            meta.setLore(lore);
            if (claimed) { meta.addEnchant(Enchantment.UNBREAKING, 1, true); meta.addItemFlags(ItemFlag.HIDE_ENCHANTS); }
            item.setItemMeta(meta); inv.setItem(slots[day - 1], item);
        }
        inv.setItem(22, menuItem(Material.CLOCK, "§f§lYOUR STREAK", "§7Current streak: §b" + state.loginStreak + " days", "§7Miss one day and it returns to Day 1."));
        fill(inv); player.openInventory(inv);
    }

    private void openUpdateVote(Player player) {
        PlayerData state = state(player);
        Inventory inv = Bukkit.createInventory(null, 27, VOTE_TITLE);
        inv.setItem(10, voteItem(Material.BOOK, "free_battle_pass", "§a§lFREE BATTLE PASS", "§7A complete free progression track."));
        inv.setItem(12, voteItem(Material.NETHER_STAR, "premium_battle_pass", "§6§lPREMIUM BATTLE PASS", "§7An extra premium track with exclusive rewards."));
        inv.setItem(14, voteItem(Material.IRON_SWORD, "clan_wars", "§c§lCLAN WARS", "§7Whole clans fight in private tournament arenas."));
        inv.setItem(16, voteItem(Material.MAP, "lootable_structures", "§b§lMORE LOOTABLE STRUCTURES", "§7More structures with refillable POI-style loot."));
        String selected = VOTE_POLL.equals(state.votePoll) ? pretty(state.voteChoice) : "None";
        inv.setItem(22, menuItem(Material.IRON_INGOT, "§b§lNEXT UPDATE VOTE", "§7Your vote: §f" + selected, "§7Vote once to receive §f1 Iron Key§7.", "§8One reward per poll."));
        fill(inv); player.openInventory(inv);
    }

    private ItemStack voteItem(Material material, String id, String name, String description) {
        long votes = players.values().stream().filter(s -> VOTE_POLL.equals(s.votePoll) && id.equals(s.voteChoice)).count();
        return menuItem(material, name, description, "", "§eVotes: §f" + votes, "§aClick to vote");
    }

    @EventHandler public void onMenuClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.equals(LOGIN_TITLE) && !title.equals(VOTE_TITLE) && !title.equals(WHEEL_TITLE)) return;
        event.setCancelled(true);
        if (title.equals(WHEEL_TITLE) && event.getWhoClicked() instanceof Player player && event.getRawSlot() == 13) {
            spinDailyWheel(player);
            return;
        }
        if (title.equals(VOTE_TITLE) && event.getWhoClicked() instanceof Player player) {
            String choice = VOTE_OPTIONS.get(event.getRawSlot());
            if (choice != null) castVote(player, choice);
        }
    }

    private void castVote(Player player, String choice) {
        PlayerData state = state(player);
        if (VOTE_POLL.equals(state.votePoll)) {
            player.sendMessage(PREFIX + "§cYou already voted for §f" + pretty(state.voteChoice) + "§c in this poll.");
            player.closeInventory();
            return;
        }
        state.votePoll = VOTE_POLL;
        state.voteChoice = choice;
        savePlayers();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "keys give " + player.getName() + " iron 1");
        player.closeInventory();
        player.sendTitle("§b§lVOTE CONFIRMED", "§f1 Iron Key received", 5, 45, 10);
        player.sendMessage(PREFIX + "§aYou voted for §f" + pretty(choice) + "§a and received §f1 Iron Key§a.");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.5f);
    }

    private ItemStack menuItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name); meta.setLore(Arrays.asList(lore)); item.setItemMeta(meta); return item;
    }
    private void fill(Inventory inv) { ItemStack pane = menuItem(Material.GRAY_STAINED_GLASS_PANE, " "); for (int i=0;i<inv.getSize();i++) if (inv.getItem(i)==null) inv.setItem(i,pane); }
    private static String pretty(String value) { return Arrays.stream(value.split("_" )).map(s -> s.substring(0,1).toUpperCase(Locale.ROOT)+s.substring(1).toLowerCase(Locale.ROOT)).reduce((a,b)->a+" "+b).orElse(value); }

    private void grantStarter(Player player, PlayerData state) {
        int money = getConfig().getInt("starter.money", 25000);
        String type = getConfig().getString("starter.spawner-type", "skeleton");
        int amount = Math.max(1, getConfig().getInt("starter.spawner-amount", 1));
        boolean paid = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eco give " + player.getName() + " " + money);
        boolean spawner = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spawners give " + player.getName() + " " + type + " " + amount);
        if (paid && spawner) {
            state.starter = true;
            savePlayers();
            player.sendMessage("");
            player.sendMessage("§a§lWELCOME REWARD");
            player.sendMessage("§fYou received §a$" + String.format(Locale.US, "%,d", money) + " §fand §b1 Skeleton Spawner§f.");
            player.sendMessage("§7This reward can only be claimed once.");
            player.sendMessage("");
        } else getLogger().warning("Starter reward commands could not be dispatched for " + player.getName());
    }

    private void openDailyWheel(Player player) {
        PlayerData state = state(player);
        Inventory inv = Bukkit.createInventory(null, 45, WHEEL_TITLE);
        ItemStack pane = menuItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
        for (int column = 0; column < 9; column++) {
            WheelPrize preview = WHEEL_PRIZES.get(column % WHEEL_PRIZES.size());
            inv.setItem(9 + column, menuItem(preview.icon, preview.name, "§7Possible Daily Wheel reward"));
        }
        inv.setItem(4, menuItem(Material.LIME_STAINED_GLASS_PANE, "§a§l▼ WINNER ▼"));
        boolean used = LocalDate.now(zone).toString().equals(state.wheelDate);
        if (spinning.contains(player.getUniqueId())) {
            inv.setItem(13, menuItem(Material.CLOCK, "§e§lSPINNING...", "§7Your reward is being selected."));
        } else if (used && state.wheelTokens <= 0) {
            inv.setItem(13, menuItem(Material.BARRIER, "§c§lALREADY CLAIMED", "§7Come back tomorrow for another spin."));
        } else {
            ItemStack button = menuItem(Material.NETHER_STAR, "§b§lSPIN THE DAILY WHEEL", "§fClick to receive one random reward.", used ? "§dUses one Battle Pass Wheel Spin." : "§7Your free daily spin is ready.");
            ItemMeta meta = button.getItemMeta(); meta.addEnchant(Enchantment.UNBREAKING, 1, true); meta.addItemFlags(ItemFlag.HIDE_ENCHANTS); button.setItemMeta(meta);
            inv.setItem(13, button);
        }
        inv.setItem(22, menuItem(Material.LIME_STAINED_GLASS_PANE, "§a§l▲ WINNER ▲", used ? "§aDaily spin claimed" : "§7The reward stops in the middle.", "§dExtra spins: §f" + state.wheelTokens, "§7Daily spin resets at midnight Portugal time."));
        int[] previewSlots = {29, 30, 31, 32, 33, 38, 39, 40, 41, 42};
        for (int i = 0; i < WHEEL_PRIZES.size(); i++) {
            WheelPrize prize = WHEEL_PRIZES.get(i);
            inv.setItem(previewSlots[i], menuItem(prize.icon, prize.name,
                    "§7Possible reward", "§8The chances stay hidden."));
        }
        player.openInventory(inv);
    }

    private void spinDailyWheel(Player player) {
        UUID id = player.getUniqueId(); PlayerData state = state(player); String today = LocalDate.now(zone).toString();
        boolean useToken = today.equals(state.wheelDate);
        if (useToken && state.wheelTokens <= 0) { player.sendMessage(PREFIX + "§cYou already used today's Daily Wheel."); return; }
        if (!spinning.add(id)) return;
        WheelPrize winner = chooseWheelPrize();
        final int finalStep = 38;
        List<WheelPrize> reel = new ArrayList<>();
        for (int i = 0; i < finalStep + 9; i++) reel.add(chooseWheelPrize());
        reel.set(finalStep + 4, winner);
        runWheelStep(player, state, today, winner, reel, 0, finalStep, useToken);
    }

    private void runWheelStep(Player player, PlayerData state, String today, WheelPrize winner,
                              List<WheelPrize> reel, int step, int finalStep, boolean useToken) {
        UUID id = player.getUniqueId();
        if (!player.isOnline()) { spinning.remove(id); return; }
        if (player.getOpenInventory().getTitle().equals(WHEEL_TITLE)) {
            Inventory inv = player.getOpenInventory().getTopInventory();
            for (int column = 0; column < 9; column++) {
                WheelPrize shown = reel.get(step + column);
                String marker = column == 4 ? "§e§lSELECTING..." : shown.name;
                inv.setItem(9 + column, menuItem(shown.icon, marker, column == 4 ? shown.name : "§8Daily Wheel reward"));
            }
        }
        if (step >= finalStep) {
            if (useToken) state.wheelTokens = Math.max(0, state.wheelTokens - 1);
            else state.wheelDate = today;
            savePlayers();
            spinning.remove(id);
            grantWheelPrize(player, winner);
            if (player.getOpenInventory().getTitle().equals(WHEEL_TITLE)) {
                player.getOpenInventory().getTopInventory().setItem(13,
                        menuItem(winner.icon, "§a§lYOU WON!", winner.name, "", "§7Come back tomorrow."));
            }
            return;
        }
        float pitch = Math.max(0.65f, 1.65f - (step * 0.02f));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.55f, pitch);
        long delay = step < 12 ? 2L : step < 22 ? 3L : step < 29 ? 5L : step < 34 ? 8L : 12L;
        Bukkit.getScheduler().runTaskLater(this,
                () -> runWheelStep(player, state, today, winner, reel, step + 1, finalStep, useToken), delay);
    }

    private WheelPrize chooseWheelPrize() {
        int roll = ThreadLocalRandom.current().nextInt(100), total = 0;
        for (WheelPrize prize : WHEEL_PRIZES) { total += prize.weight; if (roll < total) return prize; }
        return WHEEL_PRIZES.get(0);
    }

    private void grantWheelPrize(Player player, WheelPrize prize) {
        String tagName = null;
        switch (prize.id) {
            case "money_25k" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eco give " + player.getName() + " 25000");
            case "points_250" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "points give " + player.getName() + " 250");
            case "money_50k" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eco give " + player.getName() + " 50000");
            case "diamond_block" -> giveItem(player, new ItemStack(Material.DIAMOND_BLOCK));
            case "enchanted_gapple" -> giveItem(player, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE));
            case "points_500" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "points give " + player.getName() + " 500");
            case "exclusive_tag" -> {
                String[] ids = {"wheel_jackpot", "wheel_fortune", "wheel_highroller"};
                String tag = ids[ThreadLocalRandom.current().nextInt(ids.length)];
                tagName = switch (tag) { case "wheel_jackpot" -> "JACKPOT"; case "wheel_fortune" -> "FORTUNE"; default -> "HIGH ROLLER"; };
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + player.getName() + " permission set sksmp.tag." + tag + " true");
            }
            case "skeleton_spawner" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spawners give " + player.getName() + " skeleton 1");
            case "points_1000" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "points give " + player.getName() + " 1000");
            case "money_100k" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eco give " + player.getName() + " 100000");
        }
        String display = tagName == null ? prize.name : "§d§l" + tagName + " TAG";
        player.sendTitle("§b§lDAILY WHEEL", display, 5, 55, 10);
        player.sendMessage(PREFIX + "§aYou won " + display + "§a!");
        if (tagName != null) player.sendMessage(PREFIX + "§fOpen §b/tags §fto equip your new exclusive tag.");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
    }

    private void giveItem(Player player, ItemStack item) {
        Map<Integer,ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) player.getWorld().dropItemNaturally(player.getLocation(), leftover);
    }

    private void showWeekly(Player player) {
        PlayerData state = state(player); ensureCurrentWeek(state);
        player.sendMessage("");
        player.sendMessage("§d§lWEEKLY QUESTS §8• §f" + activeWeek + " §8• §e5,000 Points");
        for (Quest q : weeklyQuests) {
            long progress = Math.min(q.target, state.weeklyProgress.getOrDefault(q.id, 0L));
            String mark = progress >= q.target ? "§a✔" : "§c✘";
            player.sendMessage(" " + mark + " §f" + q.label + " §8(§d" + format(progress) + "§7/§f" + format(q.target) + "§8)");
        }
        player.sendMessage(state.weeklyClaimed ? "§aReward claimed for this week." : "§7Complete both missions to automatically receive §e5,000 Points§7.");
        player.sendMessage("");
    }

    private void tickMinute() {
        boolean changed = refreshDay(), weekChanged = refreshWeek();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData state = state(player);
            ensureCurrent(state);
            ensureCurrentWeek(state);
            if (hasQuest(QuestType.PLAY_MINUTES)) add(player, QuestType.PLAY_MINUTES, 1);
            if (hasQuest(QuestType.AFK_MINUTES) && inAfkZone(player)) add(player, QuestType.AFK_MINUTES, 1);
            if (changed) player.sendMessage(PREFIX + "§eA new Daily Wheel spin is available! §fUse §b/daily§f.");
            if (weekChanged) showWeekly(player);
        }
        savePlayers();
    }

    private void tickMoney() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Double now = readMoney(player);
            if (now == null) continue;
            Double before = lastMoney.put(player.getUniqueId(), now);
            if (before != null && now > before + 0.01) add(player, QuestType.EARN_MONEY, Math.round(now - before));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR) public void onDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null && killer != event.getEntity()) add(killer, QuestType.KILL_PLAYERS, 1);
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR) public void onMobDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) return;
        Player killer = event.getEntity().getKiller();
        if (killer != null) add(killer, QuestType.KILL_MOBS, 1);
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR) public void onBreak(BlockBreakEvent event) {
        add(event.getPlayer(), QuestType.BREAK_BLOCKS, 1);
        if (isOre(event.getBlock().getType())) add(event.getPlayer(), QuestType.MINE_ORES, 1);
        if (isCrop(event.getBlock())) add(event.getPlayer(), QuestType.HARVEST_CROPS, 1);
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR) public void onPlace(BlockPlaceEvent event) { add(event.getPlayer(), QuestType.PLACE_BLOCKS, 1); }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR) public void onFish(PlayerFishEvent event) { if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) add(event.getPlayer(), QuestType.FISH, 1); }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR) public void onCraft(org.bukkit.event.inventory.CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player && event.getCurrentItem() != null) add(player, QuestType.CRAFT_ITEMS, event.getCurrentItem().getAmount());
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR) public void onEnchant(EnchantItemEvent event) { add(event.getEnchanter(), QuestType.ENCHANT_ITEMS, 1); }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR) public void onBreed(EntityBreedEvent event) { if (event.getBreeder() instanceof Player player) add(player, QuestType.BREED_ANIMALS, 1); }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR) public void onTrade(PlayerTradeEvent event) { add(event.getPlayer(), QuestType.TRADE_VILLAGER, 1); }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR) public void onEat(PlayerItemConsumeEvent event) { if (event.getItem().getType().isEdible()) add(event.getPlayer(), QuestType.EAT_FOOD, 1); }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR) public void onMove(PlayerMoveEvent event) {
        if (!hasQuest(QuestType.WALK_BLOCKS) || event.getTo() == null) return;
        Player player = event.getPlayer(); Location to = event.getTo(); Location from = lastWalk.put(player.getUniqueId(), to.clone());
        if (from == null || !from.getWorld().equals(to.getWorld())) return;
        double distance = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
        if (distance > 0.05 && distance < 12) {
            PlayerData state = state(player); state.walkRemainder += distance;
            long whole = (long) state.walkRemainder;
            if (whole > 0) { state.walkRemainder -= whole; add(player, QuestType.WALK_BLOCKS, whole); }
        }
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { lastMoney.remove(event.getPlayer().getUniqueId()); lastWalk.remove(event.getPlayer().getUniqueId()); savePlayers(); }

    private void add(Player player, QuestType type, long amount) {
        if (amount <= 0) return;
        refreshDay(); refreshWeek();
        PlayerData state = state(player); ensureCurrent(state); ensureCurrentWeek(state);
        updateQuestSet(player, state, type, amount, false);
        updateQuestSet(player, state, type, amount, true);
    }

    private void updateQuestSet(Player player, PlayerData state, QuestType type, long amount, boolean weekly) {
        List<Quest> set = weekly ? weeklyQuests : dailyQuests;
        Quest quest = set.stream().filter(q -> q.type == type).findFirst().orElse(null);
        if (quest == null || (weekly ? state.weeklyClaimed : state.claimed)) return;
        Map<String,Long> progressMap = weekly ? state.weeklyProgress : state.progress;
        long old = progressMap.getOrDefault(quest.id, 0L);
        long updated = Math.min(quest.target, old + amount);
        if (updated == old) return;
        progressMap.put(quest.id, updated);
        if (updated >= quest.target && old < quest.target) {
            player.sendMessage((weekly ? "§d§lWEEKLY §8» §r" : PREFIX) + "§aQuest complete: §f" + quest.label);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.7f);
        }
        if (set.stream().allMatch(q -> progressMap.getOrDefault(q.id, 0L) >= q.target)) reward(player, state, weekly);
    }

    private void reward(Player player, PlayerData state, boolean weekly) {
        int points = weekly ? getConfig().getInt("weekly.reward-points", 5000) : dailyReward;
        if (Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "points give " + player.getName() + " " + points)) {
            if (weekly) state.weeklyClaimed = true; else state.claimed = true; savePlayers();
            player.sendTitle(weekly ? "§d§lWEEKLY COMPLETE" : "§b§lDAILY COMPLETE", "§e+" + format(points) + " Points", 5, 50, 10);
            player.sendMessage((weekly ? "§d§lWEEKLY §8» §r" : PREFIX) + "§aAll quests completed! §e+" + format(points) + " Points");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
    }

    private boolean refreshDay() {
        LocalDate today = LocalDate.now(zone);
        if (today.equals(activeDate)) return false;
        activeDate = today; dailyQuests = List.of();
        for (PlayerData state : players.values()) ensureCurrent(state);
        savePlayers(); return true;
    }

    private boolean refreshWeek() {
        LocalDate today=LocalDate.now(zone); WeekFields wf=WeekFields.ISO;
        String key=today.get(wf.weekBasedYear())+"-W"+String.format(Locale.ROOT,"%02d",today.get(wf.weekOfWeekBasedYear()));
        if(key.equals(activeWeek))return false;
        activeWeek=key; weeklyQuests=questsFor(today.with(DayOfWeek.MONDAY),true);
        for(PlayerData state:players.values())ensureCurrentWeek(state);
        savePlayers(); return true;
    }

    private List<Quest> questsFor(LocalDate date, boolean weekly) {
        Random random = new Random(date.toEpochDay() * 1_000_003L + (weekly ? 0x5745454B4CL : 0x4441494C59L));
        List<QuestType> types = new ArrayList<>(Arrays.asList(QuestType.values()));
        Collections.shuffle(types, random);
        List<Quest> result = new ArrayList<>();
        for (int i = 0; i < (weekly ? 2 : 3); i++) result.add(makeQuest(types.get(i), random, weekly));
        return List.copyOf(result);
    }

    private Quest makeQuest(QuestType type, Random random, boolean weekly) {
        long[] choices = weekly ? switch(type) {
            case KILL_PLAYERS -> new long[]{15,25,35}; case KILL_MOBS -> new long[]{150,225,300};
            case EARN_MONEY -> new long[]{500000,1000000,1500000}; case AFK_MINUTES -> new long[]{120,180,240};
            case BREAK_BLOCKS -> new long[]{1500,2500,4000}; case PLACE_BLOCKS -> new long[]{500,1000,1500};
            case MINE_ORES -> new long[]{120,200,300}; case HARVEST_CROPS -> new long[]{750,1500,2500};
            case WALK_BLOCKS -> new long[]{10000,18000,25000}; case PLAY_MINUTES -> new long[]{180,360,600};
            case FISH -> new long[]{20,35,50}; case CRAFT_ITEMS -> new long[]{400,800,1200};
            case ENCHANT_ITEMS -> new long[]{10,15,25}; case BREED_ANIMALS -> new long[]{20,30,50};
            case TRADE_VILLAGER -> new long[]{25,40,60}; case EAT_FOOD -> new long[]{50,100,150};
        } : switch (type) {
            case KILL_PLAYERS -> new long[]{2,3,5}; case KILL_MOBS -> new long[]{20,35,50};
            case EARN_MONEY -> new long[]{25000,50000,70000}; case AFK_MINUTES -> new long[]{10,20,30};
            case BREAK_BLOCKS -> new long[]{128,256,384}; case PLACE_BLOCKS -> new long[]{64,128,192};
            case MINE_ORES -> new long[]{16,32,48}; case HARVEST_CROPS -> new long[]{64,128,256};
            case WALK_BLOCKS -> new long[]{750,1500,2500}; case PLAY_MINUTES -> new long[]{30,60,90};
            case FISH -> new long[]{3,5,8}; case CRAFT_ITEMS -> new long[]{32,64,128};
            case ENCHANT_ITEMS -> new long[]{1,2,3}; case BREED_ANIMALS -> new long[]{2,4,6};
            case TRADE_VILLAGER -> new long[]{3,5,8}; case EAT_FOOD -> new long[]{10,20,30};
        };
        long target = choices[random.nextInt(choices.length)];
        String label = switch (type) {
            case KILL_PLAYERS -> "Kill " + target + " players"; case KILL_MOBS -> "Kill " + target + " mobs";
            case EARN_MONEY -> "Earn $" + format(target); case AFK_MINUTES -> "Stay in the AFK Zone for " + target + " minutes";
            case BREAK_BLOCKS -> "Break " + target + " blocks"; case PLACE_BLOCKS -> "Place " + target + " blocks";
            case MINE_ORES -> "Mine " + target + " ores"; case HARVEST_CROPS -> "Harvest " + target + " crops";
            case WALK_BLOCKS -> "Travel " + format(target) + " blocks"; case PLAY_MINUTES -> "Play for " + target + " minutes";
            case FISH -> "Catch " + target + " fish"; case CRAFT_ITEMS -> "Craft " + target + " items";
            case ENCHANT_ITEMS -> "Enchant " + target + " items"; case BREED_ANIMALS -> "Breed " + target + " animals";
            case TRADE_VILLAGER -> "Complete " + target + " villager trades"; case EAT_FOOD -> "Eat " + target + " food items";
        };
        return new Quest(type.name().toLowerCase(Locale.ROOT), type, target, label);
    }

    private boolean hasQuest(QuestType type) { return dailyQuests.stream().anyMatch(q -> q.type == type) || weeklyQuests.stream().anyMatch(q -> q.type == type); }
    private PlayerData state(Player player) { return players.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerData(player.getName())); }
    private void ensureCurrent(PlayerData state) { if (!activeDate.toString().equals(state.date)) { state.date = activeDate.toString(); state.progress.clear(); state.claimed = false; state.walkRemainder = 0; } }
    private void ensureCurrentWeek(PlayerData state) { if(!activeWeek.equals(state.week)){state.week=activeWeek;state.weeklyProgress.clear();state.weeklyClaimed=false;} }

    private boolean inAfkZone(Player p) {
        String world = getConfig().getString("afk-zone.world", "world"); if (!p.getWorld().getName().equalsIgnoreCase(world)) return false;
        int x=p.getLocation().getBlockX(), y=p.getLocation().getBlockY(), z=p.getLocation().getBlockZ();
        int x1=getConfig().getInt("afk-zone.corner-one.x"), x2=getConfig().getInt("afk-zone.corner-two.x");
        int y1=getConfig().getInt("afk-zone.corner-one.y"), y2=getConfig().getInt("afk-zone.corner-two.y");
        int z1=getConfig().getInt("afk-zone.corner-one.z"), z2=getConfig().getInt("afk-zone.corner-two.z");
        return x>=Math.min(x1,x2)&&x<=Math.max(x1,x2)&&y>=Math.min(y1,y2)&&y<=Math.max(y1,y2)&&z>=Math.min(z1,z2)&&z<=Math.max(z1,z2);
    }
    private boolean isOre(Material m) { return m.name().endsWith("_ORE") || m == Material.ANCIENT_DEBRIS; }
    private boolean isCrop(Block b) { return switch (b.getType()) { case WHEAT,CARROTS,POTATOES,BEETROOTS,NETHER_WART,COCOA,SUGAR_CANE,BAMBOO,CACTUS,KELP,KELP_PLANT,MELON,PUMPKIN -> true; default -> false; }; }

    private void hookVault() {
        try { Class<?> type=Class.forName("net.milkbowl.vault.economy.Economy"); @SuppressWarnings({"rawtypes","unchecked"}) RegisteredServiceProvider<?> reg=Bukkit.getServicesManager().getRegistration((Class)type); if(reg!=null){economy=reg.getProvider();getBalance=type.getMethod("getBalance",OfflinePlayer.class);} }
        catch(Exception ex){getLogger().warning("Vault balance hook unavailable: "+ex.getMessage());}
    }
    private Double readMoney(Player player) { if(economy==null)return null; try{return ((Number)getBalance.invoke(economy,player)).doubleValue();}catch(Exception ignored){return null;} }

    private void loadPlayers() {
        ConfigurationSection root=data.getConfigurationSection("players"); if(root==null)return;
        for(String key:root.getKeys(false)) try { UUID id=UUID.fromString(key); PlayerData s=new PlayerData(root.getString(key+".name",key)); s.starter=root.getBoolean(key+".starter",false); s.date=root.getString(key+".daily.date",""); s.claimed=root.getBoolean(key+".daily.claimed",false); ConfigurationSection p=root.getConfigurationSection(key+".daily.progress"); if(p!=null)for(String q:p.getKeys(false))s.progress.put(q,p.getLong(q)); s.wheelDate=root.getString(key+".wheel.date",""); s.wheelTokens=root.getInt(key+".wheel.tokens",0); s.week=root.getString(key+".weekly.week",""); s.weeklyClaimed=root.getBoolean(key+".weekly.claimed",false); ConfigurationSection wp=root.getConfigurationSection(key+".weekly.progress");if(wp!=null)for(String q:wp.getKeys(false))s.weeklyProgress.put(q,wp.getLong(q)); s.loginDate=root.getString(key+".login.date","");s.loginStreak=root.getInt(key+".login.streak",0);s.votePoll=root.getString(key+".vote.poll","");s.voteChoice=root.getString(key+".vote.choice","");players.put(id,s); } catch(Exception ex){getLogger().warning("Ignored invalid player data "+key);}
    }
    private void savePlayers() {
        if(data==null)return; data.set("players",null);
        for(Map.Entry<UUID,PlayerData> e:players.entrySet()){String b="players."+e.getKey();PlayerData s=e.getValue();data.set(b+".name",s.name);data.set(b+".starter",s.starter);data.set(b+".wheel.date",s.wheelDate);data.set(b+".wheel.tokens",s.wheelTokens);data.set(b+".daily",null);data.set(b+".weekly.week",s.week);data.set(b+".weekly.claimed",s.weeklyClaimed);for(Map.Entry<String,Long> p:s.weeklyProgress.entrySet())data.set(b+".weekly.progress."+p.getKey(),p.getValue());data.set(b+".login.date",s.loginDate);data.set(b+".login.streak",s.loginStreak);data.set(b+".vote.poll",s.votePoll);data.set(b+".vote.choice",s.voteChoice);}
        try{data.save(dataFile);}catch(Exception ex){getLogger().log(Level.SEVERE,"Could not save players.yml",ex);}
    }
    private static String format(long n){return String.format(Locale.US,"%,d",n);}

    private enum QuestType { KILL_PLAYERS,KILL_MOBS,EARN_MONEY,AFK_MINUTES,BREAK_BLOCKS,PLACE_BLOCKS,MINE_ORES,HARVEST_CROPS,WALK_BLOCKS,PLAY_MINUTES,FISH,CRAFT_ITEMS,ENCHANT_ITEMS,BREED_ANIMALS,TRADE_VILLAGER,EAT_FOOD }
    private record Quest(String id, QuestType type, long target, String label) {}
    private record LoginReward(int points, String key) {}
    private record WheelPrize(String id, int weight, Material icon, String name) {}
    private static final class PlayerData { final String name; boolean starter,claimed,weeklyClaimed; String date="",wheelDate="",week="",loginDate="",votePoll="",voteChoice=""; int loginStreak,wheelTokens; double walkRemainder; final Map<String,Long> progress=new HashMap<>(),weeklyProgress=new HashMap<>(); PlayerData(String name){this.name=name;} }
}
