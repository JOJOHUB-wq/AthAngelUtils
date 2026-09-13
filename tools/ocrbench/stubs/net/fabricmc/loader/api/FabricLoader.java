package net.fabricmc.loader.api;

import java.nio.file.Path;

/** Offline stub for the OCR benchmark harness. Not shipped. */
public final class FabricLoader {
    private static final FabricLoader I = new FabricLoader();

    public static FabricLoader getInstance() {
        return I;
    }

    private final Path cfg = Path.of(System.getProperty("bench.cfgdir", "/tmp/ocrbench-cfg"));

    public Path getConfigDir() {
        return cfg;
    }
}
