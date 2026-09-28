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

    public EnchantmentListener(PluginSettings settings) {
        this.settings = settings;
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        if (SpearTier.fromItem(event.getItem()).isEmpty()) {
            return;
        }
        EnchantmentOffer[] offers = event.getOffers();
        if (!this.settings.enchantable()) {
            event.setCancelled(true);
            EnchantmentListener.clearOffers(offers);
            return;
        }
        for (int index = 0; index < offers.length; ++index) {
            EnchantmentOffer offer = offers[index];
            if (offer == null || !this.settings.isAllowed(offer.getEnchantment())) {
                offers[index] = null;
                continue;
            }
            int maximum = this.settings.enchantmentRule(offer.getEnchantment()).maxLevel();
            offer.setEnchantmentLevel(Math.min(offer.getEnchantmentLevel(), maximum));
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onEnchant(EnchantItemEvent event) {
        if (SpearTier.fromItem(event.getItem()).isEmpty()) {
            return;
        }
        if (!this.settings.enchantable()) {
            event.setCancelled(true);
            event.getEnchanter().sendMessage((Component)Component.text((String)"Spear enchanting is disabled.", (TextColor)NamedTextColor.RED));
            return;
        }
        Map<Enchantment, Integer> additions = event.getEnchantsToAdd();
        for (Enchantment enchantment : new ArrayList<Enchantment>(additions.keySet())) {
            if (!this.settings.isAllowed(enchantment)) {
                additions.remove(enchantment);
                continue;
            }
            int maximum = this.settings.enchantmentRule(enchantment).maxLevel();
            additions.computeIfPresent(enchantment, (ignored, level) -> Math.min(level, maximum));
        }
        if (additions.isEmpty()) {
            event.setCancelled(true);
            event.getEnchanter().sendMessage((Component)Component.text((String)"None of that table's enchantments are allowed on spears.", (TextColor)NamedTextColor.RED));
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (result == null || SpearTier.fromItem(result).isEmpty()) {
            return;
        }
        Map<Enchantment, Integer> resultEnchantments = result.getEnchantments();
        if (!this.settings.enchantable() && !resultEnchantments.isEmpty()) {
            event.setResult(null);
            return;
        }
        ItemStack adjusted = result.clone();
        ItemMeta meta = adjusted.getItemMeta();
        boolean changed = false;
        for (Map.Entry<Enchantment, Integer> entry : resultEnchantments.entrySet()) {
            Enchantment enchantment = entry.getKey();
            if (!this.settings.isAllowed(enchantment)) {
                meta.removeEnchant(enchantment);
                changed = true;
                continue;
            }
            EnchantmentRule rule = this.settings.enchantmentRule(enchantment);
            if (entry.getValue() <= rule.maxLevel()) continue;
            meta.removeEnchant(enchantment);
            meta.addEnchant(enchantment, rule.maxLevel(), true);
            changed = true;
        }
        if (changed) {
            adjusted.setItemMeta(meta);
            event.setResult(adjusted);
        }
    }

    private static void clearOffers(EnchantmentOffer[] offers) {
        for (int index = 0; index < offers.length; ++index) {
            offers[index] = null;
        }
    }
}
