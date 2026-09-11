package ua.atherium.agnelutils.client.flow;

import java.awt.image.BufferedImage;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;
import ua.atherium.agnelutils.AthAgnelUtils;

public final class CaptchaTest {
    private CaptchaTest() {
    }

    public static String run() {
        try {
            CaptchaOcr.reload();
            Path dir = FabricLoader.getInstance().getConfigDir().resolve("athagnelutils-captcha");
            List<Path> pngs = new ArrayList<>();
            if (Files.isDirectory(dir)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "map-*.png")) {
                    for (Path p : ds) {
                        pngs.add(p);
                    }
                }
            }
            if (pngs.isEmpty()) {
                return "[AthAgnel] Нема дампів карт. Дочекайся капчі в грі (шаблонів: " + CaptchaOcr.templateCount() + ")";
            }
            pngs.sort((a, b) -> {
                try {
                    return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                } catch (Exception e) {
                    return 0;
                }
            });
            Path newest = pngs.get(0);
            BufferedImage img = ImageIO.read(newest.toFile());
            CaptchaOcr.SolveResult r = CaptchaOcr.solve(img);
            StringBuilder sb = new StringBuilder();
            sb.append("[AthAgnel] ").append(newest.getFileName()).append(" -> '").append(r.text()).append("'");
            for (CaptchaOcr.CharResult c : r.chars()) {
                sb.append(' ').append(c.label()).append(':').append(String.format("%.2f", c.score()));
            }
            sb.append(" | conf=").append(r.confident(0.6, 0.7)).append(" | tpl=").append(CaptchaOcr.templateCount());
            AthAgnelUtils.LOGGER.info("[AthAgnelUtils][ocr-test] {}", sb);
            return sb.toString();
        } catch (Throwable t) {
            return "[AthAgnel] captcha test failed: " + t;
        }
    }
}
