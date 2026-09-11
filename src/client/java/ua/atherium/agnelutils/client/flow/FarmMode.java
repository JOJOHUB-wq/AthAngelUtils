package ua.atherium.agnelutils.client.flow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import ua.atherium.agnelutils.AthAgnelUtils;
import ua.atherium.agnelutils.client.ScreenTracker;
import ua.atherium.agnelutils.client.config.ConfigStore;
import ua.atherium.agnelutils.client.util.Msg;

public final class FarmMode {
    private enum State {
        OFF, TO_DISCONNECT, WAIT_SCREEN, TO_CONNECT, WAIT_JOIN, WAIT_CAPTCHA, WAIT_SOLVE
    }

    private static volatile State state = State.OFF;
    private static volatile long stateAtMs = 0;
    private static volatile int cycles = 0;
    private static volatile int target = 30;
    private static volatile boolean expectingDisconnect = false;
    private static volatile long lastJoinMs = 0;
    private static volatile long joinRequestMs = 0;

    private FarmMode() {
    }

    public static boolean active() {
        return state != State.OFF;
    }

    public static void start(int n) {
        if (active()) {
            return;
        }
        target = Math.max(1, Math.min(200, n));
        cycles = 0;
        expectingDisconnect = false;
        NickSwapper.rememberOriginal();
        AthAgnelUtils.LOGGER.info("[AthAgnelUtils][farm] start, target={} orig={}", target, NickSwapper.originalNick());
        Msg.title("Фарм капч запущено: 0/" + target + ". Вводь цифри як завжди, дані збираються самі.");
        var rc = rc();
        if (rc != null) {
            rc.setSuppressed(true);
        }
        toDisconnect("start");
    }

    public static void stop(String reason) {
        if (!active()) {
            return;
        }
        state = State.OFF;
        var rc = rc();
        if (rc != null) {
            rc.setSuppressed(false);
        }
        NickSwapper.restore();
        AthAgnelUtils.LOGGER.info("[AthAgnelUtils][farm] stop after {}/{} ({})", cycles, target, reason);
        Msg.title("Фарм стопнуто: " + cycles + "/" + target + " (" + reason + ")");
    }

    public static String status() {
        if (!active()) {
            return "[AthAgnel] Фарм вимкнений. /agnel farm start [N]";
        }
        return "[AthAgnel] Фарм: " + cycles + "/" + target + " стадія=" + state;
    }

    public static void noteJoin() {
        lastJoinMs = System.currentTimeMillis();
    }

    public static boolean consumeExpectedDisconnect() {
        if (active() && expectingDisconnect) {
            expectingDisconnect = false;
            return true;
        }
        return false;
    }

    private static ua.atherium.agnelutils.client.flow.ReconnectState rc() {
        var inst = ua.atherium.agnelutils.client.AthAgnelUtilsClient.INSTANCE;
        return inst == null ? null : inst.reconnectState();
    }

    private static void toDisconnect(String why) {
        state = State.TO_DISCONNECT;
        stateAtMs = System.currentTimeMillis();
        AthAgnelUtils.LOGGER.info("[AthAgnelUtils][farm] -> disconnect ({})", why);
    }

    private static void doDisconnect(Minecraft client) {
        try {
            expectingDisconnect = true;
            var screen = ScreenTracker.current();
            if (screen == null) {
                screen = new net.minecraft.client.gui.screens.TitleScreen();
            }
            client.disconnect(screen, false);
        } catch (Throwable t) {
            expectingDisconnect = false;
            AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][farm] disconnect failed: {}", t.toString());
            state = State.WAIT_SCREEN;
            stateAtMs = System.currentTimeMillis();
        }
    }

    private static void doConnect(Minecraft client) {
        try {
            var r = rc();
            if (r == null) {
                stop("no-reconnect-state");
                return;
            }
            var cfg = ConfigStore.get();
            String nick = cfg.farmPrefix + String.format("%02d", cfg.farmStartIndex + cycles);
            if (client.player == null && !nick.equals(NickSwapper.gameNick())) {
                if (!NickSwapper.setNick(nick)) {
                    stop("nick-failed");
                    return;
                }
                Msg.title("Фарм: нік " + nick + " (" + (cycles + 1) + "/" + target + ")");
            }
            var server = r.lastServerCopy();
            if (server == null) {
                stop("no-server");
                return;
            }
            var parent = ScreenTracker.current() instanceof DisconnectedScreen dc ? dc
                : new net.minecraft.client.gui.screens.TitleScreen();
            ReconnectHelper.connect(client, server, parent);
            joinRequestMs = System.currentTimeMillis();
            state = State.WAIT_JOIN;
            stateAtMs = joinRequestMs;
        } catch (Throwable t) {
            AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][farm] connect failed: {}", t.toString());
            stop("connect-failed");
        }
    }

    public static void tick(Minecraft client) {
        if (!active()) {
            return;
        }
        var cfg = ConfigStore.get();
        long now = System.currentTimeMillis();
        boolean inGame = client.player != null && client.level != null;
        var screen = ScreenTracker.current();
        switch (state) {
            case TO_DISCONNECT -> {
                if (now - stateAtMs > 1500) {
                    if (inGame) {
                        doDisconnect(client);
                        state = State.WAIT_SCREEN;
                        stateAtMs = now;
                    } else {
                        state = State.TO_CONNECT;
                        stateAtMs = now;
                    }
                }
            }
            case WAIT_SCREEN -> {
                if (screen instanceof DisconnectedScreen) {
                    state = State.TO_CONNECT;
                    stateAtMs = now;
                } else if (now - stateAtMs > 15000) {
                    toDisconnect("screen-timeout");
                }
            }
            case TO_CONNECT -> {
                if (inGame) {
                    state = State.WAIT_JOIN;
                    stateAtMs = now;
                } else if (now - stateAtMs > cfg.farmDelaySec * 1000L) {
                    doConnect(client);
                }
            }
            case WAIT_JOIN -> {
                if (inGame) {
                    state = State.WAIT_CAPTCHA;
                    stateAtMs = now;
                } else if (now - stateAtMs > 30000) {
                    state = State.TO_CONNECT;
                    stateAtMs = now;
                }
            }
            case WAIT_CAPTCHA -> {
                if (CaptchaFlow.lastRequestMs() > joinRequestMs) {
                    state = State.WAIT_SOLVE;
                    stateAtMs = now;
                } else if (now - stateAtMs > 25000) {
                    AthAgnelUtils.LOGGER.info("[AthAgnelUtils][farm] no captcha this join, next cycle");
                    nextCycle();
                }
            }
            case WAIT_SOLVE -> {
                if (CaptchaFlow.lastPassedMs() > stateAtMs) {
                    var r = rc();
                    if (r != null) {
                        r.noteSuccess();
                    }
                    nextCycle();
                } else if (now - stateAtMs > cfg.farmSolveWaitSec * 1000L) {
                    AthAgnelUtils.LOGGER.info("[AthAgnelUtils][farm] solve timeout, next cycle");
                    nextCycle();
                }
            }
            default -> {
            }
        }
    }

    private static void nextCycle() {
        cycles++;
        if (cycles >= target) {
            stop("done");
            Msg.title("§aФарм готовий: " + cycles + " капч. Кидай мені папку athagnelutils-captcha!");
            return;
        }
        Msg.title("Фарм: " + cycles + "/" + target);
        toDisconnect("next-cycle");
    }
}
