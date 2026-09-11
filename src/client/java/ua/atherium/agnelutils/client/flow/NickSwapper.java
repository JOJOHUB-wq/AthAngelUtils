package ua.atherium.agnelutils.client.flow;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import ua.atherium.agnelutils.AthAgnelUtils;

public final class NickSwapper {
    private static volatile String originalNick = "";
    private static volatile String currentNick = "";

    private NickSwapper() {
    }

    public static String gameNick() {
        try {
            return Minecraft.getInstance().getUser().getName();
        } catch (Throwable t) {
            return currentNick.isEmpty() ? "?" : currentNick;
        }
    }

    public static void rememberOriginal() {
        if (originalNick.isEmpty()) {
            originalNick = gameNick();
            currentNick = originalNick;
        }
    }

    public static String originalNick() {
        return originalNick;
    }

    public static boolean setNick(String nick) {
        try {
            if (nick == null || nick.isEmpty() || nick.length() > 16) {
                return false;
            }
            Minecraft client = Minecraft.getInstance();
            User old = client.getUser();
            UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + nick).getBytes(StandardCharsets.UTF_8));
            User nu = new User(nick, id, old.getAccessToken(), Optional.empty(), Optional.empty());
            Field f = Minecraft.class.getDeclaredField("user");
            f.setAccessible(true);
            f.set(client, nu);
            currentNick = nick;
            AthAgnelUtils.LOGGER.info("[AthAgnelUtils][nick] swapped to {}", nick);
            return Minecraft.getInstance().getUser().getName().equals(nick);
        } catch (Throwable t) {
            AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][nick] swap failed: {}", t.toString());
            return false;
        }
    }

    public static boolean restore() {
        if (originalNick.isEmpty()) {
            return false;
        }
        return setNick(originalNick);
    }
}
