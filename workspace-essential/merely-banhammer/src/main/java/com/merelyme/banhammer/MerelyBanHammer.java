package com.merelyme.banhammer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MerelyBanHammer extends JavaPlugin implements Listener, CommandExecutor {
    private static final String PREFIX = "§8[§c§lBAN HAMMER§8] §r";
    private static final long CLICK_GUARD_MS = 750L;

    private NamespacedKey markerKey;
    private NamespacedKey reasonKey;
    private NamespacedKey ownerKey;
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        markerKey = new NamespacedKey(this, "ban_hammer");
        reasonKey = new NamespacedKey(this, "reason");
        ownerKey = new NamespacedKey(this, "owner");
        Objects.requireNonNull(getCommand("banhammer")).setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("MerelyBanHammer enabled: one hit applies /tempban <player> 7d <reason>.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + "§cOnly players can hold the Ban Hammer.");
            return true;
        }
        if (!player.hasPermission("merelybanhammer.use")) {
            player.sendMessage(PREFIX + "§cYou do not have permission to use this command.");
            return true;
        }
        if (!player.hasPermission("skmod.tempban")) {
            player.sendMessage(PREFIX + "§cYou also need the §fskmod.tempban §cpermission.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(PREFIX + "§7Usage: §f/banhammer <reason>");
            return true;
        }

        String reason = String.join(" ", args).trim();
        if (reason.length() > 160) {
            player.sendMessage(PREFIX + "§cThe reason must have at most 160 characters.");
            return true;
        }

        ItemStack hammer = createHammer(player, reason);
        Map<Integer, ItemStack> left = player.getInventory().addItem(hammer);
        if (!left.isEmpty()) {
            player.sendMessage(PREFIX + "§cYour inventory is full. Free one slot and try again.");
            return true;
        }
        player.sendMessage(PREFIX + "§aBan Hammer armed for 7 days.");
        player.sendMessage(PREFIX + "§7Reason: §f" + reason);
        player.sendMessage(PREFIX + "§7Hit or right-click the player once. The hit deals no damage.");
        return true;
    }

    private ItemStack createHammer(Player owner, String reason) {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c§lBAN HAMMER");
        meta.setLore(List.of(
                "§7Temporary ban: §f7 days",
                "§7Reason: §f" + reason,
                "§8Hit or right-click a player once"
        ));
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(markerKey, PersistentDataType.BYTE, (byte) 1);
        data.set(reasonKey, PersistentDataType.STRING, reason);
        data.set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player staff) || !(event.getEntity() instanceof Player target)) return;
        ItemStack item = staff.getInventory().getItemInMainHand();
        if (!isHammer(item)) return;
        event.setCancelled(true);
        use(staff, target, item, EquipmentSlot.HAND);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onRightClick(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player target)) return;
        Player staff = event.getPlayer();
        ItemStack item = event.getHand() == EquipmentSlot.OFF_HAND
                ? staff.getInventory().getItemInOffHand()
                : staff.getInventory().getItemInMainHand();
        if (!isHammer(item)) return;
        event.setCancelled(true);
        use(staff, target, item, event.getHand());
    }

    private void use(Player staff, Player target, ItemStack item, EquipmentSlot hand) {
        long now = System.currentTimeMillis();
        long previous = lastUse.getOrDefault(staff.getUniqueId(), 0L);
        if (now - previous < CLICK_GUARD_MS) return;
        lastUse.put(staff.getUniqueId(), now);

        if (!staff.hasPermission("merelybanhammer.use") || !staff.hasPermission("skmod.tempban")) {
            staff.sendMessage(PREFIX + "§cYou do not have permission to use this Ban Hammer.");
            return;
        }
        if (staff.getUniqueId().equals(target.getUniqueId())) {
            staff.sendMessage(PREFIX + "§cYou cannot use the Ban Hammer on yourself.");
            return;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        String owner = data.get(ownerKey, PersistentDataType.STRING);
        if (!staff.getUniqueId().toString().equals(owner)) {
            staff.sendMessage(PREFIX + "§cThis Ban Hammer belongs to another staff member.");
            return;
        }
        String reason = data.get(reasonKey, PersistentDataType.STRING);
        if (reason == null || reason.isBlank()) {
            staff.sendMessage(PREFIX + "§cThis Ban Hammer has no valid reason.");
            return;
        }

        removeOne(staff, item, hand);
        String safeReason = reason.replace('\n', ' ').replace('\r', ' ').trim();
        boolean handled = Bukkit.dispatchCommand(staff, "tempban " + target.getName() + " 7d " + safeReason);
        if (!handled) {
            staff.getInventory().addItem(item.asOne());
            staff.sendMessage(PREFIX + "§cThe tempban command was not handled, so the Ban Hammer was returned.");
            return;
        }
        staff.sendMessage(PREFIX + "§aBan Hammer used on §f" + target.getName() + "§a for §f7 days§a.");
    }

    private boolean isHammer(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD || !item.hasItemMeta()) return false;
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(markerKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private void removeOne(Player player, ItemStack item, EquipmentSlot hand) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
            return;
        }
        if (hand == EquipmentSlot.OFF_HAND) player.getInventory().setItemInOffHand(null);
        else player.getInventory().setItemInMainHand(null);
    }
}
