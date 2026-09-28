/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.format.NamedTextColor
 *  net.kyori.adventure.text.format.TextColor
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.enchantments.Enchantment
 *  org.bukkit.entity.Player
 */
package dev.nullkeeper.spearconfig.command;

import dev.nullkeeper.spearconfig.SpearConfigPlugin;
import dev.nullkeeper.spearconfig.config.EnchantmentRule;
import dev.nullkeeper.spearconfig.config.PluginSettings;
import dev.nullkeeper.spearconfig.gui.ConfigGui;
import dev.nullkeeper.spearconfig.model.AttackType;
import dev.nullkeeper.spearconfig.model.DisplayColor;
import dev.nullkeeper.spearconfig.model.SpearTier;
import dev.nullkeeper.spearconfig.service.SpearTracker;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;

public final class SpearConfigCommand
implements CommandExecutor,
TabCompleter {
    private static final List<String> ROOTS = List.of("gui", "reload", "refresh", "set", "toggle", "enchantlevel", "whitelist");
    private static final List<String> SET_OPTIONS = List.of("limit", "cooldown", "revealinterval", "color");
    private static final List<String> TOGGLE_OPTIONS = List.of("limit", "cooldown", "blockmobdrop", "enchantable", "glow", "tab", "reveal", "announcements");
    private final SpearConfigPlugin plugin;
    private final PluginSettings settings;
    private final SpearTracker tracker;
    private final ConfigGui gui;

    public SpearConfigCommand(SpearConfigPlugin spearConfigPlugin, PluginSettings pluginSettings, SpearTracker spearTracker, ConfigGui configGui) {
        this.plugin = spearConfigPlugin;
        this.settings = pluginSettings;
        this.tracker = spearTracker;
        this.gui = configGui;
    }

    public boolean onCommand(CommandSender commandSender, Command command, String string, String[] stringArray) {
        if (!commandSender.hasPermission("spearconfig.admin")) {
            SpearConfigCommand.error(commandSender, "You do not have permission to use Spear Config.");
            return true;
        }
        if (stringArray.length == 0) {
            this.sendHelp(commandSender);
            return true;
        }
        return switch (stringArray[0].toLowerCase(Locale.ROOT)) {
            case "gui" -> this.openGui(commandSender);
            case "reload" -> this.reload(commandSender);
            case "refresh" -> this.refresh(commandSender);
            case "set" -> this.set(commandSender, stringArray);
            case "toggle" -> this.toggle(commandSender, stringArray);
            case "enchantlevel" -> this.enchantLevel(commandSender, stringArray);
            case "whitelist" -> this.whitelist(commandSender, stringArray);
            default -> {
                SpearConfigCommand.error(commandSender, "Unknown subcommand. Use /spearconfig for help.");
                yield true;
            }
        };
    }

    private boolean openGui(CommandSender commandSender) {
        if (!(commandSender instanceof Player)) {
            SpearConfigCommand.error(commandSender, "Only a player can open the settings GUI.");
            return true;
        }
        Player player = (Player)commandSender;
        this.gui.openMain(player);
        return true;
    }

    private boolean reload(CommandSender commandSender) {
        this.plugin.reloadPluginConfiguration();
        SpearConfigCommand.success(commandSender, "Reloaded config.yml and restarted configuration-dependent tasks.");
        return true;
    }

    private boolean refresh(CommandSender commandSender) {
        Map<SpearTier, Integer> map = this.plugin.refreshTrackedSpears();
        String string = Arrays.stream(SpearTier.values()).map(spearTier -> spearTier.key() + "=" + String.valueOf(map.getOrDefault(spearTier, 0))).collect(Collectors.joining(", "));
        SpearConfigCommand.success(commandSender, "Refreshed online inventories, ender chests, and nested shulkers: " + string);
        return true;
    }

    private boolean set(CommandSender commandSender, String[] stringArray) {
        if (stringArray.length < 2) {
            SpearConfigCommand.error(commandSender, "Usage: /spearconfig set <limit|cooldown|revealinterval|color> ...");
            return true;
        }
        return switch (stringArray[1].toLowerCase(Locale.ROOT)) {
            case "limit" -> this.setLimit(commandSender, stringArray);
            case "cooldown" -> this.setCooldown(commandSender, stringArray);
            case "revealinterval" -> this.setRevealInterval(commandSender, stringArray);
            case "color" -> this.setColor(commandSender, stringArray);
            default -> {
                SpearConfigCommand.error(commandSender, "Unknown setting. Choose limit, cooldown, revealinterval, or color.");
                yield true;
            }
        };
    }

    private boolean setLimit(CommandSender commandSender, String[] stringArray) {
        if (stringArray.length != 4) {
            SpearConfigCommand.error(commandSender, "Usage: /spearconfig set limit <tier> <amount>");
            return true;
        }
        Optional<SpearTier> optional = SpearTier.fromKey(stringArray[2]);
        Integer n = SpearConfigCommand.nonNegativeInteger(stringArray[3]);
        if (optional.isEmpty() || n == null) {
            SpearConfigCommand.error(commandSender, "Use a valid tier and a whole amount of 0 or more.");
            return true;
        }
        this.plugin.getConfig().set("limits." + optional.get().key() + ".amount", (Object)n);
        this.plugin.applyConfigurationChanges();
        SpearConfigCommand.success(commandSender, "Set the " + optional.get().key() + " spear limit to " + n + "; its master switch is " + SpearConfigCommand.enabledText(this.settings.limitEnabled(optional.get())) + ".");
        return true;
    }

    private boolean setCooldown(CommandSender commandSender, String[] stringArray) {
        if (stringArray.length != 4) {
            SpearConfigCommand.error(commandSender, "Usage: /spearconfig set cooldown <jab|lunge> <seconds>");
            return true;
        }
        Optional<AttackType> optional = AttackType.fromKey(stringArray[2]);
        Integer n = SpearConfigCommand.nonNegativeInteger(stringArray[3]);
        if (optional.isEmpty() || n == null) {
            SpearConfigCommand.error(commandSender, "Use jab or lunge and a whole number of seconds 0 or greater.");
            return true;
        }
        this.plugin.getConfig().set("cooldown." + optional.get().key() + ".seconds", (Object)n);
        this.plugin.applyConfigurationChanges();
        SpearConfigCommand.success(commandSender, "Set the " + optional.get().key() + " cooldown to " + n + " seconds; its master switch is " + SpearConfigCommand.enabledText(this.settings.cooldownEnabled(optional.get())) + ".");
        return true;
    }

    private boolean setRevealInterval(CommandSender commandSender, String[] stringArray) {
        if (stringArray.length != 3) {
            SpearConfigCommand.error(commandSender, "Usage: /spearconfig set revealinterval <seconds>");
            return true;
        }
        Integer n = SpearConfigCommand.nonNegativeInteger(stringArray[2]);
        if (n == null || n < 5) {
            SpearConfigCommand.error(commandSender, "The reveal interval must be at least 5 seconds.");
            return true;
        }
        this.plugin.getConfig().set("reveal.interval", (Object)n);
        this.plugin.applyConfigurationChanges();
        SpearConfigCommand.success(commandSender, "Set the reveal interval to " + n + " seconds.");
        return true;
    }

    private boolean setColor(CommandSender commandSender, String[] stringArray) {
        if (stringArray.length != 3) {
            SpearConfigCommand.error(commandSender, "Usage: /spearconfig set color <color>");
            return true;
        }
        Optional<DisplayColor> optional = DisplayColor.fromKey(stringArray[2]);
        if (optional.isEmpty()) {
            SpearConfigCommand.error(commandSender, "Unknown color. Use tab completion to see supported colors.");
            return true;
        }
        this.plugin.getConfig().set("color", (Object)optional.get().name());
        this.plugin.applyConfigurationChanges();
        SpearConfigCommand.success(commandSender, "Set the shared spear color to " + optional.get().displayName() + ".");
        return true;
    }

    private boolean toggle(CommandSender commandSender, String[] stringArray) {
        if (stringArray.length < 2 || !TOGGLE_OPTIONS.contains(stringArray[1].toLowerCase(Locale.ROOT))) {
            SpearConfigCommand.error(commandSender, "Usage: /spearconfig toggle <setting> [tier|attack|announcement-mode]");
            return true;
        }
        String string = stringArray[1].toLowerCase(Locale.ROOT);
        if ("limit".equals(string)) {
            return this.toggleLimit(commandSender, stringArray);
        }
        if ("cooldown".equals(string)) {
            return this.toggleCooldown(commandSender, stringArray);
        }
        if ("announcements".equals(string) && stringArray.length == 3) {
            return this.toggleAnnouncementMode(commandSender, stringArray);
        }
        if (stringArray.length != 2) {
            SpearConfigCommand.error(commandSender, "That toggle does not accept another argument.");
            return true;
        }
        if ("announcements".equals(string)) {
            boolean bl = !this.settings.chatAnnouncements() || !this.settings.titleAnnouncements();
            this.plugin.getConfig().set("announcements.chat", (Object)bl);
            this.plugin.getConfig().set("announcements.title", (Object)bl);
            this.plugin.applyConfigurationChanges();
            SpearConfigCommand.success(commandSender, "All announcement modes are now " + SpearConfigCommand.enabledText(bl) + ".");
            return true;
        }
        String string2 = switch (string) {
            case "blockmobdrop" -> "blockMobDrop";
            case "reveal" -> "reveal.enabled";
            default -> string;
        };
        boolean bl = !this.plugin.getConfig().getBoolean(string2);
        this.plugin.getConfig().set(string2, (Object)bl);
        this.plugin.applyConfigurationChanges();
        SpearConfigCommand.success(commandSender, string + " is now " + SpearConfigCommand.enabledText(bl) + ".");
        return true;
    }

    private boolean toggleLimit(CommandSender commandSender, String[] stringArray) {
        if (stringArray.length != 3) {
            SpearConfigCommand.error(commandSender, "Usage: /spearconfig toggle limit <tier>");
            return true;
        }
        Optional<SpearTier> optional = SpearTier.fromKey(stringArray[2]);
        if (optional.isEmpty()) {
            SpearConfigCommand.error(commandSender, "Choose wood, stone, copper, iron, gold, diamond, or netherite.");
            return true;
        }
        boolean bl = !this.settings.limitEnabled(optional.get());
        this.plugin.getConfig().set("limits." + optional.get().key() + ".enabled", (Object)bl);
        this.plugin.applyConfigurationChanges();
        SpearConfigCommand.success(commandSender, optional.get().displayName() + " spear limits are now " + SpearConfigCommand.enabledText(bl) + ".");
        return true;
    }

    private boolean toggleCooldown(CommandSender commandSender, String[] stringArray) {
        if (stringArray.length != 3) {
            SpearConfigCommand.error(commandSender, "Usage: /spearconfig toggle cooldown <jab|lunge>");
            return true;
        }
        Optional<AttackType> optional = AttackType.fromKey(stringArray[2]);
        if (optional.isEmpty()) {
            SpearConfigCommand.error(commandSender, "Choose jab or lunge.");
            return true;
        }
        boolean bl = !this.settings.cooldownEnabled(optional.get());
        this.plugin.getConfig().set("cooldown." + optional.get().key() + ".enabled", (Object)bl);
        this.plugin.applyConfigurationChanges();
        SpearConfigCommand.success(commandSender, optional.get().displayName() + " cooldowns are now " + SpearConfigCommand.enabledText(bl) + ".");
        return true;
    }

    private boolean toggleAnnouncementMode(CommandSender commandSender, String[] stringArray) {
        boolean bl;
        String string;
        String string2 = stringArray[2].toLowerCase(Locale.ROOT);
        if ("chat".equals(string2)) {
            string = "announcements.chat";
            bl = this.settings.chatAnnouncements();
        } else if ("title".equals(string2)) {
            string = "announcements.title";
            bl = this.settings.titleAnnouncements();
        } else {
            SpearConfigCommand.error(commandSender, "Usage: /spearconfig toggle announcements <chat|title>");
            return true;
        }
        boolean bl2 = !bl;
        this.plugin.getConfig().set(string, (Object)bl2);
        this.plugin.applyConfigurationChanges();
        SpearConfigCommand.success(commandSender, string2 + " announcements are now " + SpearConfigCommand.enabledText(bl2) + ".");
        return true;
    }

    private boolean enchantLevel(CommandSender commandSender, String[] stringArray) {
        if (stringArray.length != 3) {
            SpearConfigCommand.error(commandSender, "Usage: /spearconfig enchantlevel <enchantment> <max-level>");
            return true;
        }
        Optional<Enchantment> optional = this.settings.findCompatibleEnchantment(stringArray[1]);
        Integer n = SpearConfigCommand.positiveInteger(stringArray[2]);
        if (optional.isEmpty()) {
            SpearConfigCommand.error(commandSender, "That enchantment is not reported as spear-compatible by this server.");
            return true;
        }
        if (!this.settings.isAllowed(optional.get())) {
            SpearConfigCommand.error(commandSender, "Allow that enchantment before setting its maximum level.");
            return true;
        }
        if (n == null || n > optional.get().getMaxLevel()) {
            SpearConfigCommand.error(commandSender, "Choose a level from 1 to " + optional.get().getMaxLevel() + ".");
            return true;
        }
        this.plugin.getConfig().set(PluginSettings.enchantmentPath(optional.get().getKey()) + ".maxLevel", (Object)n);
        this.plugin.applyConfigurationChanges();
        SpearConfigCommand.success(commandSender, "Set " + SpearConfigCommand.enchantmentName(optional.get()) + " max level to " + n + ".");
        return true;
    }

    private boolean whitelist(CommandSender commandSender, String[] stringArray) {
        if (stringArray.length < 2) {
            SpearConfigCommand.error(commandSender, "Usage: /spearconfig whitelist <add|remove|list> [enchantment]");
            return true;
        }
        String string = stringArray[1].toLowerCase(Locale.ROOT);
        if ("list".equals(string)) {
            return this.listWhitelist(commandSender, stringArray);
        }
        if (stringArray.length != 3 || !"add".equals(string) && !"remove".equals(string)) {
            SpearConfigCommand.error(commandSender, "Usage: /spearconfig whitelist <add|remove> <enchantment>");
            return true;
        }
        Optional<Enchantment> optional = this.settings.findCompatibleEnchantment(stringArray[2]);
        if (optional.isEmpty()) {
            SpearConfigCommand.error(commandSender, "That enchantment is not reported as spear-compatible by this server.");
            return true;
        }
        boolean bl = "add".equals(string);
        this.plugin.getConfig().set(PluginSettings.enchantmentPath(optional.get().getKey()) + ".allowed", (Object)bl);
        this.plugin.applyConfigurationChanges();
        SpearConfigCommand.success(commandSender, (bl ? "Allowed " : "Blocked ") + SpearConfigCommand.enchantmentName(optional.get()) + " on spears.");
        return true;
    }

    private boolean listWhitelist(CommandSender commandSender, String[] stringArray) {
        if (stringArray.length != 2) {
            SpearConfigCommand.error(commandSender, "Usage: /spearconfig whitelist list");
            return true;
        }
        List<String> list = this.settings.compatibleEnchantments().stream().filter(this.settings::isAllowed).map(enchantment -> {
            EnchantmentRule enchantmentRule = this.settings.enchantmentRule((Enchantment)enchantment);
            return SpearConfigCommand.enchantmentName(enchantment) + " (max " + enchantmentRule.maxLevel() + ")";
        }).toList();
        SpearConfigCommand.success(commandSender, (String)(list.isEmpty() ? "No spear enchantments are currently allowed." : "Allowed spear enchantments: " + String.join((CharSequence)", ", list)));
        return true;
    }

    private void sendHelp(CommandSender commandSender) {
        commandSender.sendMessage((Component)Component.text((String)"Spear Config commands:", (TextColor)NamedTextColor.GOLD));
        commandSender.sendMessage((Component)Component.text((String)"/spearconfig gui", (TextColor)NamedTextColor.YELLOW));
        commandSender.sendMessage((Component)Component.text((String)"/spearconfig reload | refresh", (TextColor)NamedTextColor.YELLOW));
        commandSender.sendMessage((Component)Component.text((String)"/spearconfig set <limit|cooldown|revealinterval|color> ...", (TextColor)NamedTextColor.YELLOW));
        commandSender.sendMessage((Component)Component.text((String)"/spearconfig toggle <setting> [tier|attack|announcement-mode]", (TextColor)NamedTextColor.YELLOW));
        commandSender.sendMessage((Component)Component.text((String)"  limit <tier> | cooldown <jab|lunge> | announcements <chat|title>", (TextColor)NamedTextColor.GRAY));
        commandSender.sendMessage((Component)Component.text((String)"/spearconfig enchantlevel <enchantment> <max-level>", (TextColor)NamedTextColor.YELLOW));
        commandSender.sendMessage((Component)Component.text((String)"/spearconfig whitelist <add|remove|list> ...", (TextColor)NamedTextColor.YELLOW));
    }

    public List<String> onTabComplete(CommandSender commandSender, Command command, String string, String[] stringArray) {
        if (!commandSender.hasPermission("spearconfig.admin")) {
            return List.of();
        }
        if (stringArray.length == 1) {
            return SpearConfigCommand.partial(stringArray[0], ROOTS);
        }
        String string2 = stringArray[0].toLowerCase(Locale.ROOT);
        if ("set".equals(string2)) {
            return this.completeSet(stringArray);
        }
        if ("toggle".equals(string2)) {
            return this.completeToggle(stringArray);
        }
        if ("enchantlevel".equals(string2)) {
            if (stringArray.length == 2) {
                return SpearConfigCommand.partial(stringArray[1], this.enchantmentKeys(true));
            }
            if (stringArray.length == 3) {
                return SpearConfigCommand.partial(stringArray[2], List.of("1"));
            }
        }
        if ("whitelist".equals(string2)) {
            if (stringArray.length == 2) {
                return SpearConfigCommand.partial(stringArray[1], List.of("add", "remove", "list"));
            }
            if (stringArray.length == 3 && "add".equalsIgnoreCase(stringArray[1])) {
                return SpearConfigCommand.partial(stringArray[2], this.enchantmentKeys(false));
            }
            if (stringArray.length == 3 && "remove".equalsIgnoreCase(stringArray[1])) {
                return SpearConfigCommand.partial(stringArray[2], this.enchantmentKeys(true));
            }
        }
        return List.of();
    }

    private List<String> completeSet(String[] stringArray) {
        if (stringArray.length == 2) {
            return SpearConfigCommand.partial(stringArray[1], SET_OPTIONS);
        }
        if (stringArray.length == 3) {
            return switch (stringArray[1].toLowerCase(Locale.ROOT)) {
                case "limit" -> SpearConfigCommand.partial(stringArray[2], Arrays.stream(SpearTier.values()).map(SpearTier::key).toList());
                case "cooldown" -> SpearConfigCommand.partial(stringArray[2], Arrays.stream(AttackType.values()).map(AttackType::key).toList());
                case "revealinterval" -> SpearConfigCommand.partial(stringArray[2], List.of("5", "60", "300"));
                case "color" -> SpearConfigCommand.partial(stringArray[2], Arrays.stream(DisplayColor.values()).map(DisplayColor::key).toList());
                default -> List.of();
            };
        }
        if (stringArray.length == 4 && "limit".equalsIgnoreCase(stringArray[1])) {
            return SpearConfigCommand.partial(stringArray[3], List.of("0", "1", "10"));
        }
        if (stringArray.length == 4 && "cooldown".equalsIgnoreCase(stringArray[1])) {
            return SpearConfigCommand.partial(stringArray[3], List.of("0", "1", "5"));
        }
        return List.of();
    }

    private List<String> completeToggle(String[] stringArray) {
        if (stringArray.length == 2) {
            return SpearConfigCommand.partial(stringArray[1], TOGGLE_OPTIONS);
        }
        if (stringArray.length == 3) {
            return switch (stringArray[1].toLowerCase(Locale.ROOT)) {
                case "limit" -> SpearConfigCommand.partial(stringArray[2], Arrays.stream(SpearTier.values()).map(SpearTier::key).toList());
                case "cooldown" -> SpearConfigCommand.partial(stringArray[2], Arrays.stream(AttackType.values()).map(AttackType::key).toList());
                case "announcements" -> SpearConfigCommand.partial(stringArray[2], List.of("chat", "title"));
                default -> List.of();
            };
        }
        return List.of();
    }

    private List<String> enchantmentKeys(boolean bl) {
        return this.settings.compatibleEnchantments().stream().filter(enchantment -> this.settings.isAllowed((Enchantment)enchantment) == bl).map(SpearConfigCommand::enchantmentName).toList();
    }

    private static List<String> partial(String string, List<String> list) {
        String string2 = string.toLowerCase(Locale.ROOT);
        ArrayList<String> arrayList = new ArrayList<String>();
        for (String string3 : list) {
            if (!string3.toLowerCase(Locale.ROOT).startsWith(string2)) continue;
            arrayList.add(string3);
        }
        return arrayList;
    }

    private static String enchantmentName(Enchantment enchantment) {
        return "minecraft".equals(enchantment.getKey().getNamespace()) ? enchantment.getKey().getKey() : enchantment.getKey().asString();
    }

    private static Integer nonNegativeInteger(String string) {
        try {
            int n = Integer.parseInt(string);
            return n >= 0 ? Integer.valueOf(n) : null;
        }
        catch (NumberFormatException numberFormatException) {
            return null;
        }
    }

    private static Integer positiveInteger(String string) {
        Integer n = SpearConfigCommand.nonNegativeInteger(string);
        return n != null && n > 0 ? n : null;
    }

    private static String enabledText(boolean bl) {
        return bl ? "enabled" : "disabled";
    }

    private static void success(CommandSender commandSender, String string) {
        commandSender.sendMessage((Component)Component.text((String)string, (TextColor)NamedTextColor.GREEN));
    }

    private static void error(CommandSender commandSender, String string) {
        commandSender.sendMessage((Component)Component.text((String)string, (TextColor)NamedTextColor.RED));
    }
}

