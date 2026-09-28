/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.entity.EntityType
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.entity.EntityDeathEvent
 */
package dev.nullkeeper.spearconfig.listener;

import dev.nullkeeper.spearconfig.config.PluginSettings;
import dev.nullkeeper.spearconfig.model.SpearTier;
import java.util.EnumSet;
import java.util.Set;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public final class MobDropListener
implements Listener {
    private static final Set<EntityType> NATURAL_SPEAR_MOBS = EnumSet.of(EntityType.ZOMBIE, new EntityType[]{EntityType.HUSK, EntityType.ZOMBIE_VILLAGER, EntityType.ZOMBIE_HORSE, EntityType.CAMEL_HUSK, EntityType.PIGLIN, EntityType.ZOMBIFIED_PIGLIN});
    private final PluginSettings settings;

    public MobDropListener(PluginSettings pluginSettings) {
        this.settings = pluginSettings;
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent entityDeathEvent) {
        if (!this.settings.blockMobDrop() || !NATURAL_SPEAR_MOBS.contains(entityDeathEvent.getEntityType())) {
            return;
        }
        entityDeathEvent.getDrops().removeIf(itemStack -> SpearTier.fromItem(itemStack).isPresent());
    }
}

