package merelyme.virtualmenu2;

import java.util.Locale;

public final class MenuSelecto {
    private static final String[] MARKED = {
        "crates", "afk_crate", "stats", "rank_perks", "help",
        "shop_3", "shop_4", "shop_5", "playtime", "one_v_one",
        "two_v_two", "duel_players", "tournaments", "kits",
        "kit_preview", "media", "rules", "battle_pass", "update",
        "daily_wheel", "glory_shop", "glory", "afk_crate_4"
    };

    private MenuSelecto() {}

    public static boolean isCustom(String title) {
        return texture(title, 3) != null;
    }

    public static String texture(String title, int requestedRows) {
        int rows = Math.max(1, Math.min(6, requestedRows));
        int marker = title.codePoints()
            .filter(c -> c >= 0xE200 && c <= 0xE216)
            .findFirst()
            .orElse(-1);
        String clean = title.replaceAll("[\\uE000-\\uF8FF]", "")
            .trim().toUpperCase(Locale.ROOT);

        if (marker == 0xE20E) {
            String kit = kitTexture(clean, rows);
            return kit != null ? kit : MARKED[marker - 0xE200];
        }
        if (marker >= 0) return MARKED[marker - 0xE200];

        if (clean.contains("AFK")) return auto("afk_crate", rows);
        if (clean.contains("CRATE")) return auto("crates", rows);
        if (clean.contains("CLAIM")) return auto("claim", rows);
        if (clean.contains("SHOP")) return auto("shop", rows);
        if (clean.contains("STAT")) return auto("stats", rows);
        if (clean.contains("RANK") && (clean.contains("PERK") || clean.contains("PERM"))) return auto("rank_perks", rows);
        if (clean.contains("SELL")) return auto("sell", rows);
        if (clean.contains("ORDER")) return auto("orders", rows);
        if (clean.contains("AUCTION") || clean.equals("AH")) return auto("auction_house", rows);
        if (clean.contains("VOTE")) return auto("vote", rows);
        if (clean.contains("UPDATE")) return auto("update", rows);
        if (clean.contains("BATTLE") && clean.contains("PASS")) return auto("battle_pass", rows);
        if (clean.contains("KIT")) {
            String kit = kitTexture(clean, rows);
            return kit != null ? kit : auto("kits", rows);
        }
        if (clean.contains("HOME")) return auto("homes", rows);
        if (clean.contains("TOURNAMENT")) return auto("tournaments", rows);
        if (clean.equals("RTP") || (clean.contains("RANDOM") && clean.contains("TELEPORT"))) return auto("rtp", rows);
        if (clean.contains("PLAYTIME")) return auto("playtime", rows);
        if (clean.contains("REWARD")) return auto("rewards", rows);
        if (clean.contains("2V2")) return auto("two_v_two", rows);
        if (clean.contains("1V1")) return auto("one_v_one", rows);
        return null;
    }

    private static String kitTexture(String title, int rows) {
        if (title.contains("MEMBER")) return auto("kit_member", rows);
        if (title.contains("KNIGHT")) return auto("kit_knight", rows);
        if (title.contains("WARRIOR")) return auto("kit_warrior", rows);
        if (title.contains("MACER")) return auto("kit_macer", rows);
        if (title.contains("PRIME")) return auto("kit_prime", rows);
        return null;
    }

    private static String auto(String name, int rows) {
        return "auto_" + name + "_" + rows;
    }
}
