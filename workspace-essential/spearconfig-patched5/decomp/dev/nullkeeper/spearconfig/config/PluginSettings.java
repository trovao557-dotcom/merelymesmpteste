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

    public PluginSettings(SpearConfigPlugin spearConfigPlugin) {
        this.plugin = spearConfigPlugin;
    }

    public void reload() {
        int n;
        boolean bl;
        String string;
        boolean bl2;
        FileConfiguration fileConfiguration = this.plugin.getConfig();
        boolean bl3 = bl2 = PluginSettings.migrateLegacyConfig(fileConfiguration);
        if (bl2) {
            this.plugin.getLogger().info("Migrated legacy v0.1 configuration to the v1.0 schema.");
        }
        for (SpearTier enum_ : SpearTier.values()) {
            string = "limits." + enum_.key();
            bl = fileConfiguration.getBoolean(string + ".enabled", false);
            n = Math.max(0, fileConfiguration.getInt(string + ".amount", 0));
            this.limitEnabled.put(enum_, bl);
            this.limits.put(enum_, n);
            bl3 |= PluginSettings.normalize(fileConfiguration, string + ".amount", n);
        }
        this.blockMobDrop = fileConfiguration.getBoolean("blockMobDrop", true);
        this.enchantable = fileConfiguration.getBoolean("enchantable", true);
        this.glow = fileConfiguration.getBoolean("glow", false);
        this.tab = fileConfiguration.getBoolean("tab", false);
        this.revealEnabled = fileConfiguration.getBoolean("reveal.enabled", false);
        this.chatAnnouncements = fileConfiguration.getBoolean("announcements.chat", true);
        this.titleAnnouncements = fileConfiguration.getBoolean("announcements.title", true);
        for (Enum enum_ : AttackType.values()) {
            string = "cooldown." + ((AttackType)enum_).key();
            bl = fileConfiguration.getBoolean(string + ".enabled", false);
            n = Math.max(0, fileConfiguration.getInt(string + ".seconds", 0));
            this.cooldownEnabled.put((AttackType)enum_, bl);
            this.cooldowns.put((AttackType)enum_, n);
            bl3 |= PluginSettings.normalize(fileConfiguration, string + ".seconds", n);
        }
        this.revealInterval = Math.max(5, fileConfiguration.getInt("reveal.interval", 300));
        bl3 |= PluginSettings.normalize(fileConfiguration, "reveal.interval", this.revealInterval);
        String string2 = fileConfiguration.getString("color", DisplayColor.GOLD.name());
        this.color = DisplayColor.fromKey(string2 == null ? "" : string2).orElse(DisplayColor.GOLD);
        bl3 |= PluginSettings.normalize(fileConfiguration, "color", this.color.name());
        if (bl3 |= this.discoverCompatibleEnchantments(fileConfiguration)) {
            this.plugin.saveConfig();
        }
    }

    static boolean migrateLegacyConfig(FileConfiguration fileConfiguration) {
        int n;
        Number number;
        Object object;
        String string;
        boolean bl = false;
        for (SpearTier spearTier : SpearTier.values()) {
            string = "limits." + spearTier.key();
            object = fileConfiguration.get(string);
            if (!(object instanceof Number)) continue;
            number = (Number)object;
            n = Math.max(0, number.intValue());
            fileConfiguration.set(string, null);
            fileConfiguration.set(string + ".enabled", (Object)(n > 0 ? 1 : 0));
            fileConfiguration.set(string + ".amount", (Object)n);
            bl = true;
        }
        for (AttackType attackType : AttackType.values()) {
            string = "cooldown." + attackType.key();
            object = fileConfiguration.get(string);
            if (!(object instanceof Number)) continue;
            number = (Number)object;
            n = Math.max(0, number.intValue());
            fileConfiguration.set(string, null);
            fileConfiguration.set(string + ".enabled", (Object)(n > 0 ? 1 : 0));
            fileConfiguration.set(string + ".seconds", (Object)n);
            bl = true;
        }
        Object object2 = fileConfiguration.get("announcements");
        if (object2 instanceof Boolean) {
            Boolean bl2 = (Boolean)object2;
            fileConfiguration.set("announcements", null);
            fileConfiguration.set("announcements.chat", (Object)bl2);
            fileConfiguration.set("announcements.title", (Object)bl2);
            bl = true;
        }
        return bl;
    }

    private boolean discoverCompatibleEnchantments(FileConfiguration fileConfiguration) {
        Object object22;
        this.compatibleEnchantments.clear();
        this.enchantmentRules.clear();
        ItemStack itemStack = new ItemStack(SpearTier.WOOD.material());
        ArrayList<Object> arrayList = new ArrayList<Object>();
        for (Object object22 : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)) {
            if (!object22.canEnchantItem(itemStack)) continue;
            arrayList.add(object22);
        }
        arrayList.sort(Comparator.comparing(enchantment -> enchantment.getKey().asString()));
        boolean bl = false;
        for (Enchantment enchantment2 : arrayList) {
            NamespacedKey namespacedKey = enchantment2.getKey();
            this.compatibleEnchantments.put(namespacedKey, enchantment2);
            String string = PluginSettings.enchantmentPath(namespacedKey);
            if (!fileConfiguration.contains(string + ".allowed")) {
                fileConfiguration.set(string + ".allowed", (Object)true);
                bl = true;
            }
            if (!fileConfiguration.contains(string + ".maxLevel")) {
                fileConfiguration.set(string + ".maxLevel", (Object)enchantment2.getMaxLevel());
                bl = true;
            }
            boolean bl2 = fileConfiguration.getBoolean(string + ".allowed", true);
            int n = Math.max(1, Math.min(enchantment2.getMaxLevel(), fileConfiguration.getInt(string + ".maxLevel", enchantment2.getMaxLevel())));
            bl |= PluginSettings.normalize(fileConfiguration, string + ".maxLevel", n);
            this.enchantmentRules.put(namespacedKey, new EnchantmentRule(bl2, n));
        }
        object22 = this.compatibleEnchantments.keySet().stream().map(NamespacedKey::asString).collect(Collectors.joining(", "));
        this.plugin.getLogger().info("Detected " + this.compatibleEnchantments.size() + " spear-compatible enchantments from Paper: " + (String)object22);
        return bl;
    }

    private static boolean normalize(FileConfiguration fileConfiguration, String string, Object object) {
        Object object2 = fileConfiguration.get(string);
        if (object.equals(object2)) {
            return false;
        }
        fileConfiguration.set(string, object);
        return true;
    }

    public int limit(SpearTier spearTier) {
        return this.limits.getOrDefault((Object)spearTier, 0);
    }

    public boolean limitEnabled(SpearTier spearTier) {
        return this.limitEnabled.getOrDefault((Object)spearTier, false);
    }

    public int cooldown(AttackType attackType) {
        return this.cooldowns.getOrDefault((Object)attackType, 0);
    }

    public boolean cooldownEnabled(AttackType attackType) {
        return this.cooldownEnabled.getOrDefault((Object)attackType, false);
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

    public Optional<Enchantment> findCompatibleEnchantment(String string) {
        String string2 = string.toLowerCase(Locale.ROOT);
        return this.compatibleEnchantments.values().stream().filter(enchantment -> {
            NamespacedKey namespacedKey = enchantment.getKey();
            return namespacedKey.asString().equalsIgnoreCase(string2) || namespacedKey.getKey().equalsIgnoreCase(string2);
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

    public static String enchantmentPath(NamespacedKey namespacedKey) {
        return "enchantments." + namespacedKey.asString();
    }
}

