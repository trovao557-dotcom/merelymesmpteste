/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.NamespacedKey
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.InventoryHolder
 */
package dev.nullkeeper.spearconfig.gui;

import dev.nullkeeper.spearconfig.gui.GuiPage;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ConfigGuiHolder
implements InventoryHolder {
    private final GuiPage page;
    private final int enchantmentPage;
    private final Map<Integer, NamespacedKey> enchantmentLevelSlots = new HashMap<Integer, NamespacedKey>();
    private final Map<Integer, NamespacedKey> enchantmentToggleSlots = new HashMap<Integer, NamespacedKey>();
    private Inventory inventory;

    ConfigGuiHolder(GuiPage page, int enchantmentPage) {
        this.page = page;
        this.enchantmentPage = enchantmentPage;
    }

    public GuiPage page() {
        return this.page;
    }

    public int enchantmentPage() {
        return this.enchantmentPage;
    }

    public Map<Integer, NamespacedKey> enchantmentLevelSlots() {
        return this.enchantmentLevelSlots;
    }

    public Map<Integer, NamespacedKey> enchantmentToggleSlots() {
        return this.enchantmentToggleSlots;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Inventory getInventory() {
        return this.inventory;
    }
}

