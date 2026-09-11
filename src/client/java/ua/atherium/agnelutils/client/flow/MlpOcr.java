package ua.atherium.agnelutils.client.flow;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import ua.atherium.agnelutils.AthAgnelUtils;

public final class MlpOcr {
    private static volatile boolean loaded = false;
    private static String classes = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static float[][] w1 = new float[0][0];
    private static float[] b1 = new float[0];
    private static float[][] w2 = new float[0][0];
    private static float[] b2 = new float[0];

    public record Prediction(char label, double prob, double margin) {
    }

    private MlpOcr() {
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }
        try (InputStream in = MlpOcr.class.getClassLoader()
            .getResourceAsStream("assets/athagnelutils/captcha/mlp.json")) {
            if (in == null) {
                AthAgnelUtils.LOGGER.info("[AthAgnelUtils][mlp] no bundled model, template-only mode");
                return;
            }
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            classes = o.get("classes").getAsString();
            w1 = readMatrix(o.getAsJsonArray("w1"));
            b1 = readVector(o.getAsJsonArray("b1"));
            w2 = readMatrix(o.getAsJsonArray("w2"));
            b2 = readVector(o.getAsJsonArray("b2"));
            loaded = w1.length == 768 && b1.length == w2.length && w2.length > 0
                && b2.length == classes.length() && w2[0].length == b2.length;
            AthAgnelUtils.LOGGER.info("[AthAgnelUtils][mlp] loaded: {} ({} classes)", loaded, classes.length());
        } catch (Throwable t) {
            AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][mlp] load failed: {}", t.toString());
            loaded = false;
        }
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static Prediction predict(float[] x) {
        if (!loaded || x.length != w1.length) {
            return new Prediction('?', 0, 0);
        }
        int hn = b1.length;
        float[] h = new float[hn];
        for (int j = 0; j < hn; j++) {
            float s = b1[j];
            for (int i = 0; i < x.length; i++) {
                s += x[i] * w1[i][j];
            }
            h[j] = s > 0 ? s : 0;
        }
        float[] z = new float[b2.length];
        for (int k = 0; k < b2.length; k++) {
            float s = b2[k];
            for (int j = 0; j < hn; j++) {
                s += h[j] * w2[j][k];
            }
            z[k] = s;
        }
        float mx = z[0];
        for (float v : z) {
            if (v > mx) {
                mx = v;
            }
        }
        double sum = 0;
        double[] p = new double[z.length];
        for (int k = 0; k < z.length; k++) {
            p[k] = Math.exp(z[k] - mx);
            sum += p[k];
        }
        int top = 0;
        int second = -1;
        for (int k = 0; k < p.length; k++) {
            p[k] /= sum;
            if (p[k] > p[top]) {
                second = top;
                top = k;
            } else if (second < 0 || p[k] > p[second]) {
                second = k;
            }
        }
        double margin = second < 0 ? 1.0 : p[top] - p[second];
        return new Prediction(classes.charAt(top), p[top], margin);
    }

    private static float[][] readMatrix(JsonArray a) {
        float[][] m = new float[a.size()][];
        for (int i = 0; i < a.size(); i++) {
            JsonArray row = a.get(i).getAsJsonArray();
            m[i] = new float[row.size()];
            for (int j = 0; j < row.size(); j++) {
                m[i][j] = row.get(j).getAsFloat();
            }
        }
        return m;
    }

    private static float[] readVector(JsonArray a) {
        float[] v = new float[a.size()];
        for (int i = 0; i < a.size(); i++) {
            v[i] = a.get(i).getAsFloat();
        }
        return v;
    }
}
