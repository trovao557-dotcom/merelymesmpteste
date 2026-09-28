/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.format.NamedTextColor
 *  net.kyori.adventure.text.format.TextColor
 *  org.bukkit.enchantments.Enchantment
 *  org.bukkit.enchantments.EnchantmentOffer
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.enchantment.EnchantItemEvent
 *  org.bukkit.event.enchantment.PrepareItemEnchantEvent
 *  org.bukkit.event.inventory.PrepareAnvilEvent
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 */
package dev.nullkeeper.spearconfig.listener;

import dev.nullkeeper.spearconfig.config.EnchantmentRule;
import dev.nullkeeper.spearconfig.config.PluginSettings;
import dev.nullkeeper.spearconfig.model.SpearTier;
import java.util.ArrayList;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class EnchantmentListener
implements Listener {
    private final PluginSettings settings;

    public EnchantmentListener(PluginSettings pluginSettings) {
        this.settings = pluginSettings;
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onPrepareEnchant(PrepareItemEnchantEvent prepareItemEnchantEvent) {
        if (SpearTier.fromItem(prepareItemEnchantEvent.getItem()).isEmpty()) {
            return;
        }
        EnchantmentOffer[] enchantmentOfferArray = prepareItemEnchantEvent.getOffers();
        if (!this.settings.enchantable()) {
            prepareItemEnchantEvent.setCancelled(true);
            EnchantmentListener.clearOffers(enchantmentOfferArray);
            return;
        }
        for (int i = 0; i < enchantmentOfferArray.length; ++i) {
            EnchantmentOffer enchantmentOffer = enchantmentOfferArray[i];
            if (enchantmentOffer == null || !this.settings.isAllowed(enchantmentOffer.getEnchantment())) {
                enchantmentOfferArray[i] = null;
                continue;
            }
            int n = this.settings.enchantmentRule(enchantmentOffer.getEnchantment()).maxLevel();
            enchantmentOffer.setEnchantmentLevel(Math.min(enchantmentOffer.getEnchantmentLevel(), n));
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onEnchant(EnchantItemEvent enchantItemEvent) {
        if (SpearTier.fromItem(enchantItemEvent.getItem()).isEmpty()) {
            return;
        }
        if (!this.settings.enchantable()) {
            enchantItemEvent.setCancelled(true);
            enchantItemEvent.getEnchanter().sendMessage((Component)Component.text((String)"Spear enchanting is disabled.", (TextColor)NamedTextColor.RED));
            return;
        }
        Map map = enchantItemEvent.getEnchantsToAdd();
        for (Enchantment enchantment2 : new ArrayList(map.keySet())) {
            if (!this.settings.isAllowed(enchantment2)) {
                map.remove(enchantment2);
                continue;
            }
            int n = this.settings.enchantmentRule(enchantment2).maxLevel();
            map.computeIfPresent(enchantment2, (enchantment, n2) -> Math.min(n2, n));
        }
        if (map.isEmpty()) {
            enchantItemEvent.setCancelled(true);
            enchantItemEvent.getEnchanter().sendMessage((Component)Component.text((String)"None of that table's enchantments are allowed on spears.", (TextColor)NamedTextColor.RED));
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent prepareAnvilEvent) {
        ItemStack itemStack = prepareAnvilEvent.getResult();
        if (itemStack == null || SpearTier.fromItem(itemStack).isEmpty()) {
            return;
        }
        Map map = itemStack.getEnchantments();
        if (!this.settings.enchantable() && !map.isEmpty()) {
            prepareAnvilEvent.setResult(null);
            return;
        }
        ItemStack itemStack2 = itemStack.clone();
        ItemMeta itemMeta = itemStack2.getItemMeta();
        boolean bl = false;
        for (Map.Entry entry : map.entrySet()) {
            Enchantment enchantment = (Enchantment)entry.getKey();
            if (!this.settings.isAllowed(enchantment)) {
                itemMeta.removeEnchant(enchantment);
                bl = true;
                continue;
            }
            EnchantmentRule enchantmentRule = this.settings.enchantmentRule(enchantment);
            if ((Integer)entry.getValue() <= enchantmentRule.maxLevel()) continue;
            itemMeta.removeEnchant(enchantment);
            itemMeta.addEnchant(enchantment, enchantmentRule.maxLevel(), true);
            bl = true;
        }
        if (bl) {
            itemStack2.setItemMeta(itemMeta);
            prepareAnvilEvent.setResult(itemStack2);
        }
    }

    private static void clearOffers(EnchantmentOffer[] enchantmentOfferArray) {
        for (int i = 0; i < enchantmentOfferArray.length; ++i) {
            enchantmentOfferArray[i] = null;
        }
    }
}

