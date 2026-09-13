package ua.atherium.agnelutils.client.flow;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;
import ua.atherium.agnelutils.AthAgnelUtils;

public final class CaptchaOcr {
    public static final int TW = 24;
    public static final int TH = 32;
    private static final double[] ANGLES = {-12, -8, -4, 0, 4, 8, 12};

    public record CharResult(char label, double score) {
    }

    public record SolveResult(String text, List<CharResult> chars, double avg) {
        public boolean confident(double minEach, double minAvg) {
            if (text.length() != 5) {
                return false;
            }
            if (avg < minAvg) {
                return false;
            }
            for (CharResult c : chars) {
                if (c.label() == '?' || c.score() < minEach) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final Map<Character, java.util.List<float[][]>> TEMPLATES = new HashMap<>();

    private CaptchaOcr() {
    }

    public static synchronized void reload() {
        TEMPLATES.clear();
        loadBundled();
        Path dir = FabricLoader.getInstance().getConfigDir()
            .resolve("athagnelutils-captcha").resolve("templates");
        try {
            Files.createDirectories(dir);
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.png")) {
                for (Path p : ds) {
                    String name = p.getFileName().toString();
                    if (name.length() < 5) {
                        continue;
                    }
                    char label = Character.toUpperCase(name.charAt(0));
                    BufferedImage img = ImageIO.read(p.toFile());
                    if (img == null) {
                        continue;
                    }
                    TEMPLATES.computeIfAbsent(label, k -> new ArrayList<>()).add(normalize(img));
                }
            }
        } catch (IOException e) {
            AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][ocr] template load failed: {}", e.toString());
        }
        AthAgnelUtils.LOGGER.info("[AthAgnelUtils][ocr] templates: {} ({})", templateCount(), TEMPLATES.keySet());
        MlpOcr.load();
    }

    private static void loadBundled() {
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        var cl = CaptchaOcr.class.getClassLoader();
        for (int i = 0; i < chars.length(); i++) {
            char label = chars.charAt(i);
            loadTemplateResource(cl, label + ".png", label);
            // 20 слотів і без break — додаткові шаблони (новий шрифт) лежать на індексах 10+,
            // тож пропуски в нумерації більше не зупиняють завантаження.
            for (int v = 0; v < 20; v++) {
                loadTemplateResource(cl, label + "_" + v + ".png", label);
            }
        }
    }

    private static boolean loadTemplateResource(ClassLoader cl, String name, char label) {
        try (var in = cl.getResourceAsStream("assets/athagnelutils/captcha/" + name)) {
            if (in == null) {
                return false;
            }
            BufferedImage img = ImageIO.read(in);
            if (img == null) {
                return false;
            }
            TEMPLATES.computeIfAbsent(label, k -> new ArrayList<>()).add(normalize(img));
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    public static synchronized int templateCount() {
        int n = 0;
        for (java.util.List<float[][]> l : TEMPLATES.values()) {
            n += l.size();
        }
        return n;
    }

    public static SolveResult solve(BufferedImage map) {
        List<CharResult> out = new ArrayList<>();
        if (map == null) {
            return new SolveResult("", out, 0);
        }
        int w = map.getWidth();
        int h = map.getHeight();
        int[] px = new int[w * h];
        map.getRGB(0, 0, w, h, px, 0, w);
        boolean[] white = new boolean[w * h];
        boolean[] solid = new boolean[w * h];
        for (int i = 0; i < px.length; i++) {
            int r = (px[i] >> 16) & 0xFF;
            int g = (px[i] >> 8) & 0xFF;
            int b = px[i] & 0xFF;
            white[i] = r >= 180 && g >= 180 && b >= 180;
            int mx = Math.max(r, Math.max(g, b));
            int mn = Math.min(r, Math.min(g, b));
            boolean skyish = b > r + 30 && b > g + 10 && mx > 170;
            solid[i] = !white[i] && (mx - mn) >= 40 && !skyish;
        }
        List<int[]> boxes = tileCenters(white, solid, w, h, px);
        if (boxes.size() != 5) {
            boxes = glyphsOnTiles(white, solid, w, h);
        }
        if (boxes.size() != 5) {
            boxes = stripBoxes(white, solid, w, h);
        }
        if (boxes.size() != 5) {
            boxes = glyphGroupsInTiles(white, solid, w, h);
        }
        if (boxes.size() != 5) {
            boxes = tileBoxes(solid, w, h);
        }
        boxes.sort((a, b) -> Integer.compare(a[0], b[0]));
        if (boxes.size() > 5) {
            boxes = boxes.subList(0, 5);
        }
        StringBuilder text = new StringBuilder();
        double sum = 0;
        synchronized (CaptchaOcr.class) {
            for (int[] box : boxes) {
                float[][] glyph = box[4] == 0 ? extractStripGlyph(white, tileBlobMask(solid, w, h), w, h, box)
                    : box[4] == -1 ? extractCenterGlyph(white, w, h, box)
                    : extractGlyph(white, w, h, box);
                CharResult r = match(glyph);
                out.add(r);
                text.append(r.label());
                sum += r.score();
            }
        }
        while (text.length() < 5) {
            text.append('?');
            out.add(new CharResult('?', 0));
        }
        double avg = out.isEmpty() ? 0 : sum / out.size();
        return new SolveResult(text.toString(), out, avg);
    }

    private static List<int[]> glyphBoxesDilated(boolean[] white, int w, int h) {
        boolean[] dilated = dilate(white, w, h, 2);
        List<int[]> comps = components(dilated, w, h);
        if (comps.isEmpty()) {
            return new ArrayList<>();
        }
        comps.sort((a, b) -> Integer.compare(b[4], a[4]));
        List<int[]> rest = new ArrayList<>();
        double total = (double) w * h;
        for (int i = 1; i < comps.size(); i++) {
            int[] c = comps.get(i);
            if (c[4] >= total * 0.0006 && c[4] <= total * 0.06) {
                rest.add(c);
            }
        }
        rest.sort((a, b) -> Integer.compare(b[4], a[4]));
        List<int[]> top = new ArrayList<>(rest.subList(0, Math.min(5, rest.size())));
        top.sort((a, b) -> Integer.compare(a[0], b[0]));
        return top;
    }

    static boolean[] dilate(boolean[] src, int w, int h, int iters) {        boolean[] cur = src.clone();
        boolean[] tmp = new boolean[w * h];
        for (int it = 0; it < iters; it++) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    boolean any = false;
                    for (int dy = -1; dy <= 1 && !any; dy++) {
                        for (int dx = -1; dx <= 1 && !any; dx++) {
                            int nx = x + dx;
                            int ny = y + dy;
                            if (nx >= 0 && ny >= 0 && nx < w && ny < h && cur[ny * w + nx]) {
                                any = true;
                            }
                        }
                    }
                    tmp[y * w + x] = any;
                }
            }
            boolean[] t = cur;
            cur = tmp;
            tmp = t;
        }
        return cur;
    }

    static boolean[] floodOutside(boolean[] blocked, int w, int h) {
        boolean[] outside = new boolean[w * h];
        int[] stack = new int[w * h];
        int sp = 0;
        for (int x = 0; x < w; x++) {
            if (!blocked[x]) {
                outside[x] = true;
                stack[sp++] = x;
            }
            int b = (h - 1) * w + x;
            if (!blocked[b] && !outside[b]) {
                outside[b] = true;
                stack[sp++] = b;
            }
        }
        for (int y = 0; y < h; y++) {
            int l = y * w;
            if (!blocked[l] && !outside[l]) {
                outside[l] = true;
                stack[sp++] = l;
            }
            int r = y * w + w - 1;
            if (!blocked[r] && !outside[r]) {
                outside[r] = true;
                stack[sp++] = r;
            }
        }
        while (sp > 0) {
            int cur = stack[--sp];
            int cx = cur % w;
            int cy = cur / w;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) {
                        continue;
                    }
                    int nx = cx + dx;
                    int ny = cy + dy;
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                        continue;
                    }
                    int ni = ny * w + nx;
                    if (!blocked[ni] && !outside[ni]) {
                        outside[ni] = true;
                        stack[sp++] = ni;
                    }
                }
            }
        }
        return outside;
    }

    private static List<int[]> tileCenters(boolean[] white, boolean[] solid, int w, int h, int[] px) {
        boolean[] opened = opening(solid, w, h, 2);
        List<int[]> tiles = new ArrayList<>();
        for (int[] c : components(opened, w, h)) {
            if (c[4] < 400) {
                continue;
            }
            int bw = c[2] - c[0] + 1;
            int bh = c[3] - c[1] + 1;
            double fill = (double) c[4] / ((double) bw * bh);
            if (fill >= 0.45) {
                tiles.add(c);
                continue;
            }
            tiles.addAll(splitByColor(px, opened, w, h, c));
        }
        if (tiles.isEmpty()) {
            return new ArrayList<>();
        }
        List<Integer> widths = new ArrayList<>();
        for (int[] t : tiles) {
            widths.add(t[2] - t[0] + 1);
        }
        widths.sort(Integer::compareTo);
        int med = widths.get(widths.size() / 2);
        List<int[]> centers = new ArrayList<>();
        for (int[] t : tiles) {
            int bw = t[2] - t[0] + 1;
            int n = Math.max(1, (int) Math.round((double) bw / med));
            n = Math.min(n, 3);
            for (int k = 0; k < n; k++) {
                int cx = t[0] + ((k * 2 + 1) * bw) / (n * 2);
                int cy = (t[1] + t[3]) / 2;
                int r = Math.min(bw, t[3] - t[1] + 1) * 3 / 8;
                centers.add(new int[]{cx - r, cy - r, cx + r, cy + r, -1});
            }
        }
        if (centers.size() != 5) {
            return new ArrayList<>();
        }
        centers.sort((a, b) -> Integer.compare(a[0], b[0]));
        return centers;
    }

    private static List<int[]> splitByColor(int[] px, boolean[] mesh, int w, int h, int[] blob) {
        List<int[]> out = new ArrayList<>();
        int bw = blob[2] - blob[0] + 1;
        int bh = blob[3] - blob[1] + 1;
        boolean[] sub = new boolean[bw * bh];
        for (int y = 0; y < bh; y++) {
            System.arraycopy(mesh, (blob[1] + y) * w + blob[0], sub, y * bw, bw);
        }
        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
        for (int y = 0; y < bh; y++) {
            for (int x = 0; x < bw; x++) {
                if (!sub[y * bw + x]) {
                    continue;
                }
                int v = px[(blob[1] + y) * w + blob[0] + x];
                int key = ((((v >> 16) & 0xFF) / 48) << 16) | ((((v >> 8) & 0xFF) / 48) << 8) | ((v & 0xFF) / 48);
                counts.put(key, counts.getOrDefault(key, 0) + 1);
            }
        }
        int total = 0;
        for (int n : counts.values()) {
            total += n;
        }
        List<int[]> order = new ArrayList<>();
        for (var e : counts.entrySet()) {
            order.add(new int[]{e.getKey(), e.getValue()});
        }
        order.sort((a, b) -> Integer.compare(b[1], a[1]));
        int taken = 0;
        for (int[] e : order) {
            if (taken >= 6 || e[1] < total * 0.06) {
                break;
            }
            taken++;
            int qr = ((e[0] >> 16) & 0xFF) * 48 + 24;
            int qg = ((e[0] >> 8) & 0xFF) * 48 + 24;
            int qb = (e[0] & 0xFF) * 48 + 24;
            boolean[] cmask = new boolean[bw * bh];
            for (int y = 0; y < bh; y++) {
                for (int x = 0; x < bw; x++) {
                    if (!sub[y * bw + x]) {
                        continue;
                    }
                    int v = px[(blob[1] + y) * w + blob[0] + x];
                    int dr = Math.abs(((v >> 16) & 0xFF) - qr);
                    int dg = Math.abs(((v >> 8) & 0xFF) - qg);
                    int db = Math.abs((v & 0xFF) - qb);
                    if (dr + dg + db < 90) {
                        cmask[y * bw + x] = true;
                    }
                }
            }
            for (int[] c : components(cmask, bw, bh)) {
                if (c[4] >= 800) {
                    out.add(new int[]{blob[0] + c[0], blob[1] + c[1], blob[0] + c[2], blob[1] + c[3], c[4]});
                }
            }
        }
        return out;
    }

    private static float[][] extractCenterGlyph(boolean[] white, int w, int h, int[] box) {
        int x0 = Math.max(0, box[0]);
        int y0 = Math.max(0, box[1]);
        int x1 = Math.min(w - 1, box[2]);
        int y1 = Math.min(h - 1, box[3]);
        int bw = Math.max(1, x1 - x0 + 1);
        int bh = Math.max(1, y1 - y0 + 1);
        boolean[] inner = new boolean[bw * bh];
        for (int y = 0; y < bh; y++) {
            System.arraycopy(white, (y0 + y) * w + x0, inner, y * bw, bw);
        }
        int gx0 = bw;
        int gy0 = bh;
        int gx1 = -1;
        int gy1 = -1;
        for (int y = 0; y < bh; y++) {
            for (int x = 0; x < bw; x++) {
                if (inner[y * bw + x]) {
                    if (x < gx0) {
                        gx0 = x;
                    }
                    if (y < gy0) {
                        gy0 = y;
                    }
                    if (x > gx1) {
                        gx1 = x;
                    }
                    if (y > gy1) {
                        gy1 = y;
                    }
                }
            }
        }
        if (gx1 < gx0) {
            return new float[TH][TW];
        }
        return letterboxMask(inner, bw, bh, gx0, gy0, gx1, gy1);
    }

    private static List<int[]> glyphsOnTiles(boolean[] white, boolean[] solid, int w, int h) {
        boolean[] inner = erode(tileBlobMask(solid, w, h), w, h, 2);
        boolean[] cand = new boolean[w * h];
        for (int i = 0; i < cand.length; i++) {
            cand[i] = white[i] && inner[i];
        }
        List<int[]> islands = new ArrayList<>();
        for (int[] c : components(cand, w, h)) {
            if (c[4] >= 15) {
                islands.add(new int[]{c[0], c[1], c[2], c[3], c[4]});
            }
        }
        if (islands.size() < 5) {
            return new ArrayList<>();
        }
        islands.sort((a, b) -> Integer.compare(a[0], b[0]));
        while (islands.size() > 5) {
            int bestIdx = 0;
            int bestGap = Integer.MAX_VALUE;
            for (int i = 0; i < islands.size() - 1; i++) {
                int gap = islands.get(i + 1)[0] - islands.get(i)[2];
                if (gap < bestGap) {
                    bestGap = gap;
                    bestIdx = i;
                }
            }
            int[] a = islands.get(bestIdx);
            int[] b = islands.remove(bestIdx + 1);
            islands.set(bestIdx, new int[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]),
                Math.max(a[2], b[2]), Math.max(a[3], b[3]), a[4] + b[4]});
        }
        return islands;
    }

    static boolean[] erode(boolean[] src, int w, int h, int iters) {
        boolean[] cur = src.clone();
        boolean[] tmp = new boolean[w * h];
        for (int it = 0; it < iters; it++) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    boolean all = true;
                    for (int dy = -1; dy <= 1 && all; dy++) {
                        for (int dx = -1; dx <= 1 && all; dx++) {
                            int nx = x + dx;
                            int ny = y + dy;
                            if (nx < 0 || ny < 0 || nx >= w || ny >= h || !cur[ny * w + nx]) {
                                all = false;
                            }
                        }
                    }
                    tmp[y * w + x] = all;
                }
            }
            boolean[] t = cur;
            cur = tmp;
            tmp = t;
        }
        return cur;
    }

    private static List<int[]> stripBoxes(boolean[] white, boolean[] solid, int w, int h) {
        int sx0 = w;
        int sy0 = h;
        int sx1 = -1;
        int sy1 = -1;
        int n = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (solid[y * w + x]) {
                    n++;
                    if (x < sx0) {
                        sx0 = x;
                    }
                    if (y < sy0) {
                        sy0 = y;
                    }
                    if (x > sx1) {
                        sx1 = x;
                    }
                    if (y > sy1) {
                        sy1 = y;
                    }
                }
            }
        }
        List<int[]> out = new ArrayList<>();
        if (n < 500 || sx1 <= sx0) {
            return out;
        }
        int bw = sx1 - sx0 + 1;
        for (int i = 0; i < 5; i++) {
            int zx0 = sx0 + (i * bw) / 5;
            int zx1 = sx0 + ((i + 1) * bw) / 5;
            int pad = Math.max(2, bw / 25);
            out.add(new int[]{Math.max(0, zx0 - pad), Math.max(0, sy0), Math.min(w - 1, zx1 + pad), Math.min(h - 1, sy1), 0});
        }
        return out;
    }

    private static List<int[]> glyphGroupsInTiles(boolean[] white, boolean[] solid, int w, int h) {
        boolean[] tiles = opening(solid, w, h, 2);
        List<int[]> comps = components(tiles, w, h);
        List<int[]> islands = new ArrayList<>();
        for (int[] t : comps) {
            if (t[4] < 400) {
                continue;
            }
            int x0 = Math.max(0, t[0] + 2);
            int y0 = Math.max(0, t[1] + 2);
            int x1 = Math.min(w - 1, t[2] - 2);
            int y1 = Math.min(h - 1, t[3] - 2);
            int bw = x1 - x0 + 1;
            int bh = y1 - y0 + 1;
            if (bw < 6 || bh < 6) {
                continue;
            }
            boolean[] inner = new boolean[bw * bh];
            for (int y = 0; y < bh; y++) {
                System.arraycopy(white, (y0 + y) * w + x0, inner, y * bw, bw);
            }
            double tileArea = (double) (t[2] - t[0] + 1) * (t[3] - t[1] + 1);
            for (int[] c : components(inner, bw, bh)) {
                if (c[4] >= tileArea * 0.008) {
                    islands.add(new int[]{x0 + c[0], y0 + c[1], x0 + c[2], y0 + c[3], c[4]});
                }
            }
        }
        if (islands.size() < 5) {
            return new ArrayList<>();
        }
        islands.sort((a, b) -> Integer.compare(a[0], b[0]));
        while (islands.size() > 5) {
            int bestIdx = 0;
            int bestGap = Integer.MAX_VALUE;
            for (int i = 0; i < islands.size() - 1; i++) {
                int gap = islands.get(i + 1)[0] - islands.get(i)[2];
                if (gap < bestGap) {
                    bestGap = gap;
                    bestIdx = i;
                }
            }
            int[] a = islands.get(bestIdx);
            int[] b = islands.remove(bestIdx + 1);
            islands.set(bestIdx, new int[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]),
                Math.max(a[2], b[2]), Math.max(a[3], b[3]), a[4] + b[4]});
        }
        return islands;
    }

    private static List<int[]> glyphBoxesFromWhite(boolean[] white, int w, int h) {
        List<int[]> comps = components(white, w, h);
        if (comps.isEmpty()) {
            return new ArrayList<>();
        }
        comps.sort((a, b) -> Integer.compare(b[4], a[4]));
        List<int[]> rest = new ArrayList<>();
        double total = (double) w * h;
        for (int i = 1; i < comps.size(); i++) {
            int[] c = comps.get(i);
            if (c[4] >= total * 0.0006 && c[4] <= total * 0.06) {
                rest.add(c);
            }
        }
        List<int[]> merged = mergeTouching(rest, 4);
        merged.sort((a, b) -> Integer.compare(b[4], a[4]));
        List<int[]> top = new ArrayList<>(merged.subList(0, Math.min(5, merged.size())));
        top.sort((a, b) -> Integer.compare(a[0], b[0]));
        return top;
    }

    private static List<int[]> mergeTouching(List<int[]> boxes, int gap) {
        List<int[]> cur = new ArrayList<>(boxes);
        boolean changed = true;
        while (changed) {
            changed = false;
            outer:
            for (int i = 0; i < cur.size(); i++) {
                for (int j = i + 1; j < cur.size(); j++) {
                    int[] a = cur.get(i);
                    int[] b = cur.get(j);
                    if (a[0] - gap <= b[2] && b[0] - gap <= a[2] && a[1] - gap <= b[3] && b[1] - gap <= a[3]) {
                        cur.set(i, new int[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]),
                            Math.max(a[2], b[2]), Math.max(a[3], b[3]), a[4] + b[4]});
                        cur.remove(j);
                        changed = true;
                        break outer;
                    }
                }
            }
        }
        return cur;
    }

    private static List<int[]> tileBoxes(boolean[] solid, int w, int h) {
        boolean[] tiles = opening(solid, w, h, 2);
        List<int[]> comps = components(tiles, w, h);
        comps.sort((a, b) -> Integer.compare(b[4], a[4]));
        List<int[]> picked = new ArrayList<>();
        for (int[] c : comps) {
            if (c[4] < 400) {
                break;
            }
            picked.add(c);
            if (picked.size() >= 6) {
                break;
            }
        }
        return splitWide(picked, w);
    }

    private static List<int[]> splitWide(List<int[]> picked, int imgW) {        List<Integer> widths = new ArrayList<>();
        for (int[] c : picked) {
            if (c[0] >= 0) {
                widths.add(c[2] - c[0] + 1);
            }
        }
        widths.sort(Integer::compareTo);
        int med = widths.isEmpty() ? 25 : widths.get(widths.size() / 2);
        List<int[]> boxes = new ArrayList<>();
        for (int[] c : picked) {
            int cw = c[2] - c[0] + 1;
            if (cw > med * 1.7 && boxes.size() + 2 <= 6) {
                int mid = (c[0] + c[2]) / 2;
                boxes.add(new int[]{c[0], c[1], mid, c[3], c[4] / 2});
                boxes.add(new int[]{mid + 1, c[1], c[2], c[3], c[4] / 2});
            } else {
                boxes.add(c);
            }
        }
        return boxes;
    }
    private static boolean[] tileBlobMask(boolean[] solid, int w, int h) {
        boolean[] opened = opening(solid, w, h, 2);
        boolean[] mask = new boolean[w * h];
        for (int[] c : components(opened, w, h)) {
            if (c[4] < 400) {
                continue;
            }
            for (int y = c[1]; y <= c[3]; y++) {
                for (int x = c[0]; x <= c[2]; x++) {
                    if (opened[y * w + x]) {
                        mask[y * w + x] = true;
                    }
                }
            }
        }
        return mask;
    }

    private static float[][] extractStripGlyph(boolean[] white, boolean[] tileMask, int w, int h, int[] box) {
        int x0 = Math.max(0, box[0]);
        int y0 = Math.max(0, box[1]);
        int x1 = Math.min(w - 1, box[2]);
        int y1 = Math.min(h - 1, box[3]);
        int bw = Math.max(1, x1 - x0 + 1);
        int bh = Math.max(1, y1 - y0 + 1);
        boolean[] inner = new boolean[bw * bh];
        boolean[] innerTile = new boolean[bw * bh];
        for (int y = 0; y < bh; y++) {
            System.arraycopy(white, (y0 + y) * w + x0, inner, y * bw, bw);
            System.arraycopy(tileMask, (y0 + y) * w + x0, innerTile, y * bw, bw);
        }
        List<int[]> comps = components(inner, bw, bh);
        boolean[] use = new boolean[bw * bh];
        for (int[] c : comps) {
            if (c[4] < 12) {
                continue;
            }
            int inside = 0;
            int total = 0;
            for (int y = c[1]; y <= c[3]; y++) {
                for (int x = c[0]; x <= c[2]; x++) {
                    if (!inner[y * bw + x]) {
                        continue;
                    }
                    total++;
                    boolean near = false;
                    for (int dy = -4; dy <= 4 && !near; dy++) {
                        for (int dx = -4; dx <= 4 && !near; dx++) {
                            int nx = x + dx;
                            int ny = y + dy;
                            if (nx < 0 || ny < 0 || nx >= bw || ny >= bh) {
                                continue;
                            }
                            if (innerTile[ny * bw + nx]) {
                                near = true;
                            }
                        }
                    }
                    if (near) {
                        inside++;
                    }
                }
            }
            if (total >= 12 && (double) inside / total >= 0.75) {
                for (int y = c[1]; y <= c[3]; y++) {
                    for (int x = c[0]; x <= c[2]; x++) {
                        if (inner[y * bw + x]) {
                            use[y * bw + x] = true;
                        }
                    }
                }
            }
        }
        int gx0 = bw;
        int gy0 = bh;
        int gx1 = -1;
        int gy1 = -1;
        for (int y = 0; y < bh; y++) {
            for (int x = 0; x < bw; x++) {
                if (use[y * bw + x]) {
                    if (x < gx0) {
                        gx0 = x;
                    }
                    if (y < gy0) {
                        gy0 = y;
                    }
                    if (x > gx1) {
                        gx1 = x;
                    }
                    if (y > gy1) {
                        gy1 = y;
                    }
                }
            }
        }
        if (gx1 < gx0) {
            return new float[TH][TW];
        }
        return letterboxMask(use, bw, bh, gx0, gy0, gx1, gy1);
    }

    private static float[][] letterboxMask(boolean[] mask, int bw, int bh, int gx0, int gy0, int gx1, int gy1) {
        int gw = gx1 - gx0 + 1;
        int gh = gy1 - gy0 + 1;
        double scale = Math.min((double) TW / gw, (double) TH / gh);
        int dw = Math.max(1, (int) Math.round(gw * scale));
        int dh = Math.max(1, (int) Math.round(gh * scale));
        int ox = (TW - dw) / 2;
        int oy = (TH - dh) / 2;
        float[][] m = new float[TH][TW];
        for (int y = 0; y < dh; y++) {
            for (int x = 0; x < dw; x++) {
                int sx = gx0 + (x * gw) / dw;
                int sy = gy0 + (y * gh) / dh;
                m[oy + y][ox + x] = mask[sy * bw + sx] ? 1f : 0f;
            }
        }
        return m;
    }

    private static float[][] extractGlyph(boolean[] white, int w, int h, int[] box) {
        int x0 = Math.max(0, box[0] + 1);
        int y0 = Math.max(0, box[1] + 1);
        int x1 = Math.min(w - 1, box[2] - 1);
        int y1 = Math.min(h - 1, box[3] - 1);
        int bw = Math.max(1, x1 - x0 + 1);
        int bh = Math.max(1, y1 - y0 + 1);
        boolean[] inner = new boolean[bw * bh];
        for (int y = 0; y < bh; y++) {
            System.arraycopy(white, (y0 + y) * w + x0, inner, y * bw, bw);
        }
        boolean[] glyphMask = inner;
        int gx0 = bw;
        int gy0 = bh;
        int gx1 = -1;
        int gy1 = -1;
        for (int y = 0; y < bh; y++) {
            for (int x = 0; x < bw; x++) {
                if (glyphMask[y * bw + x]) {
                    if (x < gx0) {
                        gx0 = x;
                    }
                    if (y < gy0) {
                        gy0 = y;
                    }
                    if (x > gx1) {
                        gx1 = x;
                    }
                    if (y > gy1) {
                        gy1 = y;
                    }
                }
            }
        }
        if (gx1 < gx0) {
            return new float[TH][TW];
        }
        int gw = gx1 - gx0 + 1;
        int gh = gy1 - gy0 + 1;
        double scale = Math.min((double) TW / gw, (double) TH / gh);
        int dw = Math.max(1, (int) Math.round(gw * scale));
        int dh = Math.max(1, (int) Math.round(gh * scale));
        int ox = (TW - dw) / 2;
        int oy = (TH - dh) / 2;
        float[][] m = new float[TH][TW];
        for (int y = 0; y < dh; y++) {
            for (int x = 0; x < dw; x++) {
                int sx = gx0 + (x * gw) / dw;
                int sy = gy0 + (y * gh) / dh;
                m[oy + y][ox + x] = glyphMask[sy * bw + sx] ? 1f : 0f;
            }
        }
        return m;
    }

    static boolean[] largestComponentMask(boolean[] mask, int w, int h) {
        boolean[] seen = new boolean[w * h];
        boolean[] best = new boolean[w * h];
        int bestCount = 0;
        int[] stack = new int[w * h];
        boolean[] cur = new boolean[w * h];
        for (int i = 0; i < w * h; i++) {
            if (!mask[i] || seen[i]) {
                continue;
            }
            int sp = 0;
            stack[sp++] = i;
            seen[i] = true;
            int count = 0;
            java.util.Arrays.fill(cur, false);
            while (sp > 0) {
                int c = stack[--sp];
                int cx = c % w;
                int cy = c / w;
                cur[c] = true;
                count++;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) {
                            continue;
                        }
                        int nx = cx + dx;
                        int ny = cy + dy;
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                            continue;
                        }
                        int ni = ny * w + nx;
                        if (mask[ni] && !seen[ni]) {
                            seen[ni] = true;
                            stack[sp++] = ni;
                        }
                    }
                }
            }
            if (count > bestCount) {
                bestCount = count;
                System.arraycopy(cur, 0, best, 0, cur.length);
            }
        }
        return best;
    }

    private static CharResult match(float[][] glyph) {
        float[] flat = new float[TW * TH];
        for (int y = 0; y < TH; y++) {
            for (int x = 0; x < TW; x++) {
                flat[y * TW + x] = glyph[y][x];
            }
        }
        if (MlpOcr.isLoaded()) {
            MlpOcr.Prediction p = MlpOcr.predict(flat);
            if (p.prob() >= 0.5 && p.margin() >= 0.2) {
                return new CharResult(p.label(), p.prob());
            }
        }
        if (TEMPLATES.isEmpty()) {
            MlpOcr.Prediction p = MlpOcr.isLoaded() ? MlpOcr.predict(flat) : null;
            if (p != null) {
                return new CharResult(p.label(), p.prob() * 0.9);
            }
            return new CharResult('?', 0);
        }
        char best = '?';
        double bestScore = 0;
        for (Map.Entry<Character, java.util.List<float[][]>> e : TEMPLATES.entrySet()) {
            for (float[][] tpl : e.getValue()) {
                for (double ang : ANGLES) {
                    float[][] r = rotate(glyph, ang);
                    double s = similarity(r, tpl);
                    if (s > bestScore) {
                        bestScore = s;
                        best = e.getKey();
                    }
                }
            }
        }
        return new CharResult(best, bestScore);
    }

    static float[][] rotate(float[][] m, double deg) {
        if (deg == 0) {
            return m;
        }
        double rad = Math.toRadians(deg);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        float[][] out = new float[TH][TW];
        double cx = (TW - 1) / 2.0;
        double cy = (TH - 1) / 2.0;
        for (int y = 0; y < TH; y++) {
            for (int x = 0; x < TW; x++) {
                double dx = x - cx;
                double dy = y - cy;
                int sx = (int) Math.round(cx + dx * cos + dy * sin);
                int sy = (int) Math.round(cy - dx * sin + dy * cos);
                if (sx >= 0 && sx < TW && sy >= 0 && sy < TH) {
                    out[y][x] = m[sy][sx];
                }
            }
        }
        return out;
    }

    static double similarity(float[][] a, float[][] b) {
        double inter = 0;
        double union = 0;
        double bgEq = 0;
        double bgTot = 0;
        for (int y = 0; y < TH; y++) {
            for (int x = 0; x < TW; x++) {
                boolean pa = a[y][x] > 0.5f;
                boolean pb = b[y][x] > 0.5f;
                if (pa || pb) {
                    union++;
                    if (pa && pb) {
                        inter++;
                    }
                } else {
                    bgTot++;
                    bgEq++;
                }
            }
        }
        double jaccard = union == 0 ? 1.0 : inter / union;
        double bg = bgTot == 0 ? 1.0 : bgEq / bgTot;
        return 0.85 * jaccard + 0.15 * bg;
    }

    static float[][] normalize(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        // Полярність шрифту мінялась між версіями сервера: старі капчі — білі цифри на
        // темних плитках, нові — темні цифри на світлих. Авто-визначаємо, який колір є
        // чорнилом (меншість у габариті), щоб шаблони й нейронка бачили однаковий вхід.
        boolean[][] light = new boolean[h][w];
        int lightCount = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = img.getRGB(x, y) & 0xFF;
                light[y][x] = v > 128;
                if (light[y][x]) {
                    lightCount++;
                }
            }
        }
        boolean inkIsLight = lightCount * 2 <= w * h; // чорнило = меншість
        boolean[][] ink = new boolean[h][w];
        int x0 = w;
        int y0 = h;
        int x1 = -1;
        int y1 = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                ink[y][x] = inkIsLight ? light[y][x] : !light[y][x];
                if (ink[y][x]) {
                    if (x < x0) {
                        x0 = x;
                    }
                    if (y < y0) {
                        y0 = y;
                    }
                    if (x > x1) {
                        x1 = x;
                    }
                    if (y > y1) {
                        y1 = y;
                    }
                }
            }
        }
        float[][] m = new float[TH][TW];
        if (x1 < x0) {
            return m;
        }
        int gw = x1 - x0 + 1;
        int gh = y1 - y0 + 1;
        double scale = Math.min((double) TW / gw, (double) TH / gh);
        int dw = Math.max(1, (int) Math.round(gw * scale));
        int dh = Math.max(1, (int) Math.round(gh * scale));
        int ox = (TW - dw) / 2;
        int oy = (TH - dh) / 2;
        for (int y = 0; y < dh; y++) {
            for (int x = 0; x < dw; x++) {
                int sx = x0 + (x * gw) / dw;
                int sy = y0 + (y * gh) / dh;
                m[oy + y][ox + x] = ink[sy][sx] ? 1f : 0f;
            }
        }
        return m;
    }

    static boolean[] opening(boolean[] src, int w, int h, int iters) {
        boolean[] cur = src.clone();
        boolean[] tmp = new boolean[w * h];
        for (int it = 0; it < iters; it++) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    boolean all = true;
                    for (int dy = -1; dy <= 1 && all; dy++) {
                        for (int dx = -1; dx <= 1 && all; dx++) {
                            int nx = x + dx;
                            int ny = y + dy;
                            if (nx < 0 || ny < 0 || nx >= w || ny >= h || !cur[ny * w + nx]) {
                                all = false;
                            }
                        }
                    }
                    tmp[y * w + x] = all;
                }
            }
            boolean[] t = cur;
            cur = tmp;
            tmp = t;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    boolean any = false;
                    for (int dy = -1; dy <= 1 && !any; dy++) {
                        for (int dx = -1; dx <= 1 && !any; dx++) {
                            int nx = x + dx;
                            int ny = y + dy;
                            if (nx >= 0 && ny >= 0 && nx < w && ny < h && cur[ny * w + nx]) {
                                any = true;
                            }
                        }
                    }
                    tmp[y * w + x] = any;
                }
            }
            t = cur;
            cur = tmp;
            tmp = t;
        }
        return cur;
    }

    static List<int[]> components(boolean[] mask, int w, int h) {
        List<int[]> out = new ArrayList<>();
        boolean[] seen = new boolean[w * h];
        int[] stack = new int[w * h];
        for (int i = 0; i < w * h; i++) {
            if (!mask[i] || seen[i]) {
                continue;
            }
            int sp = 0;
            stack[sp++] = i;
            seen[i] = true;
            int x0 = w;
            int y0 = h;
            int x1 = -1;
            int y1 = -1;
            int area = 0;
            while (sp > 0) {
                int cur = stack[--sp];
                int cx = cur % w;
                int cy = cur / w;
                area++;
                if (cx < x0) {
                    x0 = cx;
                }
                if (cy < y0) {
                    y0 = cy;
                }
                if (cx > x1) {
                    x1 = cx;
                }
                if (cy > y1) {
                    y1 = cy;
                }
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) {
                            continue;
                        }
                        int nx = cx + dx;
                        int ny = cy + dy;
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                            continue;
                        }
                        int ni = ny * w + nx;
                        if (mask[ni] && !seen[ni]) {
                            seen[ni] = true;
                            stack[sp++] = ni;
                        }
                    }
                }
            }
            out.add(new int[]{x0, y0, x1, y1, area});
        }
        return out;
    }
}
