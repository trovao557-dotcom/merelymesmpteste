/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Material
 *  org.bukkit.configuration.ConfigurationSection
 */
package net.skstudios.orders;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

public final class Categories {
    private final Category all;
    private final List<Category> ordered = new ArrayList<Category>();
    private final Map<Material, Category> resolved = new EnumMap<Material, Category>(Material.class);

    public Categories(ConfigurationSection configurationSection, Logger logger) {
        String string = "all";
        this.all = new Category(string, configurationSection.getString("category-all.display", "All"), Categories.material(configurationSection.getString("category-all.icon", "CHEST"), logger, Material.CHEST), Rule.ANY, List.of());
        ConfigurationSection configurationSection2 = configurationSection.getConfigurationSection("categories");
        if (configurationSection2 != null) {
            for (String string2 : configurationSection2.getKeys(false)) {
                Rule rule;
                ConfigurationSection configurationSection3 = configurationSection2.getConfigurationSection(string2);
                if (configurationSection3 == null) continue;
                String string3 = string2.toLowerCase(Locale.ROOT);
                if (string3.equals(string)) {
                    logger.warning("Category 'all' is reserved for the unfiltered view - skipping it.");
                    continue;
                }
                try {
                    rule = Rule.valueOf(configurationSection3.getString("match", "contains").toUpperCase(Locale.ROOT));
                }
                catch (IllegalArgumentException illegalArgumentException) {
                    logger.warning("Unknown match rule at categories." + string2 + " - using 'contains'. Valid: contains, edible, block, any.");
                    rule = Rule.CONTAINS;
                }
                ArrayList<String> arrayList = new ArrayList<String>();
                for (String string4 : configurationSection3.getStringList("contains")) {
                    arrayList.add(string4.toLowerCase(Locale.ROOT).replace(' ', '_'));
                }
                this.ordered.add(new Category(string3, configurationSection3.getString("display", string3), Categories.material(configurationSection3.getString("icon", "PAPER"), logger, Material.PAPER), rule, List.copyOf(arrayList)));
            }
        }
        if (this.ordered.isEmpty()) {
            logger.warning("No categories configured - the filter button will only offer 'All'.");
        }
    }

    private static Material material(String string, Logger logger, Material material) {
        Material material2 = Material.matchMaterial((String)(string == null ? "" : string.toUpperCase(Locale.ROOT)));
        if (material2 == null) {
            logger.warning("Unknown category icon '" + string + "' - using " + String.valueOf(material) + ".");
            return material;
        }
        return material2;
    }

    public Category all() {
        return this.all;
    }

    public List<Category> cycle() {
        ArrayList<Category> arrayList = new ArrayList<Category>(this.ordered.size() + 1);
        arrayList.add(this.all);
        arrayList.addAll(this.ordered);
        return arrayList;
    }

    public List<Category> real() {
        return List.copyOf(this.ordered);
    }

    public Category next(Category category) {
        List<Category> list = this.cycle();
        int n = list.indexOf(category);
        return list.get((n + 1) % list.size());
    }

    public Category byId(String string) {
        if (string == null || string.isEmpty() || string.equalsIgnoreCase(this.all.id())) {
            return this.all;
        }
        for (Category category : this.ordered) {
            if (!category.id().equalsIgnoreCase(string)) continue;
            return category;
        }
        return this.all;
    }

    public Category classify(Material material) {
        Category category = this.resolved.get(material);
        if (category != null) {
            return category;
        }
        Category category2 = this.compute(material);
        this.resolved.put(material, category2);
        return category2;
    }

    private Category compute(Material material) {
        String string = material.name().toLowerCase(Locale.ROOT);
        for (Category category : this.ordered) {
            boolean bl;
            if (!(bl = (switch (category.rule().ordinal()) {
                default -> throw new MatchException(null, null);
                case 0 -> Categories.containsAny(string, category.contains());
                case 1 -> material.isEdible();
                case 2 -> material.isBlock();
                case 3 -> true;
            }))) continue;
            return category;
        }
        return this.ordered.isEmpty() ? this.all : this.ordered.get(this.ordered.size() - 1);
    }

    private static boolean containsAny(String string, List<String> list) {
        for (String string2 : list) {
            if (string2.isEmpty() || !string.contains(string2)) continue;
            return true;
        }
        return false;
    }

    public record Category(String id, String display, Material icon, Rule rule, List<String> contains) {
    }

    public static enum Rule {
        CONTAINS,
        EDIBLE,
        BLOCK,
        ANY;

    }
}

