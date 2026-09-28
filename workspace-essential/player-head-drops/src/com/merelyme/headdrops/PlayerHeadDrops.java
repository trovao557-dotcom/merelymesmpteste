package com.merelyme.headdrops;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.block.TileState;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerHeadDrops extends JavaPlugin implements Listener {
    private NamespacedKey ownerKey;
    private NamespacedKey ownerNameKey;
    private final Set<UUID> active = new HashSet<>();

    @Override
    public void onEnable() {
        ownerKey = new NamespacedKey(this, "head_owner");
        ownerNameKey = new NamespacedKey(this, "head_owner_name");
        saveDefaultConfig();
        for (String raw : getConfig().getStringList("active-heads")) {
            try { active.add(UUID.fromString(raw)); } catch (IllegalArgumentException ignored) { }
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("Player heads: 3% drop chance, one active head per player.");
    }

    @Override
    public void onDisable() {
        saveActive();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) return;
        if (active.contains(victim.getUniqueId())) return;
        if (Math.random() >= 0.03D) return;

        event.getDrops().add(createHead(victim.getUniqueId(), victim.getName()));
        active.add(victim.getUniqueId());
        saveActive();
        Bukkit.broadcastMessage(ChatColor.GOLD + killer.getName() + ChatColor.YELLOW
                + " found " + ChatColor.GOLD + victim.getName() + "'s Head" + ChatColor.YELLOW + "! (3% drop)");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        UUID owner = ownerOf(event.getItemInHand());
        if (owner == null || !(event.getBlockPlaced().getState() instanceof TileState tile)) return;
        String ownerName = ownerNameOf(event.getItemInHand(), owner);
        tile.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.toString());
        tile.getPersistentDataContainer().set(ownerNameKey, PersistentDataType.STRING, ownerName);
        tile.update(true, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        TaggedBlock tagged = taggedBlock(event.getBlock());
        if (tagged == null) return;
        event.setDropItems(false);
        event.getBlock().getWorld().dropItemNaturally(
                event.getBlock().getLocation().add(0.5, 0.3, 0.5),
                createHead(tagged.owner(), tagged.ownerName()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDespawn(ItemDespawnEvent event) {
        release(ownerOf(event.getEntity().getItemStack()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDestroyed(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item item)) return;
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.LAVA
                && cause != EntityDamageEvent.DamageCause.FIRE
                && cause != EntityDamageEvent.DamageCause.FIRE_TICK
                && cause != EntityDamageEvent.DamageCause.VOID) return;
        release(ownerOf(item.getItemStack()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        releaseExploded(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        releaseExploded(event.blockList());
    }

    private void releaseExploded(List<Block> blocks) {
        for (Block block : blocks) {
            TaggedBlock tagged = taggedBlock(block);
            if (tagged != null) release(tagged.owner());
        }
    }

    private ItemStack createHead(UUID owner, String ownerName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        OfflinePlayer profile = Bukkit.getOfflinePlayer(owner);
        meta.setOwningPlayer(profile);
        meta.setDisplayName(ChatColor.GOLD + ownerName + "'s Head");
        meta.setLore(List.of(ChatColor.GRAY + "Rare player drop", ChatColor.DARK_GRAY + "3% chance"));
        meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.toString());
        meta.getPersistentDataContainer().set(ownerNameKey, PersistentDataType.STRING, ownerName);
        head.setItemMeta(meta);
        return head;
    }

    private UUID ownerOf(ItemStack stack) {
        if (stack == null || stack.getType() != Material.PLAYER_HEAD) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        String raw = meta.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try { return UUID.fromString(raw); } catch (IllegalArgumentException ignored) { return null; }
    }

    private String ownerNameOf(ItemStack stack, UUID owner) {
        ItemMeta meta = stack.getItemMeta();
        String stored = meta == null ? null : meta.getPersistentDataContainer().get(ownerNameKey, PersistentDataType.STRING);
        if (stored != null && !stored.isBlank()) return stored;
        String known = Bukkit.getOfflinePlayer(owner).getName();
        return known == null ? owner.toString().substring(0, 8) : known;
    }

    private TaggedBlock taggedBlock(Block block) {
        if (!(block.getState() instanceof TileState tile)) return null;
        String raw = tile.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            UUID owner = UUID.fromString(raw);
            String name = tile.getPersistentDataContainer().get(ownerNameKey, PersistentDataType.STRING);
            if (name == null || name.isBlank()) name = owner.toString().substring(0, 8);
            return new TaggedBlock(owner, name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void release(UUID owner) {
        if (owner != null && active.remove(owner)) saveActive();
    }

    private void saveActive() {
        List<String> values = new ArrayList<>(active.size());
        for (UUID uuid : active) values.add(uuid.toString());
        getConfig().set("active-heads", values);
        saveConfig();
    }

    private record TaggedBlock(UUID owner, String ownerName) { }
}
