package ua.atherium.agnelutils.client.util;

import net.minecraft.network.chat.Component;

public final class ChatText {
    private ChatText() {
    }

    public static String plain(Component component) {
        if (component == null) {
            return "";
        }
        return component.getString();
    }

    public static String norm(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase().replaceAll("§.", "").replaceAll("[§&]#[0-9a-fA-F]{6}", "");
    }
}
