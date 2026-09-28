/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.format.NamedTextColor
 *  org.bukkit.Material
 */
package dev.nullkeeper.spearconfig.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

public enum DisplayColor {
    BLACK(NamedTextColor.BLACK, Material.BLACK_DYE),
    DARK_BLUE(NamedTextColor.DARK_BLUE, Material.BLUE_DYE),
    DARK_GREEN(NamedTextColor.DARK_GREEN, Material.GREEN_DYE),
    DARK_AQUA(NamedTextColor.DARK_AQUA, Material.CYAN_DYE),
    DARK_RED(NamedTextColor.DARK_RED, Material.RED_DYE),
    DARK_PURPLE(NamedTextColor.DARK_PURPLE, Material.PURPLE_DYE),
    GOLD(NamedTextColor.GOLD, Material.ORANGE_DYE),
    GRAY(NamedTextColor.GRAY, Material.LIGHT_GRAY_DYE),
    DARK_GRAY(NamedTextColor.DARK_GRAY, Material.GRAY_DYE),
    BLUE(NamedTextColor.BLUE, Material.BLUE_DYE),
    GREEN(NamedTextColor.GREEN, Material.LIME_DYE),
    AQUA(NamedTextColor.AQUA, Material.LIGHT_BLUE_DYE),
    RED(NamedTextColor.RED, Material.RED_DYE),
    LIGHT_PURPLE(NamedTextColor.LIGHT_PURPLE, Material.MAGENTA_DYE),
    YELLOW(NamedTextColor.YELLOW, Material.YELLOW_DYE),
    WHITE(NamedTextColor.WHITE, Material.WHITE_DYE);

    private final NamedTextColor textColor;
    private final Material icon;

    private DisplayColor(NamedTextColor textColor, Material icon) {
        this.textColor = textColor;
        this.icon = icon;
    }

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        String[] words = this.key().split("_");
        StringBuilder display = new StringBuilder();
        for (String word : words) {
            if (!display.isEmpty()) {
                display.append(' ');
            }
            display.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return display.toString();
    }

    public NamedTextColor textColor() {
        return this.textColor;
    }

    public Material icon() {
        return this.icon;
    }

    public static Optional<DisplayColor> fromKey(String value) {
        String normalized = value.toUpperCase(Locale.ROOT);
        return Arrays.stream(DisplayColor.values()).filter(color -> color.name().equals(normalized)).findFirst();
    }
}

