package ua.atherium.agnelutils.client.flow;

import net.minecraft.client.Minecraft;
import ua.atherium.agnelutils.AthAgnelUtils;
import ua.atherium.agnelutils.client.config.ConfigStore;
import ua.atherium.agnelutils.client.util.Msg;

public final class BuyerFlow {
    private BuyerFlow() {
    }

    public static void open() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        String cmd = ConfigStore.get().buyerCommand;
        AthAgnelUtils.LOGGER.info("[AthAgnelUtils][buyer] open /{}", cmd);
        Msg.title("Відкриваю скупщика: /" + cmd);
        Msg.chat("§7[AthAgnel] Пройди всі сторінки і увімкни предмети з шахти. Я все запишу в screens.log, авто-режим зроблю наступним.");
        try {
            client.player.connection.sendCommand(cmd);
        } catch (Throwable t) {
            AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][buyer] open failed: {}", t.toString());
        }
    }
}
