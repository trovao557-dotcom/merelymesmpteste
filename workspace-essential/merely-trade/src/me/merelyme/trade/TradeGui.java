package me.merelyme.trade;

import net.skstudios.core.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class TradeGui implements InventoryHolder {
    static final int[] OWN_SLOTS = {0, 1, 2, 3, 9, 10, 11, 12, 18, 19, 20, 21};
    static final int[] PARTNER_SLOTS = {5, 6, 7, 8, 14, 15, 16, 17, 23, 24, 25, 26};
    static final int[] DIVIDER = {4, 13, 22, 31};
    static final int ACCEPT = 27;
    static final int CANCEL = 28;
    static final int PARTNER_STATUS = 35;

    private final TradeSession session;
    private final Player viewer;
    private Inventory inventory;

    public TradeGui(TradeSession session, Player viewer) {
        this.session = session;
        this.viewer = viewer;
    }

    public TradeSession session() {
        return this.session;
    }

    public Player viewer() {
        return this.viewer;
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    public void open() {
        Player partner = this.session.partner(this.viewer);
        String name = partner == null ? "?" : partner.getName();
        this.inventory = Bukkit.createInventory(this, 36, Text.item("&8Trade &7| &#00B5FF" + name));
        this.paintChrome();
        this.viewer.openInventory(this.inventory);
    }

    public void paintChrome() {
        if (this.inventory == null) {
            return;
        }
        for (int slot : DIVIDER) {
            this.inventory.setItem(slot, pane(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        for (int slot = 27; slot < 36; slot++) {
            if (slot != ACCEPT && slot != CANCEL && slot != PARTNER_STATUS) {
                this.inventory.setItem(slot, pane(Material.BLACK_STAINED_GLASS_PANE, " "));
            }
        }
        this.inventory.setItem(32, pane(Material.GRAY_STAINED_GLASS_PANE, " "));
        this.inventory.setItem(33, pane(Material.GRAY_STAINED_GLASS_PANE, " "));
        this.inventory.setItem(34, pane(Material.GRAY_STAINED_GLASS_PANE, " "));

        Player partner = this.session.partner(this.viewer);
        String partnerName = partner == null ? "?" : partner.getName();
        boolean meReady = this.session.ready(this.viewer);
        boolean theyReady = partner != null && this.session.ready(partner);
        this.inventory.setItem(ACCEPT, named(
                meReady ? Material.LIME_CONCRETE : Material.GREEN_CONCRETE,
                meReady ? "&#71F521&lREADY" : "&#71F521&lACCEPT",
                List.of("&7Drag items onto your side", meReady ? "&7Click to unready" : "&7Click when the offer looks right")
        ));
        this.inventory.setItem(CANCEL, named(Material.RED_CONCRETE, "&#FF2121&lCANCEL", List.of("&7Close and get your items back")));
        this.inventory.setItem(PARTNER_STATUS, named(
                theyReady ? Material.LIME_STAINED_GLASS_PANE : Material.YELLOW_STAINED_GLASS_PANE,
                theyReady ? "&#71F521" + partnerName + " is ready" : "&e" + partnerName + " is looking",
                List.of("&7Both must accept to finish")
        ));
    }

    public void showPartner(List<ItemStack> items) {
        if (this.inventory == null) {
            return;
        }
        for (int i = 0; i < PARTNER_SLOTS.length; i++) {
            if (i < items.size()) {
                this.inventory.setItem(PARTNER_SLOTS[i], items.get(i).clone());
            } else {
                this.inventory.setItem(PARTNER_SLOTS[i], null);
            }
        }
    }

    public List<ItemStack> snapshotOwn() {
        List<ItemStack> out = new ArrayList<ItemStack>();
        if (this.inventory == null) {
            return out;
        }
        for (int slot : OWN_SLOTS) {
            ItemStack item = this.inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                out.add(item.clone());
            }
        }
        return out;
    }

    public List<ItemStack> takeOwnItems() {
        List<ItemStack> out = new ArrayList<ItemStack>();
        if (this.inventory == null) {
            return out;
        }
        for (int slot : OWN_SLOTS) {
            ItemStack item = this.inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                out.add(item.clone());
                this.inventory.setItem(slot, null);
            }
        }
        return out;
    }

    public void putOwnItems(List<ItemStack> items) {
        if (this.inventory == null) {
            return;
        }
        int i = 0;
        for (int slot : OWN_SLOTS) {
            if (i < items.size()) {
                this.inventory.setItem(slot, items.get(i).clone());
                i++;
            } else {
                this.inventory.setItem(slot, null);
            }
        }
    }

    public int firstEmptyOwn() {
        if (this.inventory == null) {
            return -1;
        }
        for (int slot : OWN_SLOTS) {
            ItemStack item = this.inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                return slot;
            }
        }
        return -1;
    }

    public static boolean isOwnSlot(int slot) {
        return ownIndex(slot) >= 0;
    }

    public static int ownIndex(int slot) {
        for (int i = 0; i < OWN_SLOTS.length; i++) {
            if (OWN_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    public static boolean isPartnerSlot(int slot) {
        for (int value : PARTNER_SLOTS) {
            if (value == slot) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack pane(Material material, String name) {
        return named(material, name, List.of());
    }

    private static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item(name));
        if (!lore.isEmpty()) {
            meta.lore(Text.lore(new ArrayList<String>(lore)));
        }
        item.setItemMeta(meta);
        return item;
    }
}
