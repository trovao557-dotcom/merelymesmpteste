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

    public SpearConfigCommand(SpearConfigPlugin plugin, PluginSettings settings, SpearTracker tracker, ConfigGui gui) {
        this.plugin = plugin;
        this.settings = settings;
        this.tracker = tracker;
        this.gui = gui;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("spearconfig.admin")) {
            SpearConfigCommand.error(sender, "You do not have permission to use Spear Config.");
            return true;
        }
        if (args.length == 0) {
            this.sendHelp(sender);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "gui" -> this.openGui(sender);
            case "reload" -> this.reload(sender);
            case "refresh" -> this.refresh(sender);
            case "set" -> this.set(sender, args);
            case "toggle" -> this.toggle(sender, args);
            case "enchantlevel" -> this.enchantLevel(sender, args);
            case "whitelist" -> this.whitelist(sender, args);
            default -> {
                SpearConfigCommand.error(sender, "Unknown subcommand. Use /spearconfig for help.");
                yield true;
            }
        };
    }

    private boolean openGui(CommandSender sender) {
        if (!(sender instanceof Player)) {
            SpearConfigCommand.error(sender, "Only a player can open the settings GUI.");
            return true;
        }
        Player player = (Player)sender;
        this.gui.openMain(player);
        return true;
    }

    private boolean reload(CommandSender sender) {
        this.plugin.reloadPluginConfiguration();
        SpearConfigCommand.success(sender, "Reloaded config.yml and restarted configuration-dependent tasks.");
        return true;
    }

    private boolean refresh(CommandSender sender) {
        Map<SpearTier, Integer> refreshed = this.plugin.refreshTrackedSpears();
        String summary = Arrays.stream(SpearTier.values()).map(tier -> tier.key() + "=" + String.valueOf(refreshed.getOrDefault(tier, 0))).collect(Collectors.joining(", "));
        SpearConfigCommand.success(sender, "Refreshed online inventories, ender chests, and nested shulkers: " + summary);
        return true;
    }

    private boolean set(CommandSender sender, String[] args) {
        if (args.length < 2) {
            SpearConfigCommand.error(sender, "Usage: /spearconfig set <limit|cooldown|revealinterval|color> ...");
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "limit" -> this.setLimit(sender, args);
            case "cooldown" -> this.setCooldown(sender, args);
            case "revealinterval" -> this.setRevealInterval(sender, args);
            case "color" -> this.setColor(sender, args);
            default -> {
                SpearConfigCommand.error(sender, "Unknown setting. Choose limit, cooldown, revealinterval, or color.");
                yield true;
            }
        };
    }

    private boolean setLimit(CommandSender sender, String[] args) {
        if (args.length != 4) {
            SpearConfigCommand.error(sender, "Usage: /spearconfig set limit <tier> <amount>");
            return true;
        }
        Optional<SpearTier> tier = SpearTier.fromKey(args[2]);
        Integer amount = SpearConfigCommand.nonNegativeInteger(args[3]);
        if (tier.isEmpty() || amount == null) {
            SpearConfigCommand.error(sender, "Use a valid tier and a whole amount of 0 or more.");
            return true;
        }
        this.plugin.getConfig().set("limits." + tier.get().key() + ".amount", (Object)amount);
        this.plugin.applyConfigurationChanges();
        SpearConfigCommand.success(sender, "Set the " + tier.get().key() + " spear limit to " + amount + "; its master switch is " + SpearConfigCommand.enabledText(this.settings.limitEnabled(tier.get())) + ".");
        return true;
    }

    private boolean setCooldown(CommandSender sender, String[] args) {
        if (args.length != 4) {
            SpearConfigCommand.error(sender, "Usage: /spearconfig set cooldown <jab|lunge> <seconds>");
            return true;
        }
        Optional<AttackType> type = AttackType.fromKey(args[2]);
        Integer seconds = SpearConfigCommand.nonNegativeInteger(args[3]);
        if (type.isEmpty() || seconds == null) {
            SpearConfigCommand.error(sender, "Use jab or lunge and a whole number of seconds 0 or greater.");
            return true;
        }
        this.plugin.getConfig().set("cooldown." + type.get().key() + ".seconds", (Object)seconds);
        this.plugin.applyConfigurationChanges();
        SpearConfigCommand.success(sender, "Set the " + type.get().key() + " cooldown to " + seconds + " seconds; its master switch is " + SpearConfigCommand.enabledText(this.settings.cooldownEnabled(type.get())) + ".");
        return true;
    }

    private boolean setRevealInterval(CommandSender sender, String[] args) {
        if (args.length != 3) {
            SpearConfigCommand.error(sender, "Usage: /spearconfig set revealinterval <seconds>");
            return true;
        }
        Integer seconds = SpearConfigCommand.nonNegativeInteger(args[2]);
        if (seconds == null || seconds < 5) {
            SpearConfigCommand.error(sender, "The reveal interval must be at least 5 seconds.");
            return true;
        }
        this.plugin.getConfig().set("reveal.interval", (Object)seconds);
        this.plugin.applyConfigurationChanges();
        SpearConfigCommand.success(sender, "Set the reveal interval to " + seconds + " seconds.");
        return true;
    }

    private boolean setColor(CommandSender sender, String[] args) {
        if (args.length != 3) {
            SpearConfigCommand.error(sender, "Usage: /spearconfig set color <color>");
            return true;
        }
        Optional<DisplayColor> color = DisplayColor.fromKey(args[2]);
        if (color.isEmpty()) {
            SpearConfigCommand.error(sender, "Unknown color. Use tab completion to see supported colors.");
            return true;
        }
        this.plugin.getConfig().set("color", (Object)color.get().name());
        this.plugin.applyConfigurationChanges();
        SpearConfigCommand.success(sender, "Set the shared spear color to " + color.get().displayName() + ".");
        return true;
    }

    private boolean toggle(CommandSender sender, String[] args) {
        if (args.length < 2 || !TOGGLE_OPTIONS.contains(args[1].toLowerCase(Locale.ROOT))) {
            SpearConfigCommand.error(sender, "Usage: /spearconfig toggle <setting> [tier|attack|announcement-mode]");
            return true;
        }
        String option = args[1].toLowerCase(Locale.ROOT);
        if ("limit".equals(option)) {
            return this.toggleLimit(sender, args);
        }
        if ("cooldown".equals(option)) {
            return this.toggleCooldown(sender, args);
        }
        if ("announcements".equals(option) && args.length == 3) {
            return this.toggleAnnouncementMode(sender, args);
        }
        if (args.length != 2) {
            SpearConfigCommand.error(sender, "That toggle does not accept another argument.");
            return true;
        }
        if ("announcements".equals(option)) {
            boolean enabled = !this.settings.chatAnnouncements() || !this.settings.titleAnnouncements();
            this.plugin.getConfig().set("announcements.chat", (Object)enabled);
            this.plugin.getConfig().set("announcements.title", (Object)enabled);
            this.plugin.applyConfigurationChanges();
            SpearConfigCommand.success(sender, "All announcement modes are now " + SpearConfigCommand.enabledText(enabled) + ".");
            return true;
        }
        String path = switch (option) {
            case "blockmobdrop" -> "blockMobDrop";
            case "reveal" -> "reveal.enabled";
            default -> option;
        };
        boolean enabled = !this.plugin.getConfig().getBoolean(path);
        this.plugin.getConfig().set(path, (Object)enabled);
        this.plugin.applyConfigurationChanges();
        SpearConfigCommand.success(sender, option + " is now " + SpearConfigCommand.enabledText(enabled) + ".");
        return true;
    }

    private boolean toggleLimit(CommandSender sender, String[] args) {
        if (args.length != 3) {
            SpearConfigCommand.error(sender, "Usage: /spearconfig toggle limit <tier>");
            return true;
        }
        Optional<SpearTier> tier = SpearTier.fromKey(args[2]);
        if (tier.isEmpty()) {
            SpearConfigCommand.error(sender, "Choose wood, stone, copper, iron, gold, diamond, or netherite.");
            return true;
        }
        boolean enabled = !this.settings.limitEnabled(tier.get());
        this.plugin.getConfig().set("limits." + tier.get().key() + ".enabled", (Object)enabled);
        this.plugin.applyConfigurationChanges();
        SpearConfigCommand.success(sender, tier.get().displayName() + " spear limits are now " + SpearConfigCommand.enabledText(enabled) + ".");
        return true;
    }

    private boolean toggleCooldown(CommandSender sender, String[] args) {
        if (args.length != 3) {
            SpearConfigCommand.error(sender, "Usage: /spearconfig toggle cooldown <jab|lunge>");
            return true;
        }
        Optional<AttackType> type = AttackType.fromKey(args[2]);
        if (type.isEmpty()) {
            SpearConfigCommand.error(sender, "Choose jab or lunge.");
            return true;
        }
        boolean enabled = !this.settings.cooldownEnabled(type.get());
        this.plugin.getConfig().set("cooldown." + type.get().key() + ".enabled", (Object)enabled);
        this.plugin.applyConfigurationChanges();
        SpearConfigCommand.success(sender, type.get().displayName() + " cooldowns are now " + SpearConfigCommand.enabledText(enabled) + ".");
        return true;
    }

    private boolean toggleAnnouncementMode(CommandSender sender, String[] args) {
        boolean current;
        String path;
        String mode = args[2].toLowerCase(Locale.ROOT);
        if ("chat".equals(mode)) {
            path = "announcements.chat";
            current = this.settings.chatAnnouncements();
        } else if ("title".equals(mode)) {
            path = "announcements.title";
            current = this.settings.titleAnnouncements();
        } else {
            SpearConfigCommand.error(sender, "Usage: /spearconfig toggle announcements <chat|title>");
            return true;
        }
        boolean enabled = !current;
        this.plugin.getConfig().set(path, (Object)enabled);
        this.plugin.applyConfigurationChanges();
        SpearConfigCommand.success(sender, mode + " announcements are now " + SpearConfigCommand.enabledText(enabled) + ".");
        return true;
    }

    private boolean enchantLevel(CommandSender sender, String[] args) {
        if (args.length != 3) {
            SpearConfigCommand.error(sender, "Usage: /spearconfig enchantlevel <enchantment> <max-level>");
            return true;
        }
        Optional<Enchantment> enchantment = this.settings.findCompatibleEnchantment(args[1]);
        Integer level = SpearConfigCommand.positiveInteger(args[2]);
        if (enchantment.isEmpty()) {
            SpearConfigCommand.error(sender, "That enchantment is not reported as spear-compatible by this server.");
            return true;
        }
        if (!this.settings.isAllowed(enchantment.get())) {
            SpearConfigCommand.error(sender, "Allow that enchantment before setting its maximum level.");
            return true;
        }
        if (level == null || level > enchantment.get().getMaxLevel()) {
            SpearConfigCommand.error(sender, "Choose a level from 1 to " + enchantment.get().getMaxLevel() + ".");
            return true;
        }
        this.plugin.getConfig().set(PluginSettings.enchantmentPath(enchantment.get().getKey()) + ".maxLevel", (Object)level);
        this.plugin.applyConfigurationChanges();
        SpearConfigCommand.success(sender, "Set " + SpearConfigCommand.enchantmentName(enchantment.get()) + " max level to " + level + ".");
        return true;
    }

    private boolean whitelist(CommandSender sender, String[] args) {
        if (args.length < 2) {
            SpearConfigCommand.error(sender, "Usage: /spearconfig whitelist <add|remove|list> [enchantment]");
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if ("list".equals(action)) {
            return this.listWhitelist(sender, args);
        }
        if (args.length != 3 || !"add".equals(action) && !"remove".equals(action)) {
            SpearConfigCommand.error(sender, "Usage: /spearconfig whitelist <add|remove> <enchantment>");
            return true;
        }
        Optional<Enchantment> enchantment = this.settings.findCompatibleEnchantment(args[2]);
        if (enchantment.isEmpty()) {
            SpearConfigCommand.error(sender, "That enchantment is not reported as spear-compatible by this server.");
            return true;
        }
        boolean allowed = "add".equals(action);
        this.plugin.getConfig().set(PluginSettings.enchantmentPath(enchantment.get().getKey()) + ".allowed", (Object)allowed);
        this.plugin.applyConfigurationChanges();
        SpearConfigCommand.success(sender, (allowed ? "Allowed " : "Blocked ") + SpearConfigCommand.enchantmentName(enchantment.get()) + " on spears.");
        return true;
    }

    private boolean listWhitelist(CommandSender sender, String[] args) {
        if (args.length != 2) {
            SpearConfigCommand.error(sender, "Usage: /spearconfig whitelist list");
            return true;
        }
        List<String> allowed = this.settings.compatibleEnchantments().stream().filter(this.settings::isAllowed).map(enchantment -> {
            EnchantmentRule rule = this.settings.enchantmentRule((Enchantment)enchantment);
            return SpearConfigCommand.enchantmentName(enchantment) + " (max " + rule.maxLevel() + ")";
        }).toList();
        SpearConfigCommand.success(sender, (String)(allowed.isEmpty() ? "No spear enchantments are currently allowed." : "Allowed spear enchantments: " + String.join((CharSequence)", ", allowed)));
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage((Component)Component.text((String)"Spear Config commands:", (TextColor)NamedTextColor.GOLD));
        sender.sendMessage((Component)Component.text((String)"/spearconfig gui", (TextColor)NamedTextColor.YELLOW));
        sender.sendMessage((Component)Component.text((String)"/spearconfig reload | refresh", (TextColor)NamedTextColor.YELLOW));
        sender.sendMessage((Component)Component.text((String)"/spearconfig set <limit|cooldown|revealinterval|color> ...", (TextColor)NamedTextColor.YELLOW));
        sender.sendMessage((Component)Component.text((String)"/spearconfig toggle <setting> [tier|attack|announcement-mode]", (TextColor)NamedTextColor.YELLOW));
        sender.sendMessage((Component)Component.text((String)"  limit <tier> | cooldown <jab|lunge> | announcements <chat|title>", (TextColor)NamedTextColor.GRAY));
        sender.sendMessage((Component)Component.text((String)"/spearconfig enchantlevel <enchantment> <max-level>", (TextColor)NamedTextColor.YELLOW));
        sender.sendMessage((Component)Component.text((String)"/spearconfig whitelist <add|remove|list> ...", (TextColor)NamedTextColor.YELLOW));
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("spearconfig.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return SpearConfigCommand.partial(args[0], ROOTS);
        }
        String root = args[0].toLowerCase(Locale.ROOT);
        if ("set".equals(root)) {
            return this.completeSet(args);
        }
        if ("toggle".equals(root)) {
            return this.completeToggle(args);
        }
        if ("enchantlevel".equals(root)) {
            if (args.length == 2) {
                return SpearConfigCommand.partial(args[1], this.enchantmentKeys(true));
            }
            if (args.length == 3) {
                return SpearConfigCommand.partial(args[2], List.of("1"));
            }
        }
        if ("whitelist".equals(root)) {
            if (args.length == 2) {
                return SpearConfigCommand.partial(args[1], List.of("add", "remove", "list"));
            }
            if (args.length == 3 && "add".equalsIgnoreCase(args[1])) {
                return SpearConfigCommand.partial(args[2], this.enchantmentKeys(false));
            }
            if (args.length == 3 && "remove".equalsIgnoreCase(args[1])) {
                return SpearConfigCommand.partial(args[2], this.enchantmentKeys(true));
            }
        }
        return List.of();
    }

    private List<String> completeSet(String[] args) {
        if (args.length == 2) {
            return SpearConfigCommand.partial(args[1], SET_OPTIONS);
        }
        if (args.length == 3) {
            return switch (args[1].toLowerCase(Locale.ROOT)) {
                case "limit" -> SpearConfigCommand.partial(args[2], Arrays.stream(SpearTier.values()).map(SpearTier::key).toList());
                case "cooldown" -> SpearConfigCommand.partial(args[2], Arrays.stream(AttackType.values()).map(AttackType::key).toList());
                case "revealinterval" -> SpearConfigCommand.partial(args[2], List.of("5", "60", "300"));
                case "color" -> SpearConfigCommand.partial(args[2], Arrays.stream(DisplayColor.values()).map(DisplayColor::key).toList());
                default -> List.of();
            };
        }
        if (args.length == 4 && "limit".equalsIgnoreCase(args[1])) {
            return SpearConfigCommand.partial(args[3], List.of("0", "1", "10"));
        }
        if (args.length == 4 && "cooldown".equalsIgnoreCase(args[1])) {
            return SpearConfigCommand.partial(args[3], List.of("0", "1", "5"));
        }
        return List.of();
    }

    private List<String> completeToggle(String[] args) {
        if (args.length == 2) {
            return SpearConfigCommand.partial(args[1], TOGGLE_OPTIONS);
        }
        if (args.length == 3) {
            return switch (args[1].toLowerCase(Locale.ROOT)) {
                case "limit" -> SpearConfigCommand.partial(args[2], Arrays.stream(SpearTier.values()).map(SpearTier::key).toList());
                case "cooldown" -> SpearConfigCommand.partial(args[2], Arrays.stream(AttackType.values()).map(AttackType::key).toList());
                case "announcements" -> SpearConfigCommand.partial(args[2], List.of("chat", "title"));
                default -> List.of();
            };
        }
        return List.of();
    }

    private List<String> enchantmentKeys(boolean allowed) {
        return this.settings.compatibleEnchantments().stream().filter(enchantment -> this.settings.isAllowed((Enchantment)enchantment) == allowed).map(SpearConfigCommand::enchantmentName).toList();
    }

    private static List<String> partial(String token, List<String> values) {
        String normalized = token.toLowerCase(Locale.ROOT);
        ArrayList<String> matches = new ArrayList<String>();
        for (String value : values) {
            if (!value.toLowerCase(Locale.ROOT).startsWith(normalized)) continue;
            matches.add(value);
        }
        return matches;
    }

    private static String enchantmentName(Enchantment enchantment) {
        return "minecraft".equals(enchantment.getKey().getNamespace()) ? enchantment.getKey().getKey() : enchantment.getKey().asString();
    }

    private static Integer nonNegativeInteger(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= 0 ? Integer.valueOf(parsed) : null;
        }
        catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Integer positiveInteger(String value) {
        Integer parsed = SpearConfigCommand.nonNegativeInteger(value);
        return parsed != null && parsed > 0 ? parsed : null;
    }

    private static String enabledText(boolean enabled) {
        return enabled ? "enabled" : "disabled";
    }

    private static void success(CommandSender sender, String message) {
        sender.sendMessage((Component)Component.text((String)message, (TextColor)NamedTextColor.GREEN));
    }

    private static void error(CommandSender sender, String message) {
        sender.sendMessage((Component)Component.text((String)message, (TextColor)NamedTextColor.RED));
    }
}

