package com.merelyme.gameplay;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class MerelyGameplayFixes extends JavaPlugin implements Listener {
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Wind Burst anvil and protected-zone interaction fixes enabled.");
    }

    private boolean isWindBurstMace(ItemStack item) {
        return item != null
                && item.getType() == Material.MACE
                && item.getEnchantmentLevel(Enchantment.WIND_BURST) > 0;
    }

    private boolean isBlockedCombination(AnvilInventory inventory) {
        return isWindBurstMace(inventory.getFirstItem())
                && isWindBurstMace(inventory.getSecondItem());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        if (!isBlockedCombination(inventory)) {
            return;
        }

        inventory.setMaximumRepairCost(40);
        inventory.setRepairCost(60);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onAnvilResultClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory() instanceof AnvilInventory inventory)
                || event.getRawSlot() != 2
                || !isBlockedCombination(inventory)) {
            return;
        }

        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) {
            player.sendMessage("§cWind Burst maces cannot be combined.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onProtectedSwitchUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getItem() == null
                || event.getItem().getType() != Material.WIND_CHARGE
                || event.getClickedBlock() == null
                || !isProtectedSwitch(event.getClickedBlock().getType())
                || !isProtectedZone(event.getClickedBlock().getWorld().getName(),
                        event.getClickedBlock().getX(), event.getClickedBlock().getY(), event.getClickedBlock().getZ())) {
            return;
        }

        event.setCancelled(true);
    }

    private boolean isProtectedSwitch(Material material) {
        String name = material.name();
        return name.endsWith("_TRAPDOOR")
                || name.endsWith("_DOOR")
                || name.endsWith("_FENCE_GATE")
                || name.endsWith("_BUTTON")
                || name.endsWith("_PRESSURE_PLATE")
                || material == Material.LEVER;
    }

    private boolean isProtectedZone(String world, int x, int y, int z) {
        if (!world.equalsIgnoreCase("world")) {
            return false;
        }

        boolean spawnSafeZone = x >= 103 && x <= 223
                && y >= 61 && y <= 199
                && z >= 97 && z <= 217;
        boolean poi = x >= -343 && x <= -158
                && y >= -64 && y <= 320
                && z >= -506 && z <= -321;
        return spawnSafeZone || poi;
    }
}
