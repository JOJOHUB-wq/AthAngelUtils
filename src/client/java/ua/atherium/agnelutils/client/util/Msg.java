package ua.atherium.agnelutils.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class Msg {
    private Msg() {
    }

    public static void chat(String text) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        client.player.sendSystemMessage(Component.literal(text));
    }

    public static void actionbar(String text) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        client.player.sendOverlayMessage(Component.literal(text));
    }

    public static void title(String text) {
        chat("[AthAgnel] " + text);
    }
}
