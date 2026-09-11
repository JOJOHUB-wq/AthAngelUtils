package ua.atherium.agnelutils.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import ua.atherium.agnelutils.AthAgnelUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("athagnelutils.json");
    private static ModConfig cached = new ModConfig();

    private ConfigStore() {
    }

    public static synchronized ModConfig get() {
        return cached;
    }

    public static synchronized void load() {
        try {
            if (Files.exists(PATH)) {
                String json = Files.readString(PATH);
                ModConfig parsed = GSON.fromJson(json, ModConfig.class);
                if (parsed != null) {
                    cached = parsed;
                }
            } else {
                save();
            }
        } catch (IOException | RuntimeException e) {
            AthAgnelUtils.LOGGER.warn("[AthAgnelUtils] config load failed, using defaults: {}", e.toString());
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(cached));
        } catch (IOException e) {
            AthAgnelUtils.LOGGER.warn("[AthAgnelUtils] config save failed: {}", e.toString());
        }
    }

    public static synchronized void reload() {
        load();
    }
}
