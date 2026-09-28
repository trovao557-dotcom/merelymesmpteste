package com.merelyme.arenafixes;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class MerelyArenaFixes extends JavaPlugin implements Listener {
    private final Map<UUID, Location> matchSpawns = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isArena(player.getWorld())) enterArena(player);
        }
        getLogger().info("Arena offhand, countdown inventory, potion and height fixes enabled.");
    }

    private boolean isArena(World world) {
        if (world == null) return false;
        String name = world.getName().toLowerCase(Locale.ROOT);
        return name.startsWith("mt_") || name.contains("tournament") ||
                name.contains("duel") || name.contains("bot") || name.contains("train");
    }

    private void enterArena(Player player) {
        matchSpawns.put(player.getUniqueId(), player.getLocation().clone());
        for (long delay : new long[]{1L, 10L, 40L}) {
            Bukkit.getScheduler().runTaskLater(this, () -> prepare(player), delay);
        }
    }

    private void prepare(Player player) {
        if (!player.isOnline() || !isArena(player.getWorld()) || player.getGameMode() == GameMode.SPECTATOR) return;
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand == null || offhand.getType().isAir()) {
            player.getInventory().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 180, 1, false, true, true), true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 180, 1, false, true, true), true);
        if (!player.hasMetadata("merely_arena_potions")) {
            player.setMetadata("merely_arena_potions", new org.bukkit.metadata.FixedMetadataValue(this, true));
            throwPotion(player, PotionEffectType.STRENGTH);
            throwPotion(player, PotionEffectType.SPEED);
        }
        player.updateInventory();
    }

    private void throwPotion(Player player, PotionEffectType type) {
        ItemStack item = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.addCustomEffect(new PotionEffect(type, 20 * 180, 1, false, true, true), true);
        item.setItemMeta(meta);
        Location at = player.getLocation().clone().add(0, 1.8, 0);
        ThrownPotion potion = player.getWorld().spawn(at, ThrownPotion.class);
        potion.setShooter(player);
        potion.setItem(item);
        potion.setVelocity(new Vector(0, -0.35, 0));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        player.removeMetadata("merely_arena_potions", this);
        if (isArena(player.getWorld())) enterArena(player);
        else matchSpawns.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isArena(player.getWorld())) event.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && isArena(player.getWorld())) event.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isArena(event.getPlayer().getWorld())) event.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onHeldSlot(PlayerItemHeldEvent event) {
        if (isArena(event.getPlayer().getWorld())) event.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || to.getY() < 268 || !isArena(to.getWorld())) return;
        Location spawn = matchSpawns.get(event.getPlayer().getUniqueId());
        if (spawn == null || spawn.getWorld() != to.getWorld()) {
            spawn = new Location(to.getWorld(), 255.5, 168.0, 224.5, to.getYaw(), to.getPitch());
        }
        event.setTo(spawn.clone());
        event.getPlayer().setFallDistance(0);
        event.getPlayer().sendActionBar("§cArena height limit reached.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        matchSpawns.remove(event.getPlayer().getUniqueId());
    }
}
