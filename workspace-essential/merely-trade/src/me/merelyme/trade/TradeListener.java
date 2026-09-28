package me.merelyme.trade;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public final class TradeListener implements Listener {
    private final MerelyTradePlugin plugin;

    public TradeListener(MerelyTradePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof TradeGui gui)) {
            return;
        }
        TradeSession session = this.plugin.trades().sessionOf(player);
        if (session == null || gui.session() != session || session.completing || session.cancelled) {
            event.setCancelled(true);
            return;
        }

        int raw = event.getRawSlot();
        boolean top = raw < event.getView().getTopInventory().getSize();

        if (!top) {
            if (event.getClick().isShiftClick() || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                event.setCancelled(true);
                ItemStack current = event.getCurrentItem();
                if (current == null || current.getType().isAir()) {
                    return;
                }
                int empty = gui.firstEmptyOwn();
                if (empty < 0) {
                    return;
                }
                gui.getInventory().setItem(empty, current.clone());
                event.setCurrentItem(null);
                this.queueSync(session);
            }
            return;
        }

        if (TradeGui.isOwnSlot(raw)) {
            if (event.getClick() == ClickType.NUMBER_KEY || event.getClick() == ClickType.SWAP_OFFHAND) {
                event.setCancelled(true);
                return;
            }
            this.queueSync(session);
            return;
        }

        event.setCancelled(true);
        if (raw == TradeGui.ACCEPT) {
            this.plugin.trades().toggleReady(player);
            return;
        }
        if (raw == TradeGui.CANCEL) {
            this.plugin.trades().cancelByPlayer(player);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof TradeGui gui)) {
            return;
        }
        TradeSession session = this.plugin.trades().sessionOf(player);
        if (session == null || gui.session() != session) {
            event.setCancelled(true);
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int raw : event.getRawSlots()) {
            if (raw >= topSize) {
                continue;
            }
            if (!TradeGui.isOwnSlot(raw)) {
                event.setCancelled(true);
                return;
            }
        }
        this.queueSync(session);
    }

    private void queueSync(TradeSession session) {
        Bukkit.getScheduler().runTask(this.plugin, () -> this.plugin.trades().syncViews(session));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof TradeGui gui) {
            this.plugin.trades().onClosed(player, gui);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        TradeSession session = this.plugin.trades().sessionOf(event.getPlayer());
        if (session != null) {
            this.plugin.trades().cancel(session, "&#FF2121Trade cancelled - " + event.getPlayer().getName() + " left.");
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        TradeSession session = this.plugin.trades().sessionOf(event.getEntity());
        if (session != null) {
            this.plugin.trades().cancel(session, "&#FF2121Trade cancelled - a player died.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player hurt) || !(event.getDamager() instanceof Player)) {
            return;
        }
        TradeSession session = this.plugin.trades().sessionOf(hurt);
        if (session != null) {
            this.plugin.trades().cancel(session, "&#FF2121Trade cancelled - combat started.");
        }
    }
}
