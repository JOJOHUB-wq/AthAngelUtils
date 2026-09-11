package ua.atherium.agnelutils.client.flow;

import net.minecraft.client.Minecraft;
import ua.atherium.agnelutils.AthAgnelUtils;
import ua.atherium.agnelutils.client.TickScheduler;
import ua.atherium.agnelutils.client.config.ConfigStore;
import ua.atherium.agnelutils.client.util.ChatText;
import ua.atherium.agnelutils.client.util.Msg;

public final class AuthFlow {
    private long lastLoginSendMs = 0;
    private long captchaAtMs = 0;
    private boolean captchaOpen = false;
    private int wrongPassCount = 0;

    public void resetSession() {
        wrongPassCount = 0;
        lastLoginSendMs = 0;
        captchaOpen = false;
    }

    public void onGameMessage(String plain) {
        String n = ChatText.norm(plain);
        var cfg = ConfigStore.get();
        if (n.isEmpty()) {
            return;
        }
        if (n.contains("введите капчу")) {
            captchaOpen = true;
            captchaAtMs = System.currentTimeMillis();
            CaptchaFlow.onRequest(plain);
            return;
        }
        if (n.contains("успешно прошли проверку") || n.contains("проверку на бота") || n.contains("капча введена верно")) {
            captchaOpen = false;
            wrongPassCount = 0;
            CaptchaFlow.onPassed(plain);
            if (cfg.autoHub) {
                HubFlow.requestHub("captcha-passed");
            }
            return;
        }
        if (n.contains("неверный пароль") || n.contains("неправильный пароль") || n.contains("incorrect password")) {
            wrongPassCount++;
            AthAgnelUtils.LOGGER.info("[AthAgnelUtils][auth] wrong password ({}/{})", wrongPassCount, cfg.loginMaxRetries + 1);
            if (cfg.autoLogin && wrongPassCount <= cfg.loginMaxRetries) {
                Msg.title("Пароль не зайшов, пробую ще раз через " + (cfg.loginRetryDelayMs / 1000.0) + "с…");
                TickScheduler.runLater(cfg.loginRetryDelayMs, () -> sendLogin(cfg.password));
            } else if (cfg.autoLogin) {
                Msg.title("§cПароль не підходить! §fПеревір /agnel pass <пароль>");
            }
            return;
        }
        if (!cfg.autoLogin) {
            return;
        }
        boolean needLogin = n.contains("войдите") || n.contains("авторизуйтесь") || n.contains("введите пароль")
            || n.contains("/l ") || n.contains("/login") || n.contains("зайдите")
            || (n.contains("зарегистрируйтесь") && n.contains("/l"));
        boolean needReg = n.contains("зарегистрируйтесь") && n.contains("/reg") && !needLogin;
        if (needLogin && !needReg) {
            long now = System.currentTimeMillis();
            if (now - lastLoginSendMs < 4000) {
                return;
            }
            lastLoginSendMs = now;
            String pass = cfg.password;
            AthAgnelUtils.LOGGER.info("[AthAgnelUtils][auth] login trigger");
            Msg.title("Автологін через " + (cfg.loginDelayMs / 1000.0) + "с…");
            TickScheduler.runLater(cfg.loginDelayMs, () -> sendLogin(pass));
        } else if (needReg) {
            long now = System.currentTimeMillis();
            if (now - lastLoginSendMs < 4000) {
                return;
            }
            lastLoginSendMs = now;
            String pass = cfg.farmPass.isEmpty() ? cfg.password : cfg.farmPass;
            AthAgnelUtils.LOGGER.info("[AthAgnelUtils][auth] register trigger");
            Msg.title("Авто-реєстрація через " + (cfg.loginDelayMs / 1000.0) + "с…");
            TickScheduler.runLater(cfg.loginDelayMs, () -> sendReg(pass));
        }
    }

    private static void sendLogin(String pass) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.player.connection == null) {
            return;
        }
        try {
            client.player.connection.sendCommand("l " + pass);
            AthAgnelUtils.LOGGER.info("[AthAgnelUtils][auth] sent /l ***");
        } catch (Throwable t) {
            AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][auth] sendCommand failed, fallback to chat: {}", t.toString());
            try {
                client.player.connection.sendChat("/l " + pass);
            } catch (Throwable t2) {
                AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][auth] fallback failed: {}", t2.toString());
            }
        }
    }

    private static void sendReg(String pass) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.player.connection == null) {
            return;
        }
        try {
            client.player.connection.sendCommand("reg " + pass + " " + pass);
            AthAgnelUtils.LOGGER.info("[AthAgnelUtils][auth] sent /reg ***");
        } catch (Throwable t) {
            AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][auth] reg failed: {}", t.toString());
        }
    }

    public boolean isCaptchaOpen() {
        return captchaOpen && System.currentTimeMillis() - captchaAtMs < 120_000;
    }
}
