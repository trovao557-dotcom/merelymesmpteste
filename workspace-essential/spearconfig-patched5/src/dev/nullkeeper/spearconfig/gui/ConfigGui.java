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

    public ConfigGui(SpearConfigPlugin plugin, PluginSettings settings, SpearTracker tracker, ChatInputManager chatInput) {
        this.plugin = plugin;
        this.settings = settings;
        this.tracker = tracker;
        this.chatInput = chatInput;
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
    public void onClick(InventoryClickEvent event) {
        Player player;
        ConfigGuiHolder holder;
        block17: {
            block16: {
                InventoryHolder inventoryHolder = event.getView().getTopInventory().getHolder();
                if (!(inventoryHolder instanceof ConfigGuiHolder)) {
                    return;
                }
                holder = (ConfigGuiHolder)inventoryHolder;
                event.setCancelled(true);
                HumanEntity humanEntity = event.getWhoClicked();
                if (!(humanEntity instanceof Player)) break block16;
                player = (Player)humanEntity;
                if (event.getRawSlot() >= 0 && event.getRawSlot() < 45) break block17;
            }
            return;
        }
        int slot = event.getRawSlot();
        if (slot == 36 && holder.page() != GuiPage.MAIN) {
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> this.openMain(player));
            return;
        }
        switch (holder.page()) {
            case MAIN: {
                this.handleMain(player, slot);
                break;
            }
            case LIMITS: {
                this.handleLimits(player, slot);
                break;
            }
            case COOLDOWNS: {
                this.handleCooldowns(player, slot);
                break;
            }
            case MOB_DROPS: {
                this.handleMobDrops(player, slot);
                break;
            }
            case ENCHANTMENTS: {
                this.handleEnchantments(player, holder, slot);
                break;
            }
            case VISUALS: {
                this.handleVisuals(player, slot, event.isLeftClick(), event.isRightClick());
                break;
            }
            case REVEAL: {
                this.handleReveal(player, slot);
                break;
            }
            case ANNOUNCEMENTS: {
                this.handleAnnouncements(player, slot);
                break;
            }
            case TRACKING: {
                this.handleTracking(player, slot);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ConfigGuiHolder && event.getRawSlots().stream().anyMatch(slot -> slot < 45)) {
            event.setCancelled(true);
        }
    }

    private void handleMain(Player player, int slot) {
        GuiPage page = switch (slot) {
            case 10 -> GuiPage.LIMITS;
            case 12 -> GuiPage.COOLDOWNS;
            case 14 -> GuiPage.MOB_DROPS;
            case 16 -> GuiPage.ENCHANTMENTS;
            case 28 -> GuiPage.VISUALS;
            case 30 -> GuiPage.REVEAL;
            case 32 -> GuiPage.ANNOUNCEMENTS;
            case 34 -> GuiPage.TRACKING;
            default -> null;
        };
        if (page != null) {
            this.schedule(player, page, 0);
        }
    }

    private void handleLimits(Player player, int slot) {
        int valueIndex = ConfigGui.indexOf(LIMIT_VALUE_SLOTS, slot);
        if (valueIndex >= 0) {
            SpearTier tier = SpearTier.values()[valueIndex];
            this.requestInteger(player, tier.displayName() + " spear limit", 0, Integer.MAX_VALUE, value -> this.plugin.getConfig().set("limits." + tier.key() + ".amount", (Object)value), GuiPage.LIMITS, 0);
            return;
        }
        int toggleIndex = ConfigGui.indexOf(LIMIT_TOGGLE_SLOTS, slot);
        if (toggleIndex >= 0) {
            SpearTier tier = SpearTier.values()[toggleIndex];
            this.plugin.getConfig().set("limits." + tier.key() + ".enabled", (Object)(!this.settings.limitEnabled(tier) ? 1 : 0));
            this.applyAndReopen(player, GuiPage.LIMITS, 0);
        }
    }

    private void handleCooldowns(Player player, int slot) {
        AttackType valueType = slot == 11 ? AttackType.JAB : (slot == 15 ? AttackType.LUNGE : null);
        if (valueType != null) {
            this.requestInteger(player, valueType.displayName() + " cooldown seconds", 0, Integer.MAX_VALUE, value -> this.plugin.getConfig().set("cooldown." + valueType.key() + ".seconds", (Object)value), GuiPage.COOLDOWNS, 0);
            return;
        }
        AttackType toggleType = slot == 29 ? AttackType.JAB : (slot == 33 ? AttackType.LUNGE : null);
        if (toggleType != null) {
            this.plugin.getConfig().set("cooldown." + toggleType.key() + ".enabled", (Object)(!this.settings.cooldownEnabled(toggleType) ? 1 : 0));
            this.applyAndReopen(player, GuiPage.COOLDOWNS, 0);
        }
    }

    private void handleMobDrops(Player player, int slot) {
        if (slot != 22) {
            return;
        }
        this.plugin.getConfig().set("blockMobDrop", (Object)(!this.settings.blockMobDrop() ? 1 : 0));
        this.applyAndReopen(player, GuiPage.MOB_DROPS, 0);
    }

    private void handleEnchantments(Player player, ConfigGuiHolder holder, int slot) {
        NamespacedKey levelKey = holder.enchantmentLevelSlots().get(slot);
        if (levelKey != null) {
            this.settings.findCompatibleEnchantment(levelKey.asString()).ifPresent(enchantment -> this.requestInteger(player, ConfigGui.friendlyEnchantmentName(enchantment) + " maximum level", 1, enchantment.getMaxLevel(), value -> this.plugin.getConfig().set(PluginSettings.enchantmentPath(levelKey) + ".maxLevel", (Object)value), GuiPage.ENCHANTMENTS, holder.enchantmentPage()));
            return;
        }
        NamespacedKey toggleKey = holder.enchantmentToggleSlots().get(slot);
        if (toggleKey != null) {
            this.settings.findCompatibleEnchantment(toggleKey.asString()).ifPresent(enchantment -> {
                this.plugin.getConfig().set(PluginSettings.enchantmentPath(toggleKey) + ".allowed", (Object)(!this.settings.isAllowed((Enchantment)enchantment) ? 1 : 0));
                this.applyAndReopen(player, GuiPage.ENCHANTMENTS, holder.enchantmentPage());
            });
            return;
        }
        int pageCount = this.enchantmentPageCount();
        if (slot == 38 && holder.enchantmentPage() > 0) {
            this.schedule(player, GuiPage.ENCHANTMENTS, holder.enchantmentPage() - 1);
        } else if (slot == 42 && holder.enchantmentPage() + 1 < pageCount) {
            this.schedule(player, GuiPage.ENCHANTMENTS, holder.enchantmentPage() + 1);
        } else if (slot == 40 && holder.enchantmentPage() + 1 == pageCount) {
            this.plugin.getConfig().set("enchantable", (Object)(!this.settings.enchantable() ? 1 : 0));
            this.applyAndReopen(player, GuiPage.ENCHANTMENTS, holder.enchantmentPage());
        }
    }

    private void handleVisuals(Player player, int slot, boolean left, boolean right) {
        if (slot == 13) {
            this.plugin.getConfig().set("glow", (Object)(!this.settings.glow() ? 1 : 0));
        } else if (slot == 31) {
            this.plugin.getConfig().set("tab", (Object)(!this.settings.tab() ? 1 : 0));
        } else if (slot == 22 && (left || right)) {
            List<DisplayColor> colors = Arrays.asList(DisplayColor.values());
            int direction = left ? 1 : -1;
            int next = Math.floorMod(colors.indexOf((Object)this.settings.color()) + direction, colors.size());
            this.plugin.getConfig().set("color", (Object)colors.get(next).name());
        } else {
            return;
        }
        this.applyAndReopen(player, GuiPage.VISUALS, 0);
    }

    private void handleReveal(Player player, int slot) {
        if (slot == 13) {
            this.plugin.getConfig().set("reveal.enabled", (Object)(!this.settings.revealEnabled() ? 1 : 0));
            this.applyAndReopen(player, GuiPage.REVEAL, 0);
        } else if (slot == 31) {
            this.requestInteger(player, "position reveal interval in seconds", 5, Integer.MAX_VALUE, value -> this.plugin.getConfig().set("reveal.interval", (Object)value), GuiPage.REVEAL, 0);
        }
    }

    private void handleAnnouncements(Player player, int slot) {
        if (slot == 13) {
            this.plugin.getConfig().set("announcements.chat", (Object)(!this.settings.chatAnnouncements() ? 1 : 0));
        } else if (slot == 31) {
            this.plugin.getConfig().set("announcements.title", (Object)(!this.settings.titleAnnouncements() ? 1 : 0));
        } else {
            return;
        }
        this.applyAndReopen(player, GuiPage.ANNOUNCEMENTS, 0);
    }

    private void handleTracking(Player player, int slot) {
        if (slot == 13) {
            Map<SpearTier, Integer> counts = this.plugin.refreshTrackedSpears();
            int total = counts.values().stream().mapToInt(Integer::intValue).sum();
            player.sendMessage((Component)Component.text((String)("Refreshed tracked spears: " + total + " total."), (TextColor)NamedTextColor.GREEN));
            this.schedule(player, GuiPage.TRACKING, 0);
        } else if (slot == 31) {
            this.plugin.reloadPluginConfiguration();
            player.sendMessage((Component)Component.text((String)"Reloaded config.yml and restarted tasks.", (TextColor)NamedTextColor.GREEN));
            this.schedule(player, GuiPage.TRACKING, 0);
        }
    }

    private void open(Player player, GuiPage page, int enchantmentPage) {
        switch (page) {
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
                this.openEnchantments(player, enchantmentPage);
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
        for (int index = 0; index < SpearTier.values().length; ++index) {
            SpearTier tier = SpearTier.values()[index];
            inventory.setItem(LIMIT_VALUE_SLOTS[index], ConfigGui.valueItem(tier.material(), tier.displayName() + " Limit", this.settings.limit(tier), "Tracked: " + this.tracker.count(tier), "0 blocks every craft while enabled."));
            inventory.setItem(LIMIT_TOGGLE_SLOTS[index], ConfigGui.toggleItem(tier.displayName() + " Limit", this.settings.limitEnabled(tier), "Disabled means unlimited crafting.", "Configured amount: " + this.settings.limit(tier)));
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

    private void openEnchantments(Player player, int requestedPage) {
        int pageCount = this.enchantmentPageCount();
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        Inventory inventory = this.create(GuiPage.ENCHANTMENTS, page, "Spear Enchants " + (page + 1) + "/" + pageCount);
        ConfigGuiHolder holder = (ConfigGuiHolder)inventory.getHolder();
        List<Enchantment> enchantments = this.settings.compatibleEnchantments();
        int firstIndex = page * ENCHANTMENTS_PER_PAGE;
        int endIndex = Math.min(firstIndex + ENCHANTMENTS_PER_PAGE, enchantments.size());
        for (int index = firstIndex; index < endIndex; ++index) {
            Enchantment enchantment = enchantments.get(index);
            int rowStart = ENCHANTMENT_ROW_STARTS[index - firstIndex];
            this.addEnchantmentRow(inventory, holder, rowStart, enchantment);
        }
        if (enchantments.isEmpty()) {
            inventory.setItem(22, ConfigGui.item(Material.BARRIER, "No Compatible Enchantments", "Paper did not report any enchantments", "as valid for a spear."));
        }
        if (page > 0) {
            inventory.setItem(38, ConfigGui.item(Material.ARROW, "Previous Page", "Open page " + page + "."));
        }
        if (page + 1 < pageCount) {
            inventory.setItem(42, ConfigGui.item(Material.ARROW, "Next Page", "Open page " + (page + 2) + "."));
        } else {
            inventory.setItem(40, ConfigGui.toggleItem("Spear Enchanting", this.settings.enchantable(), "Master control for tables and anvils."));
        }
        player.openInventory(inventory);
    }

    private void addEnchantmentRow(Inventory inventory, ConfigGuiHolder holder, int rowStart, Enchantment enchantment) {
        EnchantmentRule rule = this.settings.enchantmentRule(enchantment);
        String name = ConfigGui.friendlyEnchantmentName(enchantment);
        int naturalMaximum = enchantment.getMaxLevel();
        for (int level = 1; level <= 5; ++level) {
            String availability;
            Material material;
            int slot = rowStart + level - 1;
            holder.enchantmentLevelSlots().put(slot, enchantment.getKey());
            if (level > naturalMaximum) {
                material = Material.GRAY_DYE;
                availability = "Not available for this enchantment.";
            } else if (level == rule.maxLevel()) {
                material = Material.ENCHANTED_BOOK;
                availability = "Current configured maximum.";
            } else if (level < rule.maxLevel()) {
                material = Material.BOOK;
                availability = "Allowed by the current maximum.";
            } else {
                material = Material.GUNPOWDER;
                availability = "Above the current maximum.";
            }
            inventory.setItem(slot, ConfigGui.item(material, name + " \u2014 Level " + level, availability, "Current maximum: " + rule.maxLevel(), "Natural maximum: " + naturalMaximum, "", "Click to enter an exact level in chat."));
        }
        int toggleSlot = rowStart + 6;
        holder.enchantmentToggleSlots().put(toggleSlot, enchantment.getKey());
        inventory.setItem(toggleSlot, ConfigGui.toggleItem(name, rule.allowed(), "Maximum level: " + rule.maxLevel(), "Click to allow or block this enchantment."));
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

    private Inventory create(GuiPage page, int enchantmentPage, String title) {
        ConfigGuiHolder holder = new ConfigGuiHolder(page, enchantmentPage);
        Inventory inventory = Bukkit.createInventory((InventoryHolder)holder, (int)45, (Component)Component.text((String)title, (TextColor)NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        holder.setInventory(inventory);
        ConfigGui.fillBorder(inventory);
        if (page != GuiPage.MAIN) {
            inventory.setItem(36, ConfigGui.item(Material.ARROW, "Back", "Return to the main menu."));
        }
        return inventory;
    }

    private static void fillBorder(Inventory inventory) {
        ItemStack border = ConfigGui.item(Material.GRAY_STAINED_GLASS_PANE, " ", new String[0]);
        for (int slot = 0; slot < 45; ++slot) {
            int row = slot / 9;
            int column = slot % 9;
            if (row != 0 && row != 4 && column != 0 && column != 8) continue;
            inventory.setItem(slot, border);
        }
    }

    private static ItemStack valueItem(Material material, String name, int value, String ... details) {
        ArrayList<Object> lore = new ArrayList<Object>();
        lore.add("Current value: " + value);
        lore.addAll(Arrays.asList(details));
        lore.add("");
        lore.add("Click to enter an exact integer in chat.");
        return ConfigGui.item(material, name, (String[])lore.toArray(String[]::new));
    }

    private static ItemStack toggleItem(String name, boolean enabled, String ... details) {
        ArrayList<Object> lore = new ArrayList<Object>();
        lore.add("Status: " + (enabled ? "Enabled" : "Disabled"));
        lore.addAll(Arrays.asList(details));
        lore.add("");
        lore.add("Click to toggle.");
        return ConfigGui.item(enabled ? Material.LIME_DYE : Material.BARRIER, name, (String[])lore.toArray(String[]::new));
    }

    private static ItemStack item(Material material, String name, String ... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text((String)name, (TextColor)NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        if (loreLines.length > 0) {
            meta.lore(Arrays.stream(loreLines).map(line -> (TextComponent)Component.text((String)line, (TextColor)NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)).toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    private void requestInteger(Player player, String label, int minimum, int maximum, IntConsumer update, GuiPage page, int enchantmentPage) {
        Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> this.chatInput.requestInteger(player, label, minimum, maximum, value -> {
            update.accept(value);
            this.plugin.applyConfigurationChanges();
            this.open(player, page, enchantmentPage);
        }, () -> this.open(player, page, enchantmentPage)));
    }

    private void applyAndReopen(Player player, GuiPage page, int enchantmentPage) {
        this.plugin.applyConfigurationChanges();
        this.schedule(player, page, enchantmentPage);
    }

    private void schedule(Player player, GuiPage page, int enchantmentPage) {
        Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> this.open(player, page, enchantmentPage));
    }

    private int enchantmentPageCount() {
        return Math.max(1, (this.settings.compatibleEnchantments().size() + ENCHANTMENTS_PER_PAGE - 1) / ENCHANTMENTS_PER_PAGE);
    }

    private int trackedTotal() {
        return this.tracker.snapshot().values().stream().mapToInt(Integer::intValue).sum();
    }

    private static int indexOf(int[] values, int sought) {
        for (int index = 0; index < values.length; ++index) {
            if (values[index] != sought) continue;
            return index;
        }
        return -1;
    }

    private static String friendlyEnchantmentName(Enchantment enchantment) {
        String key = enchantment.getKey().getKey();
        String[] words = key.split("[_./-]+");
        StringBuilder friendly = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!friendly.isEmpty()) {
                friendly.append(' ');
            }
            friendly.append(word.substring(0, 1).toUpperCase(Locale.ROOT));
            friendly.append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return friendly.isEmpty() ? key : friendly.toString();
    }
}
