package me.merelyme.pack;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class MerelyPack extends JavaPlugin implements Listener {
    private static final UUID PACK_ID = UUID.fromString("a7c4e910-6b2f-4d11-9e3a-0f8b21c45d73");
    private static final String[] RANK_IDS = { "member", "knight", "warrior", "macer", "prime", "media", "partner", "helper", "mod", "owner", "admin" };
    private static final int[] RANK_WEIGHTS = { 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 95 };
    private String url;
    private String sha1;
    private String prompt;
    private boolean enabled;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadCfg();
        getServer().getPluginManager().registerEvents(this, this);
        if (this.enabled) {
            applyLuckPermsPrefixes();
            getLogger().info("MerelyPack ready - forcing rank icons pack on join.");
        } else {
            getLogger().info("MerelyPack disabled - no resource pack on join.");
        }
    }

    private void reloadCfg() {
        reloadConfig();
        this.enabled = getConfig().getBoolean("enabled", false);
        this.url = getConfig().getString("url", "");
        this.sha1 = getConfig().getString("sha1", "");
        this.prompt = getConfig().getString("prompt", "MerelyMe SMP rank icons");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!this.enabled) {
            return;
        }
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> sendPack(player), 20L);
        Bukkit.getScheduler().runTaskLater(this, () -> clearChatTag(player), 40L);
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        PlayerResourcePackStatusEvent.Status status = event.getStatus();
        if (status == PlayerResourcePackStatusEvent.Status.DECLINED) {
            event.getPlayer().kickPlayer("§cMerelyMe SMP requires the rank resource pack. Click Yes when asked.");
        } else if (status == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD) {
            getLogger().warning("Resource pack download failed for " + event.getPlayer().getName());
        }
    }

    private void sendPack(Player player) {
        if (!player.isOnline() || this.url == null || this.url.isEmpty()) {
            return;
        }
        byte[] hash = hexToBytes(this.sha1);
        net.kyori.adventure.text.Component promptText = net.kyori.adventure.text.Component.text(this.prompt);
        try {
            player.setResourcePack(PACK_ID, this.url, hash, promptText, true);
            return;
        } catch (Throwable ignored) {
        }
        try {
            player.setResourcePack(this.url, hash, promptText, true);
        } catch (Throwable first) {
            getLogger().warning("Could not send resource pack: " + first.getMessage());
        }
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() < 40) {
            return new byte[0];
        }
        byte[] out = new byte[20];
        for (int i = 0; i < 20; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private void applyLuckPermsPrefixes() {
        for (int i = 0; i < RANK_IDS.length; i++) {
            char ch = (char) (0xE000 + i);
            String cmd = "lp group " + RANK_IDS[i] + " meta setprefix " + RANK_WEIGHTS[i] + " &f" + ch + "\uF802";
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    private void clearChatTag(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + player.getName() + " meta removesuffix 110");
    }
}
