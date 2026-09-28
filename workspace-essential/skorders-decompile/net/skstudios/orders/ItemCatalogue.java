/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Material
 */
package net.skstudios.orders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.skstudios.orders.Categories;
import org.bukkit.Material;

public final class ItemCatalogue {
    private final NameStyle style;
    private final Map<Material, String> names = new EnumMap<Material, String>(Material.class);
    private final Map<Material, String> searchable = new EnumMap<Material, String>(Material.class);
    private final List<Material> ascending;
    private final List<Material> descending;
    private final Map<String, List<Material>> ascendingByCategory = new HashMap<String, List<Material>>();
    private final Map<String, List<Material>> descendingByCategory = new HashMap<String, List<Material>>();

    public ItemCatalogue(Categories categories, Set<Material> set, NameStyle nameStyle) {
        ArrayList arrayList;
        this.style = nameStyle;
        ArrayList<Material> arrayList2 = new ArrayList<Material>(1500);
        for (Material material3 : Material.values()) {
            if (material3.isLegacy() || material3.isAir() || !material3.isItem() || set.contains(material3)) continue;
            arrayList = this.prettify(material3);
            this.names.put(material3, (String)((Object)arrayList));
            this.searchable.put(material3, ((String)((Object)arrayList)).toLowerCase(Locale.ROOT));
            arrayList2.add(material3);
        }
        arrayList2.sort((material, material2) -> this.searchable.get(material).compareTo(this.searchable.get(material2)));
        this.ascending = List.copyOf(arrayList2);
        ArrayList arrayList3 = new ArrayList(arrayList2);
        Collections.reverse(arrayList3);
        this.descending = List.copyOf(arrayList3);
        for (Categories.Category category : categories.real()) {
            ArrayList<Material> arrayList4 = new ArrayList<Material>();
            for (Material material4 : this.ascending) {
                if (categories.classify(material4) != category) continue;
                arrayList4.add(material4);
            }
            this.ascendingByCategory.put(category.id(), List.copyOf(arrayList4));
            arrayList = new ArrayList(arrayList4);
            Collections.reverse(arrayList);
            this.descendingByCategory.put(category.id(), List.copyOf(arrayList));
        }
    }

    private String prettify(Material material) {
        String string = material.name();
        return switch (this.style.ordinal()) {
            default -> throw new MatchException(null, null);
            case 3 -> string;
            case 2 -> string.replace('_', ' ');
            case 1 -> string.toLowerCase(Locale.ROOT).replace('_', ' ');
            case 0 -> {
                StringBuilder var3_3 = new StringBuilder(string.length());
                for (String var7_7 : string.toLowerCase(Locale.ROOT).split("_")) {
                    if (var7_7.isEmpty()) continue;
                    if (!var3_3.isEmpty()) {
                        var3_3.append(' ');
                    }
                    var3_3.append(Character.toUpperCase(var7_7.charAt(0))).append(var7_7, 1, var7_7.length());
                }
                yield var3_3.toString();
            }
        };
    }

    public int size() {
        return this.ascending.size();
    }

    public String displayName(Material material) {
        String string = this.names.get(material);
        return string != null ? string : this.prettify(material);
    }

    public List<Material> view(Categories categories, Categories.Category category, boolean bl) {
        if (category == null || category == categories.all()) {
            return bl ? this.ascending : this.descending;
        }
        Map<String, List<Material>> map = bl ? this.ascendingByCategory : this.descendingByCategory;
        List<Material> list = map.get(category.id());
        return list != null ? list : List.of();
    }

    public List<Material> search(List<Material> list, String string) {
        if (string == null || string.isEmpty()) {
            return list;
        }
        String string2 = string.toLowerCase(Locale.ROOT);
        ArrayList<Material> arrayList = new ArrayList<Material>();
        for (Material material : list) {
            String string3 = this.searchable.get(material);
            if (string3 == null || !string3.contains(string2)) continue;
            arrayList.add(material);
        }
        return arrayList;
    }

    public static enum NameStyle {
        TITLE,
        LOWER,
        UPPER,
        RAW;

    }
}

