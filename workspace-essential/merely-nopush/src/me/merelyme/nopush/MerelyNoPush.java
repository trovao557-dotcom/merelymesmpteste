package me.merelyme.nopush;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Iterator;

public final class MerelyNoPush extends JavaPlugin implements Listener {
    private Object regionContainer;
    private Method createQuery;
    private Method adaptLocation;
    private Method getApplicableRegions;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        setupWorldGuardQuery();
        Bukkit.getScheduler().runTaskTimer(this, this::applyAll, 1L, 100L);
        getLogger().info("MerelyNoPush ready - collision is disabled only inside named WorldGuard regions.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(this, () -> this.apply(event.getPlayer()));
    }

    /** Keep projectile damage enabled outside protected WorldGuard regions. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void restoreProjectileDamage(EntityDamageByEntityEvent event) {
        if (!event.isCancelled() || event.getCause() != EntityDamageEvent.DamageCause.PROJECTILE) return;
        if (!(event.getEntity() instanceof Player)) return;
        if (!(event.getDamager() instanceof Projectile)) return;
        if (isInsideNamedRegion(event.getEntity().getLocation())) return;
        event.setCancelled(false);
    }

    private void applyAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.apply(player);
        }
    }

    private void apply(Player player) {
        boolean protectedRegion = isInsideNamedRegion(player.getLocation());
        if (player.isCollidable() == !protectedRegion) {
            player.setCollidable(!protectedRegion);
        }
    }

    private void setupWorldGuardQuery() {
        try {
            Object worldGuard = Class.forName("com.sk89q.worldguard.WorldGuard")
                    .getMethod("getInstance").invoke(null);
            Object platform = worldGuard.getClass().getMethod("getPlatform").invoke(worldGuard);
            this.regionContainer = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            this.createQuery = regionContainer.getClass().getMethod("createQuery");
            Class<?> adapter = Class.forName("com.sk89q.worldguard.bukkit.BukkitAdapter");
            this.adaptLocation = adapter.getMethod("adapt", Location.class);
            Object query = createQuery.invoke(regionContainer);
            this.getApplicableRegions = query.getClass().getMethod("getApplicableRegions", Class.forName("com.sk89q.worldedit.util.Location"));
            getLogger().info("WorldGuard integration enabled.");
        } catch (Exception ex) {
            regionContainer = null;
            getLogger().warning("WorldGuard integration unavailable; collision remains enabled outside protected regions.");
        }
    }

    private boolean isInsideNamedRegion(Location location) {
        if (regionContainer == null) {
            return false;
        }
        try {
            Object query = createQuery.invoke(regionContainer);
            Object adapted = adaptLocation.invoke(null, location);
            Object regions = getApplicableRegions.invoke(query, adapted);
            if (!(regions instanceof Iterable<?>)) {
                return false;
            }
            Iterator<?> iterator = ((Iterable<?>) regions).iterator();
            while (iterator.hasNext()) {
                Object region = iterator.next();
                Method id = region.getClass().getMethod("getId");
                String name = String.valueOf(id.invoke(region));
                if (!"__global__".equalsIgnoreCase(name)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // A failed query must never make the whole server non-collidable.
        }
        return false;
    }
}
