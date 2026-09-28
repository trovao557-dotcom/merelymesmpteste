package merely.stackfix;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Keeps ordinary resource items in clean, mergeable stacks. */
public final class MerelyStackFix extends JavaPlugin implements Listener {
    private static final Set<Material> CLEAN_RESOURCES = EnumSet.of(
        Material.COAL, Material.CHARCOAL, Material.RAW_IRON, Material.IRON_INGOT,
        Material.RAW_COPPER, Material.COPPER_INGOT, Material.RAW_GOLD, Material.GOLD_INGOT,
        Material.NETHERITE_SCRAP, Material.DIAMOND, Material.EMERALD,
        Material.REDSTONE, Material.LAPIS_LAZULI, Material.QUARTZ, Material.AMETHYST_SHARD
    );
    private final Set<UUID> pending = new java.util.HashSet<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, true);
        }
        getLogger().info("Ordinary resource stacks are being merged automatically.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) schedule(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) schedule(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) schedule(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        schedule(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        schedule(event.getPlayer());
    }

    private void schedule(Player player) {
        UUID id = player.getUniqueId();
        if (!pending.add(id)) return;
        Bukkit.getScheduler().runTask(this, () -> {
            pending.remove(id);
            if (player.isOnline()) merge(player.getInventory());
        });
    }

    private static void merge(Inventory inventory) {
        ItemStack[] slots = inventory.getStorageContents();
        for (int i = 0; i < slots.length; i++) {
            ItemStack item = slots[i];
            if (empty(item)) continue;
            ItemStack clean = cleanResource(item);
            if (clean != item) {
                slots[i] = clean;
                item = clean;
            }
            for (int j = i + 1; j < slots.length && item.getAmount() < item.getMaxStackSize(); j++) {
                ItemStack other = slots[j];
                if (empty(other)) continue;
                ItemStack cleanOther = cleanResource(other);
                if (cleanOther != other) {
                    slots[j] = cleanOther;
                    other = cleanOther;
                }
                if (!item.isSimilar(other)) continue;
                int move = Math.min(other.getAmount(), item.getMaxStackSize() - item.getAmount());
                if (move <= 0) continue;
                item.setAmount(item.getAmount() + move);
                other.setAmount(other.getAmount() - move);
                if (other.getAmount() <= 0) slots[j] = null;
            }
        }
        inventory.setStorageContents(slots);
    }

    private static ItemStack cleanResource(ItemStack item) {
        if (item == null || !CLEAN_RESOURCES.contains(item.getType()) || !item.hasItemMeta()) return item;
        return new ItemStack(item.getType(), item.getAmount());
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }
}
