package ua.atherium.agnelutils.client.flow;

import net.minecraft.client.Minecraft;
import ua.atherium.agnelutils.AthAgnelUtils;
import ua.atherium.agnelutils.client.TickScheduler;
import ua.atherium.agnelutils.client.config.ConfigStore;
import ua.atherium.agnelutils.client.util.Msg;

public final class CaptchaFlow {
    private static volatile int autoAttempts = 0;
    private static volatile String lastSent = "";
    private static volatile String lastShotPath = "";
    private static volatile long lastRequestMs = 0;
    private static volatile long lastPassedMs = 0;

    public static long lastRequestMs() {
        return lastRequestMs;
    }

    public static long lastPassedMs() {
        return lastPassedMs;
    }

    private CaptchaFlow() {
    }

    public static void resetAttempt() {
        autoAttempts = 0;
        lastSent = "";
    }

    public static void noteShot(String path) {
        lastShotPath = path == null ? "" : path;
    }

    public static void noteUserTyped() {
        if (!lastSent.isEmpty()) {
            AthAgnelUtils.LOGGER.info("[AthAgnelUtils][ocr] user typed, confirm invalidated");
        }
        lastSent = "";
    }

    public static void noteUserAnswer(String text) {
        try {
            if (text == null || !text.matches("[0-9]{4,6}")) {
                return;
            }
            long age = System.currentTimeMillis() - lastRequestMs;
            if (age < 0 || age > 120_000) {
                return;
            }
            var dir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                .resolve("athagnelutils-captcha");
            java.nio.file.Files.createDirectories(dir);
            java.util.List<java.nio.file.Path> shots = new java.util.ArrayList<>();
            try (var ds = java.nio.file.Files.newDirectoryStream(dir, "shot-*.png")) {
                for (var p : ds) {
                    shots.add(p);
                }
            }
            shots.sort((a, b) -> {
                try {
                    return java.nio.file.Files.getLastModifiedTime(b).compareTo(java.nio.file.Files.getLastModifiedTime(a));
                } catch (Exception e) {
                    return 0;
                }
            });
            if (shots.isEmpty()) {
                return;
            }
            var src = shots.get(0);
            long shotAge = System.currentTimeMillis() - java.nio.file.Files.getLastModifiedTime(src).toMillis();
            if (shotAge > 120_000) {
                return;
            }
            var dst = dir.resolve("userlabel-" + text + "-" + System.currentTimeMillis() + ".png");
            java.nio.file.Files.copy(src, dst);
            AthAgnelUtils.LOGGER.info("[AthAgnelUtils][ocr] WEAK-LABEL '{}' -> {}", text, dst.getFileName());
            Msg.chat("§7[AthAgnel] Зберіг твою відповідь як мітку (" + text + "), дякую.");
        } catch (Throwable t) {
            AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][ocr] weak-label failed: {}", t.toString());
        }
    }

    public static boolean wasAttempted() {
        return autoAttempts > 0;
    }

    public static boolean canAttempt() {
        return autoAttempts < Math.max(1, ConfigStore.get().captchaMaxAutoAttempts);
    }

    /** Перша спроба — будь-які повні 5 символів; повторна — тільки впевнена або інший текст. */
    public static boolean shouldSend(CaptchaOcr.SolveResult r) {
        if (r == null || r.text().length() != 5 || r.text().contains("?")) {
            return false;
        }
        if (!canAttempt() || r.text().equals(lastSent)) {
            return false;
        }
        if (autoAttempts == 0) {
            return true;
        }
        var cfg = ConfigStore.get();
        return r.confident(cfg.captchaMinScore, cfg.captchaMinAvg);
    }

    public static void sendAnswer(String text, String source) {
        if (!canAttempt() || (text != null && text.equals(lastSent))) {
            return;
        }
        autoAttempts++;
        lastSent = text == null ? "" : text;
        ua.atherium.agnelutils.client.ClientStats.captchaAuto++;
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.connection.sendChat(text);
            AthAgnelUtils.LOGGER.info("[AthAgnelUtils][ocr] auto-sent '{}' via {}", text, source);
            Msg.title("§aВідправив капчу: §f" + text);
        }
    }

    public static void onRequest(String raw) {
        var cfg = ConfigStore.get();
        AthAgnelUtils.LOGGER.info("[AthAgnelUtils][captcha] REQUEST: {}", raw);
        autoAttempts = 0;
        lastSent = "";
        lastRequestMs = System.currentTimeMillis();
        ua.atherium.agnelutils.client.ClientStats.captchas++;
        if (!cfg.captchaAssist) {
            return;
        }
        Msg.title("§cКапча! §fПробую розпізнати…");
        MapCapture.collect();
        TickScheduler.runLater(2500, ScreenshotOcr::captureAndSolve);
        TickScheduler.runLater(4500, CaptchaFlow::tryAutoSolve);
        TickScheduler.runLater(7000, ScreenshotOcr::captureAndSolve);
        TickScheduler.runLater(12000, () -> {
            if (!wasAttempted()) {
                Msg.title("§cКапча! §fВведи код з картинки в чат вручну");
            }
        });
    }

    static void tryAutoSolve() {
        var cfg = ConfigStore.get();
        try {
            var img = MapCapture.lastImage();
            if (img == null) {
                AthAgnelUtils.LOGGER.info("[AthAgnelUtils][ocr] no map image, manual fallback");
                return;
            }
            CaptchaOcr.SolveResult r = CaptchaOcr.solve(img);
            StringBuilder sb = new StringBuilder();
            for (CaptchaOcr.CharResult c : r.chars()) {
                sb.append(c.label()).append('(').append(String.format("%.2f", c.score())).append(") ");
            }
            AthAgnelUtils.LOGGER.info("[AthAgnelUtils][ocr] map={} text='{}' avg={} [{}]",
                MapCapture.lastId(), r.text(), String.format("%.3f", r.avg()), sb.toString().trim());
            if (!cfg.captchaAutoSend || !canAttempt()) {
                Msg.chat("§7[AthAgnel] OCR: " + r.text() + " (автовідправка вимк/вже пробував) — вводь руками");
                return;
            }
            if (shouldSend(r)) {
                TickScheduler.runLater(0, () -> CaptchaFlow.sendAnswer(r.text(), "map"));
            } else {
                Msg.title("§cНе розібрав (" + r.text() + ") — введи руками");
            }
        } catch (Throwable t) {
            AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][ocr] solve failed: {}", t.toString());
        }
    }

    public static void onPassed(String raw) {
        AthAgnelUtils.LOGGER.info("[AthAgnelUtils][captcha] PASSED: {}", raw);
        lastPassedMs = System.currentTimeMillis();
        if (!lastSent.isEmpty() && !lastShotPath.isEmpty()) {
            try {
                var src = java.nio.file.Path.of(lastShotPath);
                var dst = src.resolveSibling("confirmed-" + lastSent + "-" + System.currentTimeMillis() + ".png");
                java.nio.file.Files.copy(src, dst);
                AthAgnelUtils.LOGGER.info("[AthAgnelUtils][ocr] CONFIRMED '{}' -> {}", lastSent, dst.getFileName());
            } catch (Throwable t) {
                AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][ocr] confirm save failed: {}", t.toString());
            }
        }
        lastSent = "";
        Msg.title("§aКапча пройдена, їдемо далі…");
    }
}
