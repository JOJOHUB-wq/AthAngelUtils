package ua.atherium.agnelutils.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class ScreenTracker {
    private static volatile Screen current;

    private ScreenTracker() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((Minecraft client, Screen screen, int w, int h) -> {
            current = screen;
            ScreenEvents.remove(screen).register(s -> {
                if (current == s) {
                    current = null;
                }
            });
        });
    }

    public static Screen current() {
        return current;
    }

    public static String currentName() {
        Screen s = current;
        return s == null ? "" : s.getClass().getSimpleName();
    }
}
