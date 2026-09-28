/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.entity.Player
 */
package net.skstudios.orders;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class Sounds {
    private final Map<String, Cue> cues = new HashMap<String, Cue>();

    public Sounds(ConfigurationSection configurationSection) {
        if (configurationSection == null) {
            return;
        }
        for (String string : configurationSection.getKeys(false)) {
            String string2;
            ConfigurationSection configurationSection2 = configurationSection.getConfigurationSection(string);
            if (configurationSection2 == null || (string2 = configurationSection2.getString("sound", "")) == null || string2.isEmpty()) continue;
            this.cues.put(string, new Cue(string2, (float)configurationSection2.getDouble("volume", 1.0), (float)configurationSection2.getDouble("pitch", 1.0)));
        }
    }

    public void play(Player player, String string) {
        Cue cue = this.cues.get(string);
        if (cue == null || player == null) {
            return;
        }
        player.playSound(player.getLocation(), cue.key(), cue.volume(), cue.pitch());
    }

    private record Cue(String key, float volume, float pitch) {
    }
}

