package com.merelyme.tournament;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import com.fastasyncworldedit.core.history.DiskStorageHistory;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extension.platform.Actor;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.ShulkerBox;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class MerelyTournament extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private enum Phase { CLOSED, OPEN, PREPARING, RUNNING }
    private enum MatchState { COUNTDOWN, FIGHTING, FINISHED }

    private final Set<UUID> registered = new LinkedHashSet<>();
    private final Set<UUID> participants = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Match> matchesByPlayer = new HashMap<>();
    private final Map<UUID, Match> spectatorMatches = new HashMap<>();
    private final Map<UUID, UUID> spectatorTargets = new HashMap<>();
    private final Map<UUID, String> pendingRestore = new HashMap<>();
    private final Set<UUID> internalTeleports = ConcurrentHashMap.newKeySet();
    private final Set<UUID> blockedPearlTeleports = ConcurrentHashMap.newKeySet();
    private final Map<String, Offer> clickedOffers = new HashMap<>();
    private final Map<UUID, DamageCredit> recentDamage = new HashMap<>();
    private final Map<UUID, PermissionAttachment> tournamentBypasses = new HashMap<>();
    private final Random random = new Random();
    private Phase phase = Phase.CLOSED;
    private int round = 0;
    private int initialEntrants = 0;
    private List<UUID> roundWinners = new ArrayList<>();
    private int roundMatchesRemaining = 0;
    private long tournamentGeneration = 0;
    private long tournamentStartedAt = 0;
    private YamlConfiguration glory;
    private YamlConfiguration snapshots;
    private File gloryFile;
    private File snapshotsFile;
    private volatile Path arenaWorldParent;
    private LocalDate lastAutomaticRun;
    private DuelManager duels;
    private Object economy;
    private Method economyBalance;
    private Method economyWithdraw;
    private Method economyDeposit;
    private GloryPlaceholder gloryPlaceholder;

    private static final String PREFIX = "§8[§6§lTOURNAMENT§8] §r";
    private static final String MAIN_TITLE = "§f\uF804\uE20C";
    private static final String SHOP_TITLE = "§f\uF804\uE214";
    private static final String GLORY_TITLE = "§f\uF804\uE215";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("EEEE HH:mm", Locale.ENGLISH);
    private static final List<String> KILL_EFFECT_IDS=List.of("flame","soul","lightning","freeze","void","money","doublelightning","meteor","fireworks");
    private static final List<String> PARTICLE_EFFECT_IDS=List.of("flame","soul","electric","magic","cherry","sculk","crown");
    private record DamageCredit(UUID attacker, long expiresAt) {}

    @Override public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        reloadConfig();
        hookDuelEconomy();
        gloryFile = new File(getDataFolder(), "glory.yml");
        snapshotsFile = new File(getDataFolder(), "snapshots.yml");
        glory = YamlConfiguration.loadConfiguration(gloryFile);
        snapshots = YamlConfiguration.loadConfiguration(snapshotsFile);
        migrateRemovedDragonEffects();
        String saved = glory.getString("system.last-automatic-run", "");
        try { if (!saved.isBlank()) lastAutomaticRun = LocalDate.parse(saved); } catch (Exception ignored) {}
        Objects.requireNonNull(getCommand("tournament")).setExecutor(this);
        Objects.requireNonNull(getCommand("tournament")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("tournamentspectate")).setExecutor(this);
        Objects.requireNonNull(getCommand("tournamentspectate")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("tournamentunspectate")).setExecutor(this);
        Objects.requireNonNull(getCommand("giveglory")).setExecutor(this);
        Objects.requireNonNull(getCommand("giveglory")).setTabCompleter(this);
        duels = new DuelManager(this);
        registerGloryPlaceholder();
        Bukkit.getScheduler().runTaskLater(this, this::registerGloryPlaceholder, 40L);
        getServer().getPluginManager().registerEvents(this, this);
        new BukkitRunnable() { @Override public void run() { scheduleTick(); } }.runTaskTimer(this, 20L, 20L);
        new BukkitRunnable() { @Override public void run() { cosmeticTick(); } }.runTaskTimer(this, 10L, 10L);
        new BukkitRunnable() { @Override public void run() { followSpectators(); } }.runTaskTimer(this, 100L, 100L);
        Bukkit.getScheduler().runTaskLater(this, ()->{repairMainWorldBorder();configureTemplateWorld();cleanupStaleWorlds();}, 40L);
        for (Player p : Bukkit.getOnlinePlayers()) {
            recoverIfNeeded(p);
            Bukkit.getScheduler().runTaskLater(this, () -> clearOrphanedInvulnerability(p), 30L);
        }
        getLogger().info("MerelyTournament enabled. Arena centre 255,168,224; spawns 301,168,178 and 209,168,270.");
    }

    @Override public void onDisable() {
        tournamentGeneration++;
        if (gloryPlaceholder != null && gloryPlaceholder.isRegistered()) gloryPlaceholder.unregister();
        if (duels != null) duels.shutdown();
        returnAllSpectators("Tournament system reloading.");
        for (Match match : new HashSet<>(matchesByPlayer.values())) deactivateMatch(match);
        restoreAllOnlineSnapshots();
        saveGlory();
        saveSnapshots();
    }

    private ZoneId zone() { return ZoneId.of(getConfig().getString("timezone", "Europe/Lisbon")); }

    private void scheduleTick() {
        ZonedDateTime now = ZonedDateTime.now(zone());
        if ((phase == Phase.PREPARING || phase == Phase.RUNNING) && tournamentStartedAt > 0) {
            long maximumMillis = Math.max(10, getConfig().getInt("maximum-duration-minutes", 45)) * 60_000L;
            if (System.currentTimeMillis() - tournamentStartedAt >= maximumMillis) {
                failTournament("Safety timeout reached. Every stored inventory is being restored.");
                return;
            }
        }
        DayOfWeek day = DayOfWeek.valueOf(getConfig().getString("registration-day", "SUNDAY"));
        int openHour = getConfig().getInt("registration-open-hour", 12);
        int startHour = getConfig().getInt("start-hour", 16);
        if (phase == Phase.CLOSED && now.getDayOfWeek() == day && now.getHour() >= openHour && now.getHour() < startHour && !now.toLocalDate().equals(lastAutomaticRun)) {
            openRegistration(false);
        }
        if (phase == Phase.OPEN && now.getDayOfWeek() == day && now.getHour() >= startHour && !now.toLocalDate().equals(lastAutomaticRun) && (duels == null || !duels.hasActiveMatches())) {
            lastAutomaticRun = now.toLocalDate();
            glory.set("system.last-automatic-run", lastAutomaticRun.toString());
            saveGlory();
            startTournament(null);
        }
        if (phase == Phase.OPEN) {
            long seconds = Duration.between(now, nextStart(now)).getSeconds();
            if (seconds >= 0 && seconds % 30 == 0) {
                for (Player p : Bukkit.getOnlinePlayers()) p.sendActionBar(Component.text("§6Weekly Tournament §8• §f" + registered.size() + " joined §8• §e" + formatDuration(seconds)));
            }
        }
    }

    private ZonedDateTime nextStart(ZonedDateTime now) {
        DayOfWeek target = DayOfWeek.valueOf(getConfig().getString("registration-day", "SUNDAY"));
        int add = (target.getValue() - now.getDayOfWeek().getValue() + 7) % 7;
        ZonedDateTime result = now.plusDays(add).withHour(getConfig().getInt("start-hour", 16)).withMinute(0).withSecond(0).withNano(0);
        if (result.isBefore(now)) result = result.plusWeeks(1);
        return result;
    }

    private String formatDuration(long total) {
        total = Math.max(0, total);
        long d = total / 86400, h = total % 86400 / 3600, m = total % 3600 / 60, s = total % 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        return m + "m " + s + "s";
    }

    private void openRegistration(boolean manual) {
        if (phase == Phase.RUNNING || phase == Phase.PREPARING) return;
        phase = Phase.OPEN;
        registered.clear();
        String when = manual ? "Registration is now open!" : "Registration is open until " + getConfig().getInt("start-hour", 16) + ":00 Portugal time.";
        Bukkit.broadcastMessage(PREFIX + "§e" + when + " §fUse §6/tournament§f to join.");
    }

    private void openMain(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, MAIN_TITLE);
        boolean joined = registered.contains(p.getUniqueId());
        ItemStack join = item(joined ? Material.RED_DYE : Material.LIME_DYE, joined ? "§cLeave Tournament" : "§aJoin Tournament",
                phase == Phase.OPEN ? (joined ? "§7Click to leave registration." : "§7Click to enter this week's bracket.") : "§7Registration is currently closed.");
        ItemStack shop = item(Material.NETHER_STAR, "§6Glory Shop", "§7Balance: §e" + balance(p.getUniqueId()) + " Glory", "§7Tags, effects, resources and spawners.");
        ItemStack status = item(Material.CLOCK, "§eTournament Status", statusLines().toArray(String[]::new));
        inv.setItem(11, join); inv.setItem(13, status); inv.setItem(15, shop);
        fill(inv);
        p.openInventory(inv);
    }

    private List<String> statusLines() {
        ZonedDateTime next = nextStart(ZonedDateTime.now(zone()));
        return List.of("§7State: §f" + phase, "§7Registered: §f" + registered.size() + "/" + getConfig().getInt("maximum-players", 16),
                "§7Next automatic start: §f" + TIME.format(next), "§7Portugal time §8(automatically localized by Discord timestamps)");
    }

    private void openShop(Player p) {
        Inventory inv = Bukkit.createInventory(null, 45, SHOP_TITLE);
        inv.setItem(10, item(Material.NAME_TAG, "§6Exclusive Tags", "§7Clean tournament titles."));
        inv.setItem(12, item(Material.BLAZE_POWDER, "§cParticles", "§7Equip a permanent particle trail."));
        inv.setItem(14, item(Material.DRAGON_BREATH, "§dKill Effects", "§7A visual effect when you eliminate a player."));
        inv.setItem(16, item(Material.NETHERITE_INGOT, "§bResources", "§7Powerful items bought with Glory."));
        inv.setItem(22, item(Material.SPAWNER, "§aSpawners", "§7Tournament-priced spawners."));
        inv.setItem(31, item(Material.SUNFLOWER, "§eYour Balance", "§f" + balance(p.getUniqueId()) + " Glory Points"));
        inv.setItem(36, item(Material.ARROW, "§cBack"));
        fill(inv); p.openInventory(inv);
    }

    private void openCategory(Player p, Category category) {
        String title = GLORY_TITLE;
        Inventory inv = Bukkit.createInventory(null, 54, title);
        List<Offer> offers = offers(category);
        clickedOffers.entrySet().removeIf(e -> e.getKey().startsWith(p.getUniqueId() + ":"));
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
        for (int i = 0; i < offers.size() && i < slots.length; i++) {
            Offer o = offers.get(i); boolean owned = owns(p.getUniqueId(), o);
            List<String> lore = new ArrayList<>(); lore.add("§7Cost: §e" + o.cost + " Glory");
            if (o.cosmetic) lore.add(owned ? "§aOwned — click to equip." : "§fClick to unlock."); else lore.add("§fClick to buy.");
            ItemStack display = item(o.material, colour(o.colour + o.name), lore.toArray(String[]::new));
            if (o.amount > 1) display.setAmount(Math.min(o.amount, display.getMaxStackSize()));
            inv.setItem(slots[i], display);
            clickedOffers.put(p.getUniqueId() + ":" + slots[i] + ":" + title, o);
        }
        inv.setItem(45, item(Material.ARROW, "§cBack"));
        inv.setItem(49, item(Material.SUNFLOWER, "§eBalance: §f" + balance(p.getUniqueId()) + " Glory"));
        fill(inv); p.openInventory(inv);
    }

    private enum Category { TAGS("Tags"), PARTICLES("Particles"), KILLS("Kill Effects"), RESOURCES("Resources"), SPAWNERS("Spawners"); final String label; Category(String l){label=l;} }
    private record Offer(Category category, String id, String name, String colour, Material material, int amount, int cost, boolean cosmetic, EntityType spawner) {}

    private List<Offer> offers(Category c) {
        List<Offer> all = List.of(
            new Offer(Category.TAGS,"gladiator","&8[&#FFE066&lGLA&#FFB000&lDIA&#FF6A00&lTOR&8]","",Material.NAME_TAG,1,250,true,null),
            new Offer(Category.TAGS,"vanguard","&8[&#66F2FF&lVAN&#00C8FF&lGU&#406BFF&lARD&8]","",Material.NAME_TAG,1,350,true,null),
            new Offer(Category.TAGS,"warlord","&8[&#FF6B6B&lWAR&#FF2121&lLO&#8B0000&lRD&8]","",Material.NAME_TAG,1,450,true,null),
            new Offer(Category.TAGS,"godmode","&8[&#D98CFF&lGOD&#B84DFF&lMO&#7A1FFF&lDE&8]","",Material.NAME_TAG,1,600,true,null),
            new Offer(Category.TAGS,"champion","&8[&#FFF176&lCHAM&#FFD700&lPI&#FF8C00&lON&8]","",Material.NAME_TAG,1,750,true,null),
            new Offer(Category.PARTICLES,"flame","Flame Trail","§c",Material.BLAZE_POWDER,1,150,true,null),
            new Offer(Category.PARTICLES,"soul","Soul Trail","§b",Material.SOUL_LANTERN,1,200,true,null),
            new Offer(Category.PARTICLES,"electric","Electric Trail","§e",Material.LIGHTNING_ROD,1,250,true,null),
            new Offer(Category.PARTICLES,"magic","Magic Spiral","§5",Material.ENCHANTED_BOOK,1,350,true,null),
            new Offer(Category.PARTICLES,"cherry","Cherry Bloom","§d",Material.PINK_PETALS,1,375,true,null),
            new Offer(Category.PARTICLES,"sculk","Sculk Aura","§3",Material.SCULK_CATALYST,1,425,true,null),
            new Offer(Category.PARTICLES,"crown","Royal Crown","§6",Material.GOLDEN_HELMET,1,500,true,null),
            new Offer(Category.KILLS,"flame","Flame Burst","§c",Material.FIRE_CHARGE,1,200,true,null),
            new Offer(Category.KILLS,"soul","Soul Explosion","§b",Material.SOUL_CAMPFIRE,1,250,true,null),
            new Offer(Category.KILLS,"lightning","Lightning Strike","§e",Material.LIGHTNING_ROD,1,300,true,null),
            new Offer(Category.KILLS,"freeze","Frozen Shatter","§b",Material.BLUE_ICE,1,350,true,null),
            new Offer(Category.KILLS,"void","Void Collapse","§5",Material.ENDER_EYE,1,400,true,null),
            new Offer(Category.KILLS,"money","Glory Rain","§e",Material.GOLD_INGOT,1,425,true,null),
            new Offer(Category.KILLS,"doublelightning","Double Lightning","§e",Material.LIGHTNING_ROD,1,450,true,null),
            new Offer(Category.KILLS,"meteor","Meteor Strike","§6",Material.MAGMA_CREAM,1,500,true,null),
            new Offer(Category.KILLS,"fireworks","Victory Fireworks","§6",Material.FIREWORK_ROCKET,1,500,true,null),
            new Offer(Category.RESOURCES,"upgrade","Netherite Upgrade","§b",Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,1,20,false,null),
            new Offer(Category.RESOURCES,"totem","Totem of Undying","§e",Material.TOTEM_OF_UNDYING,1,30,false,null),
            new Offer(Category.RESOURCES,"netherite","Netherite Ingot","§8",Material.NETHERITE_INGOT,1,40,false,null),
            new Offer(Category.RESOURCES,"wandering_trader","Wandering Trader Spawn Egg","§b",Material.WANDERING_TRADER_SPAWN_EGG,1,50,false,null),
            new Offer(Category.RESOURCES,"gapple","Enchanted Golden Apple","§d",Material.ENCHANTED_GOLDEN_APPLE,1,80,false,null),
            new Offer(Category.RESOURCES,"elytra","Elytra","§f",Material.ELYTRA,1,125,false,null),
            new Offer(Category.RESOURCES,"windburst","Wind Burst II Book","§b",Material.ENCHANTED_BOOK,1,700,false,null),
            new Offer(Category.SPAWNERS,"cave_spider","Cave Spider Spawner","§7",Material.SPAWNER,1,25,false,EntityType.CAVE_SPIDER),
            new Offer(Category.SPAWNERS,"spider","Spider Spawner","§7",Material.SPAWNER,1,35,false,EntityType.SPIDER),
            new Offer(Category.SPAWNERS,"zombie","Zombie Spawner","§2",Material.SPAWNER,1,45,false,EntityType.ZOMBIE),
            new Offer(Category.SPAWNERS,"skeleton","Skeleton Spawner","§f",Material.SPAWNER,1,60,false,EntityType.SKELETON),
            new Offer(Category.SPAWNERS,"blaze","Blaze Spawner","§6",Material.SPAWNER,1,100,false,EntityType.BLAZE),
            new Offer(Category.SPAWNERS,"iron_golem","Iron Golem Spawner","§f",Material.SPAWNER,1,200,false,EntityType.IRON_GOLEM)
        );
        return all.stream().filter(o -> o.category == c).toList();
    }

    private void buy(Player p, Offer o) {
        UUID id = p.getUniqueId();
        if (o.cosmetic && owns(id,o)) { equip(p,o); return; }
        int have = balance(id);
        if (have < o.cost) { p.sendMessage(PREFIX + "§cYou need §e" + (o.cost-have) + "§c more Glory Points."); return; }
        setBalance(id, have-o.cost);
        if (o.cosmetic) {
            glory.set("players."+id+".owned."+o.category.name().toLowerCase()+"."+o.id, true); equip(p,o);
        } else {
            if (o.spawner != null && Bukkit.getPluginManager().isPluginEnabled("SKSpawners")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spawners give " + p.getName() + " " + o.id + " " + o.amount);
            } else {
                ItemStack reward = rewardItem(o);
                Map<Integer,ItemStack> left = p.getInventory().addItem(reward);
                left.values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
            }
            p.sendMessage(PREFIX + "§aPurchased " + o.name + " for §e" + o.cost + " Glory§a.");
        }
        saveGlory(); openCategory(p,o.category);
    }

    private ItemStack rewardItem(Offer o) {
        ItemStack item = new ItemStack(o.material, o.amount);
        if (o.id.equals("windburst")) {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta book) {
                Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("wind_burst")); if (e != null) book.addStoredEnchant(e,2,true); item.setItemMeta(book);
            }
        }
        if (o.spawner != null && item.getItemMeta() instanceof BlockStateMeta meta && meta.getBlockState() instanceof CreatureSpawner spawner) {
            spawner.setSpawnedType(o.spawner); meta.setBlockState(spawner); meta.setDisplayName(o.colour + o.name); item.setItemMeta(meta);
        }
        return item;
    }

    private boolean owns(UUID id, Offer o) { return glory.getBoolean("players."+id+".owned."+o.category.name().toLowerCase()+"."+o.id); }
    private void equip(Player p, Offer o) {
        String key = "players."+p.getUniqueId()+".equipped."+o.category.name().toLowerCase();
        glory.set(key,o.id); saveGlory();
        if (o.category == Category.TAGS) applyTournamentTag(p, o);
        if (o.category == Category.KILLS) playKillEffect(o.id, p.getLocation());
        if (o.category == Category.PARTICLES) p.sendTitle("§d§l"+o.name,"§fParticle effect equipped",5,35,10);
        p.sendMessage(PREFIX + "§aEquipped " + colour(o.colour + o.name) + "§a.");
    }

    private void applyTournamentTag(Player p, Offer tag) {
        String tagId = "tourney_" + tag.id;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + p.getName() + " permission set sksmp.tag." + tagId + " true");
        Bukkit.getScheduler().runTaskLater(this, () -> p.performCommand("tags " + tagId), 2L);
    }

    private void cosmeticTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            String effect = glory.getString("players."+p.getUniqueId()+".equipped.particles","");
            Location l = p.getLocation().add(0, 1.1, 0);
            switch(effect) {
                case "flame" -> particle(p.getWorld(),Particle.FLAME,l,2,0.3,0.5,0.3,0);
                case "soul" -> particle(p.getWorld(),Particle.SOUL,l,2,0.3,0.5,0.3,0);
                case "electric" -> particle(p.getWorld(),Particle.ELECTRIC_SPARK,l,2,0.3,0.5,0.3,0);
                case "rainbow" -> rainbowAuraTrail(p);
                case "magic" -> magicSpiralTrail(p);
                case "cherry" -> cherryBloomTrail(p);
                case "sculk" -> sculkAuraTrail(p);
                case "crown" -> royalCrownTrail(p);
            }
        }
    }

    private void rainbowAuraTrail(Player player){int[][] colours={{255,70,70},{255,170,40},{255,235,80},{70,230,110},{60,190,255},{130,90,255},{235,80,255}};double spin=player.getTicksLived()*.11;for(int i=0;i<colours.length;i++){double angle=spin+i*Math.PI*2/colours.length;Location point=player.getLocation().add(Math.cos(angle)*.62,.18+Math.sin(spin+i)*.12,Math.sin(angle)*.62);dust(player.getWorld(),point,colours[i][0],colours[i][1],colours[i][2],.9f);}}
    private void magicSpiralTrail(Player player){double spin=player.getTicksLived()*.14;for(int i=0;i<5;i++){double angle=spin+i*1.25,y=.25+i*.34;Location point=player.getLocation().add(Math.cos(angle)*.42,y,Math.sin(angle)*.42);particle(player.getWorld(),i==4?Particle.END_ROD:Particle.ENCHANT,point,1,0,0,0,.02);}}
    private void cherryBloomTrail(Player player){Location centre=player.getLocation().add(0,1.15,0);particle(player.getWorld(),Particle.CHERRY_LEAVES,centre,3,.42,.7,.42,.01);if(player.getTicksLived()%20==0)particle(player.getWorld(),Particle.HEART,centre.clone().add(0,.35,0),1,.15,.15,.15,0);}
    private void sculkAuraTrail(Player player){double spin=player.getTicksLived()*.09;for(int i=0;i<6;i++){double angle=spin+i*Math.PI/3;Location point=player.getLocation().add(Math.cos(angle)*.48,.12+(i%2)*.25,Math.sin(angle)*.48);particle(player.getWorld(),i%3==0?Particle.SCULK_SOUL:Particle.SOUL_FIRE_FLAME,point,1,0,0,0,.01);}}

    private void royalCrownTrail(Player player){
        Location centre=player.getLocation().add(0,2.28,0);double rotation=player.getTicksLived()*.08;
        for(int i=0;i<10;i++){double angle=rotation+i*Math.PI*2/10.0;Location point=centre.clone().add(Math.cos(angle)*.36,0,Math.sin(angle)*.36);dust(player.getWorld(),point,255,190,35,1.05f);if(i%2==0){Location jewel=point.clone().add(0,.18,0);dust(player.getWorld(),jewel,255,235,120,1.2f);particle(player.getWorld(),Particle.END_ROD,jewel,1,0,0,0,0);}}
    }

    private Location relative(Player player,double right,double up,double forward){Vector facing=player.getLocation().getDirection().setY(0);if(facing.lengthSquared()<.001)facing=new Vector(0,0,1);facing.normalize();Vector side=new Vector(-facing.getZ(),0,facing.getX());return player.getLocation().clone().add(side.multiply(right)).add(facing.multiply(forward)).add(0,up,0);}

    private void playKillEffect(Player killer, Location location) {
        String effect = glory.getString("players."+killer.getUniqueId()+".equipped.kills","");
        playKillEffect(effect, location);
    }

    private void playKillEffect(String effect, Location location) {
        if (effect.isBlank()) return;
        Location base = location.clone().add(0, 0.2, 0);
        World world = base.getWorld();
        Sound sound = switch (effect) {
            case "flame" -> Sound.ENTITY_BLAZE_SHOOT;
            case "soul" -> Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE;
            case "lightning" -> Sound.ENTITY_LIGHTNING_BOLT_THUNDER;
            case "freeze" -> Sound.BLOCK_GLASS_BREAK;
            case "void" -> Sound.BLOCK_PORTAL_TRAVEL;
            case "money" -> Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            case "doublelightning" -> Sound.ENTITY_LIGHTNING_BOLT_THUNDER;
            case "meteor" -> Sound.ENTITY_GENERIC_EXPLODE;
            default -> Sound.ENTITY_FIREWORK_ROCKET_BLAST;
        };
        if (effect.equals("lightning")) world.strikeLightningEffect(base);
        world.playSound(base, sound, .85f, effect.equals("soul") ? 1.45f : 1.1f);
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick > 24) { cancel(); return; }
                try {
                    switch (effect) {
                        case "flame" -> flamePhoenixFrame(world, base, tick);
                        case "soul" -> soulHelixFrame(world, base, tick);
                        case "lightning" -> lightningCageFrame(world, base, tick);
                        case "freeze" -> frozenShatterFrame(world, base, tick);
                        case "void" -> voidCollapseFrame(world, base, tick);
                        case "money" -> gloryRainFrame(world, base, tick);
                        case "doublelightning" -> doubleLightningFrame(world, base, tick);
                        case "meteor" -> meteorStrikeFrame(world, base, tick);
                        case "fireworks" -> victoryFireworksFrame(world, base, tick);
                        default -> { cancel(); return; }
                    }
                } catch (Exception exception) {
                    getLogger().log(Level.WARNING,"Kill effect '"+effect+"' stopped safely",exception);
                    cancel();
                }
                tick++;
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private void flamePhoenixFrame(World world,Location base,int tick){
        double lift=tick*.055;
        for(int wing=-1;wing<=1;wing+=2)for(int i=0;i<9;i++){
            double spread=.14+i*.12,curve=Math.sin(i*Math.PI/8.0)*.8;
            particle(world,i%4==0?Particle.LAVA:Particle.FLAME,base.clone().add(wing*spread,.35+curve+lift,(tick-12)*.025),1,0,0,0,.01);
        }
        if(tick==12)particle(world,Particle.FLAME,base.clone().add(0,1.1,0),45,.55,.7,.55,.08);
    }

    private void soulHelixFrame(World world,Location base,int tick){
        for(int i=0;i<14;i++){
            double angle=tick*.32+i*.55,y=.12+i*.14;
            for(int side=0;side<2;side++){double a=angle+side*Math.PI;particle(world,side==0?Particle.SOUL_FIRE_FLAME:Particle.SOUL,base.clone().add(Math.cos(a)*.58,y,Math.sin(a)*.58),1,0,0,0,0);}
        }
        if(tick%8==0)world.playSound(base,Sound.PARTICLE_SOUL_ESCAPE,.35f,1.6f);
    }

    private void lightningCageFrame(World world,Location base,int tick){
        double y=(tick%13)*.16;
        for(int i=0;i<8;i++){double a=i*Math.PI/4.0;Location column=base.clone().add(Math.cos(a)*.85,y,Math.sin(a)*.85);particle(world,Particle.ELECTRIC_SPARK,column,2,.03,.08,.03,.02);if(tick%4==0)particle(world,Particle.END_ROD,column.clone().add(0,.2,0),1,0,0,0,0);}
        if(tick==8||tick==18){world.strikeLightningEffect(base);particle(world,Particle.FLASH,base.clone().add(0,1,0),1,0,0,0,0);}
    }

    private void frozenShatterFrame(World world,Location base,int tick){double radius=.18+tick*.045;for(int i=0;i<14;i++){double angle=i*Math.PI*2/14.0;double y=.2+(i%5)*.34+tick*.018;Location shard=base.clone().add(Math.cos(angle)*radius,y,Math.sin(angle)*radius);particle(world,i%3==0?Particle.END_ROD:Particle.SNOWFLAKE,shard,1,0,0,0,.015);}if(tick==14){particle(world,Particle.SNOWFLAKE,base.clone().add(0,1,0),65,.7,1,.7,.12);world.playSound(base,Sound.BLOCK_GLASS_BREAK,.9f,.65f);}}
    private void voidCollapseFrame(World world,Location base,int tick){double radius=Math.max(.08,1.55-tick*.06),spin=tick*.35;for(int i=0;i<20;i++){double angle=spin+i*Math.PI*2/20.0;Location point=base.clone().add(Math.cos(angle)*radius,.15+(i%6)*.3,Math.sin(angle)*radius);particle(world,i%4==0?Particle.SCULK_SOUL:Particle.REVERSE_PORTAL,point,1,0,0,0,.03);}if(tick==23){particle(world,Particle.FLASH,base.clone().add(0,1,0),1,0,0,0,0);particle(world,Particle.PORTAL,base.clone().add(0,1,0),70,.3,.7,.3,.2);}}
    private void gloryRainFrame(World world,Location base,int tick){for(int i=0;i<8;i++){double angle=i*.9+tick*.12,radius=.35+(i%3)*.38;Location drop=base.clone().add(Math.cos(angle)*radius,3.2-((tick*.16+i*.43)%3.2),Math.sin(angle)*radius);dust(world,drop,255,190+(i%2)*45,35,1.05f);if(i%3==0)particle(world,Particle.HAPPY_VILLAGER,drop,1,0,0,0,0);}if(tick%8==0)world.playSound(base,Sound.ENTITY_EXPERIENCE_ORB_PICKUP,.35f,1.35f);}
    private void doubleLightningFrame(World world,Location base,int tick){double y=(tick%12)*.18;for(int i=0;i<10;i++){double angle=i*Math.PI/5+tick*.14;Location spark=base.clone().add(Math.cos(angle)*.75,y,Math.sin(angle)*.75);particle(world,Particle.ELECTRIC_SPARK,spark,1,0,0,0,.02);}if(tick==6||tick==17){Location strike=base.clone().add(tick==6?-.55:.55,0,0);world.strikeLightningEffect(strike);particle(world,Particle.FLASH,strike.clone().add(0,1,0),1,0,0,0,0);}}
    private void meteorStrikeFrame(World world,Location base,int tick){double progress=Math.min(1,tick/14.0);Location meteor=base.clone().add(2.4*(1-progress),4.5*(1-progress),-1.8*(1-progress));particle(world,Particle.FLAME,meteor,8,.12,.12,.12,.04);particle(world,Particle.SMOKE,meteor,5,.16,.16,.16,.02);if(tick==14){particle(world,Particle.EXPLOSION,base.clone().add(0,.3,0),8,.55,.3,.55,.12);particle(world,Particle.LAVA,base.clone().add(0,.3,0),24,.75,.35,.75,.08);world.playSound(base,Sound.ENTITY_GENERIC_EXPLODE,1f,.65f);}}

    private void victoryFireworksFrame(World world,Location base,int tick){
        int[] burstTicks={8,14,20};int[][] colours={{255,70,170},{70,210,255},{255,200,45}};
        for(int rocket=0;rocket<3;rocket++){int start=rocket*6;if(tick>=start&&tick<start+8){double y=.2+(tick-start)*.23;Location trail=base.clone().add((rocket-1)*.65,y,0);particle(world,Particle.FIREWORK,trail,3,.03,.04,.03,.01);particle(world,Particle.END_ROD,trail,1,0,0,0,0);}}
        for(int burst=0;burst<3;burst++)if(tick>=burstTicks[burst]&&tick<burstTicks[burst]+5){double radius=.18+(tick-burstTicks[burst])*.22;Location centre=base.clone().add((burst-1)*.65,2.0,0);for(int i=0;i<18;i++){double yaw=i*Math.PI*2/18.0,pitch=((i%6)-2.5)*.22;Location point=centre.clone().add(Math.cos(yaw)*Math.cos(pitch)*radius,Math.sin(pitch)*radius,Math.sin(yaw)*Math.cos(pitch)*radius);dust(world,point,colours[burst][0],colours[burst][1],colours[burst][2],1.15f);}if(tick==burstTicks[burst]){particle(world,Particle.FIREWORK,centre,28,.25,.25,.25,.12);world.playSound(centre,Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST,.75f,1.05f+burst*.15f);}}
    }

    private void dust(World world,Location location,int red,int green,int blue,float size){world.spawnParticle(Particle.DUST,location,1,0,0,0,0,new Particle.DustOptions(Color.fromRGB(red,green,blue),size));}

    private void particle(World world,Particle particle,Location location,int count,double offsetX,double offsetY,double offsetZ,double extra){
        if(particle.getDataType()==Float.class)world.spawnParticle(particle,location,count,offsetX,offsetY,offsetZ,extra,1.0f);
        else if(particle.getDataType()==Color.class)world.spawnParticle(particle,location,count,offsetX,offsetY,offsetZ,extra,Color.fromRGB(80,210,255));
        else world.spawnParticle(particle,location,count,offsetX,offsetY,offsetZ,extra);
    }

    private int balance(UUID id) { return glory.getInt("players."+id+".balance",0); }
    private void setBalance(UUID id,int amount) { glory.set("players."+id+".balance",Math.max(0,amount)); saveGlory(); }
    private void addGlory(UUID id,int amount,String reason) {
        setBalance(id,balance(id)+amount); Player p=Bukkit.getPlayer(id); if(p!=null)p.sendMessage(PREFIX+"§a+"+amount+" Glory §7("+reason+")");
    }
    private boolean saveGlory(){ return saveAtomic(glory,gloryFile,"glory.yml"); }
    private void migrateRemovedDragonEffects(){ConfigurationSection players=glory.getConfigurationSection("players");if(players==null)return;boolean changed=false;for(String id:players.getKeys(false)){String particleKey="players."+id+".equipped.particles",killKey="players."+id+".equipped.kills";if(glory.getString(particleKey,"").equals("dragon")){glory.set(particleKey,"magic");glory.set("players."+id+".owned.particles.magic",true);changed=true;}if(glory.getString(killKey,"").equals("dragon")){glory.set(killKey,"void");glory.set("players."+id+".owned.kills.void",true);changed=true;}}if(changed)saveGlory();}
    private boolean saveSnapshots(){ return saveAtomic(snapshots,snapshotsFile,"snapshots.yml"); }
    private boolean saveAtomic(YamlConfiguration data,File target,String label){
        File temporary=new File(target.getParentFile(),target.getName()+".tmp");
        try{data.save(temporary);try{Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ignored){Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);}return true;}catch(IOException e){getLogger().log(Level.SEVERE,"Could not safely save "+label,e);return false;}
    }
    private void retrySnapshotSave(int attempt){if(attempt>5||!isEnabled())return;Bukkit.getScheduler().runTaskLater(this,()->{if(!saveSnapshots())retrySnapshotSave(attempt+1);},20L*attempt);}

    private void register(Player p) {
        if (phase != Phase.OPEN) { p.sendMessage(PREFIX+"§cRegistration is closed. Next tournament: §e"+TIME.format(nextStart(ZonedDateTime.now(zone())))+" Portugal time§c."); return; }
        if (registered.contains(p.getUniqueId())) { registered.remove(p.getUniqueId()); p.sendMessage(PREFIX+"§cYou left the tournament."); return; }
        if (registered.size() >= getConfig().getInt("maximum-players",16)) { p.sendMessage(PREFIX+"§cThe tournament is full."); return; }
        registered.add(p.getUniqueId()); p.sendMessage(PREFIX+"§aYou joined the weekly tournament! §7("+registered.size()+" registered)");
    }

    private void startTournament(CommandSender sender) {
        if (phase == Phase.RUNNING || phase == Phase.PREPARING) { if(sender!=null)sender.sendMessage(PREFIX+"§cA tournament is already active."); return; }
        if (duels != null && duels.hasActiveMatches()) { senderOrConsole(sender,"§cWait for the active duels to finish before starting the weekly tournament."); return; }
        if (phase != Phase.OPEN) {
            senderOrConsole(sender,"§cRegistration is closed. Use /tournament open, then every fighter must join before /tournament start.");
            return;
        }
        if (sender instanceof Player starter && !registered.contains(starter.getUniqueId())) {
            registered.add(starter.getUniqueId());
            starter.sendMessage(PREFIX+"§aYou were automatically added as a fighter because you started the tournament.");
        }
        try {
            validateTournamentKit();
        } catch(Exception e) {
            senderOrConsole(sender,"§cTournament kit validation failed: "+e.getMessage());
            return;
        }
        List<UUID> online = registered.stream().filter(id->Bukkit.getPlayer(id)!=null).collect(Collectors.toCollection(ArrayList::new));
        // A bracket only needs one pair. Keep this at two even when an older
        // live config still contains the previous four-player requirement.
        int minimum = 2;
        if (online.size() < minimum) { if(sender!=null)sender.sendMessage(PREFIX+"§cNeed at least "+minimum+" online registered players. Current: "+online.size()); else Bukkit.broadcastMessage(PREFIX+"§cCancelled: not enough registered players."); phase=Phase.CLOSED; return; }
        String fighters=online.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).map(Player::getName).collect(Collectors.joining(", "));
        senderOrConsole(sender,"§aStarting with §f"+fighters+"§a. Arena cloning may take a few seconds.");
        World template=loadAndValidateTemplate(sender);
        if(template==null)return;
        Collections.shuffle(online,random); tournamentGeneration++; tournamentStartedAt=System.currentTimeMillis(); phase=Phase.PREPARING; round=0; initialEntrants=online.size(); participants.clear(); participants.addAll(online);
        for(UUID id:online){Player p=Bukkit.getPlayer(id);if(p!=null){p.closeInventory();if(!snapshot(p)){failTournament("Could not safely store "+p.getName()+"'s inventory.");return;}}}
        if(!backupSnapshots()){failTournament("Could not create the inventory recovery copy. No player will be moved.");return;}
        for(UUID id:online){Player p=Bukkit.getPlayer(id);if(p!=null&&!holdParticipant(p)){failTournament("Could not move "+p.getName()+" to the safe waiting position.");return;}}
        Bukkit.broadcastMessage(PREFIX+"§6The bracket is being prepared with §e"+online.size()+" players§6. Every match uses its own private arena world.");
        prepareRound(online);
    }

    private void prepareRound(List<UUID> players) {
        long generation = tournamentGeneration;
        round++; roundWinners = Collections.synchronizedList(new ArrayList<>()); roundMatchesRemaining = players.size()/2;
        if (players.size()%2==1) { UUID bye=players.remove(players.size()-1); roundWinners.add(bye); Player p=Bukkit.getPlayer(bye); if(p!=null){if(!holdParticipant(p)){failTournament("Could not keep "+p.getName()+" in the safe waiting position.");return;}p.sendMessage(PREFIX+"§aYou received a bye to the next round because the bracket has an odd number of players.");} }
        if (roundMatchesRemaining==0) { finishTournament(roundWinners.getFirst()); return; }
        String templateName=getConfig().getString("template-world","tournament_template");
        World template=loadWorld(templateName,true);
        if(template==null){failTournament("Template world could not be loaded: "+templateName);return;}
        for(Player p:template.getPlayers())internalTeleport(p,mainSpawn());
        template.save(true);
        File source=template.getWorldFolder();
        arenaWorldParent=source.toPath().getParent();
        List<Pair> pairs=new ArrayList<>(); for(int i=0;i<players.size();i+=2)pairs.add(new Pair(players.get(i),players.get(i+1),"mt_match_"+System.currentTimeMillis()+"_"+round+"_"+(i/2+1)));
        Bukkit.getScheduler().runTaskAsynchronously(this,()->{
            try { for(Pair pair:pairs) copyWorld(source.toPath(),source.toPath().resolveSibling(pair.world)); }
            catch(Exception e){getLogger().log(Level.SEVERE,"Arena clone failed",e);for(Pair pair:pairs)deleteArenaFolderLater(pair.world);Bukkit.getScheduler().runTask(this,()->{if(generation==tournamentGeneration)failTournament("Could not clone the arena. Check the console.");});return;}
            Bukkit.getScheduler().runTask(this,()->{
                if(generation!=tournamentGeneration||phase==Phase.CLOSED){for(Pair pair:pairs)deleteArenaFolderLater(pair.world);return;}
                phase=Phase.RUNNING;
                for(Pair pair:pairs){World w=loadArena(pair.world);if(w==null){failTournament("Could not load "+pair.world);return;}startMatch(pair.a,pair.b,w);}
            });
        });
    }

    private void copyWorld(Path source, Path target) throws IOException {
        if(Files.exists(target))deleteTree(target);
        try(var stream=Files.walk(source)){for(Path path:stream.toList()){
            Path rel=source.relativize(path); String first=rel.getNameCount()==0?"":rel.getName(0).toString(); String name=path.getFileName()==null?"":path.getFileName().toString();
            String relative=rel.toString().replace('\\','/');
            if(first.equals("playerdata")||first.equals("stats")||first.equals("advancements")||name.equals("uid.dat")||name.equals("session.lock")||relative.equals("data/paper/metadata.dat"))continue;
            Path dest=target.resolve(rel); if(Files.isDirectory(path))Files.createDirectories(dest);else Files.copy(path,dest,StandardCopyOption.REPLACE_EXISTING);
        }}
    }
    private void deleteTree(Path root)throws IOException{if(!Files.exists(root))return;try(var s=Files.walk(root)){for(Path p:s.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}

    private World loadArena(String name) {
        World w=loadWorld(name,true); if(w==null)return null;
        if(isMainWorld(w)){
            getLogger().severe("Refused to use the main world as a tournament arena: "+w.getName());
            repairMainWorldBorder();
            return null;
        }
        applyArenaWorldRules(w); w.setAutoSave(false);
        WorldBorder border=w.getWorldBorder();border.setCenter(getConfig().getDouble("arena.center-x",255),getConfig().getDouble("arena.center-z",224));border.setSize(getConfig().getDouble("arena.initial-border-size",120));border.setDamageAmount(2);border.setDamageBuffer(0);
        if(!preloadAndValidateArena(w)){
            getLogger().severe("The cloned arena "+name+" is missing its map blocks. Players were not moved.");
            Bukkit.unloadWorld(w,false);
            deleteArenaFolderLater(name);
            return null;
        }
        return w;
    }

    private boolean preloadAndValidateArena(World world){
        Location a=arenaSpawn(world,true).clone().subtract(0,1,0),b=arenaSpawn(world,false).clone().subtract(0,1,0);
        a.getChunk().load(true);b.getChunk().load(true);
        Material floorA=a.getBlock().getType(),floorB=b.getBlock().getType();
        if(floorA.isAir()||floorB.isAir())return false;
        getLogger().info("Loaded both fighter chunks and verified map floors: "+floorA+" / "+floorB+".");
        return true;
    }

    private World loadWorld(String configuredName,boolean voidGenerator){
        NamespacedKey key=configuredName.contains(":")?NamespacedKey.fromString(configuredName):NamespacedKey.minecraft(configuredName);
        if(key==null)return null;World loaded=Bukkit.getWorld(key);if(loaded==null)loaded=Bukkit.getWorld(configuredName);if(loaded!=null)return loaded;
        WorldCreator creator=WorldCreator.ofKey(key);if(voidGenerator&&Bukkit.getPluginManager().isPluginEnabled("VoidGen"))creator.generator("VoidGen",Bukkit.getConsoleSender());return creator.createWorld();
    }

    private void configureTemplateWorld(){
        String name=getConfig().getString("template-world","tournament_template");
        World world=loadWorld(name,true);
        if(world!=null){arenaWorldParent=world.getWorldFolder().toPath().getParent();applyArenaWorldRules(world);world.setAutoSave(true);world.save();getLogger().info("Tournament template key "+world.getKey()+" uses folder "+world.getWorldFolder()+".");}
    }

    private void applyArenaWorldRules(World world){
        world.setDifficulty(Difficulty.NORMAL);
        world.setSpawnFlags(false,false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING,false);
        world.setGameRule(GameRule.KEEP_INVENTORY,true);
        world.setGameRule(GameRule.SHOW_DEATH_MESSAGES,false);
        world.setGameRule(GameRule.NATURAL_REGENERATION,true);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE,true);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE,true);
        world.setGameRule(GameRule.MOB_GRIEFING,true);
        Location centre=new Location(world,getConfig().getDouble("arena.center-x",255)+.5,getConfig().getDouble("arena.player-one.y",168),getConfig().getDouble("arena.center-z",224)+.5);
        world.setSpawnLocation(centre);
    }

    private void recoverArenaFromFawe(CommandSender sender){
        if(!Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit")){
            sender.sendMessage(PREFIX+"§cFastAsyncWorldEdit is not enabled, so the arena history cannot be recovered.");
            return;
        }
        String worldName=getConfig().getString("template-world","tournament_template");
        World world=loadWorld(worldName,true);
        if(world==null){sender.sendMessage(PREFIX+"§cCould not load the tournament template world.");return;}
        World targetWorld=world;
        Location floorOne=arenaSpawn(world,true).clone().subtract(0,1,0),floorTwo=arenaSpawn(world,false).clone().subtract(0,1,0);
        sender.sendMessage(PREFIX+"§eLoading both arena spawn chunks safely...");
        java.util.concurrent.CompletableFuture.allOf(world.getChunkAtAsync(floorOne),world.getChunkAtAsync(floorTwo)).whenComplete((unused,loadError)->Bukkit.getScheduler().runTask(this,()->{
            if(loadError!=null){sender.sendMessage(PREFIX+"§cCould not load the arena chunks. Recovery was cancelled safely.");getLogger().log(Level.SEVERE,"Arena chunk loading failed",loadError);return;}
            beginArenaHistoryReplay(sender,targetWorld,floorOne,floorTwo);
        }));
    }

    private void beginArenaHistoryReplay(CommandSender sender,World world,Location floorOne,Location floorTwo){
        if(!floorOne.getBlock().getType().isAir()&&!floorTwo.getBlock().getType().isAir()){
            sender.sendMessage(PREFIX+"§aThe arena already exists at both fighter spawns. Recovery was not needed.");
            return;
        }
        String worldName=world.getName();
        File historyRoot=new File(new File(getDataFolder().getParentFile(),"FastAsyncWorldEdit"),"history"+File.separator+worldName);
        File[] userFolders=historyRoot.listFiles(File::isDirectory);
        if(userFolders==null||userFolders.length==0){sender.sendMessage(PREFIX+"§cNo FastAsyncWorldEdit history was found for "+worldName+".");return;}
        record HistoryFile(File folder,UUID user,int index){}
        List<HistoryFile> history=new ArrayList<>();
        for(File folder:userFolders){
            UUID user;try{user=UUID.fromString(folder.getName());}catch(IllegalArgumentException ignored){continue;}
            File[] files=folder.listFiles((dir,name)->name.matches("\\d+\\.bd"));if(files==null)continue;
            for(File file:files){try{history.add(new HistoryFile(folder,user,Integer.parseInt(file.getName().substring(0,file.getName().length()-3))));}catch(NumberFormatException ignored){}}
        }
        history.sort(Comparator.comparingInt(HistoryFile::index));
        if(history.isEmpty()){sender.sendMessage(PREFIX+"§cThe arena history folder contains no recoverable edits.");return;}
        Actor actor=BukkitAdapter.adapt(sender);com.sk89q.worldedit.world.World faweWorld=BukkitAdapter.adapt(world);
        sender.sendMessage(PREFIX+"§eRecovering §f"+history.size()+"§e saved arena edits. Players will not be moved and inventories will not be touched.");
        Bukkit.getScheduler().runTaskAsynchronously(this,()->{
            try{
                for(HistoryFile entry:history)new DiskStorageHistory(entry.folder(),faweWorld,entry.user(),entry.index()).redo(actor);
                Bukkit.getScheduler().runTaskLater(this,()->{
                    world.save();
                    Location a=arenaSpawn(world,true).clone().subtract(0,1,0),b=arenaSpawn(world,false).clone().subtract(0,1,0);
                    if(a.getBlock().getType().isAir()||b.getBlock().getType().isAir()){
                        sender.sendMessage(PREFIX+"§cHistory replay finished, but one fighter floor is still empty. Tournament start remains safely blocked.");
                        getLogger().severe("Arena history replay did not restore both configured fighter floors.");
                    }else{
                        sender.sendMessage(PREFIX+"§aArena recovered and verified: §f"+a.getBlock().getType()+" §a/ §f"+b.getBlock().getType()+"§a.");
                        getLogger().info("Recovered tournament arena from "+history.size()+" FastAsyncWorldEdit history files.");
                    }
                },40L);
            }catch(Throwable error){
                getLogger().log(Level.SEVERE,"Could not recover the arena from FastAsyncWorldEdit history",error);
                Bukkit.getScheduler().runTask(this,()->sender.sendMessage(PREFIX+"§cArena recovery failed. Tournament start remains safely blocked; check the console."));
            }
        });
    }

    private World loadAndValidateTemplate(CommandSender sender){
        String name=getConfig().getString("template-world","tournament_template");
        World template=loadWorld(name,true);
        if(template==null){senderOrConsole(sender,"§cThe arena template world could not be loaded. No inventories were changed.");return null;}
        applyArenaWorldRules(template);
        Location a=arenaSpawn(template,true),b=arenaSpawn(template,false);
        a.getChunk().load(true);b.getChunk().load(true);
        if(a.clone().subtract(0,1,0).getBlock().getType().isAir()||b.clone().subtract(0,1,0).getBlock().getType().isAir()){
            senderOrConsole(sender,"§cThe arena is missing beneath one of the configured spawns. No inventories were changed.");return null;
        }
        return template;
    }

    private void verifyArenaClone(CommandSender sender){
        if(phase!=Phase.CLOSED){sender.sendMessage(PREFIX+"§cClone verification is only available while the tournament is closed.");return;}
        World template=loadAndValidateTemplate(sender);if(template==null)return;
        for(Player player:template.getPlayers())internalTeleport(player,mainSpawn());
        template.save(true);File source=template.getWorldFolder();arenaWorldParent=source.toPath().getParent();
        String name="mt_match_verify_"+System.currentTimeMillis();Path target=source.toPath().resolveSibling(name);
        sender.sendMessage(PREFIX+"§eTesting a private arena clone in the Paper dimension folder...");
        Bukkit.getScheduler().runTaskAsynchronously(this,()->{
            try{copyWorld(source.toPath(),target);}catch(Exception error){getLogger().log(Level.SEVERE,"Private arena clone verification failed",error);Bukkit.getScheduler().runTask(this,()->{sender.sendMessage(PREFIX+"§cClone copy failed: "+error.getMessage());reloadTemplate();});return;}
            Bukkit.getScheduler().runTask(this,()->{World clone=loadArena(name);if(clone==null){sender.sendMessage(PREFIX+"§cThe copied arena did not contain both fighter floors.");reloadTemplate();return;}Location a=arenaSpawn(clone,true).clone().subtract(0,1,0),b=arenaSpawn(clone,false).clone().subtract(0,1,0);sender.sendMessage(PREFIX+"§aPrivate arena clone verified in §d"+clone.getKey()+"§a: floors §f"+a.getBlock().getType()+" §a/ §f"+b.getBlock().getType()+"§a.");cleanupWorld(clone);reloadTemplate();});
        });
    }

    private boolean backupSnapshots(){ return backupSnapshots("tournament",tournamentStartedAt); }
    boolean backupSnapshots(String prefix,long timestamp){
        try{
            File directory=new File(getDataFolder(),"snapshot-backups");
            Files.createDirectories(directory.toPath());
            File backup=new File(directory,prefix+"-"+timestamp+".yml");
            Files.copy(snapshotsFile.toPath(),backup.toPath(),StandardCopyOption.REPLACE_EXISTING);
            getLogger().info("Created inventory recovery copy: "+backup.getName());
            return true;
        }catch(IOException e){getLogger().log(Level.SEVERE,"Could not create inventory recovery copy",e);return false;}
    }

    private void startMatch(UUID aId, UUID bId, World w) {
        Player a=participants.contains(aId)?Bukkit.getPlayer(aId):null,b=participants.contains(bId)?Bukkit.getPlayer(bId):null;
        if(a==null||b==null){
            UUID missingA=a==null?aId:null,missingB=b==null?bId:null;
            if(missingA!=null){participants.remove(missingA);markRestore(missingA,"Disconnected before match");addGlory(missingA,getConfig().getInt("rewards.participant",100),"Tournament participation");}
            if(missingB!=null){participants.remove(missingB);markRestore(missingB,"Disconnected before match");addGlory(missingB,getConfig().getInt("rewards.participant",100),"Tournament participation");}
            UUID winner=a!=null?aId:b!=null?bId:null;if(winner!=null){roundWinners.add(winner);Player wp=Bukkit.getPlayer(winner);if(wp!=null&&!holdParticipant(wp)){failTournament("Could not keep "+wp.getName()+" in the safe waiting position.");return;}}roundMatchesRemaining--;cleanupWorld(w);checkRound();return;
        }
        Match m=new Match(aId,bId,w);matchesByPlayer.put(aId,m);matchesByPlayer.put(bId,m);m.bar=Bukkit.createBossBar("§6Round "+round+" §8• §ePrivate Arena",BarColor.YELLOW,BarStyle.SEGMENTED_10);m.bar.addPlayer(a);m.bar.addPlayer(b);a.sendMessage(PREFIX+"§7This fight is in a private arena world. Other matches cannot enter it.");b.sendMessage(PREFIX+"§7This fight is in a private arena world. Other matches cannot enter it.");
        Location spawnA=arenaSpawn(w,true),spawnB=arenaSpawn(w,false);spawnA.getChunk().load(true);spawnB.getChunk().load(true);
        if(!prepareFighter(a,spawnA)||!prepareFighter(b,spawnB)){failTournament("A fighter could not be teleported into their private arena. Every inventory is being restored.");return;}
        m.state=MatchState.COUNTDOWN;
        m.countdownTask=new BukkitRunnable(){int n=3;public void run(){if(m.state==MatchState.FINISHED){cancel();return;}if(n>0){m.bar.setTitle("§6Fight starts in §e"+n);m.bar.setProgress(n/3.0);a.sendTitle("§e§l"+n,"§7Get ready",0,22,0);b.sendTitle("§e§l"+n,"§7Get ready",0,22,0);n--;return;}m.state=MatchState.FIGHTING;m.startedAt=System.currentTimeMillis();unlock(a);unlock(b);m.bar.setColor(BarColor.GREEN);m.bar.setStyle(BarStyle.SOLID);Bukkit.broadcastMessage(PREFIX+"§f"+a.getName()+" §7vs §f"+b.getName()+" §7— Round "+round);startMatchTimer(m);cancel();}}.runTaskTimer(this,20L,20L);
    }

    private boolean prepareFighter(Player p,Location loc){p.closeInventory();p.sendTitle("§6§lARENA LOADING","§7Please wait...",0,40,5);applyTournamentBypasses(p);if(!internalTeleport(p,loc)||p.getWorld()!=loc.getWorld())return false;getLogger().info("Teleported fighter "+p.getName()+" to "+loc.getWorld().getName()+" at "+loc.getBlockX()+","+loc.getBlockY()+","+loc.getBlockZ()+".");p.getInventory().clear();p.getInventory().setArmorContents(new ItemStack[4]);p.getInventory().setItemInOffHand(null);for(PotionEffect e:p.getActivePotionEffects())p.removePotionEffect(e.getType());p.setGameMode(GameMode.SURVIVAL);p.setFlying(false);p.setAllowFlight(false);p.setHealth(p.getMaxHealth());p.setFoodLevel(20);p.setSaturation(5);p.setExhaustion(0);p.setFireTicks(0);p.setFallDistance(0);giveTournamentKit(p);p.setInvulnerable(true);p.setWalkSpeed(0);p.setFlySpeed(0);p.setVelocity(new Vector());p.sendMessage(PREFIX+"§aArena loaded: §f"+loc.getWorld().getName()+"§a. Fight begins after the countdown.");return true;}
    private boolean holdParticipant(Player p){p.closeInventory();Location waiting=snapshotLocation(p.getUniqueId());if(waiting==null)waiting=mainSpawn();waiting.getChunk().load(true);if(!internalTeleport(p,waiting)||p.getWorld()!=waiting.getWorld())return false;p.getInventory().clear();p.getInventory().setArmorContents(new ItemStack[4]);p.getInventory().setItemInOffHand(null);for(PotionEffect e:p.getActivePotionEffects())p.removePotionEffect(e.getType());p.setGameMode(GameMode.ADVENTURE);p.setFlying(false);p.setAllowFlight(false);p.setInvulnerable(true);p.setFireTicks(0);p.setFallDistance(0);p.setWalkSpeed(.2f);p.setFlySpeed(.1f);p.setVelocity(new Vector());p.sendActionBar(Component.text("§dTournament waiting §8• §aYour original position and inventory are safely stored"));return true;}
    private void unlock(Player p){p.setInvulnerable(false);p.setWalkSpeed(.2f);p.setFlySpeed(.1f);p.sendTitle("§a§lFIGHT!","§7The border shrinks after 5 minutes",0,30,10);}

    private void startMatchTimer(Match m){
        m.timerTask=new BukkitRunnable(){int seconds=0;public void run(){if(m.state!=MatchState.FIGHTING||phase!=Phase.RUNNING||matchesByPlayer.get(m.a)!=m||matchesByPlayer.get(m.b)!=m){cancel();return;}seconds++;int shrink=getConfig().getInt("arena.shrink-after-seconds",300),forced=getConfig().getInt("arena.forced-damage-after-seconds",420);Player a=Bukkit.getPlayer(m.a),b=Bukkit.getPlayer(m.b);if(a==null||b==null){endMatch(m,a==null?m.b:m.a,a==null?m.a:m.b,"disconnect");cancel();return;}if(a.getWorld()!=m.world||b.getWorld()!=m.world){failTournament("A fighter left their private arena unexpectedly. Damage timers were stopped safely.");cancel();return;}if(seconds==shrink){m.world.getWorldBorder().setSize(getConfig().getDouble("arena.final-border-size",3),getConfig().getInt("arena.shrink-duration-seconds",120));Bukkit.broadcastMessage(PREFIX+"§cSudden Death! §7The border is shrinking in "+a.getName()+" vs "+b.getName()+".");m.bar.setColor(BarColor.RED);}if(seconds==forced){m.world.getWorldBorder().setDamageAmount(6);Bukkit.broadcastMessage(PREFIX+"§4The border is fully closed! §cSudden Death now deals triple damage.");}if(seconds>=forced){if(seconds%2==0){a.damage(12);if(m.state==MatchState.FIGHTING&&a.getWorld()==m.world&&b.getWorld()==m.world)b.damage(12);}else{b.damage(12);if(m.state==MatchState.FIGHTING&&a.getWorld()==m.world&&b.getWorld()==m.world)a.damage(12);}}String stage=seconds<shrink?"Border shrinks in "+formatDuration(shrink-seconds):seconds<forced?"Border shrinking • "+formatDuration(forced-seconds):"Sudden Death • 3x damage";m.bar.setTitle("§6"+a.getName()+" §8vs §6"+b.getName()+" §8• §f"+stage);m.bar.setProgress(seconds<shrink?Math.max(0,1.0-(seconds/(double)shrink)):Math.max(0,1.0-((seconds-shrink)/(double)Math.max(1,forced-shrink))));}}.runTaskTimer(this,20L,20L);
    }

    private void deactivateMatch(Match m){returnSpectators(m,"The match has ended.");m.state=MatchState.FINISHED;if(m.countdownTask!=null&&!m.countdownTask.isCancelled())m.countdownTask.cancel();if(m.timerTask!=null&&!m.timerTask.isCancelled())m.timerTask.cancel();if(m.bar!=null)m.bar.removeAll();}

    private boolean startSpectating(Player viewer,Player target){
        if(participants.contains(viewer.getUniqueId())){viewer.sendMessage(PREFIX+"§cTournament fighters cannot spectate another match.");return true;}
        if(spectatorMatches.containsKey(viewer.getUniqueId())){viewer.sendMessage(PREFIX+"§cYou are already spectating. Use §f/tournamentunspectate§c first.");return true;}
        Match match=matchesByPlayer.get(target.getUniqueId());if(match==null||match.state==MatchState.FINISHED){viewer.sendMessage(PREFIX+"§cThat player is not in a live tournament match.");return true;}
        if(snapshots.contains("players."+viewer.getUniqueId())){viewer.sendMessage(PREFIX+"§cYour previous state still needs recovery. Rejoin or ask an admin to run recovery first.");scheduleRestoreWhenReady(viewer.getUniqueId(),0);return true;}
        if(!snapshot(viewer)){viewer.sendMessage(PREFIX+"§cCould not safely store your inventory, so spectator mode was cancelled.");return true;}
        Location destination=target.getLocation().clone().add(0,2,0);destination.getChunk().load(true);viewer.closeInventory();viewer.getInventory().clear();viewer.getInventory().setArmorContents(new ItemStack[4]);viewer.getInventory().setItemInOffHand(null);viewer.setGameMode(GameMode.SPECTATOR);viewer.setInvulnerable(true);spectatorMatches.put(viewer.getUniqueId(),match);spectatorTargets.put(viewer.getUniqueId(),target.getUniqueId());
        if(!internalTeleport(viewer,destination)||viewer.getWorld()!=match.world){spectatorMatches.remove(viewer.getUniqueId());spectatorTargets.remove(viewer.getUniqueId());restore(viewer);viewer.sendMessage(PREFIX+"§cThe arena teleport was rejected, so spectator mode was cancelled safely.");return true;}
        viewer.setSpectatorTarget(target);if(match.bar!=null)match.bar.addPlayer(viewer);viewer.sendMessage(PREFIX+"§aNow watching §f"+target.getName()+"§a in private arena §f"+match.world.getName()+"§a. Use §f/tournamentunspectate §ato leave.");return true;
    }

    private boolean startAnySpectating(Player viewer,Player target){
        if(spectatorMatches.containsKey(viewer.getUniqueId())||(duels!=null&&duels.isSpectating(viewer.getUniqueId()))){viewer.sendMessage(PREFIX+"§cYou are already watching a match. Use §f/unwatch §cfirst.");return true;}
        if(participants.contains(viewer.getUniqueId())||(duels!=null&&duels.isInMatch(viewer.getUniqueId()))){viewer.sendMessage(PREFIX+"§cFighters cannot spectate another arena.");return true;}
        Match tournamentMatch=matchesByPlayer.get(target.getUniqueId());
        if(tournamentMatch!=null&&tournamentMatch.state!=MatchState.FINISHED)return startSpectating(viewer,target);
        if(duels!=null&&duels.isSpectatable(target.getUniqueId()))return duels.startSpectating(viewer,target);
        viewer.sendMessage(PREFIX+"§cThat player is not fighting in a live tournament, 1v1 or 2v2 arena.");
        return true;
    }

    private void stopAnySpectating(Player viewer){
        if(spectatorMatches.containsKey(viewer.getUniqueId())){stopSpectating(viewer,"You left spectator mode.");return;}
        if(duels!=null&&duels.isSpectating(viewer.getUniqueId())){duels.stopSpectating(viewer,"You left spectator mode.");return;}
        viewer.sendMessage(PREFIX+"§7You are not spectating a match.");
    }

    private void followSpectators(){
        for(Map.Entry<UUID,Match> entry:new HashMap<>(spectatorMatches).entrySet()){
            UUID viewerId=entry.getKey();Match match=entry.getValue();Player viewer=Bukkit.getPlayer(viewerId);
            if(viewer==null)continue;
            if(match==null||match.state==MatchState.FINISHED){stopSpectating(viewer,"The match has ended.");continue;}
            UUID targetId=spectatorTargets.get(viewerId);Player target=targetId==null?null:Bukkit.getPlayer(targetId);
            if(target==null||matchesByPlayer.get(target.getUniqueId())!=match||target.getWorld()!=match.world){
                Player first=Bukkit.getPlayer(match.a),second=Bukkit.getPlayer(match.b);
                target=first!=null&&matchesByPlayer.get(match.a)==match?first:second!=null&&matchesByPlayer.get(match.b)==match?second:null;
                if(target==null){stopSpectating(viewer,"No fighter is available to spectate.");continue;}
                spectatorTargets.put(viewerId,target.getUniqueId());
            }
            Location destination=target.getLocation().clone().add(0,2,0);destination.getChunk().load(true);
            viewer.setSpectatorTarget(null);viewer.setGameMode(GameMode.SPECTATOR);viewer.setInvulnerable(true);
            if(!internalTeleport(viewer,destination)||viewer.getWorld()!=match.world){stopSpectating(viewer,"Spectator follow stopped because the arena teleport failed.");continue;}
            viewer.setSpectatorTarget(target);
        }
    }
    private void stopSpectating(Player viewer,String message){Match match=spectatorMatches.remove(viewer.getUniqueId());spectatorTargets.remove(viewer.getUniqueId());if(match==null){viewer.sendMessage(PREFIX+"§7You are not spectating a tournament match.");return;}viewer.setSpectatorTarget(null);if(match.bar!=null)match.bar.removePlayer(viewer);restore(viewer);if(message!=null&&!message.isBlank())viewer.sendMessage(PREFIX+"§7"+message);}
    private void returnSpectators(Match match,String message){List<UUID> viewers=spectatorMatches.entrySet().stream().filter(e->e.getValue()==match).map(Map.Entry::getKey).toList();for(UUID id:viewers){Player viewer=Bukkit.getPlayer(id);spectatorMatches.remove(id);spectatorTargets.remove(id);if(viewer!=null){viewer.setSpectatorTarget(null);if(match.bar!=null)match.bar.removePlayer(viewer);restore(viewer);viewer.sendMessage(PREFIX+"§7"+message);}}}
    private void returnAllSpectators(String message){for(Match match:new HashSet<>(spectatorMatches.values()))returnSpectators(match,message);spectatorMatches.clear();spectatorTargets.clear();}
    private List<String> spectatablePlayers(){Set<String> names=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);matchesByPlayer.entrySet().stream().filter(e->e.getValue().state!=MatchState.FINISHED).map(e->Bukkit.getPlayer(e.getKey())).filter(Objects::nonNull).map(Player::getName).forEach(names::add);if(duels!=null)names.addAll(duels.spectatablePlayers());return List.copyOf(names);}
    private void showActiveArenas(CommandSender sender){Set<Match> matches=new LinkedHashSet<>(matchesByPlayer.values());if(matches.isEmpty()){sender.sendMessage(PREFIX+"§7No private tournament arenas are active.");return;}Set<String> worlds=new HashSet<>();for(Match match:matches){String a=Optional.ofNullable(Bukkit.getPlayer(match.a)).map(Player::getName).orElse(Bukkit.getOfflinePlayer(match.a).getName()),b=Optional.ofNullable(Bukkit.getPlayer(match.b)).map(Player::getName).orElse(Bukkit.getOfflinePlayer(match.b).getName());boolean unique=worlds.add(match.world.getName());sender.sendMessage(PREFIX+(unique?"§a✔ ":"§c✘ ")+"§f"+a+" §8vs §f"+b+" §8• §d"+match.world.getName());}sender.sendMessage(PREFIX+(worlds.size()==matches.size()?"§aEvery match has a separate private world.":"§cDuplicate arena worlds detected."));}

    private void endMatch(Match m,UUID winner,UUID loser,String reason){if(m.state==MatchState.FINISHED)return;deactivateMatch(m);matchesByPlayer.remove(m.a);matchesByPlayer.remove(m.b);roundWinners.add(winner);participants.remove(loser);Player loserPlayer=Bukkit.getPlayer(loser);if(loserPlayer!=null)clearTournamentBypasses(loserPlayer);Player wp=Bukkit.getPlayer(winner);if(wp!=null){wp.setInvulnerable(true);wp.setGameMode(GameMode.ADVENTURE);wp.sendMessage(PREFIX+"§aYou won the match! Preparing the next round...");}markRestore(loser,"Eliminated: "+reason);awardElimination(loser);Bukkit.broadcastMessage(PREFIX+"§e"+(wp!=null?wp.getName():Bukkit.getOfflinePlayer(winner).getName())+" §7advances to the next round.");roundMatchesRemaining--;Bukkit.getScheduler().runTaskLater(this,()->{scheduleRestoreWhenReady(loser,0);if(wp!=null&&!holdParticipant(wp)){failTournament("Could not return "+wp.getName()+" to the safe waiting position.");return;}cleanupWorld(m.world);checkRound();},10L);}

    private void checkRound(){if(roundMatchesRemaining>0)return;Bukkit.getScheduler().runTaskLater(this,()->{List<UUID> next=new ArrayList<>(roundWinners);if(next.size()==1){finishTournament(next.getFirst());return;}for(UUID id:next){Player p=Bukkit.getPlayer(id);if(p==null||!participants.contains(id)){participants.remove(id);markRestore(id,"Disconnected between rounds");} }next.removeIf(id->Bukkit.getPlayer(id)==null||!participants.contains(id));if(next.size()==1){finishTournament(next.getFirst());return;}if(next.isEmpty()){failTournament("All remaining players disconnected.");return;}phase=Phase.PREPARING;prepareRound(next);},40L);}

    private void awardElimination(UUID id){int remaining=roundWinners.size()+roundMatchesRemaining;int amount;if(remaining<=1)amount=getConfig().getInt("rewards.finalist",600);else if(remaining<=2)amount=getConfig().getInt("rewards.semifinalist",350);else if(remaining<=4)amount=getConfig().getInt("rewards.quarterfinalist",200);else amount=getConfig().getInt("rewards.participant",100);addGlory(id,amount,"Tournament placement");}

    private void finishTournament(UUID winner){tournamentGeneration++;tournamentStartedAt=0;phase=Phase.CLOSED;for(Match match:new HashSet<>(matchesByPlayer.values()))deactivateMatch(match);participants.remove(winner);addGlory(winner,getConfig().getInt("rewards.champion",1000),"Weekly Champion");Player p=Bukkit.getPlayer(winner);if(p!=null){p.sendTitle("§6§lCHAMPION","§f+"+getConfig().getInt("rewards.champion",1000)+" Glory Points",10,100,20);restore(p);}else markRestore(winner,"Champion restore");Bukkit.broadcastMessage(PREFIX+"§6§l"+Bukkit.getOfflinePlayer(winner).getName()+" IS THE WEEKLY CHAMPION!");registered.clear();matchesByPlayer.clear();reloadTemplate();}
    private void failTournament(String reason){Phase stoppedPhase=phase;tournamentGeneration++;tournamentStartedAt=0;getLogger().severe(reason);Bukkit.broadcastMessage(PREFIX+"§cTournament stopped: "+reason);Set<Match> activeMatches=new HashSet<>(matchesByPlayer.values());Set<World> activeWorlds=activeMatches.stream().map(m->m.world).collect(Collectors.toSet());for(Match match:activeMatches)deactivateMatch(match);phase=Phase.CLOSED;restoreAllOnlineSnapshots();for(UUID id:new HashSet<>(participants))if(Bukkit.getPlayer(id)==null)markRestore(id,"Emergency recovery");participants.clear();registered.clear();matchesByPlayer.clear();for(World world:activeWorlds)cleanupWorld(world);if(stoppedPhase!=Phase.PREPARING)Bukkit.getScheduler().runTaskLater(this,this::cleanupStaleWorlds,40L);reloadTemplate();}
    private void restoreAllOnlineSnapshots(){ConfigurationSection section=snapshots.getConfigurationSection("players");if(section==null)return;for(String key:new HashSet<>(section.getKeys(false))){try{Player player=Bukkit.getPlayer(UUID.fromString(key));if(player!=null)restore(player);}catch(IllegalArgumentException ignored){getLogger().warning("Ignored invalid snapshot UUID: "+key);}}}
    private void reloadTemplate(){String n=getConfig().getString("template-world","tournament_template");loadWorld(n,true);}

    private void recoverFromSnapshotBackup(CommandSender sender,String playerName,String requestedFile){
        File directory=new File(getDataFolder(),"snapshot-backups");
        File backup;
        if(requestedFile==null||requestedFile.isBlank()||requestedFile.equalsIgnoreCase("latest")){
            File[] files=directory.listFiles((dir,name)->name.matches("(?:tournament|duel)-\\d+\\.yml"));
            if(files==null||files.length==0){sender.sendMessage(PREFIX+"§cNo tournament inventory backups were found.");return;}
            backup=Arrays.stream(files).max(Comparator.comparingLong(File::lastModified)).orElseThrow();
        }else{
            String safe=requestedFile.endsWith(".yml")?requestedFile:requestedFile+".yml";
            if(!safe.matches("(?:tournament|duel)-\\d+\\.yml")){sender.sendMessage(PREFIX+"§cInvalid backup file name.");return;}
            backup=new File(directory,safe);
        }
        if(!backup.isFile()){sender.sendMessage(PREFIX+"§cBackup not found: §f"+backup.getName());return;}
        YamlConfiguration stored=YamlConfiguration.loadConfiguration(backup);
        ConfigurationSection players=stored.getConfigurationSection("players");
        if(players==null){sender.sendMessage(PREFIX+"§cThat backup contains no players.");return;}
        String playerKey=players.getKeys(false).stream().filter(key->playerName.equalsIgnoreCase(stored.getString("players."+key+".name",""))).findFirst().orElse(null);
        if(playerKey==null){sender.sendMessage(PREFIX+"§cPlayer §f"+playerName+" §cwas not found in §f"+backup.getName());return;}
        UUID id;
        try{id=UUID.fromString(playerKey);}catch(IllegalArgumentException error){sender.sendMessage(PREFIX+"§cThe backup contains an invalid player ID.");return;}
        String sourceRoot="players."+playerKey,destinationRoot="players."+playerKey;
        ConfigurationSection source=stored.getConfigurationSection(sourceRoot);
        if(source==null){sender.sendMessage(PREFIX+"§cThe stored player data is incomplete.");return;}
        snapshots.set(destinationRoot,null);
        for(Map.Entry<String,Object> entry:source.getValues(true).entrySet())if(!(entry.getValue() instanceof ConfigurationSection))snapshots.set(destinationRoot+"."+entry.getKey(),entry.getValue());
        if(!saveSnapshots()){
            snapshots=YamlConfiguration.loadConfiguration(snapshotsFile);
            sender.sendMessage(PREFIX+"§cThe recovered inventory could not be saved; no live inventory was changed.");
            return;
        }
        Player online=Bukkit.getPlayer(id);
        if(online!=null){
            sender.sendMessage(PREFIX+"§eRecovering §f"+playerName+"§e from §f"+backup.getName()+"§e...");
            scheduleRestoreWhenReady(id,0);
        }else{
            markRestore(id,"Recovered by admin from "+backup.getName());
            sender.sendMessage(PREFIX+"§aStored the recovered inventory for §f"+playerName+"§a. It will be restored when they join.");
        }
    }
    private void cleanupWorld(World w){cleanupWorld(w,0);}
    private void cleanupWorld(World w,int attempt){
        if(w==null||w.getName().equals(getConfig().getString("template-world")))return;
        File folder=w.getWorldFolder();World loaded=Bukkit.getWorld(w.getUID());
        if(loaded!=null){
            if(loaded.getPlayers().stream().anyMatch(Player::isDead)){
                if(attempt<300)Bukkit.getScheduler().runTaskLater(this,()->cleanupWorld(w,attempt+1),20L);
                else getLogger().warning("Left arena "+w.getName()+" for next-start cleanup because a player did not respawn.");
                return;
            }
            for(Player p:new ArrayList<>(loaded.getPlayers())){resetTournamentView(p);internalTeleport(p,mainSpawn());}
            if(!Bukkit.unloadWorld(loaded,false)){
                if(attempt<300)Bukkit.getScheduler().runTaskLater(this,()->cleanupWorld(w,attempt+1),20L);
                else getLogger().warning("Could not unload arena "+w.getName()+"; it will be cleaned on the next start.");
                return;
            }
        }
        Bukkit.getScheduler().runTaskAsynchronously(this,()->{try{deleteTree(folder.toPath());}catch(IOException e){getLogger().warning("Could not delete match world "+folder+": "+e.getMessage());}});
    }
    private File arenaFolder(String name){World loaded=Bukkit.getWorld(name);if(loaded!=null)return loaded.getWorldFolder();Path parent=arenaWorldParent;if(parent!=null)return parent.resolve(name).toFile();return new File(Bukkit.getWorldContainer(),name);}
    private void deleteArenaFolderLater(String name){File folder=arenaFolder(name);Bukkit.getScheduler().runTaskAsynchronously(this,()->{try{deleteTree(folder.toPath());}catch(IOException e){getLogger().warning("Could not delete cancelled arena "+name);}});}
    private void cleanupStaleWorlds(){
        Set<File> folders=new LinkedHashSet<>();Path parent=arenaWorldParent;if(parent!=null){File[] current=parent.toFile().listFiles(f->f.isDirectory()&&(f.getName().startsWith("mt_match_")||f.getName().startsWith("mt_duel_")));if(current!=null)folders.addAll(Arrays.asList(current));}File[] legacy=Bukkit.getWorldContainer().listFiles(f->f.isDirectory()&&(f.getName().startsWith("mt_match_")||f.getName().startsWith("mt_duel_")));if(legacy!=null)folders.addAll(Arrays.asList(legacy));
        for(File folder:folders){World loaded=Bukkit.getWorld(folder.getName());if(loaded!=null){for(Player p:loaded.getPlayers())internalTeleport(p,mainSpawn());Bukkit.unloadWorld(loaded,false);}Bukkit.getScheduler().runTaskAsynchronously(this,()->{try{deleteTree(folder.toPath());}catch(IOException e){getLogger().warning("Could not remove stale arena "+folder.getName());}});}
    }

    private Location arenaSpawn(World w,boolean one){String p="arena."+(one?"player-one":"player-two");return new Location(w,getConfig().getDouble(p+".x"),getConfig().getDouble(p+".y"),getConfig().getDouble(p+".z"),(float)getConfig().getDouble(p+".yaw"),(float)getConfig().getDouble(p+".pitch"));}
    private Location mainSpawn(){World w=Bukkit.getWorld(getConfig().getString("main-world","world"));return w!=null?w.getSpawnLocation():Bukkit.getWorlds().getFirst().getSpawnLocation();}
    private boolean isMainWorld(World world){return world!=null&&world.getName().equalsIgnoreCase(getConfig().getString("main-world","world"));}
    private void repairMainWorldBorder(){
        World world=Bukkit.getWorld(getConfig().getString("main-world","world"));if(world==null)return;
        WorldBorder border=world.getWorldBorder();double size=Math.max(1,getConfig().getDouble("main-world-border.size",30000));
        double x=getConfig().getDouble("main-world-border.center-x",0),z=getConfig().getDouble("main-world-border.center-z",0);
        if(Math.abs(border.getSize()-size)>.01)border.setSize(size);
        Location center=border.getCenter();if(Math.abs(center.getX()-x)>.01||Math.abs(center.getZ()-z)>.01)border.setCenter(x,z);
        border.setDamageAmount(getConfig().getDouble("main-world-border.damage-amount",.2));border.setDamageBuffer(getConfig().getDouble("main-world-border.damage-buffer",5));
    }
    private boolean insideConfiguredMainBorder(Location location){
        double half=Math.max(1,getConfig().getDouble("main-world-border.size",30000))/2.0;
        double x=getConfig().getDouble("main-world-border.center-x",0),z=getConfig().getDouble("main-world-border.center-z",0);
        return Math.abs(location.getX()-x)<=half&&Math.abs(location.getZ()-z)<=half;
    }
    private Location snapshotLocation(UUID id){String root="players."+id;if(!snapshots.contains(root))return null;String worldName=snapshots.getString(root+".world",getConfig().getString("main-world","world"));World world=Bukkit.getWorld(worldName);if(world==null&&new File(Bukkit.getWorldContainer(),worldName).isDirectory())world=Bukkit.createWorld(new WorldCreator(worldName));if(world==null)return null;return new Location(world,snapshots.getDouble(root+".x"),snapshots.getDouble(root+".y"),snapshots.getDouble(root+".z"),(float)snapshots.getDouble(root+".yaw"),(float)snapshots.getDouble(root+".pitch"));}
    private boolean internalTeleport(Player player,Location location){internalTeleports.add(player.getUniqueId());try{return player.teleport(location);}finally{internalTeleports.remove(player.getUniqueId());}}
    private void resetTournamentView(Player player){player.setWorldBorder(null);player.sendActionBar(Component.empty());player.clearTitle();Bukkit.getScheduler().runTaskLater(this,()->{if(player.isOnline()&&activeArenaWorld(player.getUniqueId())==null)player.setWorldBorder(null);},2L);}
    private void applyTournamentBypasses(Player player){clearTournamentBypasses(player);PermissionAttachment attachment=player.addAttachment(this);attachment.setPermission("grim.exempt.noslow",true);attachment.setPermission("grim.exempt.simulation",true);tournamentBypasses.put(player.getUniqueId(),attachment);player.recalculatePermissions();}
    private void clearTournamentBypasses(Player player){PermissionAttachment attachment=tournamentBypasses.remove(player.getUniqueId());if(attachment!=null){try{player.removeAttachment(attachment);}catch(IllegalArgumentException ignored){}player.recalculatePermissions();}}
    private void senderOrConsole(CommandSender sender,String message){if(sender!=null)sender.sendMessage(PREFIX+message);else getLogger().warning(ChatColor.stripColor(message));}
    private void markRestore(UUID id,String reason){if(snapshots.contains("players."+id))pendingRestore.put(id,reason);}

    private boolean snapshot(Player p){String root="players."+p.getUniqueId();if(snapshots.contains(root))return true;snapshots.set(root+".name",p.getName());snapshots.set(root+".inventory",Arrays.asList(p.getInventory().getStorageContents()));snapshots.set(root+".armor",Arrays.asList(p.getInventory().getArmorContents()));snapshots.set(root+".offhand",p.getInventory().getItemInOffHand());snapshots.set(root+".cursor",p.getItemOnCursor());Location l=p.getLocation();snapshots.set(root+".world",l.getWorld().getName());snapshots.set(root+".x",l.getX());snapshots.set(root+".y",l.getY());snapshots.set(root+".z",l.getZ());snapshots.set(root+".yaw",l.getYaw());snapshots.set(root+".pitch",l.getPitch());snapshots.set(root+".held-slot",p.getInventory().getHeldItemSlot());snapshots.set(root+".allow-flight",p.getAllowFlight());snapshots.set(root+".flying",p.isFlying());snapshots.set(root+".level",p.getLevel());snapshots.set(root+".exp",p.getExp());snapshots.set(root+".total-exp",p.getTotalExperience());snapshots.set(root+".health",p.getHealth());snapshots.set(root+".food",p.getFoodLevel());snapshots.set(root+".saturation",p.getSaturation());snapshots.set(root+".gamemode",p.getGameMode().name());snapshots.set(root+".effects",new ArrayList<>(p.getActivePotionEffects()));if(saveSnapshots())return true;snapshots.set(root,null);return false;}

    @SuppressWarnings("unchecked") private void restore(Player p){
        clearTournamentBypasses(p);
        String root="players."+p.getUniqueId();
        if(!snapshots.contains(root))return;
        try{
            p.closeInventory();
            resetTournamentView(p);
            Location loc=snapshotLocation(p.getUniqueId());
            if(loc==null)loc=mainSpawn();
            loc.getChunk().load(true);
            // Move the player home before replacing the tournament kit. If the
            // teleport is rejected, the recovery record and original items stay intact.
            if(!internalTeleport(p,loc)||p.getWorld()!=loc.getWorld())throw new IllegalStateException("Original location teleport was rejected");
            if(isMainWorld(p.getWorld()))repairMainWorldBorder();resetTournamentView(p);
            List<ItemStack> inv=(List<ItemStack>)(List<?>)snapshots.getList(root+".inventory",List.of());
            List<ItemStack> armour=(List<ItemStack>)(List<?>)snapshots.getList(root+".armor",List.of());
            ItemStack offhand=snapshots.getItemStack(root+".offhand"),cursor=snapshots.getItemStack(root+".cursor");
            p.getInventory().clear();
            p.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            p.getInventory().setStorageContents(inv.toArray(new ItemStack[0]));
            p.getInventory().setArmorContents(armour.toArray(new ItemStack[0]));
            p.getInventory().setItemInOffHand(offhand);
            p.setItemOnCursor(cursor);
            p.updateInventory();
            if(!sameItems(inv,p.getInventory().getStorageContents())||!sameItems(armour,p.getInventory().getArmorContents())||!Objects.equals(offhand,p.getInventory().getItemInOffHand())||!Objects.equals(cursor,p.getItemOnCursor()))throw new IllegalStateException("Inventory verification failed after restoration");
            for(PotionEffect e:p.getActivePotionEffects())p.removePotionEffect(e.getType());
            List<PotionEffect> effects=(List<PotionEffect>)(List<?>)snapshots.getList(root+".effects",List.of());
            for(PotionEffect e:effects)p.addPotionEffect(e);
            p.setLevel(snapshots.getInt(root+".level"));p.setExp((float)snapshots.getDouble(root+".exp"));p.setTotalExperience(snapshots.getInt(root+".total-exp"));
            p.setFoodLevel(snapshots.getInt(root+".food",20));p.setSaturation((float)snapshots.getDouble(root+".saturation",5));
            p.setGameMode(GameMode.valueOf(snapshots.getString(root+".gamemode","SURVIVAL")));p.setAllowFlight(snapshots.getBoolean(root+".allow-flight",false));p.setFlying(snapshots.getBoolean(root+".flying",false)&&p.getAllowFlight());
            p.getInventory().setHeldItemSlot(snapshots.getInt(root+".held-slot",0));p.setInvulnerable(false);p.setWalkSpeed(.2f);p.setFlySpeed(.1f);p.setHealth(Math.min(p.getMaxHealth(),Math.max(1,snapshots.getDouble(root+".health",20))));
            snapshots.set(root,null);pendingRestore.remove(p.getUniqueId());
            if(!saveSnapshots()){getLogger().severe("Restored "+p.getName()+" but could not remove the completed recovery record; retrying.");retrySnapshotSave(1);}
            getLogger().info("Restored tournament snapshot for "+p.getName()+" with "+Arrays.stream(p.getInventory().getStorageContents()).filter(Objects::nonNull).count()+" occupied inventory slots.");
            Bukkit.getScheduler().runTaskLater(this,()->{if(p.isOnline())p.updateInventory();},2L);
            Bukkit.getScheduler().runTaskLater(this,()->{if(p.isOnline())p.updateInventory();},10L);
            p.sendMessage(PREFIX+"§aYour original inventory, location and player state were restored and verified.");if(phase==Phase.RUNNING&&!participants.contains(p.getUniqueId()))p.sendMessage(PREFIX+"§dYou may now watch another live match with §f/tournamentspectate <player>§d.");
        }catch(Exception e){getLogger().log(Level.SEVERE,"Recovery failed for "+p.getName()+"; snapshot retained",e);p.sendMessage(PREFIX+"§cAutomatic recovery failed safely. Your snapshot is still stored; contact an admin.");}
    }
    private boolean sameItems(List<ItemStack> expected,ItemStack[] actual){for(int i=0;i<actual.length;i++){ItemStack item=i<expected.size()?expected.get(i):null;if(!Objects.equals(item,actual[i]))return false;}for(int i=actual.length;i<expected.size();i++)if(expected.get(i)!=null)return false;return true;}
    private void scheduleRestoreWhenReady(UUID id,int attempt){if(!snapshots.contains("players."+id))return;Player player=Bukkit.getPlayer(id);if(player==null){markRestore(id,"Restore when player reconnects");return;}if(player.isDead()){if(attempt<120)Bukkit.getScheduler().runTaskLater(this,()->scheduleRestoreWhenReady(id,attempt+1),10L);else markRestore(id,"Waiting for respawn");return;}restore(player);}
    private void recoverIfNeeded(Player p){if(snapshots.contains("players."+p.getUniqueId()))Bukkit.getScheduler().runTaskLater(this,()->scheduleRestoreWhenReady(p.getUniqueId(),0),20L);}

    private void clearOrphanedInvulnerability(Player player){
        UUID id=player.getUniqueId();
        if(snapshots.contains("players."+id)||participants.contains(id)||matchesByPlayer.containsKey(id)||spectatorMatches.containsKey(id)||(duels!=null&&duels.isActivelyProtected(id)))return;
        if(player.isInvulnerable()){
            player.setInvulnerable(false);
            getLogger().warning("Cleared orphaned tournament invulnerability from "+player.getName()+".");
        }
    }

    private World activeArenaWorld(UUID id){
        Match tournament=matchesByPlayer.get(id);
        if(tournament!=null&&tournament.state!=MatchState.FINISHED)return tournament.world;
        return duels==null?null:duels.arenaWorld(id);
    }

    private boolean touchesBarrier(Location location){
        if(location==null)return true;
        return location.getBlock().getType()==Material.BARRIER||location.clone().add(0,1,0).getBlock().getType()==Material.BARRIER;
    }

    private void bouncePearl(Player player,EnderPearl pearl,boolean blockTeleport){
        Location hit=pearl.getLocation().clone();Vector incoming=pearl.getVelocity().clone();
        if(blockTeleport){blockedPearlTeleports.add(player.getUniqueId());Bukkit.getScheduler().runTaskLater(this,()->blockedPearlTeleports.remove(player.getUniqueId()),10L);}
        pearl.remove();
        Vector bounce=incoming.lengthSquared()>.0001?incoming.normalize().multiply(-.35):player.getLocation().getDirection().multiply(-.35);
        bounce.setY(Math.max(.18,Math.abs(incoming.getY())*.3));dropReturnedPearl(player,hit,bounce);
    }

    private void dropReturnedPearl(Player owner,Location location,Vector velocity){
        Item item=location.getWorld().dropItem(location,new ItemStack(Material.ENDER_PEARL));item.setPickupDelay(8);item.setVelocity(velocity);
        owner.sendActionBar(Component.text("§dEnder Pearl §8• §fBounced off the arena barrier"));
    }

    private ConfigurationSection tournamentKitItems(){ConfigurationSection items=getConfig().getConfigurationSection("tournament-kit.items");if(items==null)throw new IllegalStateException("Tournament kit section is missing");return items;}
    private int validateTournamentKit(){ConfigurationSection items=tournamentKitItems();Set<String> occupied=new HashSet<>();int parsed=0;for(String key:items.getKeys(false)){ConfigurationSection section=items.getConfigurationSection(key);if(section==null)throw new IllegalArgumentException("Invalid tournament kit item: "+key);try{parseItem(section);}catch(Exception e){throw new IllegalArgumentException("Invalid tournament kit item "+key+": "+e.getMessage(),e);}String slot=section.getString("slot","");validateTournamentSlot(key,slot);if(!occupied.add(slot))throw new IllegalArgumentException("Duplicate tournament kit slot: "+slot);parsed++;}if(parsed!=41)throw new IllegalStateException("Tournament kit must contain 41 entries (4 armour, offhand and 36 inventory slots), found "+parsed);return parsed;}
    private void validateTournamentSlot(String key,String slot){if(Set.of("helmet","chestplate","leggings","boots","offhand").contains(slot))return;if(slot.startsWith("hotbar-")){int n=parseSlotNumber(key,slot,"hotbar-");if(n>=1&&n<=9)return;}else if(slot.startsWith("inventory-")){int n=parseSlotNumber(key,slot,"inventory-");if(n>=1&&n<=27)return;}throw new IllegalArgumentException("Invalid tournament slot for "+key+": "+slot);}
    private int parseSlotNumber(String key,String slot,String prefix){try{return Integer.parseInt(slot.substring(prefix.length()));}catch(Exception e){throw new IllegalArgumentException("Invalid slot for "+key+": "+slot);}}
    private void giveTournamentKit(Player p){ConfigurationSection items=tournamentKitItems();for(String key:items.getKeys(false)){ConfigurationSection section=items.getConfigurationSection(key);if(section==null)throw new IllegalArgumentException("Invalid tournament kit item: "+key);ItemStack item=parseItem(section);String slot=section.getString("slot","");switch(slot){case "helmet"->p.getInventory().setHelmet(item);case "chestplate"->p.getInventory().setChestplate(item);case "leggings"->p.getInventory().setLeggings(item);case "boots"->p.getInventory().setBoots(item);case "offhand"->p.getInventory().setItemInOffHand(item);default->{if(slot.startsWith("hotbar-"))p.getInventory().setItem(parseSlotNumber(key,slot,"hotbar-")-1,item);else if(slot.startsWith("inventory-"))p.getInventory().setItem(8+parseSlotNumber(key,slot,"inventory-"),item);else throw new IllegalArgumentException("Invalid tournament slot for "+key+": "+slot);}}}}
    private ItemStack parseItem(ConfigurationSection s){String materialName=s.getString("material","");Material mat=Material.matchMaterial(materialName);if(mat==null)throw new IllegalArgumentException("Unknown material: "+materialName);int amount=s.getInt("amount",1);if(amount<1||amount>mat.getMaxStackSize())throw new IllegalArgumentException("Invalid amount "+amount+" for "+materialName);ItemStack is=new ItemStack(mat,amount);ItemMeta meta=is.getItemMeta();if(s.contains("name"))meta.setDisplayName(colour(s.getString("name")));for(String ench:s.getStringList("enchantments")){String[]x=ench.split(":");Enchantment e=Registry.ENCHANTMENT.get(NamespacedKey.minecraft(x[0].toLowerCase()));if(e==null)throw new IllegalArgumentException("Unknown enchantment: "+x[0]);meta.addEnchant(e,x.length>1?Integer.parseInt(x[1]):1,true);}if(meta instanceof PotionMeta pm&&s.contains("potion")){String potion=s.getString("potion","");try{pm.setBasePotionType(PotionType.valueOf(potion));}catch(Exception ex){throw new IllegalArgumentException("Unknown potion type: "+potion,ex);}}if(meta instanceof BlockStateMeta bm&&bm.getBlockState() instanceof ShulkerBox box){ConfigurationSection c=s.getConfigurationSection("contents");if(c!=null)for(String k:c.getKeys(false)){ConfigurationSection cs=c.getConfigurationSection(k);if(cs==null)throw new IllegalArgumentException("Invalid shulker entry: "+k);int containerSlot=cs.getInt("container-slot",-1);if(containerSlot<0||containerSlot>=box.getInventory().getSize())throw new IllegalArgumentException("Invalid shulker slot for "+k+": "+containerSlot);box.getInventory().setItem(containerSlot,parseItem(cs));}bm.setBlockState(box);}is.setItemMeta(meta);return is;}
    private String colour(String s){if(s==null)return"";java.util.regex.Matcher m=java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})").matcher(s);StringBuffer b=new StringBuffer();while(m.find()){String h=m.group(1);StringBuilder r=new StringBuilder("§x");for(char c:h.toCharArray())r.append('§').append(c);m.appendReplacement(b,java.util.regex.Matcher.quoteReplacement(r.toString()));}m.appendTail(b);return b.toString().replace('&','§');}

    private ItemStack item(Material m,String name,String...lore){ItemStack i=new ItemStack(m);ItemMeta im=i.getItemMeta();im.setDisplayName(name);if(lore.length>0)im.setLore(Arrays.asList(lore));i.setItemMeta(im);return i;}
    private void fill(Inventory inv){ItemStack pane=item(Material.GRAY_STAINED_GLASS_PANE," ");for(int i=0;i<inv.getSize();i++)if(inv.getItem(i)==null)inv.setItem(i,pane);}

    boolean duelSystemAvailable(){return phase==Phase.CLOSED||phase==Phase.OPEN;}
    boolean duelHasStoredState(UUID id){return snapshots.contains("players."+id);}
    boolean duelSnapshot(Player player){return snapshot(player);}
    void duelRestore(Player player){restore(player);}
    void duelRestoreWhenReady(UUID id){scheduleRestoreWhenReady(id,0);}
    void duelMarkRestore(UUID id,String reason){markRestore(id,reason);}
    World duelLoadTemplate(CommandSender sender){return loadAndValidateTemplate(sender);}
    void duelCopyWorld(Path source,Path target)throws IOException{copyWorld(source,target);}
    World duelLoadArena(String name){return loadArena(name);}
    void duelCleanupWorld(World world){cleanupWorld(world);}
    void duelDeleteArena(String name){deleteArenaFolderLater(name);}
    void duelReloadTemplate(){reloadTemplate();}
    Location duelArenaSpawn(World world,boolean one){return arenaSpawn(world,one);}
    boolean duelPrepareFighter(Player player,Location location){return prepareFighter(player,location);}
    boolean duelHoldPlayer(Player player){return holdParticipant(player);}
    void duelUnlock(Player player){unlock(player);}
    boolean duelTeleport(Player player,Location location){return internalTeleport(player,location);}
    Location duelMainSpawn(){return mainSpawn();}
    void duelClearBypass(Player player){clearTournamentBypasses(player);}
    void duelPlayKillEffect(Player player,Location location){playKillEffect(player,location);}
    int duelValidateKit(){return validateTournamentKit();}
    int duelDailyEarned(UUID id){String root="players."+id+".duels";String today=LocalDate.now(zone()).toString();return today.equals(glory.getString(root+".date",""))?glory.getInt(root+".earned",0):0;}
    int duelDailyCap(){return Math.max(0,getConfig().getInt("duels.daily-glory-cap",200));}
    int awardDuelGlory(UUID id,int requested,String reason){String root="players."+id+".duels";String today=LocalDate.now(zone()).toString();int earned=duelDailyEarned(id),grant=Math.max(0,Math.min(requested,duelDailyCap()-earned));glory.set(root+".date",today);glory.set(root+".earned",earned+grant);if(grant>0)addGlory(id,grant,reason);else saveGlory();return grant;}
    void duelAddGlory(UUID id,int amount,String reason){addGlory(id,amount,reason);}
    String duelSystemString(String key,String fallback){return glory.getString("system."+key,fallback);}
    void duelSetSystemString(String key,String value){glory.set("system."+key,value);saveGlory();}
    private void hookDuelEconomy(){try{Class<?> type=Class.forName("net.milkbowl.vault.economy.Economy");@SuppressWarnings({"rawtypes","unchecked"}) RegisteredServiceProvider<?> registration=Bukkit.getServicesManager().getRegistration((Class)type);if(registration!=null){economy=registration.getProvider();economyBalance=type.getMethod("getBalance",OfflinePlayer.class);economyWithdraw=type.getMethod("withdrawPlayer",OfflinePlayer.class,double.class);economyDeposit=type.getMethod("depositPlayer",OfflinePlayer.class,double.class);}}catch(Exception error){getLogger().warning("Vault economy hook unavailable for wagered duels: "+error.getMessage());}}
    double duelMoneyBalance(OfflinePlayer player){if(economy==null||economyBalance==null)return -1;try{return ((Number)economyBalance.invoke(economy,player)).doubleValue();}catch(Exception error){return -1;}}
    boolean duelTakeMoney(Player player,long amount){return amount>0&&duelMoneyBalance(player)>=amount&&economyTransaction(economyWithdraw,player,amount);}
    void duelGiveMoney(UUID id,long amount){if(amount>0)economyTransaction(economyDeposit,Bukkit.getOfflinePlayer(id),amount);}
    private boolean economyTransaction(Method operation,OfflinePlayer player,long amount){if(economy==null||operation==null)return false;try{Object response=operation.invoke(economy,player,(double)amount);return (boolean)response.getClass().getMethod("transactionSuccess").invoke(response);}catch(Exception error){getLogger().log(Level.WARNING,"Economy transaction failed for "+player.getUniqueId(),error);return false;}}

    private void registerGloryPlaceholder(){
        if(!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))return;
        if(gloryPlaceholder!=null&&gloryPlaceholder.isRegistered())return;
        if(gloryPlaceholder==null)gloryPlaceholder=new GloryPlaceholder();
        if(gloryPlaceholder.register())getLogger().info("Registered PlaceholderAPI placeholder: %merelytournament_glory%");
        else getLogger().warning("Could not register %merelytournament_glory%; TAB will show the placeholder text until this is fixed.");
    }

    private OfflinePlayer findKnownPlayer(String query){
        Player online=Bukkit.getPlayerExact(query);
        if(online!=null)return online;
        List<OfflinePlayer> exact=new ArrayList<>(),partial=new ArrayList<>();
        for(OfflinePlayer candidate:Bukkit.getOfflinePlayers()){
            String name=candidate.getName();
            if(name==null)continue;
            if(name.equalsIgnoreCase(query))exact.add(candidate);
            else if(name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))partial.add(candidate);
        }
        if(exact.size()==1)return exact.getFirst();
        return partial.size()==1?partial.getFirst():null;
    }

    @EventHandler public void onMenu(InventoryClickEvent e){if(!(e.getWhoClicked() instanceof Player p))return;String title=e.getView().getTitle();boolean tournamentMenu=title.equals(MAIN_TITLE)||title.equals(SHOP_TITLE)||title.equals(GLORY_TITLE);boolean enderChest=e.getView().getTopInventory().getType()==InventoryType.ENDER_CHEST;Match active=matchesByPlayer.get(p.getUniqueId());if(participants.contains(p.getUniqueId())&&!tournamentMenu&&!enderChest&&(active==null||active.state!=MatchState.FIGHTING)){e.setCancelled(true);return;}if(!tournamentMenu)return;e.setCancelled(true);int slot=e.getRawSlot();if(title.equals(MAIN_TITLE)){if(slot==11){register(p);openMain(p);}else if(slot==15)openShop(p);return;}if(title.equals(SHOP_TITLE)){switch(slot){case 10->openCategory(p,Category.TAGS);case 12->openCategory(p,Category.PARTICLES);case 14->openCategory(p,Category.KILLS);case 16->openCategory(p,Category.RESOURCES);case 22->openCategory(p,Category.SPAWNERS);case 36->openMain(p);}return;}if(title.equals(GLORY_TITLE)){if(slot==45){openShop(p);return;}Offer o=clickedOffers.get(p.getUniqueId()+":"+slot+":"+title);if(o!=null)buy(p,o);}}
    @EventHandler public void onJoin(PlayerJoinEvent e){Player player=e.getPlayer();recoverIfNeeded(player);Bukkit.getScheduler().runTaskLater(this,()->clearOrphanedInvulnerability(player),30L);}
    @EventHandler public void onQuit(PlayerQuitEvent e){UUID id=e.getPlayer().getUniqueId();recentDamage.remove(id);clearTournamentBypasses(e.getPlayer());Match watched=spectatorMatches.remove(id);spectatorTargets.remove(id);if(watched!=null){if(watched.bar!=null)watched.bar.removePlayer(e.getPlayer());e.getPlayer().setSpectatorTarget(null);restore(e.getPlayer());}Match m=matchesByPlayer.get(id);if(m!=null&&m.state!=MatchState.FINISHED){UUID win=m.a.equals(id)?m.b:m.a;endMatch(m,win,id,"disconnect");}else if(participants.contains(id)){participants.remove(id);markRestore(id,"Disconnected from tournament");}}
    @EventHandler public void onRespawn(PlayerRespawnEvent e){UUID id=e.getPlayer().getUniqueId();if(pendingRestore.containsKey(id)||snapshots.contains("players."+id)){Location original=snapshotLocation(id);e.setRespawnLocation(original==null?mainSpawn():original);Bukkit.getScheduler().runTaskLater(this,()->scheduleRestoreWhenReady(id,0),5L);}}
    @EventHandler(priority=EventPriority.HIGHEST) public void onDeath(PlayerDeathEvent e){Player p=e.getEntity();Match m=matchesByPlayer.get(p.getUniqueId());Player killer=creditedKiller(p);recentDamage.remove(p.getUniqueId());if(m!=null&&m.state==MatchState.FIGHTING){e.setKeepInventory(true);e.setKeepLevel(true);e.getDrops().clear();e.setDroppedExp(0);e.deathMessage(null);UUID winner=m.a.equals(p.getUniqueId())?m.b:m.a;if(killer!=null)playKillEffect(killer,p.getLocation());endMatch(m,winner,p.getUniqueId(),"defeat");Bukkit.getScheduler().runTaskLater(this,()->{Player eliminated=Bukkit.getPlayer(p.getUniqueId());if(eliminated!=null&&eliminated.isDead())eliminated.spigot().respawn();},2L);}else if(duels==null||(!duels.isInMatch(p.getUniqueId())&&!duels.isHandlingDeath(p.getUniqueId()))){if(killer!=null)playKillEffect(killer,p.getLocation());}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void onDamage(EntityDamageEvent e){if(!(e.getEntity() instanceof Player p))return;if(e.getCause()==EntityDamageEvent.DamageCause.WORLD_BORDER&&isMainWorld(p.getWorld())&&insideConfiguredMainBorder(p.getLocation())){e.setCancelled(true);repairMainWorldBorder();p.setWorldBorder(null);return;}Match m=matchesByPlayer.get(p.getUniqueId());if(m!=null&&m.state!=MatchState.FIGHTING)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void onDamageBy(EntityDamageByEntityEvent e){if(!(e.getEntity() instanceof Player victim))return;Match m=matchesByPlayer.get(victim.getUniqueId());if(m==null)return;Player attacker=null;if(e.getDamager() instanceof Player x)attacker=x;else if(e.getDamager() instanceof org.bukkit.entity.Projectile pr&&pr.getShooter() instanceof Player x)attacker=x;if(attacker==null||matchesByPlayer.get(attacker.getUniqueId())!=m||m.state!=MatchState.FIGHTING)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void rememberDamage(EntityDamageByEntityEvent e){if(!(e.getEntity() instanceof Player victim))return;Player attacker=null;if(e.getDamager() instanceof Player player)attacker=player;else if(e.getDamager() instanceof org.bukkit.entity.Projectile projectile&&projectile.getShooter() instanceof Player player)attacker=player;if(attacker!=null&&!attacker.getUniqueId().equals(victim.getUniqueId()))recentDamage.put(victim.getUniqueId(),new DamageCredit(attacker.getUniqueId(),System.currentTimeMillis()+15_000L));}
    private Player creditedKiller(Player victim){Player direct=victim.getKiller();if(direct!=null)return direct;DamageCredit credit=recentDamage.get(victim.getUniqueId());if(credit==null||credit.expiresAt()<System.currentTimeMillis())return null;return Bukkit.getPlayer(credit.attacker());}
    @EventHandler public void onMove(PlayerMoveEvent e){Match m=matchesByPlayer.get(e.getPlayer().getUniqueId());if(m!=null&&m.state==MatchState.COUNTDOWN&&e.getTo()!=null&&(e.getFrom().getX()!=e.getTo().getX()||e.getFrom().getZ()!=e.getTo().getZ()))e.setTo(e.getFrom());}
    @EventHandler public void onInteract(PlayerInteractEvent e){UUID id=e.getPlayer().getUniqueId();Match m=matchesByPlayer.get(id);if(participants.contains(id)&&(m==null||m.state!=MatchState.FIGHTING))e.setCancelled(true);}
    @EventHandler public void onInteractEntity(PlayerInteractEntityEvent e){UUID id=e.getPlayer().getUniqueId();Match m=matchesByPlayer.get(id);if(participants.contains(id)&&(m==null||m.state!=MatchState.FIGHTING))e.setCancelled(true);}
    @EventHandler public void onSwap(PlayerSwapHandItemsEvent e){UUID id=e.getPlayer().getUniqueId();Match m=matchesByPlayer.get(id);if(participants.contains(id)&&(m==null||m.state!=MatchState.FIGHTING))e.setCancelled(true);}
    @EventHandler public void onDrag(org.bukkit.event.inventory.InventoryDragEvent e){if(!(e.getWhoClicked() instanceof Player p))return;UUID id=p.getUniqueId();Match m=matchesByPlayer.get(id);boolean enderChest=e.getView().getTopInventory().getType()==InventoryType.ENDER_CHEST;if(participants.contains(id)&&!enderChest&&(m==null||m.state!=MatchState.FIGHTING))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void onPearlHit(ProjectileHitEvent event){if(!(event.getEntity() instanceof EnderPearl pearl)||!(pearl.getShooter() instanceof Player player))return;World arena=activeArenaWorld(player.getUniqueId());if(arena==null||pearl.getWorld()!=arena||event.getHitBlock()==null||event.getHitBlock().getType()!=Material.BARRIER)return;event.setCancelled(true);bouncePearl(player,pearl,true);}
    @EventHandler public void onTeleport(PlayerTeleportEvent e){UUID id=e.getPlayer().getUniqueId();if(internalTeleports.contains(id))return;if(e.getCause()==PlayerTeleportEvent.TeleportCause.ENDER_PEARL){if(blockedPearlTeleports.remove(id)){e.setCancelled(true);return;}World arena=activeArenaWorld(id);if(arena!=null&&(e.getTo()==null||e.getTo().getWorld()!=arena||!arena.getWorldBorder().isInside(e.getTo())||touchesBarrier(e.getTo()))){e.setCancelled(true);dropReturnedPearl(e.getPlayer(),e.getPlayer().getLocation(),e.getPlayer().getLocation().getDirection().multiply(-.35).setY(.18));return;}}Match watched=spectatorMatches.get(id);if(watched!=null){if(e.getTo()==null||e.getTo().getWorld()!=watched.world){e.setCancelled(true);e.getPlayer().sendMessage(PREFIX+"§cUse /tournamentunspectate to leave the private arena.");}return;}if(!participants.contains(id))return;Match m=matchesByPlayer.get(id);if(m==null||(m.state!=MatchState.FIGHTING)||(e.getTo()!=null&&e.getTo().getWorld()!=m.world))e.setCancelled(true);}
    @EventHandler public void onDrop(PlayerDropItemEvent e){UUID id=e.getPlayer().getUniqueId();Match match=matchesByPlayer.get(id);if(participants.contains(id)&&(match==null||match.state!=MatchState.FIGHTING))e.setCancelled(true);}
    @EventHandler public void onPickup(PlayerAttemptPickupItemEvent e){UUID id=e.getPlayer().getUniqueId();Match m=matchesByPlayer.get(id);if(participants.contains(id)&&(m==null||m.state!=MatchState.FIGHTING))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void onCommand(PlayerCommandPreprocessEvent e){if(!participants.contains(e.getPlayer().getUniqueId()))return;String c=e.getMessage().toLowerCase(Locale.ROOT).trim().split("\\s+")[0];if(c.startsWith("/"))c=c.substring(1);int namespace=c.lastIndexOf(':');if(namespace>=0)c=c.substring(namespace+1);if(!c.equals("ec")&&!c.equals("enderchest")){e.setCancelled(true);e.getPlayer().sendMessage(PREFIX+"§cOnly /ec and /enderchest are available while you are in the tournament.");}}
    @EventHandler public void onBreak(BlockBreakEvent e){if(e.getBlock().getWorld().getName().startsWith("mt_match_")&&participants.contains(e.getPlayer().getUniqueId()))e.setCancelled(true);}

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(command.getName().equalsIgnoreCase("tournamentspectate")){if(!(sender instanceof Player viewer)){sender.sendMessage("Players only.");return true;}if(args.length<1){viewer.sendMessage(PREFIX+"§e/watch <player> §8• §7Live: §f"+(spectatablePlayers().isEmpty()?"none":String.join(", ",spectatablePlayers())));return true;}Player target=Bukkit.getPlayer(args[0]);if(target==null){viewer.sendMessage(PREFIX+"§cPlayer not found.");return true;}return startAnySpectating(viewer,target);}
        if(command.getName().equalsIgnoreCase("tournamentunspectate")){if(sender instanceof Player viewer)stopAnySpectating(viewer);else sender.sendMessage("Players only.");return true;}
        if(command.getName().equalsIgnoreCase("giveglory"))return handleGiveGlory(sender,args);
        if(args.length>0&&args[0].equalsIgnoreCase("unlock"))return handleCosmeticUnlock(sender,args);
        if(!(sender instanceof Player p)){if(args.length==0){sender.sendMessage("Use /tournament open|start|stop|glory");return true;}}
        if(args.length==0){if(sender instanceof Player p2){if(label.equalsIgnoreCase("gloryshop"))openShop(p2);else openMain(p2);}return true;}
        String sub=args[0].toLowerCase(Locale.ROOT);
        if(sub.equals("join")&&sender instanceof Player p2){register(p2);return true;} if(sub.equals("leave")&&sender instanceof Player p2){if(phase==Phase.OPEN){registered.remove(p2.getUniqueId());p2.sendMessage(PREFIX+"§cYou left registration.");}return true;} if(sub.equals("shop")&&sender instanceof Player p2){openShop(p2);return true;} if(sub.equals("spectate")&&sender instanceof Player p2){if(args.length<2){p2.sendMessage(PREFIX+"§e/tournament spectate <player> §8• §7Live: §f"+(spectatablePlayers().isEmpty()?"none":String.join(", ",spectatablePlayers())));return true;}Player target=Bukkit.getPlayer(args[1]);if(target==null){p2.sendMessage(PREFIX+"§cPlayer not found.");return true;}return startAnySpectating(p2,target);} if(sub.equals("unspectate")&&sender instanceof Player p2){stopAnySpectating(p2);return true;} if(sub.equals("status")){sender.sendMessage(PREFIX+String.join(" §8| §r",statusLines()));return true;}
        if(!sender.hasPermission("merelytournament.admin")){sender.sendMessage(PREFIX+"§cYou do not have permission.");return true;}
        switch(sub){
            case "open"->openRegistration(true);
            case "start"->startTournament(sender);
            case "stop"->{if(phase==Phase.CLOSED)sender.sendMessage(PREFIX+"§7No tournament is active.");else failTournament("Stopped by "+sender.getName());}
            case "recover"->{Player t=args.length>1?Bukkit.getPlayer(args[1]):sender instanceof Player x?x:null;if(t==null)sender.sendMessage(PREFIX+"§cPlayer not found.");else restore(t);}
            case "recoverbackup"->{if(args.length<2){sender.sendMessage(PREFIX+"§e/tournament recoverbackup <player> [latest|backup-file]");break;}recoverFromSnapshotBackup(sender,args[1],args.length>2?args[2]:"latest");}
            case "glory"->{
                if(args.length>=2&&args[1].equalsIgnoreCase("resetall")){
                    if(args.length<3||!args[2].equalsIgnoreCase("confirm")){
                        sender.sendMessage(PREFIX+"§cThis removes every Glory balance and Glory Shop unlock. Use §f/tournament glory resetall confirm §conly when the owner has approved it.");
                        break;
                    }
                    glory.set("players",null);
                    if(saveGlory()){
                        Bukkit.getOnlinePlayers().forEach(player->player.sendActionBar(Component.empty()));
                        sender.sendMessage(PREFIX+"§aAll Glory balances, purchases, tags, particles, kill effects and equipped cosmetics were removed.");
                    }else sender.sendMessage(PREFIX+"§cThe Glory reset could not be saved. Nothing should be treated as reset; check the console.");
                    break;
                }
                if(args.length<4){sender.sendMessage(PREFIX+"§e/tournament glory <give|take|set> <player> <amount> §8or §e/tournament glory resetall confirm");break;}
                OfflinePlayer t=findKnownPlayer(args[2]);if(t==null){sender.sendMessage(PREFIX+"§cPlayer not found or the partial name matches more than one player.");break;}int amount;try{amount=Integer.parseInt(args[3]);}catch(Exception e){sender.sendMessage(PREFIX+"§cInvalid amount.");break;}switch(args[1].toLowerCase()){case"give"->addGlory(t.getUniqueId(),amount,"Admin grant");case"take"->setBalance(t.getUniqueId(),balance(t.getUniqueId())-amount);case"set"->setBalance(t.getUniqueId(),amount);default->sender.sendMessage(PREFIX+"§cUse give, take, set or resetall.");}sender.sendMessage(PREFIX+"§aGlory for §f"+(t.getName()==null?t.getUniqueId():t.getName())+" §ais now §d"+balance(t.getUniqueId())+"§a.");}
            case "effect"->{if(args.length<3){sender.sendMessage(PREFIX+"§e/tournament effect <player> <"+String.join("|",KILL_EFFECT_IDS)+">");break;}Player t=Bukkit.getPlayer(args[1]);if(t==null){sender.sendMessage(PREFIX+"§cPlayer not found.");break;}String effect=args[2].toLowerCase(Locale.ROOT);if(!KILL_EFFECT_IDS.contains(effect)){sender.sendMessage(PREFIX+"§cUnknown effect.");break;}playKillEffect(effect,t.getLocation());sender.sendMessage(PREFIX+"§aPreviewing the §f"+effect+" §aeffect at "+t.getName()+".");}
            case "effecttest"->{String effect=args.length>1?args[1].toLowerCase(Locale.ROOT):"all";List<String> effects=effect.equals("all")?KILL_EFFECT_IDS:List.of(effect);if(effects.stream().anyMatch(x->!KILL_EFFECT_IDS.contains(x))){sender.sendMessage(PREFIX+"§cUse "+String.join(", ",KILL_EFFECT_IDS)+" or all.");break;}Location location=sender instanceof Player player?player.getLocation():mainSpawn();for(int i=0;i<effects.size();i++){String selected=effects.get(i);Bukkit.getScheduler().runTaskLater(this,()->playKillEffect(selected,location),i*30L);}sender.sendMessage(PREFIX+"§aPreviewing §f"+String.join(", ",effects)+"§a.");}
            case "verify"->verifySetup(sender);
            case "verifyclone"->verifyArenaClone(sender);
            case "arenas"->showActiveArenas(sender);
            case "recoverarena"->recoverArenaFromFawe(sender);
            default->sender.sendMessage(PREFIX+"§eAdmin: /tournament open, start, stop, recover, recoverbackup, recoverarena, glory, effect, effecttest, verify, verifyclone");
        }return true;
    }

    private boolean handleCosmeticUnlock(CommandSender sender,String[] args){
        if(!sender.hasPermission("merelytournament.admin")){sender.sendMessage(PREFIX+"§cYou do not have permission.");return true;}
        if(args.length!=4){sender.sendMessage(PREFIX+"§e/tournament unlock <player> <particle|kill> <effect>");return true;}
        Player target=Bukkit.getPlayerExact(args[1]);
        if(target==null){sender.sendMessage(PREFIX+"§cThat player must be online.");return true;}
        String type=args[2].toLowerCase(Locale.ROOT),effect=args[3].toLowerCase(Locale.ROOT);
        if(type.equals("particle")&&!PARTICLE_EFFECT_IDS.contains(effect)){sender.sendMessage(PREFIX+"§cUnknown particle effect.");return true;}
        if(type.equals("kill")&&!KILL_EFFECT_IDS.contains(effect)){sender.sendMessage(PREFIX+"§cUnknown kill effect.");return true;}
        if(!type.equals("particle")&&!type.equals("kill")){sender.sendMessage(PREFIX+"§cUse particle or kill.");return true;}
        String section=type.equals("particle")?"particles":"kills";
        String base="players."+target.getUniqueId();
        glory.set(base+".owned."+section+"."+effect,true);
        glory.set(base+".equipped."+section,effect);
        if(!saveGlory()){sender.sendMessage(PREFIX+"§cThe cosmetic could not be saved.");return true;}
        target.sendMessage(PREFIX+"§aBattle Pass cosmetic unlocked and equipped: §f"+pretty(effect)+"§a.");
        sender.sendMessage(PREFIX+"§aUnlocked §f"+pretty(effect)+" §afor §f"+target.getName()+"§a.");
        return true;
    }

    private String pretty(String value){return Arrays.stream(value.split("_")).map(s->s.isEmpty()?s:Character.toUpperCase(s.charAt(0))+s.substring(1)).collect(Collectors.joining(" "));}

    private boolean handleGiveGlory(CommandSender sender,String[] args){
        if(!sender.hasPermission("merelytournament.admin")){sender.sendMessage(PREFIX+"§cYou do not have permission.");return true;}
        if(args.length<2){sender.sendMessage(PREFIX+"§e/giveglory <player> <amount>");return true;}
        Player target=Bukkit.getPlayerExact(args[0]);
        if(target==null){sender.sendMessage(PREFIX+"§cThat player must be online.");return true;}
        int amount;
        try{amount=Integer.parseInt(args[1]);}catch(NumberFormatException e){sender.sendMessage(PREFIX+"§cThe amount must be a whole number.");return true;}
        if(amount<=0){sender.sendMessage(PREFIX+"§cThe amount must be greater than zero.");return true;}
        addGlory(target.getUniqueId(),amount,"Granted by "+sender.getName());
        sender.sendMessage(PREFIX+"§aGave §d"+amount+" Glory Points §ato §f"+target.getName()+"§a. New balance: §d"+balance(target.getUniqueId()));
        return true;
    }

    private void verifySetup(CommandSender sender){
        try{
            int parsed=validateTournamentKit();int rewards=0;for(Category category:Category.values())for(Offer offer:offers(category))if(!offer.cosmetic){rewardItem(offer);rewards++;}double half=getConfig().getDouble("arena.initial-border-size",120)/2.0,cx=getConfig().getDouble("arena.center-x",255),cz=getConfig().getDouble("arena.center-z",224);for(boolean one:List.of(true,false)){String path="arena."+(one?"player-one":"player-two");if(Math.abs(getConfig().getDouble(path+".x")-cx)>=half||Math.abs(getConfig().getDouble(path+".z")-cz)>=half)throw new IllegalStateException((one?"Player one":"Player two")+" spawn is outside the border");}
            World template=loadAndValidateTemplate(sender);if(template==null)return;Location floorOne=arenaSpawn(template,true).clone().subtract(0,1,0),floorTwo=arenaSpawn(template,false).clone().subtract(0,1,0);sender.sendMessage(PREFIX+"§aVerification passed: §f"+parsed+" tournament kit entries, "+rewards+" shop rewards, both spawns inside the border, world §d"+template.getName()+"§a, floors §f"+floorOne.getBlock().getType()+" §a/ §f"+floorTwo.getBlock().getType()+"§a.");
        }catch(Exception e){sender.sendMessage(PREFIX+"§cVerification failed: "+e.getMessage());getLogger().log(Level.SEVERE,"Tournament verification failed",e);}
    }

    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){if(command.getName().equalsIgnoreCase("tournamentspectate"))return args.length==1?spectatablePlayers().stream().filter(n->n.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).toList():List.of();if(command.getName().equalsIgnoreCase("giveglory")){if(args.length==1)return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n->n.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).toList();if(args.length==2)return List.of("100","250","500","1000").stream().filter(n->n.startsWith(args[1])).toList();return List.of();}if(args.length==1){List<String>x=new ArrayList<>(List.of("join","leave","shop","status","spectate","unspectate"));if(sender.hasPermission("merelytournament.admin"))x.addAll(List.of("open","start","stop","recover","recoverbackup","recoverarena","glory","effect","effecttest","verify","verifyclone","arenas"));return x.stream().filter(s->s.startsWith(args[0].toLowerCase())).toList();}if(args.length==2&&args[0].equalsIgnoreCase("spectate"))return spectatablePlayers().stream().filter(n->n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();if(args.length==2&&args[0].equalsIgnoreCase("glory"))return List.of("give","take","set","resetall").stream().filter(s->s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();if(args.length==2&&args[0].equalsIgnoreCase("effecttest")){List<String> effects=new ArrayList<>(KILL_EFFECT_IDS);effects.addFirst("all");return effects;}if(args.length==2&&args[0].equalsIgnoreCase("recoverbackup"))return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n->n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();if(args.length==3&&args[0].equalsIgnoreCase("recoverbackup"))return List.of("latest","tournament-1789127369559.yml").stream().filter(s->s.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();if((args.length==2&&(args[0].equalsIgnoreCase("recover")||args[0].equalsIgnoreCase("effect")))||(args.length==3&&args[0].equalsIgnoreCase("glory")&&!args[1].equalsIgnoreCase("resetall")))return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();if(args.length==3&&args[0].equalsIgnoreCase("effect"))return KILL_EFFECT_IDS;return List.of();}

    private final class GloryPlaceholder extends PlaceholderExpansion {
        @Override public String getIdentifier(){return "merelytournament";}
        @Override public String getAuthor(){return "MerelyMeSMP";}
        @Override public String getVersion(){return MerelyTournament.this.getDescription().getVersion();}
        @Override public boolean persist(){return true;}
        @Override public String onRequest(OfflinePlayer player,String parameters){
            if(player==null)return "0";
            if(parameters.equalsIgnoreCase("glory"))return String.valueOf(balance(player.getUniqueId()));
            return null;
        }
    }

    private record Pair(UUID a,UUID b,String world){}
    private static final class Match{final UUID a,b;final World world;MatchState state=MatchState.COUNTDOWN;long startedAt;BossBar bar;BukkitTask countdownTask,timerTask;Match(UUID a,UUID b,World w){this.a=a;this.b=b;this.world=w;}}
}




