/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.papermc.paper.registry.RegistryAccess
 *  io.papermc.paper.registry.RegistryKey
 *  org.bukkit.NamespacedKey
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.enchantments.Enchantment
 *  org.bukkit.inventory.ItemStack
 */
package dev.nullkeeper.spearconfig.config;

import dev.nullkeeper.spearconfig.SpearConfigPlugin;
import dev.nullkeeper.spearconfig.config.EnchantmentRule;
import dev.nullkeeper.spearconfig.model.AttackType;
import dev.nullkeeper.spearconfig.model.DisplayColor;
import dev.nullkeeper.spearconfig.model.SpearTier;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

public final class PluginSettings {
    public static final int MINIMUM_REVEAL_INTERVAL = 5;
    private final SpearConfigPlugin plugin;
    private final EnumMap<SpearTier, Integer> limits = new EnumMap(SpearTier.class);
    private final EnumMap<SpearTier, Boolean> limitEnabled = new EnumMap(SpearTier.class);
    private final EnumMap<AttackType, Integer> cooldowns = new EnumMap(AttackType.class);
    private final EnumMap<AttackType, Boolean> cooldownEnabled = new EnumMap(AttackType.class);
    private final Map<NamespacedKey, Enchantment> compatibleEnchantments = new LinkedHashMap<NamespacedKey, Enchantment>();
    private final Map<NamespacedKey, EnchantmentRule> enchantmentRules = new LinkedHashMap<NamespacedKey, EnchantmentRule>();
    private boolean blockMobDrop;
    private boolean enchantable;
    private boolean glow;
    private boolean tab;
    private DisplayColor color;
    private boolean revealEnabled;
    private int revealInterval;
    private boolean chatAnnouncements;
    private boolean titleAnnouncements;

    public PluginSettings(SpearConfigPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        int value;
        boolean enabled;
        String path;
        boolean migrated;
        FileConfiguration config = this.plugin.getConfig();
        boolean changed = migrated = PluginSettings.migrateLegacyConfig(config);
        if (migrated) {
            this.plugin.getLogger().info("Migrated legacy v0.1 configuration to the v1.0 schema.");
        }
        for (SpearTier spearTier : SpearTier.values()) {
            path = "limits." + spearTier.key();
            enabled = config.getBoolean(path + ".enabled", false);
            value = Math.max(0, config.getInt(path + ".amount", 0));
            this.limitEnabled.put(spearTier, enabled);
            this.limits.put(spearTier, value);
            changed |= PluginSettings.normalize(config, path + ".amount", value);
        }
        this.blockMobDrop = config.getBoolean("blockMobDrop", true);
        this.enchantable = config.getBoolean("enchantable", true);
        this.glow = config.getBoolean("glow", false);
        this.tab = config.getBoolean("tab", false);
        this.revealEnabled = config.getBoolean("reveal.enabled", false);
        this.chatAnnouncements = config.getBoolean("announcements.chat", true);
        this.titleAnnouncements = config.getBoolean("announcements.title", true);
        for (Enum enum_ : AttackType.values()) {
            path = "cooldown." + ((AttackType)enum_).key();
            enabled = config.getBoolean(path + ".enabled", false);
            value = Math.max(0, config.getInt(path + ".seconds", 0));
            this.cooldownEnabled.put((AttackType)enum_, enabled);
            this.cooldowns.put((AttackType)enum_, value);
            changed |= PluginSettings.normalize(config, path + ".seconds", value);
        }
        this.revealInterval = Math.max(5, config.getInt("reveal.interval", 300));
        changed |= PluginSettings.normalize(config, "reveal.interval", this.revealInterval);
        String configuredColor = config.getString("color", DisplayColor.GOLD.name());
        this.color = DisplayColor.fromKey(configuredColor == null ? "" : configuredColor).orElse(DisplayColor.GOLD);
        changed |= PluginSettings.normalize(config, "color", this.color.name());
        if (changed |= this.discoverCompatibleEnchantments(config)) {
            this.plugin.saveConfig();
        }
    }

    static boolean migrateLegacyConfig(FileConfiguration config) {
        Number number;
        Object legacy;
        String path;
        boolean changed = false;
        for (SpearTier spearTier : SpearTier.values()) {
            path = "limits." + spearTier.key();
            legacy = config.get(path);
            if (!(legacy instanceof Number)) continue;
            number = (Number)legacy;
            int amount = Math.max(0, number.intValue());
            config.set(path, null);
            config.set(path + ".enabled", (Object)(amount > 0 ? 1 : 0));
            config.set(path + ".amount", (Object)amount);
            changed = true;
        }
        for (Enum enum_ : AttackType.values()) {
            path = "cooldown." + ((AttackType)enum_).key();
            legacy = config.get(path);
            if (!(legacy instanceof Number)) continue;
            number = (Number)legacy;
            int seconds = Math.max(0, number.intValue());
            config.set(path, null);
            config.set(path + ".enabled", (Object)(seconds > 0 ? 1 : 0));
            config.set(path + ".seconds", (Object)seconds);
            changed = true;
        }
        Object legacyAnnouncements = config.get("announcements");
        if (legacyAnnouncements instanceof Boolean) {
            Boolean enabled = (Boolean)legacyAnnouncements;
            config.set("announcements", null);
            config.set("announcements.chat", (Object)enabled);
            config.set("announcements.title", (Object)enabled);
            changed = true;
        }
        return changed;
    }

    private boolean discoverCompatibleEnchantments(FileConfiguration config) {
        this.compatibleEnchantments.clear();
        this.enchantmentRules.clear();
        ItemStack referenceSpear = new ItemStack(SpearTier.WOOD.material());
        ArrayList<Object> discovered = new ArrayList<Object>();
        for (Object enchantment2 : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)) {
            if (!enchantment2.canEnchantItem(referenceSpear)) continue;
            discovered.add(enchantment2);
        }
        discovered.sort(Comparator.comparing(enchantment -> enchantment.getKey().asString()));
        boolean changed = false;
        for (Enchantment enchantment2 : discovered) {
            NamespacedKey key = enchantment2.getKey();
            this.compatibleEnchantments.put(key, enchantment2);
            String path = PluginSettings.enchantmentPath(key);
            if (!config.contains(path + ".allowed")) {
                config.set(path + ".allowed", (Object)true);
                changed = true;
            }
            if (!config.contains(path + ".maxLevel")) {
                config.set(path + ".maxLevel", (Object)enchantment2.getMaxLevel());
                changed = true;
            }
            boolean allowed = config.getBoolean(path + ".allowed", true);
            int maxLevel = Math.max(1, Math.min(enchantment2.getMaxLevel(), config.getInt(path + ".maxLevel", enchantment2.getMaxLevel())));
            changed |= PluginSettings.normalize(config, path + ".maxLevel", maxLevel);
            this.enchantmentRules.put(key, new EnchantmentRule(allowed, maxLevel));
        }
        String detected = this.compatibleEnchantments.keySet().stream().map(NamespacedKey::asString).collect(Collectors.joining(", "));
        this.plugin.getLogger().info("Detected " + this.compatibleEnchantments.size() + " spear-compatible enchantments from Paper: " + detected);
        return changed;
    }

    private static boolean normalize(FileConfiguration config, String path, Object value) {
        Object current = config.get(path);
        if (value.equals(current)) {
            return false;
        }
        config.set(path, value);
        return true;
    }

    public int limit(SpearTier tier) {
        return this.limits.getOrDefault((Object)tier, 0);
    }

    public boolean limitEnabled(SpearTier tier) {
        return this.limitEnabled.getOrDefault((Object)tier, false);
    }

    public int cooldown(AttackType type) {
        return this.cooldowns.getOrDefault((Object)type, 0);
    }

    public boolean cooldownEnabled(AttackType type) {
        return this.cooldownEnabled.getOrDefault((Object)type, false);
    }

    public boolean blockMobDrop() {
        return this.blockMobDrop;
    }

    public boolean enchantable() {
        return this.enchantable;
    }

    public boolean glow() {
        return this.glow;
    }

    public boolean tab() {
        return this.tab;
    }

    public DisplayColor color() {
        return this.color;
    }

    public boolean revealEnabled() {
        return this.revealEnabled;
    }

    public int revealInterval() {
        return this.revealInterval;
    }

    public boolean chatAnnouncements() {
        return this.chatAnnouncements;
    }

    public boolean titleAnnouncements() {
        return this.titleAnnouncements;
    }

    public List<Enchantment> compatibleEnchantments() {
        return List.copyOf(this.compatibleEnchantments.values());
    }

    public Optional<Enchantment> findCompatibleEnchantment(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        return this.compatibleEnchantments.values().stream().filter(enchantment -> {
            NamespacedKey key = enchantment.getKey();
            return key.asString().equalsIgnoreCase(normalized) || key.getKey().equalsIgnoreCase(normalized);
        }).findFirst();
    }

    public boolean isCompatible(Enchantment enchantment) {
        return this.compatibleEnchantments.containsKey(enchantment.getKey());
    }

    public EnchantmentRule enchantmentRule(Enchantment enchantment) {
        return this.enchantmentRules.getOrDefault(enchantment.getKey(), new EnchantmentRule(false, Math.max(1, enchantment.getMaxLevel())));
    }

    public boolean isAllowed(Enchantment enchantment) {
        return this.isCompatible(enchantment) && this.enchantmentRule(enchantment).allowed();
    }

    public static String enchantmentPath(NamespacedKey key) {
        return "enchantments." + key.asString();
    }
}
