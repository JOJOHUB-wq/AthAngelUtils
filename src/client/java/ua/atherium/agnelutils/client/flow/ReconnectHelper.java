package ua.atherium.agnelutils.client.flow;

import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import ua.atherium.agnelutils.AthAgnelUtils;

public final class ReconnectHelper {
    private ReconnectHelper() {
    }

    public static void connect(Minecraft client, ServerData server, Screen parent) {
        if (client == null || server == null) {
            return;
        }
        try {
            // Старе вікно дисконекту прибираємо одразу: батьком даємо свіжий TitleScreen,
            // щоб не тримати протухлий DisconnectedScreen і не складати екрани один на одного.
            Screen effectiveParent = parent;
            if (parent instanceof net.minecraft.client.gui.screens.DisconnectedScreen) {
                try {
                    effectiveParent = new net.minecraft.client.gui.screens.TitleScreen();
                } catch (Throwable ignored) {
                    effectiveParent = null;
                }
            }
            ServerAddress address = ServerAddress.parseString(server.ip);
            TransferState transfer = new TransferState(Map.of(), Map.of(), false);
            ConnectScreen.startConnecting(effectiveParent, client, address, server, false, transfer);
            ReconnectState.log("connecting to " + server.ip);
        } catch (Throwable t) {
            AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][reconnect] connect failed: {}", t.toString());
        }
    }
}
