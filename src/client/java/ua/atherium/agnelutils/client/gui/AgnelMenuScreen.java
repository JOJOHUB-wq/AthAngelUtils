package ua.atherium.agnelutils.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ua.atherium.agnelutils.client.config.ConfigStore;
import ua.atherium.agnelutils.client.flow.BuyerFlow;
import ua.atherium.agnelutils.client.flow.CaptchaTest;
import ua.atherium.agnelutils.client.flow.FarmMode;
import ua.atherium.agnelutils.client.flow.HubFlow;
import ua.atherium.agnelutils.client.flow.MinerFlow;
import ua.atherium.agnelutils.client.flow.NickSwapper;
import ua.atherium.agnelutils.client.flow.RepairFlow;
import ua.atherium.agnelutils.client.util.Msg;

public final class AgnelMenuScreen extends Screen {
    private EditBox passField;
    private EditBox cyclesField;

    public AgnelMenuScreen() {
        super(Component.literal("AthAgnelUtils"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = 62;
        var cfg = ConfigStore.get();

        addRenderableWidget(Button.builder(Component.literal(minerLabel()),
            b -> {
                MinerFlow.flipToggle();
                rebuild();
            }).bounds(cx - 168, y, 108, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§fХаб §7(компас)"),
            b -> {
                HubFlow.requestHub("manual");
                Msg.chat("[AthAgnel] Хаб: тискаю компас…");
                close();
            }).bounds(cx - 54, y, 108, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§fРемонт кірки"),
            b -> {
                RepairFlow.start("manual");
                Msg.chat("[AthAgnel] Ремонт запущено");
                close();
            }).bounds(cx + 60, y, 108, 20).build());
        y += 24;

        addRenderableWidget(Button.builder(Component.literal("§fСкупщик §7(/buyer)"),
            b -> {
                BuyerFlow.open();
                close();
            }).bounds(cx - 168, y, 108, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§fКапча-тест"),
            b -> Msg.chat(CaptchaTest.run())).bounds(cx - 54, y, 108, 20).build());
        addRenderableWidget(Button.builder(Component.literal(farmLabel()),
            b -> {
                if (FarmMode.active()) {
                    FarmMode.stop("manual");
                } else {
                    applyFields();
                    FarmMode.start(cfg.farmCycles);
                }
                rebuild();
            }).bounds(cx + 60, y, 108, 20).build());
        y += 24;

        addRenderableWidget(Button.builder(Component.literal("§fРеконект"),
            b -> {
                var inst = ua.atherium.agnelutils.client.AthAgnelUtilsClient.INSTANCE;
                if (inst != null) {
                    inst.reconnectState().reset();
                    Msg.chat("[AthAgnel] Реконект скинуто, пробую знову");
                }
                close();
            }).bounds(cx - 168, y, 108, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§fReload конфіга"),
            b -> {
                ConfigStore.reload();
                Msg.chat("[AthAgnel] Конфіг перезавантажено");
                rebuild();
            }).bounds(cx - 54, y, 108, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§cЗакрити"),
            b -> close()).bounds(cx + 60, y, 108, 20).build());
        y += 30;

        passField = new EditBox(this.font, cx - 168, y, 150, 20, Component.literal("Пароль"));
        passField.setMaxLength(32);
        passField.setValue(cfg.password);
        addRenderableWidget(passField);
        addRenderableWidget(Button.builder(Component.literal("§fПароль ✓"),
            b -> {
                applyFields();
                rebuild();
            }).bounds(cx - 12, y, 90, 20).build());
        cyclesField = new EditBox(this.font, cx + 82, y, 44, 20, Component.literal("N"));
        cyclesField.setMaxLength(3);
        cyclesField.setValue(String.valueOf(cfg.farmCycles));
        addRenderableWidget(cyclesField);
        addRenderableWidget(Button.builder(Component.literal("§fN ✓"),
            b -> {
                applyFields();
                rebuild();
            }).bounds(cx + 130, y, 38, 20).build());
    }

    private void applyFields() {
        var cfg = ConfigStore.get();
        try {
            if (passField != null) {
                String p = passField.getValue().trim();
                if (!p.isEmpty()) {
                    cfg.password = p;
                }
            }
            if (cyclesField != null) {
                int n = Integer.parseInt(cyclesField.getValue().trim());
                if (n >= 1 && n <= 200) {
                    cfg.farmCycles = n;
                }
            }
            ConfigStore.save();
        } catch (Throwable ignored) {
        }
    }

    private static String minerLabel() {
        return (MinerFlow.gateOpen() ? "§aМайнер: ON" : "§cМайнер: OFF") + " §7[G]";
    }

    private static String farmLabel() {
        return FarmMode.active() ? "§cФарм: стоп" : "§aФарм: старт";
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private void close() {
        Minecraft mc = Minecraft.getInstance();
        try {
            mc.setScreenAndShow(null);
        } catch (Throwable t) {
            onClose();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(extractor, mouseX, mouseY, partialTick);
        extractor.fill(0, 0, this.width, this.height, 0xA0101018);
        extractor.fillGradient(0, 0, this.width, 30, 0xFF1A1A2E, 0xFF16213E);
        extractor.fill(0, 30, this.width, 31, 0xFFFFAA00);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        extractor.text(this.font, Component.literal("§6§lAthAgnelUtils §8| §7by AtheriumDev"), cx - 150, 10, 0xFFFFFF);
        String nick;
        try {
            nick = NickSwapper.gameNick();
        } catch (Throwable t) {
            nick = "?";
        }
        extractor.text(this.font, "§7Нік: §f" + nick + "  §7CapsLock: "
            + (MinerFlow.capsLockOn() ? "§aON" : "§8OFF"), cx - 168, 34, 0xFFFFFF);
        Minecraft mc = Minecraft.getInstance();
        String dur = "?";
        try {
            if (mc.player != null) {
                int left = MinerFlow.pickaxeLeft(mc);
                dur = left < 0 ? "нема кірки" : String.valueOf(left);
            }
        } catch (Throwable ignored) {
        }
        extractor.text(this.font, "§7Кірка: §f" + dur + "  §7"
            + FarmMode.status().replace("[AthAgnel] ", ""), cx - 168, 44, 0xFFFFFF);
        extractor.text(this.font, "§8G / Esc — закрити", cx - 168, this.height - 14, 0xFFFFFF);
        if (passField != null) {
            extractor.text(this.font, "§8пароль", passField.getX(), passField.getY() - 9, 0xFFFFFF);
        }
        if (cyclesField != null) {
            extractor.text(this.font, "§8N", cyclesField.getX(), cyclesField.getY() - 9, 0xFFFFFF);
        }
    }
}
