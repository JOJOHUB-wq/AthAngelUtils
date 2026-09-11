package ua.atherium.agnelutils.client.flow;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import ua.atherium.agnelutils.AthAgnelUtils;
import ua.atherium.agnelutils.client.TickScheduler;
import ua.atherium.agnelutils.client.config.ConfigStore;
import ua.atherium.agnelutils.client.util.Msg;

public final class MapCapture {
    private static final Set<Integer> DUMPED = new HashSet<>();
    private static volatile java.awt.image.BufferedImage lastImage;
    private static volatile int lastId = -1;

    private MapCapture() {
    }

    public static void resetSession() {
        DUMPED.clear();
        lastImage = null;
        lastId = -1;
    }

    public static java.awt.image.BufferedImage lastImage() {
        return lastImage;
    }

    public static int lastId() {
        return lastId;
    }

    public static void collect() {
        if (!ConfigStore.get().captchaDumpMaps) {
            return;
        }
        TickScheduler.runLater(500, () -> tryDump("t+0.5s"));
        TickScheduler.runLater(1500, () -> tryDump("t+1.5s"));
        TickScheduler.runLater(3000, () -> tryDump("t+3s"));
        TickScheduler.runLater(5000, () -> tryDump("t+5s"));
        TickScheduler.runLater(8000, () -> tryDump("t+8s"));
    }

    private static void tryDump(String tag) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.level == null) {
                return;
            }
            if (tryDumpFrames(client, tag)) {
                return;
            }
            ItemStack hand = client.player.getMainHandItem();
            ItemStack off = client.player.getOffhandItem();
            if (tryDumpStack(client, hand, tag, "mainhand")) {
                return;
            }
            if (tryDumpStack(client, off, tag, "offhand")) {
                return;
            }
            for (int i = 0; i < 36; i++) {
                ItemStack st = client.player.getInventory().getItem(i);
                if (tryDumpStack(client, st, tag, "inv" + i)) {
                    return;
                }
            }
            AthAgnelUtils.LOGGER.info("[AthAgnelUtils][captcha] no captcha map found ({})", tag);
        } catch (Throwable t) {
            AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][captcha] map scan failed: {}", t.toString());
        }
    }

    private static boolean tryDumpFrames(Minecraft client, String tag) {
        try {
            if (client.crosshairPickEntity instanceof net.minecraft.world.entity.decoration.ItemFrame cross) {
                if (tryDumpStack(client, cross.getItem(), tag, "crosshair-frame")) {
                    return true;
                }
            }
            var box = client.player.getBoundingBox().inflate(32);
            var frames = client.level.getEntities(client.player, box,
                e -> e instanceof net.minecraft.world.entity.decoration.ItemFrame);
            StringBuilder dbg = new StringBuilder();
            dbg.append(frames.size()).append(" frames: ");
            frames.sort((a, b) -> Double.compare(
                a.distanceToSqr(client.player), b.distanceToSqr(client.player)));
            int n = 0;
            for (var e : frames) {
                var frame = (net.minecraft.world.entity.decoration.ItemFrame) e;
                var st = frame.getItem();
                if (n < 6) {
                    dbg.append(String.format("[d=%.1f %s%s] ", Math.sqrt(e.distanceToSqr(client.player)),
                        st.isEmpty() ? "empty" : String.valueOf(st.getItem()),
                        st.isEmpty() ? "" : (" foil=" + st.hasFoil())));
                }
                n++;
                if (tryDumpStack(client, st, tag,
                    "frame@" + frame.blockPosition().toShortString())) {
                    return true;
                }
            }
            AthAgnelUtils.LOGGER.info("[AthAgnelUtils][captcha] {} ({})", dbg, tag);
            return false;
        } catch (Throwable t) {
            AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][captcha] frame scan failed: {}", t.toString());
            return false;
        }
    }

    private static boolean tryDumpStack(Minecraft client, ItemStack st, String tag, String where) {
        try {
            if (st.isEmpty() || st.getItem() != Items.FILLED_MAP) {
                return false;
            }
            MapId mapId = st.get(DataComponents.MAP_ID);
            if (mapId == null) {
                return false;
            }
            int id = mapId.id();
            if (!DUMPED.add(id)) {
                return true;
            }
            MapItemSavedData data = client.level.getMapData(mapId);
            if (data == null || data.colors == null || data.colors.length < 16384) {
                AthAgnelUtils.LOGGER.info("[AthAgnelUtils][captcha] map id={} not loaded yet ({})", id, tag);
                DUMPED.remove(id);
                return false;
            }
            Path dir = FabricLoader.getInstance().getConfigDir().resolve("athagnelutils-captcha");
            Files.createDirectories(dir);
            String base = "map-" + id + "-" + System.currentTimeMillis();
            Files.write(dir.resolve(base + ".raw"), data.colors);
            BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    int packed = data.colors[x + y * 128] & 0xFF;
                    img.setRGB(x, y, MapColor.getColorFromPackedId(packed));
                }
            }
            ImageIO.write(img, "png", dir.resolve(base + ".png").toFile());
            lastImage = img;
            lastId = id;
            AthAgnelUtils.LOGGER.info("[AthAgnelUtils][captcha] dumped map id={} from {} ({})", id, where, tag);
            Msg.title("Капчу збережено як зразок (map-" + id + "), вводь руками");
            return true;
        } catch (Throwable t) {
            AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][captcha] dump failed: {}", t.toString());
            return false;
        }
    }
}
