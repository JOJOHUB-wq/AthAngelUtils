package ua.atherium.agnelutils.client.flow;

import java.awt.image.BufferedImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import ua.atherium.agnelutils.AthAgnelUtils;
import ua.atherium.agnelutils.client.TickScheduler;

public final class ScreenshotOcr {
    private ScreenshotOcr() {
    }

    public static void captureAndSolve() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.level == null) {
                return;
            }
            var target = client.gameRenderer.mainRenderTarget();
            if (target == null) {
                return;
            }
            Screenshot.takeScreenshot(target, img -> {
                try {
                    solveImage(img);
                } catch (Throwable t) {
                    AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][shot-ocr] failed: {}", t.toString());
                } finally {
                    try {
                        img.close();
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable t) {
            AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][shot-ocr] capture failed: {}", t.toString());
        }
    }

    static void solveImage(com.mojang.blaze3d.platform.NativeImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        if (w < 200 || h < 200) {
            return;
        }
        int x0 = w / 4;
        int x1 = (w * 3) / 4;
        int y0 = h / 8;
        int y1 = (h * 7) / 10;
        int cw = x1 - x0;
        int ch = y1 - y0;
        int scale = Math.max(1, cw / 800);
        int dw = cw / scale;
        int dh = ch / scale;
        int[] px = img.getPixelsABGR();
        int stride = w;
        BufferedImage crop = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < dh; y++) {
            for (int x = 0; x < dw; x++) {
                int sx = x0 + x * scale;
                int sy = y0 + y * scale;
                int v = px[sy * stride + sx];
                int r = v & 0xFF;
                int g = (v >> 8) & 0xFF;
                int b = (v >> 16) & 0xFF;
                crop.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        CaptchaOcr.SolveResult r = CaptchaOcr.solve(crop);
        try {
            var dir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                .resolve("athagnelutils-captcha");
            java.nio.file.Files.createDirectories(dir);
            String base = "shot-" + System.currentTimeMillis();
            javax.imageio.ImageIO.write(crop, "png", dir.resolve(base + ".png").toFile());
            CaptchaFlow.noteShot(dir.resolve(base + ".png").toString());
        } catch (Throwable t) {
            AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][shot-ocr] save failed: {}", t.toString());
        }
        StringBuilder sb = new StringBuilder();
        for (CaptchaOcr.CharResult c : r.chars()) {
            sb.append(c.label()).append('(').append(String.format("%.2f", c.score())).append(") ");
        }
        AthAgnelUtils.LOGGER.info("[AthAgnelUtils][shot-ocr] {}x{} text='{}' avg={} [{}]",
            dw, dh, r.text(), String.format("%.3f", r.avg()), sb.toString().trim());
        var cfg = ua.atherium.agnelutils.client.config.ConfigStore.get();
        if (!cfg.captchaAutoSend || !CaptchaFlow.canAttempt()) {
            return;
        }
        if (CaptchaFlow.shouldSend(r)) {
            TickScheduler.runLater(0, () -> CaptchaFlow.sendAnswer(r.text(), "shot"));
        }
    }
}
