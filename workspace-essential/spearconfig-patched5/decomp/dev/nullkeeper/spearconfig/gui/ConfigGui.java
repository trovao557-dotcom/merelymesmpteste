/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.TextComponent
 *  net.kyori.adventure.text.format.NamedTextColor
 *  net.kyori.adventure.text.format.TextColor
 *  net.kyori.adventure.text.format.TextDecoration
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.enchantments.Enchantment
 *  org.bukkit.entity.HumanEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.inventory.InventoryDragEvent
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.InventoryHolder
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.plugin.Plugin
 */
package dev.nullkeeper.spearconfig.gui;

import dev.nullkeeper.spearconfig.SpearConfigPlugin;
import dev.nullkeeper.spearconfig.config.EnchantmentRule;
import dev.nullkeeper.spearconfig.config.PluginSettings;
import dev.nullkeeper.spearconfig.gui.ChatInputManager;
import dev.nullkeeper.spearconfig.gui.ConfigGuiHolder;
import dev.nullkeeper.spearconfig.gui.GuiPage;
import dev.nullkeeper.spearconfig.model.AttackType;
import dev.nullkeeper.spearconfig.model.DisplayColor;
import dev.nullkeeper.spearconfig.model.SpearTier;
import dev.nullkeeper.spearconfig.service.SpearTracker;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class ConfigGui
implements Listener {
    private static final int SIZE = 45;
    private static final int CENTER = 22;
    private static final int BACK = 36;
    private static final int PREVIOUS_PAGE = 38;
    private static final int MASTER_TOGGLE = 40;
    private static final int NEXT_PAGE = 42;
    private static final int[] LIMIT_VALUE_SLOTS = new int[]{10, 11, 12, 13, 14, 15, 16};
    private static final int[] LIMIT_TOGGLE_SLOTS = new int[]{28, 29, 30, 31, 32, 33, 34};
    private static final int[] ENCHANTMENT_ROW_STARTS = new int[]{10, 19, 28};
    private static final int ENCHANTMENTS_PER_PAGE = ENCHANTMENT_ROW_STARTS.length;
    private final SpearConfigPlugin plugin;
    private final PluginSettings settings;
    private final SpearTracker tracker;
    private final ChatInputManager chatInput;

    public ConfigGui(SpearConfigPlugin spearConfigPlugin, PluginSettings pluginSettings, SpearTracker spearTracker, ChatInputManager chatInputManager) {
        this.plugin = spearConfigPlugin;
        this.settings = pluginSettings;
        this.tracker = spearTracker;
        this.chatInput = chatInputManager;
    }

    public void openMain(Player player) {
        Inventory inventory = this.create(GuiPage.MAIN, 0, "Spear Config");
        inventory.setItem(10, ConfigGui.item(Material.CRAFTING_TABLE, "Craft Limits", "Configure all seven tier limits."));
        inventory.setItem(12, ConfigGui.item(Material.CLOCK, "Attack Cooldowns", "Configure jab and lunge separately."));
        inventory.setItem(14, ConfigGui.item(Material.ROTTEN_FLESH, "Mob Drops", "Control natural mob spear drops."));
        inventory.setItem(16, ConfigGui.item(Material.ENCHANTED_BOOK, "Enchantments", "Allow, block, and cap compatible enchantments."));
        inventory.setItem(28, ConfigGui.item(Material.GLOW_INK_SAC, "Wielder Visuals", "Configure glow, tab names, and color."));
        inventory.setItem(30, ConfigGui.item(Material.COMPASS, "Position Reveal", "Configure broadcasts and their interval."));
        inventory.setItem(32, ConfigGui.item(Material.BELL, "Announcements", "Configure chat and title announcements."));
        inventory.setItem(34, ConfigGui.item(Material.ENDER_CHEST, "Tracking / Reload", "Refresh counts or reload config.yml."));
        inventory.setItem(22, ConfigGui.item(Material.NETHERITE_SPEAR, "Spear Config", "Every category is mirrored around this center.", "Tracked spears: " + this.trackedTotal()));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent inventoryClickEvent) {
        Player player;
        ConfigGuiHolder configGuiHolder;
        block17: {
            block16: {
                InventoryHolder inventoryHolder = inventoryClickEvent.getView().getTopInventory().getHolder();
                if (!(inventoryHolder instanceof ConfigGuiHolder)) {
                    return;
                }
                configGuiHolder = (ConfigGuiHolder)inventoryHolder;
                inventoryClickEvent.setCancelled(true);
                HumanEntity humanEntity = inventoryClickEvent.getWhoClicked();
                if (!(humanEntity instanceof Player)) break block16;
                player = (Player)humanEntity;
                if (inventoryClickEvent.getRawSlot() >= 0 && inventoryClickEvent.getRawSlot() < 45) break block17;
            }
            return;
        }
        int n = inventoryClickEvent.getRawSlot();
        if (n == 36 && configGuiHolder.page() != GuiPage.MAIN) {
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> this.openMain(player));
            return;
        }
        switch (configGuiHolder.page()) {
            case MAIN: {
                this.handleMain(player, n);
                break;
            }
            case LIMITS: {
                this.handleLimits(player, n);
                break;
            }
            case COOLDOWNS: {
                this.handleCooldowns(player, n);
                break;
            }
            case MOB_DROPS: {
                this.handleMobDrops(player, n);
                break;
            }
            case ENCHANTMENTS: {
                this.handleEnchantments(player, configGuiHolder, n);
                break;
            }
            case VISUALS: {
                this.handleVisuals(player, n, inventoryClickEvent.isLeftClick(), inventoryClickEvent.isRightClick());
                break;
            }
            case REVEAL: {
                this.handleReveal(player, n);
                break;
            }
            case ANNOUNCEMENTS: {
                this.handleAnnouncements(player, n);
                break;
            }
            case TRACKING: {
                this.handleTracking(player, n);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent inventoryDragEvent) {
        if (inventoryDragEvent.getView().getTopInventory().getHolder() instanceof ConfigGuiHolder && inventoryDragEvent.getRawSlots().stream().anyMatch(n -> n < 45)) {
            inventoryDragEvent.setCancelled(true);
        }
    }

    private void handleMain(Player player, int n) {
        GuiPage guiPage;
        switch (n) {
            case 10: {
                GuiPage guiPage2 = GuiPage.LIMITS;
                break;
            }
            case 12: {
                GuiPage guiPage2 = GuiPage.COOLDOWNS;
                break;
            }
            case 14: {
                GuiPage guiPage2 = GuiPage.MOB_DROPS;
                break;
            }
            case 16: {
                GuiPage guiPage2 = GuiPage.ENCHANTMENTS;
                break;
            }
            case 28: {
                GuiPage guiPage2 = GuiPage.VISUALS;
                break;
            }
            case 30: {
                GuiPage guiPage2 = GuiPage.REVEAL;
                break;
            }
            case 32: {
                GuiPage guiPage2 = GuiPage.ANNOUNCEMENTS;
                break;
            }
            case 34: {
                GuiPage guiPage2 = GuiPage.TRACKING;
                break;
            }
            default: {
                GuiPage guiPage2 = guiPage = null;
            }
        }
        if (guiPage != null) {
            this.schedule(player, guiPage, 0);
        }
    }

    private void handleLimits(Player player, int n2) {
        int n3 = ConfigGui.indexOf(LIMIT_VALUE_SLOTS, n2);
        if (n3 >= 0) {
            SpearTier spearTier = SpearTier.values()[n3];
            this.requestInteger(player, spearTier.displayName() + " spear limit", 0, Integer.MAX_VALUE, n -> this.plugin.getConfig().set("limits." + spearTier.key() + ".amount", (Object)n), GuiPage.LIMITS, 0);
            return;
        }
        int n4 = ConfigGui.indexOf(LIMIT_TOGGLE_SLOTS, n2);
        if (n4 >= 0) {
            SpearTier spearTier = SpearTier.values()[n4];
            this.plugin.getConfig().set("limits." + spearTier.key() + ".enabled", (Object)(!this.settings.limitEnabled(spearTier) ? 1 : 0));
            this.applyAndReopen(player, GuiPage.LIMITS, 0);
        }
    }

    private void handleCooldowns(Player player, int n2) {
        AttackType attackType;
        AttackType attackType2;
        AttackType attackType3 = n2 == 11 ? AttackType.JAB : (attackType2 = n2 == 15 ? AttackType.LUNGE : null);
        if (attackType2 != null) {
            this.requestInteger(player, attackType2.displayName() + " cooldown seconds", 0, Integer.MAX_VALUE, n -> this.plugin.getConfig().set("cooldown." + attackType2.key() + ".seconds", (Object)n), GuiPage.COOLDOWNS, 0);
            return;
        }
        AttackType attackType4 = n2 == 29 ? AttackType.JAB : (attackType = n2 == 33 ? AttackType.LUNGE : null);
        if (attackType != null) {
            this.plugin.getConfig().set("cooldown." + attackType.key() + ".enabled", (Object)(!this.settings.cooldownEnabled(attackType) ? 1 : 0));
            this.applyAndReopen(player, GuiPage.COOLDOWNS, 0);
        }
    }

    private void handleMobDrops(Player player, int n) {
        if (n != 22) {
            return;
        }
        this.plugin.getConfig().set("blockMobDrop", (Object)(!this.settings.blockMobDrop() ? 1 : 0));
        this.applyAndReopen(player, GuiPage.MOB_DROPS, 0);
    }

    private void handleEnchantments(Player player, ConfigGuiHolder configGuiHolder, int n) {
        NamespacedKey namespacedKey = configGuiHolder.enchantmentLevelSlots().get(n);
        if (namespacedKey != null) {
            this.settings.findCompatibleEnchantment(namespacedKey.asString()).ifPresent(enchantment -> this.requestInteger(player, ConfigGui.friendlyEnchantmentName(enchantment) + " maximum level", 1, enchantment.getMaxLevel(), n -> this.plugin.getConfig().set(PluginSettings.enchantmentPath(namespacedKey) + ".maxLevel", (Object)n), GuiPage.ENCHANTMENTS, configGuiHolder.enchantmentPage()));
            return;
        }
        NamespacedKey namespacedKey2 = configGuiHolder.enchantmentToggleSlots().get(n);
        if (namespacedKey2 != null) {
            this.settings.findCompatibleEnchantment(namespacedKey2.asString()).ifPresent(enchantment -> {
                this.plugin.getConfig().set(PluginSettings.enchantmentPath(namespacedKey2) + ".allowed", (Object)(!this.settings.isAllowed((Enchantment)enchantment) ? 1 : 0));
                this.applyAndReopen(player, GuiPage.ENCHANTMENTS, configGuiHolder.enchantmentPage());
            });
            return;
        }
        int n2 = this.enchantmentPageCount();
        if (n == 38 && configGuiHolder.enchantmentPage() > 0) {
            this.schedule(player, GuiPage.ENCHANTMENTS, configGuiHolder.enchantmentPage() - 1);
        } else if (n == 42 && configGuiHolder.enchantmentPage() + 1 < n2) {
            this.schedule(player, GuiPage.ENCHANTMENTS, configGuiHolder.enchantmentPage() + 1);
        } else if (n == 40 && configGuiHolder.enchantmentPage() + 1 == n2) {
            this.plugin.getConfig().set("enchantable", (Object)(!this.settings.enchantable() ? 1 : 0));
            this.applyAndReopen(player, GuiPage.ENCHANTMENTS, configGuiHolder.enchantmentPage());
        }
    }

    private void handleVisuals(Player player, int n, boolean bl, boolean bl2) {
        if (n == 13) {
            this.plugin.getConfig().set("glow", (Object)(!this.settings.glow() ? 1 : 0));
        } else if (n == 31) {
            this.plugin.getConfig().set("tab", (Object)(!this.settings.tab() ? 1 : 0));
        } else if (n == 22 && (bl || bl2)) {
            List<DisplayColor> list = Arrays.asList(DisplayColor.values());
            int n2 = bl ? 1 : -1;
            int n3 = Math.floorMod(list.indexOf((Object)this.settings.color()) + n2, list.size());
            this.plugin.getConfig().set("color", (Object)list.get(n3).name());
        } else {
            return;
        }
        this.applyAndReopen(player, GuiPage.VISUALS, 0);
    }

    private void handleReveal(Player player, int n2) {
        if (n2 == 13) {
            this.plugin.getConfig().set("reveal.enabled", (Object)(!this.settings.revealEnabled() ? 1 : 0));
            this.applyAndReopen(player, GuiPage.REVEAL, 0);
        } else if (n2 == 31) {
            this.requestInteger(player, "position reveal interval in seconds", 5, Integer.MAX_VALUE, n -> this.plugin.getConfig().set("reveal.interval", (Object)n), GuiPage.REVEAL, 0);
        }
    }

    private void handleAnnouncements(Player player, int n) {
        if (n == 13) {
            this.plugin.getConfig().set("announcements.chat", (Object)(!this.settings.chatAnnouncements() ? 1 : 0));
        } else if (n == 31) {
            this.plugin.getConfig().set("announcements.title", (Object)(!this.settings.titleAnnouncements() ? 1 : 0));
        } else {
            return;
        }
        this.applyAndReopen(player, GuiPage.ANNOUNCEMENTS, 0);
    }

    private void handleTracking(Player player, int n) {
        if (n == 13) {
            Map<SpearTier, Integer> map = this.plugin.refreshTrackedSpears();
            int n2 = map.values().stream().mapToInt(Integer::intValue).sum();
            player.sendMessage((Component)Component.text((String)("Refreshed tracked spears: " + n2 + " total."), (TextColor)NamedTextColor.GREEN));
            this.schedule(player, GuiPage.TRACKING, 0);
        } else if (n == 31) {
            this.plugin.reloadPluginConfiguration();
            player.sendMessage((Component)Component.text((String)"Reloaded config.yml and restarted tasks.", (TextColor)NamedTextColor.GREEN));
            this.schedule(player, GuiPage.TRACKING, 0);
        }
    }

    private void open(Player player, GuiPage guiPage, int n) {
        switch (guiPage) {
            case MAIN: {
                this.openMain(player);
                break;
            }
            case LIMITS: {
                this.openLimits(player);
                break;
            }
            case COOLDOWNS: {
                this.openCooldowns(player);
                break;
            }
            case MOB_DROPS: {
                this.openMobDrops(player);
                break;
            }
            case ENCHANTMENTS: {
                this.openEnchantments(player, n);
                break;
            }
            case VISUALS: {
                this.openVisuals(player);
                break;
            }
            case REVEAL: {
                this.openReveal(player);
                break;
            }
            case ANNOUNCEMENTS: {
                this.openAnnouncements(player);
                break;
            }
            case TRACKING: {
                this.openTracking(player);
            }
        }
    }

    private void openLimits(Player player) {
        Inventory inventory = this.create(GuiPage.LIMITS, 0, "Craft Limits");
        for (int i = 0; i < SpearTier.values().length; ++i) {
            SpearTier spearTier = SpearTier.values()[i];
            inventory.setItem(LIMIT_VALUE_SLOTS[i], ConfigGui.valueItem(spearTier.material(), spearTier.displayName() + " Limit", this.settings.limit(spearTier), "Tracked: " + this.tracker.count(spearTier), "0 blocks every craft while enabled."));
            inventory.setItem(LIMIT_TOGGLE_SLOTS[i], ConfigGui.toggleItem(spearTier.displayName() + " Limit", this.settings.limitEnabled(spearTier), "Disabled means unlimited crafting.", "Configured amount: " + this.settings.limit(spearTier)));
        }
        inventory.setItem(22, ConfigGui.item(Material.CRAFTING_TABLE, "Craft Limit Controls", "Amounts are above; master toggles are below.", "Click an amount to type an exact value."));
        player.openInventory(inventory);
    }

    private void openCooldowns(Player player) {
        Inventory inventory = this.create(GuiPage.COOLDOWNS, 0, "Attack Cooldowns");
        inventory.setItem(11, ConfigGui.valueItem(Material.IRON_SPEAR, "Jab Cooldown", this.settings.cooldown(AttackType.JAB), "seconds", "0 means no wait while enabled."));
        inventory.setItem(15, ConfigGui.valueItem(Material.FIREWORK_ROCKET, "Lunge Cooldown", this.settings.cooldown(AttackType.LUNGE), "seconds", "0 means no wait while enabled."));
        inventory.setItem(29, ConfigGui.toggleItem("Jab Cooldown", this.settings.cooldownEnabled(AttackType.JAB), "Configured value: " + this.settings.cooldown(AttackType.JAB) + " seconds"));
        inventory.setItem(33, ConfigGui.toggleItem("Lunge Cooldown", this.settings.cooldownEnabled(AttackType.LUNGE), "Configured value: " + this.settings.cooldown(AttackType.LUNGE) + " seconds"));
        inventory.setItem(22, ConfigGui.item(Material.CLOCK, "Cooldown Controls", "Values are above; master toggles are below.", "Click a value to type an exact duration."));
        player.openInventory(inventory);
    }

    private void openMobDrops(Player player) {
        Inventory inventory = this.create(GuiPage.MOB_DROPS, 0, "Mob Drops");
        inventory.setItem(22, ConfigGui.toggleItem("Block Mob Spear Drops", this.settings.blockMobDrop(), "Filters naturally spear-carrying mob deaths."));
        player.openInventory(inventory);
    }

    private void openEnchantments(Player player, int n) {
        int n2 = this.enchantmentPageCount();
        int n3 = Math.max(0, Math.min(n, n2 - 1));
        Inventory inventory = this.create(GuiPage.ENCHANTMENTS, n3, "Spear Enchants " + (n3 + 1) + "/" + n2);
        ConfigGuiHolder configGuiHolder = (ConfigGuiHolder)inventory.getHolder();
        List<Enchantment> list = this.settings.compatibleEnchantments();
        int n4 = n3 * ENCHANTMENTS_PER_PAGE;
        int n5 = Math.min(n4 + ENCHANTMENTS_PER_PAGE, list.size());
        for (int i = n4; i < n5; ++i) {
            Enchantment enchantment = list.get(i);
            int n6 = ENCHANTMENT_ROW_STARTS[i - n4];
            this.addEnchantmentRow(inventory, configGuiHolder, n6, enchantment);
        }
        if (list.isEmpty()) {
            inventory.setItem(22, ConfigGui.item(Material.BARRIER, "No Compatible Enchantments", "Paper did not report any enchantments", "as valid for a spear."));
        }
        if (n3 > 0) {
            inventory.setItem(38, ConfigGui.item(Material.ARROW, "Previous Page", "Open page " + n3 + "."));
        }
        if (n3 + 1 < n2) {
            inventory.setItem(42, ConfigGui.item(Material.ARROW, "Next Page", "Open page " + (n3 + 2) + "."));
        } else {
            inventory.setItem(40, ConfigGui.toggleItem("Spear Enchanting", this.settings.enchantable(), "Master control for tables and anvils."));
        }
        player.openInventory(inventory);
    }

    private void addEnchantmentRow(Inventory inventory, ConfigGuiHolder configGuiHolder, int n, Enchantment enchantment) {
        int n2;
        EnchantmentRule enchantmentRule = this.settings.enchantmentRule(enchantment);
        String string = ConfigGui.friendlyEnchantmentName(enchantment);
        int n3 = enchantment.getMaxLevel();
        for (n2 = 1; n2 <= 5; ++n2) {
            String string2;
            Material material;
            int n4 = n + n2 - 1;
            configGuiHolder.enchantmentLevelSlots().put(n4, enchantment.getKey());
            if (n2 > n3) {
                material = Material.GRAY_DYE;
                string2 = "Not available for this enchantment.";
            } else if (n2 == enchantmentRule.maxLevel()) {
                material = Material.ENCHANTED_BOOK;
                string2 = "Current configured maximum.";
            } else if (n2 < enchantmentRule.maxLevel()) {
                material = Material.BOOK;
                string2 = "Allowed by the current maximum.";
            } else {
                material = Material.GUNPOWDER;
                string2 = "Above the current maximum.";
            }
            inventory.setItem(n4, ConfigGui.item(material, string + " \u2014 Level " + n2, string2, "Current maximum: " + enchantmentRule.maxLevel(), "Natural maximum: " + n3, "", "Click to enter an exact level in chat."));
        }
        n2 = n + 6;
        configGuiHolder.enchantmentToggleSlots().put(n2, enchantment.getKey());
        inventory.setItem(n2, ConfigGui.toggleItem(string, enchantmentRule.allowed(), "Maximum level: " + enchantmentRule.maxLevel(), "Click to allow or block this enchantment."));
    }

    private void openVisuals(Player player) {
        Inventory inventory = this.create(GuiPage.VISUALS, 0, "Wielder Visuals");
        inventory.setItem(13, ConfigGui.toggleItem("Wielder Glow", this.settings.glow(), "Applies to anyone carrying a spear."));
        inventory.setItem(22, ConfigGui.item(this.settings.color().icon(), "Shared Color: " + this.settings.color().displayName(), "Left-click: next color", "Right-click: previous color", "Used for glow, tab, and dropped spear names."));
        inventory.setItem(31, ConfigGui.toggleItem("Tab-list Names", this.settings.tab(), "Colors anyone carrying a spear in the tab list."));
        player.openInventory(inventory);
    }

    private void openReveal(Player player) {
        Inventory inventory = this.create(GuiPage.REVEAL, 0, "Position Reveal");
        inventory.setItem(13, ConfigGui.toggleItem("Position Reveal", this.settings.revealEnabled(), "Broadcasts every spear carrier's position."));
        inventory.setItem(31, ConfigGui.valueItem(Material.CLOCK, "Reveal Interval", this.settings.revealInterval(), "seconds", "Minimum: 5"));
        player.openInventory(inventory);
    }

    private void openAnnouncements(Player player) {
        Inventory inventory = this.create(GuiPage.ANNOUNCEMENTS, 0, "Announcements");
        inventory.setItem(13, ConfigGui.toggleItem("Chat Announcements", this.settings.chatAnnouncements(), "Covers spear crafts and tier-limit events."));
        inventory.setItem(31, ConfigGui.toggleItem("Title Announcements", this.settings.titleAnnouncements(), "Covers spear crafts and tier-limit events."));
        player.openInventory(inventory);
    }

    private void openTracking(Player player) {
        Inventory inventory = this.create(GuiPage.TRACKING, 0, "Tracking / Reload");
        inventory.setItem(13, ConfigGui.item(Material.ENDER_CHEST, "Refresh Tracking", "Scan online inventories, ender chests,", "and nested shulker boxes."));
        inventory.setItem(22, ConfigGui.item(Material.CHEST, "Tracked Total", Integer.toString(this.trackedTotal())));
        inventory.setItem(31, ConfigGui.item(Material.REPEATER, "Reload Config", "Reload config.yml and restart tasks."));
        player.openInventory(inventory);
    }

    private Inventory create(GuiPage guiPage, int n, String string) {
        ConfigGuiHolder configGuiHolder = new ConfigGuiHolder(guiPage, n);
        Inventory inventory = Bukkit.createInventory((InventoryHolder)configGuiHolder, (int)45, (Component)Component.text((String)string, (TextColor)NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        configGuiHolder.setInventory(inventory);
        ConfigGui.fillBorder(inventory);
        if (guiPage != GuiPage.MAIN) {
            inventory.setItem(36, ConfigGui.item(Material.ARROW, "Back", "Return to the main menu."));
        }
        return inventory;
    }

    private static void fillBorder(Inventory inventory) {
        ItemStack itemStack = ConfigGui.item(Material.GRAY_STAINED_GLASS_PANE, " ", new String[0]);
        for (int i = 0; i < 45; ++i) {
            int n = i / 9;
            int n2 = i % 9;
            if (n != 0 && n != 4 && n2 != 0 && n2 != 8) continue;
            inventory.setItem(i, itemStack);
        }
    }

    private static ItemStack valueItem(Material material, String string, int n, String ... stringArray) {
        ArrayList<Object> arrayList = new ArrayList<Object>();
        arrayList.add("Current value: " + n);
        arrayList.addAll(Arrays.asList(stringArray));
        arrayList.add("");
        arrayList.add("Click to enter an exact integer in chat.");
        return ConfigGui.item(material, string, (String[])arrayList.toArray(String[]::new));
    }

    private static ItemStack toggleItem(String string, boolean bl, String ... stringArray) {
        ArrayList<Object> arrayList = new ArrayList<Object>();
        arrayList.add("Status: " + (bl ? "Enabled" : "Disabled"));
        arrayList.addAll(Arrays.asList(stringArray));
        arrayList.add("");
        arrayList.add("Click to toggle.");
        return ConfigGui.item(bl ? Material.LIME_DYE : Material.BARRIER, string, (String[])arrayList.toArray(String[]::new));
    }

    private static ItemStack item(Material material, String string2, String ... stringArray) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.displayName(Component.text((String)string2, (TextColor)NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        if (stringArray.length > 0) {
            itemMeta.lore(Arrays.stream(stringArray).map(string -> (TextComponent)Component.text((String)string, (TextColor)NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)).toList());
        }
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private void requestInteger(Player player, String string, int n, int n2, IntConsumer intConsumer, GuiPage guiPage, int n3) {
        Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> this.chatInput.requestInteger(player, string, n, n2, n2 -> {
            intConsumer.accept(n2);
            this.plugin.applyConfigurationChanges();
            this.open(player, guiPage, n3);
        }, () -> this.open(player, guiPage, n3)));
    }

    private void applyAndReopen(Player player, GuiPage guiPage, int n) {
        this.plugin.applyConfigurationChanges();
        this.schedule(player, guiPage, n);
    }

    private void schedule(Player player, GuiPage guiPage, int n) {
        Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> this.open(player, guiPage, n));
    }

    private int enchantmentPageCount() {
        return Math.max(1, (this.settings.compatibleEnchantments().size() + ENCHANTMENTS_PER_PAGE - 1) / ENCHANTMENTS_PER_PAGE);
    }

    private int trackedTotal() {
        return this.tracker.snapshot().values().stream().mapToInt(Integer::intValue).sum();
    }

    private static int indexOf(int[] nArray, int n) {
        for (int i = 0; i < nArray.length; ++i) {
            if (nArray[i] != n) continue;
            return i;
        }
        return -1;
    }

    private static String friendlyEnchantmentName(Enchantment enchantment) {
        String string = enchantment.getKey().getKey();
        String[] stringArray = string.split("[_./-]+");
        StringBuilder stringBuilder = new StringBuilder();
        for (String string2 : stringArray) {
            if (string2.isEmpty()) continue;
            if (!stringBuilder.isEmpty()) {
                stringBuilder.append(' ');
            }
            stringBuilder.append(string2.substring(0, 1).toUpperCase(Locale.ROOT));
            stringBuilder.append(string2.substring(1).toLowerCase(Locale.ROOT));
        }
        return stringBuilder.isEmpty() ? string : stringBuilder.toString();
    }
}

