package ua.atherium.agnelutils.client.flow;

import net.minecraft.client.Minecraft;
import ua.atherium.agnelutils.AthAgnelUtils;
import ua.atherium.agnelutils.client.config.ConfigStore;
import ua.atherium.agnelutils.client.util.ChatText;
import ua.atherium.agnelutils.client.util.Msg;

public final class MineGuard {
    private static volatile long lastSellMs = 0;
    private static volatile long lastRewarpMs = 0;
    private static volatile long miningSinceMs = 0;
    private static volatile boolean wasMining = false;

    private MineGuard() {
    }

    public static void resetSession() {
        lastSellMs = System.currentTimeMillis();
        lastRewarpMs = System.currentTimeMillis();
        miningSinceMs = 0;
        wasMining = false;
    }

    public static void onGameMessage(String plain) {
        String n = ChatText.norm(plain);
        if (n.isEmpty()) {
            return;
        }
        if (n.contains("успешная продажа") || n.contains("успешно продано") || n.contains("продажа предметов")) {
            lastSellMs = System.currentTimeMillis();
            MinerFlow.resetDenyStreak();
            return;
        }
        if (n.contains("не можете ломать") || n.contains("нельзя ломать") || n.contains("ломать блоки в этом месте")
            || n.contains("cannot break") || n.contains("чужой регион") || n.contains("запрещено ломать")) {
            onDeny(plain);
        }
    }

    private static void onDeny(String plain) {
        AthAgnelUtils.LOGGER.info("[AthAgnelUtils][guard] deny: {}", plain);
        if (!MinerFlow.gateOpen()) {
            return;
        }
        MinerFlow.denyRewarp();
    }

    public static void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            wasMining = false;
            return;
        }
        var cfg = ConfigStore.get();
        boolean mining = MinerFlow.gateOpen();
        long now = System.currentTimeMillis();
        if (mining && !wasMining) {
            miningSinceMs = now;
            if (lastSellMs == 0) {
                lastSellMs = now;
            }
        }
        wasMining = mining;
        if (!mining) {
            return;
        }
        long rewarpEvery = cfg.rewarpIntervalMin * 60_000L;
        if (rewarpEvery > 0 && now - lastRewarpMs > rewarpEvery) {
            lastRewarpMs = now;
            AthAgnelUtils.LOGGER.info("[AthAgnelUtils][guard] periodic rewarp");
            Msg.title("Плановий реварп (тримаємось в рг шахти)…");
            MinerFlow.stopAttack();
            MinerFlow.warpMine();
            return;
        }
        long sellTimeout = cfg.sellTimeoutMin * 60_000L;
        if (sellTimeout > 0 && now - miningSinceMs > 120_000 && now - lastSellMs > sellTimeout) {
            lastSellMs = now;
            lastRewarpMs = now;
            AthAgnelUtils.LOGGER.info("[AthAgnelUtils][guard] no sales for a while, rewarp");
            Msg.title("Давно нема продажів — схоже не та зона, реварп…");
            MinerFlow.stopAttack();
            MinerFlow.warpMine();
        }
    }
}
