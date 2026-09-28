package me.merelyme.events;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class MerelyEventsPlugin extends JavaPlugin implements Listener {
    private enum EventType { POINTS, MONEY, TRASH, POI, POINTSHOP, HUNT }

    private static final Pattern DURATION = Pattern.compile("^(\\d+)(s|m|h)$", Pattern.CASE_INSENSITIVE);
    private static final DecimalFormat MONEY = new DecimalFormat("0.##");
    private static final int GLOBAL_GOAL_TARGET = 30;
    private static final long BLOCK_GOAL_TARGET = 750_000L;
    private static final int BLOCK_GOAL_REWARD = 3_000;
    private static final String CALENDAR_TITLE = "§8Server Event Calendar";
    private static final ZoneId LISBON = ZoneId.of("Europe/Lisbon");
    private static final DateTimeFormatter CALENDAR_INPUT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final DateTimeFormatter CALENDAR_DISPLAY = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy • HH:mm", Locale.ENGLISH);

    private EventType active;
    private long startedAt;
    private long endsAt;
    private BossBar bar;
    private BossBar goalBar;
    private boolean globalGoalClaimed;
    private long blockGoalProgress;
    private boolean blockGoalClaimed;
    private long blockGoalUnlockAt;
    private long blockGoalCompletionVisibleUntil;
    private File calendarFile;
    private YamlConfiguration calendarData;
    private Object economy;
    private Method getBalance;
    private Method depositPlayer;
    private Method papiSetPlaceholders;
    private final Map<UUID, Double> lastMoney = new HashMap<>();
    private final Map<UUID, Long> lastPoints = new HashMap<>();
    private final Map<Path, byte[]> pointshopBackups = new HashMap<>();
    private String keyAllCrate = "special";
    private UUID huntTarget;
    private long nextPoiRefillAt;

    @Override
    public void onEnable() {
        reloadConfig();
        globalGoalClaimed = getConfig().getBoolean("global-player-goal.claimed", false);
        blockGoalProgress = Math.max(0L, getConfig().getLong("global-block-goal.progress", 0L));
        blockGoalClaimed = getConfig().getBoolean("global-block-goal.claimed", false);
        getConfig().set("global-player-goal.target", GLOBAL_GOAL_TARGET);
        getConfig().set("global-player-goal.reward", "1 special key");
        getConfig().set("global-block-goal.target", BLOCK_GOAL_TARGET);
        getConfig().set("global-block-goal.reward", BLOCK_GOAL_REWARD + " points");
        saveConfig();
        loadCalendar();
        hookVault();
        hookPlaceholderApi();
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskLater(this, this::refreshGlobalGoal, 20L);
        getLogger().info("MerelyEvents enabled. Use /event <points|money|trash|poi|pointshop|hunt> <duration>.");
    }

    @Override
    public void onDisable() {
        saveBlockGoalProgress();
        stopEvent(false);
        if (goalBar != null) goalBar.removeAll();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("calendar")) return handleCalendar(sender, args);
        if (!sender.hasPermission("merelyevents.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to manage events.");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("goal")) {
            if (args.length == 2 && args[1].equalsIgnoreCase("reset")) {
                globalGoalClaimed = false;
                blockGoalClaimed = false;
                blockGoalProgress = 0L;
                blockGoalUnlockAt = 0L;
                blockGoalCompletionVisibleUntil = 0L;
                getConfig().set("global-player-goal.claimed", false);
                getConfig().set("global-player-goal.completed-at", null);
                getConfig().set("global-block-goal.claimed", false);
                getConfig().set("global-block-goal.completed-at", null);
                getConfig().set("global-block-goal.progress", 0L);
                saveConfig();
                refreshGlobalGoal();
                sender.sendMessage(ChatColor.GREEN + "Both global goals were reset.");
            } else {
                sender.sendMessage(ChatColor.AQUA + "Global Goal: " + ChatColor.WHITE + Bukkit.getOnlinePlayers().size() + "/" + GLOBAL_GOAL_TARGET
                        + ChatColor.GRAY + " online, reward: " + ChatColor.LIGHT_PURPLE + "1 Special Key" + ChatColor.GRAY + ". Status: "
                        + (globalGoalClaimed ? ChatColor.GREEN + "completed" : ChatColor.YELLOW + "active"));
                sender.sendMessage(ChatColor.GOLD + "Block Goal: " + ChatColor.WHITE + formatNumber(blockGoalProgress) + "/" + formatNumber(BLOCK_GOAL_TARGET)
                        + ChatColor.GRAY + ", reward: " + ChatColor.AQUA + formatNumber(BLOCK_GOAL_REWARD) + " Points" + ChatColor.GRAY + ". Status: "
                        + (blockGoalClaimed ? ChatColor.GREEN + "completed" : globalGoalClaimed ? ChatColor.YELLOW + "active" : ChatColor.GRAY + "locked"));
                sender.sendMessage(ChatColor.GRAY + "Use /event goal reset to run the goal again.");
            }
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("stop")) {
            if (active == null) sender.sendMessage(ChatColor.YELLOW + "There is no active event.");
            else stopEvent(true);
            return true;
        }

        // One-shot key giveaway. Both /event triplekeyall and the readable
        // /event triple keyall form are accepted. A third argument selects
        // the crate; otherwise the special crate is used.
        if (isTripleKeyAll(args)) {
            String crate = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT)
                    : (args.length == 2 && (args[0].equalsIgnoreCase("triplekeyall") || args[0].equalsIgnoreCase("triple-keyall"))
                    ? args[1].toLowerCase(Locale.ROOT) : "special");
            if (!validCrate(crate)) {
                sender.sendMessage(ChatColor.RED + "Use: /event triple keyall <coal|iron|gold|emerald|special>");
                return true;
            }
            distributeKeys(crate, 3, true);
            sender.sendMessage(ChatColor.GREEN + "Triple KeyAll sent to all online players.");
            return true;
        }
        if (args.length != 2) {
            sendUsage(sender);
            return true;
        }

        EventType type;
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "points", "2x", "2xpoints", "2x-points" -> type = EventType.POINTS;
            case "money", "1.5x", "1.5xmoney", "1.5x-money" -> type = EventType.MONEY;
            case "trash" -> type = EventType.TRASH;
            case "poi", "poi-supercharged", "superpoi" -> type = EventType.POI;
            case "pointshop", "shop", "promotion", "promo" -> type = EventType.POINTSHOP;
            case "hunt", "wanted", "bounty" -> type = EventType.HUNT;
            default -> { sendUsage(sender); return true; }
        }

        long seconds = parseDuration(args[1]);
        if (seconds < 10 || seconds > 86400) {
            sender.sendMessage(ChatColor.RED + "Duration must be between 10 seconds and 24 hours, for example 30s, 10m or 1h.");
            return true;
        }
        if (type == EventType.POINTS && papiSetPlaceholders == null) {
            sender.sendMessage(ChatColor.RED + "The Points hook is unavailable. Check PlaceholderAPI and SKCore.");
            return true;
        }
        if (type == EventType.MONEY && economy == null) {
            sender.sendMessage(ChatColor.RED + "The Money hook is unavailable. Check Vault and SKMoney.");
            return true;
        }
        if (type == EventType.HUNT && Bukkit.getOnlinePlayers().size() < 2) {
            sender.sendMessage(ChatColor.RED + "The Wanted Hunt requires at least two online players.");
            return true;
        }
        startEvent(type, seconds);
        sender.sendMessage(ChatColor.GREEN + "Event started for " + formatTime(seconds) + ".");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("calendar")) {
            if (!sender.hasPermission("merelyevents.admin")) return List.of();
            if (args.length == 1) return match(args[0], List.of("add", "remove", "clear", "reload"));
            if (args.length == 2 && args[0].equalsIgnoreCase("add")) return match(args[1], List.of("20-09-2026"));
            if (args.length == 3 && args[0].equalsIgnoreCase("add")) return match(args[2], List.of("16:00", "18:00", "20:00"));
            if (args.length == 2 && args[0].equalsIgnoreCase("remove")) return calendarIds(args[1]);
            return List.of();
        }
        if (!sender.hasPermission("merelyevents.admin")) return List.of();
        if (args.length == 1) return match(args[0], List.of("points", "money", "trash", "poi", "pointshop", "hunt", "triple", "triplekeyall", "goal", "stop"));
        if (args.length == 2 && args[0].equalsIgnoreCase("goal")) return match(args[1], List.of("status", "reset"));
        if (args.length == 2 && (args[0].equalsIgnoreCase("triple") || args[0].equalsIgnoreCase("triplekeyall"))) {
            return match(args[1], List.of("keyall", "coal", "iron", "gold", "emerald", "special"));
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("stop")) {
            return match(args[1], List.of("30s", "5m", "10m", "30m", "1h", "2h"));
        }
        return List.of();
    }

    @EventHandler
    public void onTabComplete(TabCompleteEvent event) {
        String lower = event.getBuffer().toLowerCase(Locale.ROOT);
        if (lower.equals("/event") || lower.startsWith("/event ")) {
            completeEventTab(event, lower);
            return;
        }
        if (!event.getSender().hasPermission("skkeyall.admin")) return;
        if (lower.startsWith("/keyall now ")) {
            String typed = lower.substring("/keyall now ".length());
            event.setCompletions(match(typed, List.of("coal", "iron", "gold", "emerald", "special")));
        }
    }

    private void completeEventTab(TabCompleteEvent event, String lower) {
        String remainder = lower.substring("/event".length());
        if (remainder.startsWith(" ")) remainder = remainder.substring(1);
        String[] parts = remainder.split(" ", -1);
        List<String> types = List.of("points", "money", "trash", "poi", "pointshop", "hunt", "triple", "goal", "stop");
        List<String> durations = List.of("30s", "5m", "10m", "30m", "1h", "2h");
        if (parts.length <= 1) {
            event.setCompletions(match(parts.length == 0 ? "" : parts[0], types));
            return;
        }
        if (parts[0].equals("triple")) {
            if (parts.length == 2) {
                event.setCompletions(match(parts[1], List.of("keyall", "coal", "iron", "gold", "emerald", "special")));
            } else if (parts.length == 3 && parts[1].equals("keyall")) {
                event.setCompletions(match(parts[2], List.of("coal", "iron", "gold", "emerald", "special")));
            }
            return;
        }
        if (parts[0].equals("goal") && parts.length == 2) {
            event.setCompletions(match(parts[1], List.of("status", "reset")));
            return;
        }
        if (parts[0].equals("hunt") && parts.length == 2) {
            List<String> choices = new java.util.ArrayList<>(durations);
            for (Player player : Bukkit.getOnlinePlayers()) choices.add(player.getName());
            event.setCompletions(match(parts[1], choices));
            return;
        }
        if (!parts[0].equals("stop") && parts.length == 2) {
            event.setCompletions(match(parts[1], durations));
        }
    }

    private List<String> match(String typed, List<String> choices) {
        String start = typed.toLowerCase(Locale.ROOT);
        return choices.stream().filter(choice -> choice.startsWith(start)).toList();
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "/event points <30s|10m|1h>" + ChatColor.GRAY + " - 2x Points");
        sender.sendMessage(ChatColor.GREEN + "/event money <30s|10m|1h>" + ChatColor.GRAY + " - 1.5x Money");
        sender.sendMessage(ChatColor.DARK_RED + "/event trash <30s|10m|1h>" + ChatColor.GRAY + " - Slowness IV + Darkness");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/event triple keyall [crate]" + ChatColor.GRAY + " - give 3 keys to everyone");
        sender.sendMessage(ChatColor.YELLOW + "/event poi <duration>" + ChatColor.GRAY + " - POI supercharged refill every 10 minutes");
        sender.sendMessage(ChatColor.AQUA + "/event pointshop <duration>" + ChatColor.GRAY + " - 40% off the PointShop");
        sender.sendMessage(ChatColor.RED + "/event hunt <duration>" + ChatColor.GRAY + " - random target (1,500 bounty), 3,000 points for the killer");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/event goal [status|reset]" + ChatColor.GRAY + " - manage the 30-player Special Key goal");
        sender.sendMessage(ChatColor.RED + "/event stop" + ChatColor.GRAY + " - stop the active event");
    }

    private boolean isTripleKeyAll(String[] args) {
        if (args.length == 1) return args[0].equalsIgnoreCase("triplekeyall") || args[0].equalsIgnoreCase("triple-keyall");
        if (args.length == 2 && (args[0].equalsIgnoreCase("triplekeyall") || args[0].equalsIgnoreCase("triple-keyall"))) return true;
        return args.length >= 2 && args[0].equalsIgnoreCase("triple") && args[1].equalsIgnoreCase("keyall");
    }

    private boolean validCrate(String crate) {
        return crate.equals("coal") || crate.equals("iron") || crate.equals("gold") || crate.equals("emerald") || crate.equals("special");
    }

    private long parseDuration(String input) {
        Matcher matcher = DURATION.matcher(input);
        if (!matcher.matches()) return -1;
        long number;
        try { number = Long.parseLong(matcher.group(1)); }
        catch (NumberFormatException ex) { return -1; }
        return switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
            case "h" -> number * 3600;
            case "m" -> number * 60;
            default -> number;
        };
    }

    private void startEvent(EventType type, long seconds) {
        stopEvent(false);
        active = type;
        startedAt = System.currentTimeMillis();
        endsAt = startedAt + seconds * 1000L;
        bar = Bukkit.createBossBar(title(seconds), color(type), BarStyle.SOLID);
        bar.setProgress(1.0);
        for (Player player : Bukkit.getOnlinePlayers()) bar.addPlayer(player);
        captureBalances();
        if (type == EventType.POI) {
            nextPoiRefillAt = System.currentTimeMillis() + 600_000L;
            runPoiRefill();
        } else if (type == EventType.POINTSHOP) {
            applyPointshopDiscount();
        } else if (type == EventType.HUNT) {
            chooseHuntTarget();
        }
        Bukkit.broadcastMessage(prefix() + ChatColor.WHITE + displayName(type) + ChatColor.AQUA + " has started for " + ChatColor.WHITE + formatTime(seconds) + ChatColor.AQUA + "!");
        if (type == EventType.HUNT && huntTarget != null) {
            Player target = Bukkit.getPlayer(huntTarget);
            if (target != null) Bukkit.broadcastMessage(prefix() + ChatColor.RED + target.getName() + ChatColor.GRAY + " is the wanted target. Bounty: " + ChatColor.AQUA + "1,500 points" + ChatColor.GRAY + ". Kill them for " + ChatColor.AQUA + "3,000 points" + ChatColor.GRAY + ".");
        }
    }

    private void stopEvent(boolean announce) {
        EventType stopped = active;
        if (stopped == EventType.POINTSHOP) restorePointshopPrices();
        active = null;
        if (bar != null) {
            bar.removeAll();
            bar = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.removePotionEffect(PotionEffectType.DARKNESS);
        }
        lastMoney.clear();
        lastPoints.clear();
        huntTarget = null;
        nextPoiRefillAt = 0L;
        if (announce && stopped != null) Bukkit.broadcastMessage(prefix() + ChatColor.WHITE + displayName(stopped) + ChatColor.GRAY + " has ended.");
    }

    private void tick() {
        refreshGlobalGoal();
        if (active == null) return;
        long remainingMs = endsAt - System.currentTimeMillis();
        if (remainingMs <= 0) {
            stopEvent(true);
            return;
        }
        long remaining = Math.max(1L, (remainingMs + 999L) / 1000L);
        long total = Math.max(1L, (endsAt - startedAt) / 1000L);
        if (bar != null) {
            bar.setTitle(title(remaining));
            bar.setProgress(Math.max(0.0, Math.min(1.0, (double) remaining / total)));
        }
        switch (active) {
            case POINTS -> tickPoints();
            case MONEY -> tickMoney();
            case TRASH -> tickTrash();
            case POI -> { if (System.currentTimeMillis() >= nextPoiRefillAt) { runPoiRefill(); nextPoiRefillAt = System.currentTimeMillis() + 600_000L; } }
            case POINTSHOP -> { /* prices are changed once at start and restored on stop */ }
            case HUNT -> { if (huntTarget == null || Bukkit.getPlayer(huntTarget) == null) chooseHuntTarget(); }
        }
    }

    private void tickPoints() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Long current = readPoints(player);
            if (current == null) continue;
            if (hasExternalBonus(player, "merely_claim_bonus_points")) {
                lastPoints.put(player.getUniqueId(), current);
                continue;
            }
            if (hasFixedAfkPoints(player)) {
                lastPoints.put(player.getUniqueId(), current);
                continue;
            }
            Long previous = lastPoints.putIfAbsent(player.getUniqueId(), current);
            if (previous == null) continue;
            long gain = current - previous;
            if (gain > 0) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "points give " + player.getName() + " " + gain);
                lastPoints.put(player.getUniqueId(), current + gain);
            } else {
                lastPoints.put(player.getUniqueId(), current);
            }
        }
    }

    private boolean hasFixedAfkPoints(Player player) {
        long now = System.currentTimeMillis();
        return player.getMetadata("merely_afk_fixed_points").stream().anyMatch(value -> value.asLong() > now);
    }

    private boolean hasExternalBonus(Player player, String metadataKey) {
        long now = System.currentTimeMillis();
        return player.getMetadata(metadataKey).stream().anyMatch(value -> value.asLong() > now);
    }

    private void tickMoney() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Double current = readMoney(player);
            if (current == null) continue;
            if (hasExternalBonus(player, "merely_claim_bonus_money")) {
                lastMoney.put(player.getUniqueId(), current);
                continue;
            }
            Double previous = lastMoney.putIfAbsent(player.getUniqueId(), current);
            if (previous == null) continue;
            double gain = current - previous;
            if (gain > 0.009) {
                double bonus = Math.round(gain * 50.0) / 100.0;
                if (bonus > 0.0 && depositMoney(player, bonus)) {
                    lastMoney.put(player.getUniqueId(), current + bonus);
                    player.sendMessage(prefix() + ChatColor.GREEN + "+$" + MONEY.format(bonus) + ChatColor.GRAY + " event bonus");
                }
            } else {
                lastMoney.put(player.getUniqueId(), current);
            }
        }
    }

    private void tickTrash() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 3, false, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 4, false, false, true));
        }
    }

    private void captureBalances() {
        lastMoney.clear();
        lastPoints.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Double money = readMoney(player);
            Long points = readPoints(player);
            if (money != null) lastMoney.put(player.getUniqueId(), money);
            if (points != null) lastPoints.put(player.getUniqueId(), points);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refreshGlobalGoal();
        if (active == null) return;
        if (bar != null) bar.addPlayer(event.getPlayer());
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!event.getPlayer().isOnline() || active == null) return;
            Double money = readMoney(event.getPlayer());
            Long points = readPoints(event.getPlayer());
            if (money != null) lastMoney.put(event.getPlayer().getUniqueId(), money);
            if (points != null) lastPoints.put(event.getPlayer().getUniqueId(), points);
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Bukkit.getScheduler().runTask(this, this::refreshGlobalGoal);
        if (active == EventType.HUNT && huntTarget != null && huntTarget.equals(event.getPlayer().getUniqueId())) {
            chooseHuntTarget();
        }
    }

    @EventHandler
    public void onHuntDeath(PlayerDeathEvent event) {
        if (active != EventType.HUNT || huntTarget == null || !huntTarget.equals(event.getEntity().getUniqueId())) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "points give " + killer.getName() + " 3000");
        Bukkit.broadcastMessage(prefix() + ChatColor.AQUA + killer.getName() + ChatColor.GREEN + " killed the wanted target and won " + ChatColor.AQUA + "3,000 points" + ChatColor.GREEN + "!");
        stopEvent(true);
    }

    @EventHandler
    public void onKeyAllChoice(PlayerCommandPreprocessEvent event) {
        String[] parts = event.getMessage().trim().split("\\s+");
        if (parts.length != 3 || !parts[0].equalsIgnoreCase("/keyall") || !parts[1].equalsIgnoreCase("now")) return;
        event.setCancelled(true);
        Player sender = event.getPlayer();
        if (!sender.hasPermission("skkeyall.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to run key giveaways.");
            return;
        }
        String crate = parts[2].toLowerCase(Locale.ROOT);
        if (!crate.equals("coal") && !crate.equals("iron") && !crate.equals("gold") && !crate.equals("emerald") && !crate.equals("special")) {
            sender.sendMessage(ChatColor.RED + "Use: /keyall now <coal|iron|gold|emerald|special>");
            return;
        }
        distributeKeys(crate, 3, true);
        sender.sendMessage(ChatColor.GRAY + "Delivered 3 keys to all online player(s).");
    }

    private void distributeKeys(String crate, int amount, boolean announce) {
        boolean delivered = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "crates keyall " + crate + " " + amount);
        if (!delivered) {
            getLogger().severe("SKCrates rejected native KeyAll for crate '" + crate + "'. No success message was broadcast.");
            return;
        }
        if (announce) {
            String pretty = Character.toUpperCase(crate.charAt(0)) + crate.substring(1) + " Key";
            Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "TRIPLE KEYALL " + ChatColor.DARK_GRAY + "» " + ChatColor.WHITE + "All online players received " + amount + "x " + ChatColor.LIGHT_PURPLE + pretty + ChatColor.WHITE + "!");
        }
    }

    private void runPoiRefill() {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "poirefillnow");
        Bukkit.broadcastMessage(prefix() + ChatColor.YELLOW + "POI Supercharged: the POI has been refilled.");
    }

    private void chooseHuntTarget() {
        List<? extends Player> players = Bukkit.getOnlinePlayers().stream().filter(Player::isOnline).toList();
        if (players.isEmpty()) { huntTarget = null; return; }
        Player next = players.get(ThreadLocalRandom.current().nextInt(players.size()));
        huntTarget = next.getUniqueId();
        if (active == EventType.HUNT) Bukkit.broadcastMessage(prefix() + ChatColor.RED + next.getName() + ChatColor.GRAY + " is now the wanted target. Bounty: " + ChatColor.AQUA + "1,500 points" + ChatColor.GRAY + ". Kill them for " + ChatColor.AQUA + "3,000 points" + ChatColor.GRAY + ".");
    }

    private void applyPointshopDiscount() {
        if (!pointshopBackups.isEmpty()) return;
        Path dir = getDataFolder().getParentFile().toPath().resolve("SKPointShop").resolve("categories");
        if (!Files.isDirectory(dir)) { getLogger().warning("SKPointShop categories directory not found; discount was not applied."); return; }
        try (var files = Files.list(dir)) {
            files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml")).forEach(path -> {
                try {
                    byte[] original = Files.readAllBytes(path);
                    String text = new String(original, StandardCharsets.UTF_8);
                    String changed = discountedPrices(text);
                    if (!changed.equals(text)) { pointshopBackups.put(path, original); Files.writeString(path, changed, StandardCharsets.UTF_8); }
                } catch (IOException | NumberFormatException ex) { getLogger().warning("Could not discount " + path.getFileName() + ": " + ex.getMessage()); }
            });
        } catch (IOException ex) { getLogger().warning("Could not read SKPointShop categories: " + ex.getMessage()); return; }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "pointshop reload");
    }

    private String discountedPrices(String text) {
        Pattern line = Pattern.compile("(?m)^(\\s*price:\\s*)(\\d+)(\\s*)$");
        Matcher matcher = line.matcher(text);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            long old = Long.parseLong(matcher.group(2));
            long discounted = Math.max(1L, Math.round(old * 0.60d));
            matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(1) + discounted + matcher.group(3)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private void restorePointshopPrices() {
        if (pointshopBackups.isEmpty()) return;
        pointshopBackups.forEach((path, bytes) -> {
            try { Files.write(path, bytes); } catch (IOException ex) { getLogger().warning("Could not restore " + path.getFileName() + ": " + ex.getMessage()); }
        });
        pointshopBackups.clear();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "pointshop reload");
    }

    private void hookVault() {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            @SuppressWarnings({"rawtypes", "unchecked"})
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) economyClass);
            if (registration == null) return;
            economy = registration.getProvider();
            getBalance = economyClass.getMethod("getBalance", OfflinePlayer.class);
            depositPlayer = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
        } catch (ReflectiveOperationException ex) {
            getLogger().warning("Vault economy hook unavailable: " + ex.getMessage());
        }
    }

    private void hookPlaceholderApi() {
        try {
            Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            papiSetPlaceholders = papi.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
        } catch (ReflectiveOperationException ex) {
            getLogger().warning("PlaceholderAPI points hook unavailable: " + ex.getMessage());
        }
    }

    private Double readMoney(Player player) {
        if (economy == null || getBalance == null) return null;
        try { return ((Number) getBalance.invoke(economy, player)).doubleValue(); }
        catch (ReflectiveOperationException ex) { return null; }
    }

    private boolean depositMoney(Player player, double amount) {
        try { depositPlayer.invoke(economy, player, amount); return true; }
        catch (ReflectiveOperationException ex) { return false; }
    }

    private Long readPoints(Player player) {
        if (papiSetPlaceholders == null) return null;
        try {
            String value = String.valueOf(papiSetPlaceholders.invoke(null, player, "%skcore_points%"));
            value = ChatColor.stripColor(value).replace(",", "").trim();
            return Long.parseLong(value);
        } catch (ReflectiveOperationException | NumberFormatException ex) {
            return null;
        }
    }

    private String title(long remaining) {
        return ChatColor.BOLD + displayName(active) + ChatColor.RESET + ChatColor.WHITE + "  •  " + formatTime(remaining);
    }

    private String displayName(EventType type) {
        if (type == null) return "Server Event";
        return switch (type) {
            case POINTS -> ChatColor.AQUA + "2x POINTS EVENT";
            case MONEY -> ChatColor.GREEN + "1.5x MONEY EVENT";
            case TRASH -> ChatColor.DARK_RED + "TRASH EVENT";
            case POI -> ChatColor.YELLOW + "POI SUPERCHARGED";
            case POINTSHOP -> ChatColor.AQUA + "40% POINTSHOP PROMOTION";
            case HUNT -> ChatColor.RED + "WANTED HUNT";
        };
    }

    private BarColor color(EventType type) {
        return switch (type) {
            case POINTS -> BarColor.BLUE;
            case MONEY -> BarColor.GREEN;
            case TRASH -> BarColor.RED;
            case POI -> BarColor.YELLOW;
            case POINTSHOP -> BarColor.PINK;
            case HUNT -> BarColor.PURPLE;
        };
    }

    private String formatTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return hours > 0 ? String.format("%d:%02d:%02d", hours, minutes, secs) : String.format("%02d:%02d", minutes, secs);
    }

    private void refreshGlobalGoal() {
        if (blockGoalClaimed) {
            if (System.currentTimeMillis() < blockGoalCompletionVisibleUntil) return;
            if (goalBar != null) {
                goalBar.removeAll();
                goalBar = null;
            }
            return;
        }
        if (globalGoalClaimed) {
            if (System.currentTimeMillis() < blockGoalUnlockAt) return;
            refreshBlockGoal();
            return;
        }
        int online = Bukkit.getOnlinePlayers().size();
        if (goalBar == null) {
            goalBar = Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SOLID);
        }
        goalBar.setTitle(ChatColor.AQUA + "" + ChatColor.BOLD + "GLOBAL GOAL " + ChatColor.WHITE + online + "/" + GLOBAL_GOAL_TARGET
                + ChatColor.DARK_GRAY + " • " + ChatColor.LIGHT_PURPLE + "1 SPECIAL KEY");
        goalBar.setProgress(Math.max(0.0, Math.min(1.0, (double) online / GLOBAL_GOAL_TARGET)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!goalBar.getPlayers().contains(player)) goalBar.addPlayer(player);
        }
        for (Player player : new ArrayList<>(goalBar.getPlayers())) {
            if (!player.isOnline()) goalBar.removePlayer(player);
        }
        if (online >= GLOBAL_GOAL_TARGET) completeGlobalGoal();
    }

    private void completeGlobalGoal() {
        if (globalGoalClaimed) return;
        globalGoalClaimed = true;
        blockGoalUnlockAt = System.currentTimeMillis() + 10_000L;
        getConfig().set("global-player-goal.claimed", true);
        getConfig().set("global-player-goal.completed-at", Instant.now().toString());
        saveConfig();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "crates keyall special 1");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        }
        if (goalBar != null) {
            goalBar.setColor(BarColor.PURPLE);
            goalBar.setProgress(1.0);
            goalBar.setTitle(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "GLOBAL GOAL COMPLETE " + ChatColor.WHITE + "• Everyone received 1 Special Key!");
        }
        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "GLOBAL GOAL " + ChatColor.DARK_GRAY + "» "
                + ChatColor.WHITE + "30 players online! Everyone received " + ChatColor.LIGHT_PURPLE + "1 Special Key" + ChatColor.WHITE + "!");
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (goalBar != null) {
                goalBar.removeAll();
                goalBar = null;
            }
            refreshBlockGoal();
        }, 200L);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onGlobalBlockBreak(BlockBreakEvent event) {
        if (!globalGoalClaimed || blockGoalClaimed || System.currentTimeMillis() < blockGoalUnlockAt
                || event.getPlayer().getGameMode() != GameMode.SURVIVAL) return;
        blockGoalProgress = Math.min(BLOCK_GOAL_TARGET, blockGoalProgress + 1L);
        if (blockGoalProgress % 250L == 0L) saveBlockGoalProgress();
        if (blockGoalProgress >= BLOCK_GOAL_TARGET) completeBlockGoal();
    }

    private void refreshBlockGoal() {
        if (!globalGoalClaimed || blockGoalClaimed) return;
        if (goalBar == null) goalBar = Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SEGMENTED_10);
        goalBar.setColor(BarColor.YELLOW);
        goalBar.setStyle(BarStyle.SEGMENTED_10);
        goalBar.setTitle(ChatColor.GOLD + "" + ChatColor.BOLD + "GLOBAL BLOCK GOAL " + ChatColor.WHITE
                + formatNumber(blockGoalProgress) + "/" + formatNumber(BLOCK_GOAL_TARGET)
                + ChatColor.DARK_GRAY + " • " + ChatColor.AQUA + formatNumber(BLOCK_GOAL_REWARD) + " POINTS");
        goalBar.setProgress(Math.max(0.0, Math.min(1.0, (double) blockGoalProgress / BLOCK_GOAL_TARGET)));
        for (Player player : Bukkit.getOnlinePlayers()) if (!goalBar.getPlayers().contains(player)) goalBar.addPlayer(player);
        for (Player player : new ArrayList<>(goalBar.getPlayers())) if (!player.isOnline()) goalBar.removePlayer(player);
    }

    private void completeBlockGoal() {
        if (blockGoalClaimed) return;
        blockGoalClaimed = true;
        blockGoalCompletionVisibleUntil = System.currentTimeMillis() + 10_000L;
        blockGoalProgress = BLOCK_GOAL_TARGET;
        getConfig().set("global-block-goal.progress", blockGoalProgress);
        getConfig().set("global-block-goal.claimed", true);
        getConfig().set("global-block-goal.completed-at", Instant.now().toString());
        saveConfig();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "points give " + player.getName() + " " + BLOCK_GOAL_REWARD);
            player.sendTitle(ChatColor.GOLD + "GLOBAL GOAL COMPLETE", ChatColor.AQUA + "You received 3,000 Points!", 10, 70, 20);
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.1f);
        }
        if (goalBar != null) {
            goalBar.setColor(BarColor.GREEN);
            goalBar.setStyle(BarStyle.SOLID);
            goalBar.setProgress(1.0);
            goalBar.setTitle(ChatColor.GREEN + "" + ChatColor.BOLD + "750,000 BLOCKS BROKEN " + ChatColor.WHITE + "• Everyone received 3,000 Points!");
        }
        Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "GLOBAL BLOCK GOAL " + ChatColor.DARK_GRAY + "» "
                + ChatColor.WHITE + "750,000 blocks broken! Every online player received " + ChatColor.AQUA + "3,000 Points" + ChatColor.WHITE + "!");
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (goalBar != null) { goalBar.removeAll(); goalBar = null; }
        }, 200L);
    }

    private void saveBlockGoalProgress() {
        getConfig().set("global-block-goal.progress", blockGoalProgress);
        getConfig().set("global-block-goal.claimed", blockGoalClaimed);
        saveConfig();
    }

    private String formatNumber(long number) {
        return String.format(Locale.US, "%,d", number);
    }

    private void loadCalendar() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        calendarFile = new File(getDataFolder(), "calendar.yml");
        calendarData = YamlConfiguration.loadConfiguration(calendarFile);
    }

    private void saveCalendar() {
        try { calendarData.save(calendarFile); }
        catch (IOException ex) { getLogger().warning("Could not save calendar.yml: " + ex.getMessage()); }
    }

    private boolean handleCalendar(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("view") || args[0].equalsIgnoreCase("list")) {
            if (!(sender instanceof Player player)) {
                sendCalendarList(sender);
            } else {
                openCalendar(player);
            }
            return true;
        }
        if (!sender.hasPermission("merelyevents.admin")) {
            sender.sendMessage(ChatColor.RED + "Only Admins can edit the event calendar.");
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            loadCalendar();
            sender.sendMessage(ChatColor.GREEN + "Event calendar reloaded.");
            return true;
        }
        if (args[0].equalsIgnoreCase("clear")) {
            calendarData.set("events", null);
            saveCalendar();
            sender.sendMessage(ChatColor.GREEN + "Event calendar cleared.");
            return true;
        }
        if (args[0].equalsIgnoreCase("remove") && args.length == 2) {
            String path = "events." + args[1];
            if (!calendarData.contains(path)) {
                sender.sendMessage(ChatColor.RED + "Calendar event #" + args[1] + " was not found.");
                return true;
            }
            String name = calendarData.getString(path + ".name", "Event");
            calendarData.set(path, null);
            saveCalendar();
            sender.sendMessage(ChatColor.GREEN + "Removed " + ChatColor.WHITE + name + ChatColor.GREEN + " from the calendar.");
            return true;
        }
        if (args[0].equalsIgnoreCase("add") && args.length >= 4) {
            ZonedDateTime when;
            try {
                when = LocalDateTime.parse(args[1] + " " + args[2], CALENDAR_INPUT).atZone(LISBON);
            } catch (DateTimeParseException ex) {
                sender.sendMessage(ChatColor.RED + "Use: /calendar add <dd-MM-yyyy> <HH:mm> <event name>");
                return true;
            }
            if (when.toInstant().isBefore(Instant.now())) {
                sender.sendMessage(ChatColor.RED + "The event date must be in the future.");
                return true;
            }
            String name = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)).trim();
            int id = nextCalendarId();
            calendarData.set("events." + id + ".name", name);
            calendarData.set("events." + id + ".time", when.toInstant().toEpochMilli());
            saveCalendar();
            sender.sendMessage(ChatColor.GREEN + "Added " + ChatColor.WHITE + name + ChatColor.GREEN + " for " + ChatColor.AQUA + CALENDAR_DISPLAY.format(when) + ChatColor.GREEN + ".");
            return true;
        }
        sender.sendMessage(ChatColor.AQUA + "/calendar" + ChatColor.GRAY + " - view upcoming events");
        sender.sendMessage(ChatColor.AQUA + "/calendar add <dd-MM-yyyy> <HH:mm> <name>" + ChatColor.GRAY + " - add an event");
        sender.sendMessage(ChatColor.AQUA + "/calendar remove <id>" + ChatColor.GRAY + " - remove an event");
        sender.sendMessage(ChatColor.AQUA + "/calendar clear" + ChatColor.GRAY + " - clear all events");
        return true;
    }

    private void openCalendar(Player player) {
        List<CalendarEntry> entries = calendarEntries();
        Inventory inventory = Bukkit.createInventory(null, 54, CALENDAR_TITLE);
        ItemStack pane = calendarItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, pane);
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
        for (int i = 0; i < Math.min(entries.size(), slots.length); i++) {
            CalendarEntry entry = entries.get(i);
            ZonedDateTime when = entry.time.atZone(LISBON);
            inventory.setItem(slots[i], calendarItem(Material.CLOCK, ChatColor.AQUA + "" + ChatColor.BOLD + entry.name,
                    List.of(ChatColor.WHITE + CALENDAR_DISPLAY.format(when), ChatColor.GRAY + "Portugal time", "", ChatColor.DARK_GRAY + "Event ID: " + entry.id)));
        }
        if (entries.isEmpty()) {
            inventory.setItem(22, calendarItem(Material.BARRIER, ChatColor.RED + "" + ChatColor.BOLD + "NO EVENTS SCHEDULED",
                    List.of(ChatColor.GRAY + "Check again soon.")));
        }
        inventory.setItem(49, calendarItem(Material.WRITABLE_BOOK, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "UPCOMING EVENTS",
                List.of(ChatColor.GRAY + "Times use the Europe/Lisbon timezone.", ChatColor.GRAY + "Admins can schedule events with /calendar add.")));
        player.openInventory(inventory);
    }

    private void sendCalendarList(CommandSender sender) {
        List<CalendarEntry> entries = calendarEntries();
        sender.sendMessage(ChatColor.AQUA + "Upcoming server events:");
        if (entries.isEmpty()) sender.sendMessage(ChatColor.GRAY + "No events are currently scheduled.");
        for (CalendarEntry entry : entries) {
            sender.sendMessage(ChatColor.DARK_GRAY + "#" + entry.id + " " + ChatColor.WHITE + entry.name + ChatColor.GRAY + " - "
                    + ChatColor.AQUA + CALENDAR_DISPLAY.format(entry.time.atZone(LISBON)));
        }
    }

    private List<CalendarEntry> calendarEntries() {
        List<CalendarEntry> entries = new ArrayList<>();
        ConfigurationSection section = calendarData.getConfigurationSection("events");
        if (section == null) return entries;
        Instant now = Instant.now();
        for (String id : section.getKeys(false)) {
            long epoch = section.getLong(id + ".time", 0L);
            String name = section.getString(id + ".name", "Server Event");
            if (epoch > 0 && Instant.ofEpochMilli(epoch).isAfter(now)) entries.add(new CalendarEntry(id, name, Instant.ofEpochMilli(epoch)));
        }
        entries.sort(Comparator.comparing(entry -> entry.time));
        return entries;
    }

    private int nextCalendarId() {
        ConfigurationSection section = calendarData.getConfigurationSection("events");
        int highest = 0;
        if (section != null) for (String key : section.getKeys(false)) {
            try { highest = Math.max(highest, Integer.parseInt(key)); } catch (NumberFormatException ignored) {}
        }
        return highest + 1;
    }

    private List<String> calendarIds(String typed) {
        ConfigurationSection section = calendarData.getConfigurationSection("events");
        if (section == null) return List.of();
        return match(typed, new ArrayList<>(section.getKeys(false)));
    }

    private ItemStack calendarItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onCalendarClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(CALENDAR_TITLE)) event.setCancelled(true);
    }

    private record CalendarEntry(String id, String name, Instant time) {}

    private String prefix() {
        return ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "EVENT " + ChatColor.DARK_GRAY + "» ";
    }
}
