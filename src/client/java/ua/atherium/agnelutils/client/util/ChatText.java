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
        // Спершу прибираємо шестизначні hex-кольори (§#a1b2c3 / &#a1b2c3), а ПОТІМ одиночні
        // §-коди. Інакше "§." зʼїдає '§#' і лишає сміття 'a1b2c3', яке ламає пошук ключових слів.
        return s.toLowerCase().replaceAll("[§&]#[0-9a-fA-F]{6}", "").replaceAll("§.", "");
    }
}
