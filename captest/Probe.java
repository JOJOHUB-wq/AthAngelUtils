import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import ua.atherium.agnelutils.client.flow.MlpOcr;

public class Probe {
    static float[] load(String p) throws Exception {
        BufferedImage img = ImageIO.read(new File(p));
        int w = img.getWidth(), h = img.getHeight();
        float[] x = new float[768];
        for (int y = 0; y < 32; y++)
            for (int xx = 0; xx < 24; xx++) {
                int sx = xx * w / 24, sy = y * h / 32;
                x[y * 24 + xx] = ((img.getRGB(sx, sy) & 0xFF) > 128) ? 1f : 0f;
            }
        return x;
    }
    public static void main(String[] a) throws Exception {
        MlpOcr.load();
        float[] blank = new float[768];
        MlpOcr.Prediction p0 = MlpOcr.predict(blank);
        System.out.println("blank -> " + p0.label() + " " + p0.prob() + " margin " + p0.margin());
        for (String f : new String[]{"capwork/real/0_0.png","capwork/real/8_0.png","capwork/real/5_0.png"}) {
            try {
                MlpOcr.Prediction p = MlpOcr.predict(load("/Users/atheriumdev/Documents/Default Project/AthAgnelUtils/" + f));
                System.out.println(f + " -> " + p.label() + " " + String.format("%.3f", p.prob()));
            } catch (Exception e) { System.out.println(f + " MISSING"); }
        }
        System.exit(0);
    }
}
