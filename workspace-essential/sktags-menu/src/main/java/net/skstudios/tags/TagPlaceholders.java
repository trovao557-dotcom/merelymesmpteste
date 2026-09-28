package net.skstudios.tags;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public final class TagPlaceholders extends PlaceholderExpansion {
    private final TagsPlugin plugin;
    TagPlaceholders(TagsPlugin plugin){this.plugin=plugin;}
    @Override public @NotNull String getIdentifier(){return plugin.placeholderPrefix();}
    @Override public @NotNull String getAuthor(){return "MerelyMeSMP";}
    @Override public @NotNull String getVersion(){return plugin.getPluginMeta().getVersion();}
    @Override public boolean persist(){return true;}
    @Override public String onRequest(OfflinePlayer player,@NotNull String params){if(player==null||!params.equalsIgnoreCase(plugin.placeholderIdentifier()))return null;return plugin.renderTag(player.getUniqueId());}
}
