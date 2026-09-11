import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;

public class Dbg5 {
    public static void main(String[] a) throws Exception {
        BufferedImage img = ImageIO.read(new File(a[0]));
        int W=img.getWidth(), H=img.getHeight();
        int[] px = new int[W*H];
        img.getRGB(0,0,W,H,px,0,W);
        Class<?> C = Class.forName("ua.atherium.agnelutils.client.flow.CaptchaOcr");
        var mt = C.getDeclaredMethod("tileBlobMask", boolean[].class,int.class,int.class); mt.setAccessible(true);
        var ms = C.getDeclaredMethod("stripBoxes", boolean[].class, boolean[].class, int.class, int.class); ms.setAccessible(true);
        var me = C.getDeclaredMethod("extractStripGlyph", boolean[].class, boolean[].class, int.class, int.class, int[].class); me.setAccessible(true);
        boolean[] white = new boolean[W*H], solid = new boolean[W*H];
        for (int i=0;i<px.length;i++){int r=(px[i]>>16)&0xFF,g=(px[i]>>8)&0xFF,b=px[i]&0xFF;
            white[i]=r>=225&&g>=225&&b>=225; int mx=Math.max(r,Math.max(g,b)),mn=Math.min(r,Math.min(g,b));
            solid[i]=!white[i]&&(mx-mn)>=40&&!(b>r+30&&b>g+10&&mx>170);}
        boolean[] tm = (boolean[]) mt.invoke(null, solid, W, H);
        int tcount=0; for(boolean x:tm) if(x) tcount++;
        System.out.println("tileMask px: "+tcount);
        List<int[]> boxes = (List<int[]>) ms.invoke(null, white, solid, W, H);
        System.out.println("strips: "+boxes.size());
        var mch = C.getDeclaredMethod("match", float[][].class); mch.setAccessible(true);
        int n=0;
        for (int[] b : boxes) {
            float[][] m = (float[][]) me.invoke(null, white, tm, W, H, b);
            int cnt=0; for(int y=0;y<32;y++) for(int x=0;x<24;x++) if(m[y][x]>0.5f) cnt++;
            Object r = mch.invoke(null, (Object) m);
            System.out.println("strip"+(n++)+" box["+b[0]+","+b[1]+","+b[2]+","+b[3]+"] ink="+cnt+" -> "+r);
        }
        System.exit(0);
    }
}
