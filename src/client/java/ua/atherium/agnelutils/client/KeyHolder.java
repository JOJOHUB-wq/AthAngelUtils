package ua.atherium.agnelutils.client;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import ua.atherium.agnelutils.AthAgnelUtils;

public final class KeyHolder {
    private static final Set<KeyMapping> HELD = new HashSet<>();

    private KeyHolder() {
    }

    public static synchronized void set(KeyMapping key, boolean down) {
        try {
            if (key == null) {
                return;
            }
            if (down) {
                key.setDown(true);
                HELD.add(key);
            } else if (HELD.remove(key)) {
                key.setDown(false);
            }
        } catch (Throwable t) {
            AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][keys] set failed: {}", t.toString());
        }
    }

    public static synchronized void releaseAll() {
        try {
            for (KeyMapping key : HELD) {
                try {
                    key.setDown(false);
                } catch (Throwable ignored) {
                }
            }
        } finally {
            HELD.clear();
        }
    }

    public static synchronized boolean held(KeyMapping key) {
        return HELD.contains(key);
    }
}
