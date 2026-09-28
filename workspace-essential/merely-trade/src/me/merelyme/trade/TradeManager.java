package me.merelyme.trade;

import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.skstudios.core.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TradeManager {
    private static final long REQUEST_MS = 30_000L;

    private final MerelyTradePlugin plugin;
    private final Map<UUID, TradeRequest> incoming = new HashMap<UUID, TradeRequest>();
    private final Map<UUID, TradeSession> sessions = new HashMap<UUID, TradeSession>();
    private final BukkitTask ticker;

    public TradeManager(MerelyTradePlugin plugin) {
        this.plugin = plugin;
        this.ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        this.ticker.cancel();
        for (TradeSession session : new ArrayList<TradeSession>(this.sessions.values())) {
            this.cancel(session, "&#FF2121Trade cancelled - plugin reload.");
        }
        this.incoming.clear();
    }

    public TradeSession sessionOf(Player player) {
        return this.sessions.get(player.getUniqueId());
    }

    public TradeGui guiOf(Player player) {
        if (player == null) {
            return null;
        }
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof TradeGui gui) {
            return gui;
        }
        return null;
    }

    public boolean request(Player from, String targetName) {
        if (this.sessionOf(from) != null) {
            this.plugin.say(from, "&#FF2121You are already in a trade.");
            return true;
        }
        Player to = Bukkit.getPlayerExact(targetName);
        if (to == null || !to.isOnline()) {
            this.plugin.say(from, "&#FF2121That player is not online.");
            return true;
        }
        if (to.getUniqueId().equals(from.getUniqueId())) {
            this.plugin.say(from, "&#FF2121You cannot trade with yourself.");
            return true;
        }
        if (this.plugin.blocksRequests(to)) {
            this.plugin.say(from, "&#FF2121" + to.getName() + " has trade requests disabled.");
            return true;
        }
        if (this.sessionOf(to) != null) {
            this.plugin.say(from, "&#FF2121They are already in a trade.");
            return true;
        }
        TradeRequest existing = this.incoming.get(from.getUniqueId());
        if (existing != null && !existing.expired() && existing.from.equals(to.getUniqueId())) {
            return this.start(to, from);
        }
        TradeRequest mine = this.incoming.get(to.getUniqueId());
        if (mine != null && !mine.expired() && mine.from.equals(from.getUniqueId())) {
            this.plugin.say(from, "&fRequest already sent to &#00B5FF" + to.getName() + "&f.");
            return true;
        }
        this.incoming.put(to.getUniqueId(), new TradeRequest(from.getUniqueId(), to.getUniqueId(), System.currentTimeMillis() + REQUEST_MS));
        this.plugin.say(from, "&fTrade request sent to &#00B5FF" + to.getName() + "&f. &7(30s)");
        to.sendMessage(Text.chat("&#00B5FF&lSMP &#9B9B9B» &#00B5FF" + from.getName() + " &fsent you a trade request."));
        to.sendMessage(Text.chat("&#71F521[ACCEPT]").clickEvent(ClickEvent.runCommand("/trade accept " + from.getName())).hoverEvent(HoverEvent.showText(Text.chat("&aAccept trade"))));
        to.sendMessage(Text.chat("&#FF2121[DENY]").clickEvent(ClickEvent.runCommand("/trade deny " + from.getName())).hoverEvent(HoverEvent.showText(Text.chat("&cDeny trade"))));
        return true;
    }

    public boolean accept(Player player, String fromName) {
        TradeRequest request = this.incoming.get(player.getUniqueId());
        if (request == null || request.expired()) {
            this.incoming.remove(player.getUniqueId());
            this.plugin.say(player, "&#FF2121You have no trade request.");
            return true;
        }
        Player from = Bukkit.getPlayer(request.from);
        if (from == null || !from.isOnline()) {
            this.incoming.remove(player.getUniqueId());
            this.plugin.say(player, "&#FF2121They went offline.");
            return true;
        }
        if (fromName != null && !from.getName().equalsIgnoreCase(fromName)) {
            this.plugin.say(player, "&#FF2121That request is from &#00B5FF" + from.getName() + "&f.");
            return true;
        }
        return this.start(from, player);
    }

    public boolean deny(Player player, String fromName) {
        TradeRequest request = this.incoming.remove(player.getUniqueId());
        if (request == null || request.expired()) {
            this.plugin.say(player, "&#FF2121You have no trade request.");
            return true;
        }
        Player from = Bukkit.getPlayer(request.from);
        if (fromName != null && from != null && !from.getName().equalsIgnoreCase(fromName)) {
            this.incoming.put(player.getUniqueId(), request);
            this.plugin.say(player, "&#FF2121That request is from &#00B5FF" + from.getName() + "&f.");
            return true;
        }
        this.plugin.say(player, "&fTrade denied.");
        if (from != null) {
            this.plugin.say(from, "&#FF2121" + player.getName() + " denied the trade.");
        }
        return true;
    }

    public boolean cancelByPlayer(Player player) {
        TradeSession session = this.sessionOf(player);
        if (session == null) {
            this.plugin.say(player, "&#FF2121You are not in a trade.");
            return true;
        }
        this.cancel(session, "&#FF2121" + player.getName() + " cancelled the trade.");
        return true;
    }

    private boolean start(Player left, Player right) {
        this.incoming.remove(left.getUniqueId());
        this.incoming.remove(right.getUniqueId());
        if (this.sessionOf(left) != null || this.sessionOf(right) != null) {
            this.plugin.say(left, "&#FF2121Somebody is already trading.");
            this.plugin.say(right, "&#FF2121Somebody is already trading.");
            return true;
        }
        TradeSession session = new TradeSession(left, right);
        this.sessions.put(left.getUniqueId(), session);
        this.sessions.put(right.getUniqueId(), session);
        new TradeGui(session, left).open();
        new TradeGui(session, right).open();
        this.plugin.say(left, "&fTrade opened with &#00B5FF" + right.getName() + "&f. &7Drag items onto your side.");
        this.plugin.say(right, "&fTrade opened with &#00B5FF" + left.getName() + "&f. &7Drag items onto your side.");
        return true;
    }

    public void syncViews(TradeSession session) {
        if (session == null || session.cancelled || session.completing) {
            return;
        }
        session.clearReady();
        session.syncing = true;
        TradeGui left = this.guiOf(session.left());
        TradeGui right = this.guiOf(session.right());
        if (left != null) {
            left.paintChrome();
            if (right != null) {
                left.showPartner(right.snapshotOwn());
            }
        }
        if (right != null) {
            right.paintChrome();
            if (left != null) {
                right.showPartner(left.snapshotOwn());
            }
        }
        session.syncing = false;
    }

    public void onClosed(Player player, TradeGui closing) {
        TradeSession session = this.sessionOf(player);
        if (session == null || session.completing || session.cancelled || session.syncing) {
            return;
        }
        this.cancel(session, "&#FF2121" + player.getName() + " closed the trade.", closing);
    }

    public void cancel(TradeSession session, String reason) {
        this.cancel(session, reason, null);
    }

    public void cancel(TradeSession session, String reason, TradeGui closing) {
        if (session.cancelled || session.completing) {
            return;
        }
        session.cancelled = true;
        this.sessions.remove(session.leftId);
        this.sessions.remove(session.rightId);
        this.giveBack(session.left(), this.takeFrom(session.left(), closing));
        this.giveBack(session.right(), this.takeFrom(session.right(), closing));
        Player left = session.left();
        Player right = session.right();
        if (left != null) {
            this.plugin.say(left, reason);
            left.closeInventory();
        }
        if (right != null) {
            this.plugin.say(right, reason);
            right.closeInventory();
        }
    }

    public void toggleReady(Player player) {
        TradeSession session = this.sessionOf(player);
        if (session == null) {
            return;
        }
        if (player.getItemOnCursor() != null && !player.getItemOnCursor().getType().isAir()) {
            this.plugin.say(player, "&#FF2121Put the item down first.");
            return;
        }
        session.setReady(player, !session.ready(player));
        this.refreshChrome(session);
        if (session.bothReady()) {
            Bukkit.getScheduler().runTask(this.plugin, () -> this.complete(session));
        }
    }

    private void refreshChrome(TradeSession session) {
        session.syncing = true;
        TradeGui left = this.guiOf(session.left());
        TradeGui right = this.guiOf(session.right());
        if (left != null) {
            left.paintChrome();
        }
        if (right != null) {
            right.paintChrome();
        }
        session.syncing = false;
    }

    public void complete(TradeSession session) {
        if (session.cancelled || session.completing || !session.bothReady() || !session.bothOnline()) {
            return;
        }
        Player left = session.left();
        Player right = session.right();
        TradeGui leftGui = this.guiOf(left);
        TradeGui rightGui = this.guiOf(right);
        if (leftGui == null || rightGui == null) {
            session.clearReady();
            this.refreshChrome(session);
            return;
        }
        List<ItemStack> leftItems = leftGui.takeOwnItems();
        List<ItemStack> rightItems = rightGui.takeOwnItems();
        if (!this.canFit(left, rightItems) || !this.canFit(right, leftItems)) {
            leftGui.putOwnItems(leftItems);
            rightGui.putOwnItems(rightItems);
            session.clearReady();
            this.syncViews(session);
            this.plugin.say(left, "&#FF2121Somebody needs more inventory space.");
            this.plugin.say(right, "&#FF2121Somebody needs more inventory space.");
            return;
        }
        session.completing = true;
        this.sessions.remove(session.leftId);
        this.sessions.remove(session.rightId);
        this.giveBack(left, rightItems);
        this.giveBack(right, leftItems);
        left.closeInventory();
        right.closeInventory();
        this.plugin.say(left, "&#71F521Trade complete with &#00B5FF" + right.getName() + "&#71F521.");
        this.plugin.say(right, "&#71F521Trade complete with &#00B5FF" + left.getName() + "&#71F521.");
    }

    private List<ItemStack> takeFrom(Player player, TradeGui closing) {
        TradeGui gui = this.guiOf(player);
        if (gui == null && closing != null && player != null && player.getUniqueId().equals(closing.viewer().getUniqueId())) {
            gui = closing;
        }
        if (gui == null) {
            return List.of();
        }
        List<ItemStack> items = gui.takeOwnItems();
        if (player.getItemOnCursor() != null && !player.getItemOnCursor().getType().isAir()) {
            items.add(player.getItemOnCursor().clone());
            player.setItemOnCursor(null);
        }
        return items;
    }

    private boolean canFit(Player player, List<ItemStack> items) {
        ItemStack[] clone = player.getInventory().getStorageContents();
        ItemStack[] copy = new ItemStack[clone.length];
        for (int i = 0; i < clone.length; i++) {
            copy[i] = clone[i] == null ? null : clone[i].clone();
        }
        for (ItemStack item : items) {
            ItemStack leftover = item.clone();
            leftover = this.simulateAdd(copy, leftover);
            if (leftover != null && leftover.getAmount() > 0) {
                return false;
            }
        }
        return true;
    }

    private ItemStack simulateAdd(ItemStack[] inv, ItemStack item) {
        int max = item.getMaxStackSize();
        for (int i = 0; i < inv.length && item.getAmount() > 0; i++) {
            ItemStack slot = inv[i];
            if (slot == null || slot.getType().isAir()) {
                continue;
            }
            if (!slot.isSimilar(item)) {
                continue;
            }
            int space = max - slot.getAmount();
            if (space <= 0) {
                continue;
            }
            int move = Math.min(space, item.getAmount());
            slot.setAmount(slot.getAmount() + move);
            item.setAmount(item.getAmount() - move);
        }
        for (int i = 0; i < inv.length && item.getAmount() > 0; i++) {
            if (inv[i] == null || inv[i].getType().isAir()) {
                inv[i] = item.clone();
                item.setAmount(0);
            }
        }
        return item.getAmount() > 0 ? item : null;
    }

    private void giveBack(Player player, List<ItemStack> items) {
        if (player == null) {
            return;
        }
        for (ItemStack item : items) {
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }

    private void tick() {
        Iterator<Map.Entry<UUID, TradeRequest>> it = this.incoming.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expired()) {
                it.remove();
            }
        }
        for (TradeSession session : new ArrayList<TradeSession>(this.sessions.values())) {
            if (session.cancelled || session.completing) {
                continue;
            }
            if (session.left() == null || session.right() == null) {
                this.cancel(session, "&#FF2121Trade cancelled - player left.");
            }
        }
    }
}
