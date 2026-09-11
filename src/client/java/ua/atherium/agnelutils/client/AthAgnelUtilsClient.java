package ua.atherium.agnelutils.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import org.lwjgl.glfw.GLFW;
import ua.atherium.agnelutils.AthAgnelUtils;
import ua.atherium.agnelutils.client.config.ConfigStore;
import ua.atherium.agnelutils.client.flow.AuthFlow;
import ua.atherium.agnelutils.client.flow.HubFlow;
import ua.atherium.agnelutils.client.flow.MinerFlow;
import ua.atherium.agnelutils.client.flow.ReconnectHelper;
import ua.atherium.agnelutils.client.flow.ReconnectState;
import ua.atherium.agnelutils.client.flow.RepairFlow;
import ua.atherium.agnelutils.client.flow.ScreenDebug;
import ua.atherium.agnelutils.client.util.ChatText;

public final class AthAgnelUtilsClient implements ClientModInitializer {
    public static AthAgnelUtilsClient INSTANCE;
    private final ReconnectState reconnect = new ReconnectState();
    private final AuthFlow auth = new AuthFlow();
    private String lastScreenName = "";

    public ReconnectState reconnectState() {
        return reconnect;
    }

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        try {
            var ver = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer("athagnelutils")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
            AthAgnelUtils.LOGGER.info("[AthAgnelUtils] version {}", ver);
        } catch (Throwable ignored) {
        }
        ConfigStore.load();
        ScreenTracker.register();
        ua.atherium.agnelutils.client.flow.CaptchaOcr.reload();
        ua.atherium.agnelutils.client.ClientStats.load();

        MinerFlow.toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.athagnelutils.menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            KeyMapping.Category.GAMEPLAY));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            try {
                var server = client.getCurrentServer();
                reconnect.onJoin(server);
                auth.resetSession();
                ua.atherium.agnelutils.client.flow.FarmMode.noteJoin();
                ua.atherium.agnelutils.client.flow.MineGuard.resetSession();
                ua.atherium.agnelutils.client.flow.MapCapture.resetSession();
                String host = server != null && server.ip != null ? server.ip : "";
                AthAgnelUtils.LOGGER.info("[AthAgnelUtils] joined {}", host);
                if (host.toLowerCase().contains(ConfigStore.get().serverHostContains.toLowerCase())) {
                    HubFlow.requestHub("join");
                }
            } catch (Throwable t) {
                AthAgnelUtils.LOGGER.warn("[AthAgnelUtils] join hook failed: {}", t.toString());
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            try {
                boolean farmExpected = ua.atherium.agnelutils.client.flow.FarmMode.consumeExpectedDisconnect();
                boolean manual = farmExpected || "PauseScreen".equals(lastScreenName);
                reconnect.onDisconnect(manual);
                MinerFlow.stopAttack();
                ua.atherium.agnelutils.client.KeyHolder.releaseAll();
                HubFlow.cancel();
                TickScheduler.clear();
                AthAgnelUtils.LOGGER.info("[AthAgnelUtils] disconnected (manual={})", manual);
            } catch (Throwable t) {
                AthAgnelUtils.LOGGER.warn("[AthAgnelUtils] disconnect hook failed: {}", t.toString());
            }
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            try {
                if (overlay) {
                    return;
                }
                String plain = ChatText.plain(message);
                auth.onGameMessage(plain);
                ua.atherium.agnelutils.client.flow.MineGuard.onGameMessage(plain);
                ua.atherium.agnelutils.client.flow.RepairFlow.onGameMessage(plain);
            } catch (Throwable t) {
                AthAgnelUtils.LOGGER.warn("[AthAgnelUtils] chat hook failed: {}", t.toString());
            }
        });

        try {
            net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.CHAT.register(msg -> {
                ua.atherium.agnelutils.client.flow.CaptchaFlow.noteUserTyped();
                try {
                    ua.atherium.agnelutils.client.flow.CaptchaFlow.noteUserAnswer(String.valueOf(msg));
                } catch (Throwable ignored) {
                }
            });
            net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.COMMAND.register(cmd -> {
                ua.atherium.agnelutils.client.flow.CaptchaFlow.noteUserTyped();
            });
        } catch (Throwable t) {
            AthAgnelUtils.LOGGER.warn("[AthAgnelUtils] send hook failed: {}", t.toString());
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                onTick(client);
            } catch (Throwable t) {
                AthAgnelUtils.LOGGER.warn("[AthAgnelUtils] tick failed: {}", t.toString());
            }
        });

        AthAgnelUtils.LOGGER.info("[AthAgnelUtils] client init done. /agnel status|hub|mine|login|reload. Miner key=G, CapsLock-gate supported.");
    }

    private void onTick(Minecraft client) {
        TickScheduler.tick(client);
        ua.atherium.agnelutils.client.ClientStats.tickSave();
        lastScreenName = ScreenTracker.currentName();
        if (ScreenTracker.current() instanceof DisconnectedScreen dc) {
            try {
                reconnect.noteReason(dc.getNarrationMessage().getString());
            } catch (Throwable ignored) {
            }
            if (!reconnect.shouldReconnect(client)) {
                return;
            }
            var server = reconnect.consumeServer();
            ua.atherium.agnelutils.client.ClientStats.reconnects++;
            AthAgnelUtils.LOGGER.info("[AthAgnelUtils][reconnect] attempt #{}", reconnect.attempts());
            ReconnectHelper.connect(client, server, dc);
            return;
        }
        if (client.player != null && client.level != null) {
            HubFlow.tick(client);
            ScreenDebug.tickScreen(client);
            RepairFlow.tick(client);
            ua.atherium.agnelutils.client.flow.MineGuard.tick(client);
            MinerFlow.tick(client);
        }
        ua.atherium.agnelutils.client.flow.FarmMode.tick(client);
    }
}
