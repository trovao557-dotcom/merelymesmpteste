/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.NamespacedKey
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.persistence.PersistentDataContainer
 */
package net.skstudios.orders;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

public final class Decoration {
    private static volatile Set<String> namespaces = Set.of("sksell");

    private Decoration() {
    }

    public static void configure(List<String> list) {
        LinkedHashSet<String> linkedHashSet = new LinkedHashSet<String>();
        for (String string : list == null ? List.of() : list) {
            if (string == null || string.isBlank()) continue;
            linkedHashSet.add(string.trim().toLowerCase(Locale.ROOT));
        }
        namespaces = Set.copyOf(linkedHashSet);
    }

    public static Set<String> namespaces() {
        return namespaces;
    }

    public static ItemStack plain(ItemStack itemStack) {
        if (itemStack == null || namespaces.isEmpty()) {
            return itemStack;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null || !itemMeta.hasLore()) {
            return itemStack;
        }
        if (!Decoration.isMarked(itemMeta.getPersistentDataContainer())) {
            return itemStack;
        }
        ItemStack itemStack2 = itemStack.clone();
        ItemMeta itemMeta2 = itemStack2.getItemMeta();
        itemMeta2.lore(null);
        PersistentDataContainer persistentDataContainer = itemMeta2.getPersistentDataContainer();
        for (NamespacedKey namespacedKey : new ArrayList(persistentDataContainer.getKeys())) {
            if (!namespaces.contains(namespacedKey.getNamespace().toLowerCase(Locale.ROOT))) continue;
            persistentDataContainer.remove(namespacedKey);
        }
        itemStack2.setItemMeta(itemMeta2);
        return itemStack2;
    }

    private static boolean isMarked(PersistentDataContainer persistentDataContainer) {
        for (NamespacedKey namespacedKey : persistentDataContainer.getKeys()) {
            if (!namespaces.contains(namespacedKey.getNamespace().toLowerCase(Locale.ROOT))) continue;
            return true;
        }
        return false;
    }
}

