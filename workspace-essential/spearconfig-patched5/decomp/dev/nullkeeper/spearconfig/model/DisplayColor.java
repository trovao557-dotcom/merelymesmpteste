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

    private DisplayColor(NamedTextColor namedTextColor, Material material) {
        this.textColor = namedTextColor;
        this.icon = material;
    }

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        String[] stringArray = this.key().split("_");
        StringBuilder stringBuilder = new StringBuilder();
        for (String string : stringArray) {
            if (!stringBuilder.isEmpty()) {
                stringBuilder.append(' ');
            }
            stringBuilder.append(Character.toUpperCase(string.charAt(0))).append(string.substring(1));
        }
        return stringBuilder.toString();
    }

    public NamedTextColor textColor() {
        return this.textColor;
    }

    public Material icon() {
        return this.icon;
    }

    public static Optional<DisplayColor> fromKey(String string) {
        String string2 = string.toUpperCase(Locale.ROOT);
        return Arrays.stream(DisplayColor.values()).filter(displayColor -> displayColor.name().equals(string2)).findFirst();
    }
}

