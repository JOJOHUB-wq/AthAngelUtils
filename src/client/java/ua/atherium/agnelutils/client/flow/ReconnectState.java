package ua.atherium.agnelutils.client.flow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import ua.atherium.agnelutils.AthAgnelUtils;
import ua.atherium.agnelutils.client.config.ConfigStore;
import ua.atherium.agnelutils.client.util.ChatText;
import ua.atherium.agnelutils.client.util.Msg;

public final class ReconnectState {
    private String lastName = "";
    private String lastIp = "";
    private long disconnectAtMs = 0;
    private int attempts = 0;
    private int fails = 0;
    private boolean manualDisconnect = false;
    private boolean stopped = false;
    private long stoppedAtMs = 0;
    private boolean suppressed = false;
    private String lastReason = "";
    private boolean reasonLogged = false;

    public synchronized void onJoin(ServerData server) {
        if (server != null) {
            this.lastName = server.name == null ? "" : server.name;
            this.lastIp = server.ip == null ? "" : server.ip;
        }
        long now = System.currentTimeMillis();
        if (disconnectAtMs == 0 || now - disconnectAtMs > 90_000) {
            this.fails = 0;
        }
        // Успішний вхід = кінець серії: скидаємо лічильник спроб, щоб наступний кік
        // стартував з базової затримки, а не з роздутого бек-офу минулої серії.
        this.attempts = 0;
        this.disconnectAtMs = 0;
        this.manualDisconnect = false;
        this.reasonLogged = false;
    }

    public synchronized void onDisconnect(boolean manual) {
        this.disconnectAtMs = System.currentTimeMillis();
        this.manualDisconnect = manual;
        this.reasonLogged = false;
    }

    public synchronized void noteReason(String reason) {
        String n = ChatText.norm(reason);
        if (n.equals(lastReason)) {
            return;
        }
        lastReason = n;
        reasonLogged = false;
    }

    public synchronized void noteSuccess() {
        fails = 0;
    }

    public synchronized void setSuppressed(boolean v) {
        suppressed = v;
    }

    public synchronized ServerData lastServerCopy() {
        if (lastIp.isEmpty()) {
            return null;
        }
        return new ServerData(lastName, lastIp, ServerData.Type.OTHER);
    }

    public synchronized void reset() {
        attempts = 0;
        fails = 0;
        stopped = false;
        stoppedAtMs = 0;
        disconnectAtMs = System.currentTimeMillis();
        reasonLogged = false;
    }

    private boolean banned() {
        return ReconnectPolicy.isBanReason(lastReason);
    }

    public synchronized long delayMs() {
        var cfg = ConfigStore.get();
        return ReconnectPolicy.delayMs(attempts, cfg.reconnectBaseDelaySec, cfg.reconnectMaxDelaySec);
    }

    public synchronized boolean shouldReconnect(Minecraft client) {
        var cfg = ConfigStore.get();
        if (!cfg.autoReconnect) {
            return false;
        }
        if (manualDisconnect || suppressed) {
            return false;
        }
        if (stopped) {
            if (System.currentTimeMillis() - stoppedAtMs > 30 * 60_000L) {
                stopped = false;
                fails = 0;
                attempts = 0;
                disconnectAtMs = System.currentTimeMillis();
                AthAgnelUtils.LOGGER.info("[AthAgnelUtils][reconnect] slow retry after stop");
                Msg.chat("§7[AthAgnel] Пробую перепідключитись знову (після паузи)…");
            } else {
                return false;
            }
        }
        if (client == null) {
            return false;
        }
        if (lastIp.isEmpty()) {
            return false;
        }
        if (cfg.onlyAngelGrief && !lastIp.toLowerCase().contains(cfg.serverHostContains.toLowerCase())) {
            return false;
        }
        if (cfg.maxReconnectAttempts > 0 && attempts >= cfg.maxReconnectAttempts) {
            return false;
        }
        if (!reasonLogged && !lastReason.isEmpty()) {
            reasonLogged = true;
            AthAgnelUtils.LOGGER.info("[AthAgnelUtils][reconnect] kick reason: {}", lastReason);
            Msg.chat("§7[AthAgnel] Причина кіку: " + lastReason);
            if (banned()) {
                stopped = true;
                stoppedAtMs = System.currentTimeMillis();
                Msg.title("§cСхоже БАН — реконект зупинено. Перевір вручну!");
                AthAgnelUtils.LOGGER.info("[AthAgnelUtils][reconnect] banned, stopped");
                return false;
            }
        }
        if (ReconnectPolicy.shouldPause(fails, cfg.reconnectMaxFails)) {
            stopped = true;
            stoppedAtMs = System.currentTimeMillis();
            Msg.title("§cСервер не приймає (" + fails + " провалів) — пауза 30 хв. /agnel recon щоб зараз");
            AthAgnelUtils.LOGGER.info("[AthAgnelUtils][reconnect] too many fails, paused 30m");
            return false;
        }
        long waited = System.currentTimeMillis() - disconnectAtMs;
        return disconnectAtMs > 0 && waited >= delayMs();
    }

    public synchronized ServerData consumeServer() {
        attempts++;
        fails++;
        disconnectAtMs = System.currentTimeMillis();
        return new ServerData(lastName, lastIp, ServerData.Type.OTHER);
    }

    public synchronized int attempts() {
        return attempts;
    }

    public static void log(String msg) {
        AthAgnelUtils.LOGGER.info("[AthAgnelUtils][reconnect] {}", msg);
    }
}
