package ua.atherium.agnelutils.client;

import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import ua.atherium.agnelutils.AthAgnelUtils;

public final class ClientStats {
    public static long sessionStartMs = System.currentTimeMillis();
    public static long attackTicks = 0;
    public static int repairs = 0;
    public static int reconnects = 0;
    public static int captchas = 0;
    public static int captchaAuto = 0;
    public static long throwsTotal = 0;
    public static double durabilityGainedTotal = 0;
    public static double perThrowEma = 2.0;
    public static int stucks = 0;
    public static int rewarps = 0;
    private static long lastSaveMs = 0;

    private ClientStats() {
    }

    public static synchronized void recordThrow(double gained) {
        throwsTotal++;
        durabilityGainedTotal += gained;
        if (gained > 0) {
            perThrowEma = perThrowEma * 0.8 + gained * 0.2;
        }
    }

    public static synchronized int estimateThrows(int need) {
        double per = Math.max(0.5, perThrowEma);
        return (int) Math.ceil(need / per) + 4;
    }

    public static String summary() {
        long mins = (System.currentTimeMillis() - sessionStartMs) / 60000;
        return "[AthAgnel] сесія " + mins + "хв | копання " + attackTicks + "т | ремонтів " + repairs
            + " | реконектів " + reconnects + " | капч " + captchas + " (авто " + captchaAuto + ")"
            + " | кидків " + throwsTotal + " | ~" + String.format("%.1f", perThrowEma) + " міцн/кидок"
            + " | стаків " + stucks + " | реварпів " + rewarps;
    }

    public static void tickSave() {
        long now = System.currentTimeMillis();
        if (now - lastSaveMs < 60_000) {
            return;
        }
        lastSaveMs = now;
        try {
            Path out = FabricLoader.getInstance().getConfigDir().resolve("athagnelutils-stats.json");
            String json = "{\"attackTicks\":" + attackTicks + ",\"repairs\":" + repairs
                + ",\"reconnects\":" + reconnects + ",\"captchas\":" + captchas
                + ",\"captchaAuto\":" + captchaAuto + ",\"throwsTotal\":" + throwsTotal
                + ",\"perThrowEma\":" + perThrowEma + ",\"stucks\":" + stucks + ",\"rewarps\":" + rewarps + "}";
            Files.writeString(out, json);
        } catch (Exception e) {
            AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][stats] save failed: {}", e.toString());
        }
    }

    public static void load() {
        try {
            Path out = FabricLoader.getInstance().getConfigDir().resolve("athagnelutils-stats.json");
            if (!Files.exists(out)) {
                return;
            }
            String s = Files.readString(out);
            perThrowEma = parseDouble(s, "\"perThrowEma\":", perThrowEma);
        } catch (Exception e) {
            AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][stats] load failed: {}", e.toString());
        }
    }

    private static double parseDouble(String s, String key, double def) {
        try {
            int i = s.indexOf(key);
            if (i < 0) {
                return def;
            }
            int j = i + key.length();
            int k = j;
            while (k < s.length() && (Character.isDigit(s.charAt(k)) || s.charAt(k) == '.' || s.charAt(k) == '-')) {
                k++;
            }
            return Double.parseDouble(s.substring(j, k));
        } catch (Exception e) {
            return def;
        }
    }
}
