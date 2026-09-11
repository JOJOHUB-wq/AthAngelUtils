package ua.atherium.agnelutils.client.flow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ua.atherium.agnelutils.AthAgnelUtils;
import ua.atherium.agnelutils.client.ScreenTracker;
import ua.atherium.agnelutils.client.TickScheduler;
import ua.atherium.agnelutils.client.config.ConfigStore;
import ua.atherium.agnelutils.client.util.ChatText;
import ua.atherium.agnelutils.client.util.Msg;

public final class HubFlow {
    private static volatile String pendingReason = null;
    private static volatile long pendingAtMs = 0;
    private static volatile long menuDeadlineMs = 0;
    private static volatile boolean compassUsed = false;
    private static volatile long lastNoCompassNotifyMs = 0;
    private static volatile long lastTimeoutNotifyMs = 0;

    private HubFlow() {
    }

    public static void requestHub(String reason) {
        var cfg = ConfigStore.get();
        if (!cfg.autoHub) {
            return;
        }
        if (FarmMode.active()) {
            return;
        }
        pendingReason = reason;
        pendingAtMs = System.currentTimeMillis() + cfg.hubCompassDelayMs;
        menuDeadlineMs = System.currentTimeMillis() + cfg.hubCompassDelayMs + cfg.hubMenuWaitMs;
        compassUsed = false;
        AthAgnelUtils.LOGGER.info("[AthAgnelUtils][hub] scheduled (reason={})", reason);
    }

    public static void tick(Minecraft client) {
        if (pendingReason == null || client == null || client.player == null || client.level == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < pendingAtMs) {
            return;
        }
        if (now > menuDeadlineMs) {
            AthAgnelUtils.LOGGER.info("[AthAgnelUtils][hub] timeout, cancel (reason={})", pendingReason);
            if (now - lastTimeoutNotifyMs > 60_000) {
                lastTimeoutNotifyMs = now;
                Msg.title("Хаб-меню не знайдено, тикни компас вручну");
            }
            pendingReason = null;
            return;
        }
        var screen = ScreenTracker.current();
        if (screen != null) {
            ScreenDebug.tickScreen(client);
            if (tryClickAnarchy(client)) {
                AthAgnelUtils.LOGGER.info("[AthAgnelUtils][hub] anarchy clicked");
                Msg.title("Анархія вибрана, чекаю телепорт…");
                pendingReason = null;
            }
            return;
        }
        if (!compassUsed) {
            compassUsed = useCompass(client);
            if (!compassUsed && now - lastNoCompassNotifyMs > 5000) {
                lastNoCompassNotifyMs = now;
                AthAgnelUtils.LOGGER.info("[AthAgnelUtils][hub] no compass in hotbar yet");
                Msg.actionbar("[AthAgnel] Візьми компас у руку (хаб)");
            }
        }
    }

    private static boolean useCompass(Minecraft client) {
        try {
            var player = client.player;
            int found = -1;
            for (int i = 0; i < 9; i++) {
                ItemStack st = player.getInventory().getItem(i);
                if (!st.isEmpty() && st.getItem() == Items.COMPASS) {
                    found = i;
                    break;
                }
            }
            if (found < 0) {
                return false;
            }
            player.getInventory().setSelectedSlot(found);
            if (client.gameMode != null) {
                client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            }
            TickScheduler.runLater(600, () -> ScreenDebug.tickScreen(Minecraft.getInstance()));
            return true;
        } catch (Throwable t) {
            AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][hub] compass use failed: {}", t.toString());
            return false;
        }
    }

    private static boolean tryClickAnarchy(Minecraft client) {
        try {
            var screen = ScreenTracker.current();
            if (!(screen instanceof AbstractContainerScreen<?> cont)) {
                return false;
            }
            var cfg = ConfigStore.get();
            String titleNorm = ChatText.norm(cont.getTitle() != null ? cont.getTitle().getString() : "");
            var menu = cont.getMenu();
            boolean isHubMenu = false;
            for (String kw : cfg.hubTitleKeywords) {
                if (!titleNorm.contains(ChatText.norm(kw))) {
                    continue;
                }
                isHubMenu = true;
                break;
            }
            if (isHubMenu && cfg.anarchySlot >= 0) {
                for (var slot : menu.slots) {
                    if (slot.index == cfg.anarchySlot && !slot.getItem().isEmpty()) {
                        AthAgnelUtils.LOGGER.info("[AthAgnelUtils][hub] click anarchy slot {} title='{}'", slot.index, titleNorm);
                        client.gameMode.handleContainerInput(menu.containerId, slot.index, 0,
                            ContainerInput.PICKUP, client.player);
                        return true;
                    }
                }
            }
            for (var slot : menu.slots) {
                ItemStack st = slot.getItem();
                if (st.isEmpty()) {
                    continue;
                }
                String nn = ChatText.norm(st.getHoverName() != null ? st.getHoverName().getString() : "");
                boolean ok = true;
                for (String kw : cfg.anarchyKeywords) {
                    if (!nn.contains(ChatText.norm(kw))) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    AthAgnelUtils.LOGGER.info("[AthAgnelUtils][hub] click slot {} title='{}'", slot.index, titleNorm);
                    client.gameMode.handleContainerInput(menu.containerId, slot.index, 0,
                        ContainerInput.PICKUP, client.player);
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][hub] click failed: {}", t.toString());
            return false;
        }
    }

    public static void cancel() {
        pendingReason = null;
    }
}
