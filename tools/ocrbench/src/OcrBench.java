package ua.atherium.agnelutils.client.flow;

import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import javax.imageio.ImageIO;

/**
 * Offline benchmark for the REAL mod OCR code (CaptchaOcr + MlpOcr). Not shipped.
 * Usage:
 *   OcrBench selftest
 *   OcrBench glyph <path>
 *   OcrBench solve <path>
 */
public final class OcrBench {
    public static void main(String[] a) throws Exception {
        CaptchaOcr.reload();
        String mode = a.length > 0 ? a[0] : "selftest";
        switch (mode) {
            case "selftest" -> System.out.println("templates=" + CaptchaOcr.templateCount()
                + " mlp=" + MlpOcr.isLoaded());
            case "glyph" -> {
                BufferedImage img = ImageIO.read(new java.io.File(a[1]));
                float[][] g = normalize(img);
                CaptchaOcr.CharResult r = match(g);
                System.out.println(r.label() + " " + String.format("%.3f", r.score()));
            }
            case "batch" -> {
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
                String line;
                int correct = 0, total = 0;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) {
                        continue;
                    }
                    String[] parts = line.split("\t");
                    if (parts.length < 2) {
                        continue;
                    }
                    BufferedImage img = ImageIO.read(new java.io.File(parts[0]));
                    if (img == null) {
                        continue;
                    }
                    float[][] g = normalize(img);
                    CaptchaOcr.CharResult r = match(g);
                    total++;
                    if (String.valueOf(r.label()).equals(parts[1])) {
                        correct++;
                    } else {
                        System.out.println("MISS " + parts[0] + " want=" + parts[1] + " got=" + r.label());
                    }
                }
                System.out.println("ACC " + correct + "/" + total + " = "
                    + String.format("%.3f", total == 0 ? 0 : (double) correct / total));
            }
            case "tpl" -> {
                BufferedImage img = ImageIO.read(new java.io.File(a[1]));
                float[][] g = normalize(img);
                String[] r = templateOnly(g);
                System.out.println(r[0] + " " + r[1]);
            }
            case "solve" -> {
                BufferedImage img = ImageIO.read(new java.io.File(a[1]));
                CaptchaOcr.SolveResult r = CaptchaOcr.solve(img);
                System.out.println("text=" + r.text() + " avg=" + String.format("%.3f", r.avg()));
                for (CaptchaOcr.CharResult c : r.chars()) {
                    System.out.println("  " + c.label() + " " + String.format("%.3f", c.score()));
                }
            }
            default -> System.out.println("unknown mode " + mode);
        }
        System.exit(0);
    }

    private static float[][] normalize(BufferedImage img) throws Exception {
        Method m = CaptchaOcr.class.getDeclaredMethod("normalize", BufferedImage.class);
        m.setAccessible(true);
        return (float[][]) m.invoke(null, img);
    }

    private static CaptchaOcr.CharResult match(float[][] g) throws Exception {
        Method m = CaptchaOcr.class.getDeclaredMethod("match", float[][].class);
        m.setAccessible(true);
        return (CaptchaOcr.CharResult) m.invoke(null, (Object) g);
    }

    @SuppressWarnings("unchecked")
    private static String[] templateOnly(float[][] g) throws Exception {
        java.lang.reflect.Field tf = CaptchaOcr.class.getDeclaredField("TEMPLATES");
        tf.setAccessible(true);
        var templates = (java.util.Map<Character, java.util.List<float[][]>>) tf.get(null);
        java.lang.reflect.Field af = CaptchaOcr.class.getDeclaredField("ANGLES");
        af.setAccessible(true);
        double[] angles = (double[]) af.get(null);
        Method rot = CaptchaOcr.class.getDeclaredMethod("rotate", float[][].class, double.class);
        rot.setAccessible(true);
        Method sim = CaptchaOcr.class.getDeclaredMethod("similarity", float[][].class, float[][].class);
        sim.setAccessible(true);
        char best = '?';
        double bestScore = 0;
        for (var e : templates.entrySet()) {
            for (float[][] tpl : e.getValue()) {
                for (double ang : angles) {
                    float[][] r = (float[][]) rot.invoke(null, g, ang);
                    double s = (double) sim.invoke(null, r, tpl);
                    if (s > bestScore) {
                        bestScore = s;
                        best = e.getKey();
                    }
                }
            }
        }
        return new String[]{String.valueOf(best), String.format("%.3f", bestScore)};
    }
}
