/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.skstudios.core.config.ConfigFile
 *  net.skstudios.core.gui.MenuConfig
 *  net.skstudios.core.text.Text
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.inventory.ItemFlag
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package net.skstudios.orders;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import net.skstudios.core.config.ConfigFile;
import net.skstudios.core.gui.MenuConfig;
import net.skstudios.core.text.Text;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class MenuFile {
    private final MenuConfig config;
    private final char contentSymbol;
    private final List<Integer> contentSlots;
    private final Decor content;

    public MenuFile(JavaPlugin javaPlugin, String string) {
        YamlConfiguration yamlConfiguration = new ConfigFile((Plugin)javaPlugin, string).load(true).yaml();
        this.config = MenuConfig.load((ConfigurationSection)yamlConfiguration, (String)"menu.layout", (Logger)javaPlugin.getLogger());
        String string2 = yamlConfiguration.getString("menu.content-symbol", "o");
        this.contentSymbol = (char)(string2 == null || string2.isEmpty() ? 111 : (int)string2.charAt(0));
        this.contentSlots = List.copyOf(this.config.layout().slotsOf(this.contentSymbol));
        this.content = new Decor(yamlConfiguration.getString("content.name", ""), List.copyOf(yamlConfiguration.getStringList("content.lore")));
        if (this.contentSymbol == this.config.fillerSymbol()) {
            javaPlugin.getLogger().warning(string + ": menu.content-symbol is the same character as menu.filler-symbol, so the whole background is a content slot. Give them different characters unless that is what you want.");
        }
    }

    public MenuConfig config() {
        return this.config;
    }

    public String title() {
        return this.config.title();
    }

    public int rows() {
        return this.config.rows();
    }

    public char contentSymbol() {
        return this.contentSymbol;
    }

    public List<Integer> contentSlots() {
        return this.contentSlots;
    }

    public int pageSize() {
        return Math.max(1, this.contentSlots.size());
    }

    public ItemStack decorate(ItemStack itemStack, Map<String, String> map) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return itemStack;
        }
        if (!this.content.name().isEmpty()) {
            itemMeta.displayName(Text.item((String)Text.apply((String)this.content.name(), map)));
        }
        if (!this.content.lore().isEmpty()) {
            itemMeta.lore(Text.lore((List)Text.apply(this.content.lore(), map)));
        }
        itemMeta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP});
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    public record Decor(String name, List<String> lore) {
    }
}

