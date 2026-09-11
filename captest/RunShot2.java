import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import ua.atherium.agnelutils.client.flow.CaptchaOcr;
import ua.atherium.agnelutils.client.flow.MlpOcr;

public class RunShot2 {
    public static void main(String[] a) throws Exception {
        BufferedImage img = ImageIO.read(new File(a[0]));
        System.out.println("img " + img.getWidth() + "x" + img.getHeight());
        var m = CaptchaOcr.class.getDeclaredMethod("loadBundled");
        m.setAccessible(true); m.invoke(null);
        MlpOcr.load();
        System.out.println("templates=" + CaptchaOcr.templateCount() + " mlp=" + MlpOcr.isLoaded());
        CaptchaOcr.SolveResult r = CaptchaOcr.solve(img);
        System.out.println("text='" + r.text() + "' avg=" + r.avg());
        for (CaptchaOcr.CharResult c : r.chars()) System.out.println("  " + c.label() + " " + String.format("%.3f", c.score()));
    }
}
