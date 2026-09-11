package ua.atherium.agnelutils.client.flow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ua.atherium.agnelutils.AthAgnelUtils;
import ua.atherium.agnelutils.client.ScreenTracker;
import ua.atherium.agnelutils.client.config.ConfigStore;
import ua.atherium.agnelutils.client.util.ChatText;
import ua.atherium.agnelutils.client.util.Msg;

public final class RepairFlow {
    private enum Phase {
        IDLE, FIX_WAIT, NUGGETS_OPEN, NUGGETS_PLACE, NUGGETS_TAKE, OPENING, IN_MENU, THROWING
    }

    private static volatile Phase phase = Phase.IDLE;
    private static volatile int containerId = -1;
    private static volatile int clicks = 0;
    private static volatile int throwsDone = 0;
    private static volatile long deadlineMs = 0;
    private static volatile long lastActionMs = 0;
    private static volatile int throwsPlanned = 0;
    private static volatile int lastBatchLeft = -1;
    private static volatile long lastAbortMs = 0;
    private static volatile int closeAttempts = 0;
    private static volatile int nuggetTakes = 0;
    private static volatile long lastFixMs = 0;
    private static volatile long fixCooldownUntilMs = 0;
    private static volatile int fixStartLeft = -1;
    private static volatile boolean dumpAll = false;

    private RepairFlow() {
    }

    public static boolean active() {
        return phase != Phase.IDLE;
    }

    public static long lastAbort() {
        return lastAbortMs;
    }

    public static long fixCooldownLeftMs() {
        long now = System.currentTimeMillis();
        long left = fixCooldownUntilMs - now;
        if (lastFixMs > 0) {
            left = Math.max(left, lastFixMs + ConfigStore.get().fixCooldownMs - now);
        }
        return Math.max(0, left);
    }

    public static void onGameMessage(String plain) {
        String n = ChatText.norm(plain);
        if (n.isEmpty() || !n.matches("(?s).*(^|[^a-zа-яёіїє])fix([^a-zа-яёіїє]|$).*")) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean relevant = phase == Phase.FIX_WAIT || (lastFixMs > 0 && now - lastFixMs < 30000);
        if (!relevant) {
            return;
        }
        AthAgnelUtils.LOGGER.info("[AthAgnelUtils][repair] fix msg: {}", plain);
        boolean cdHint = n.contains("подожд") || n.contains("почекай") || n.contains("через")
            || n.contains("cooldown") || n.contains("затримк") || n.contains("wait")
            || n.contains("повтор") || n.contains("later") || n.contains("лимит")
            || n.contains("ліміт");
        boolean okHint = n.contains("успеш") || n.contains("успіш") || n.contains("почин")
            || n.contains("отремонт") || n.contains("відремонт") || n.contains("готов")
            || n.contains("fixed") || n.contains("repaired") || n.contains("success");
        boolean noHint = n.contains("no permission") || n.contains("нема прав")
            || n.contains("нет прав") || n.contains("недоступ");
        if (cdHint) {
            long remain = parseRemainingMs(n);
            if (remain <= 0) {
                remain = ConfigStore.get().fixCooldownMs;
            }
            fixCooldownUntilMs = now + remain + 5000;
            Msg.title("§e/fix на КД, чекати ~" + (fixCooldownUntilMs - now) / 1000 + "с — чиню пляшками…");
        } else if (noHint) {
            fixCooldownUntilMs = now + ConfigStore.get().fixCooldownMs;
            AthAgnelUtils.LOGGER.info("[AthAgnelUtils][repair] /fix unavailable, cooldown set");
        } else if (okHint) {
            lastFixMs = now;
            fixCooldownUntilMs = now + ConfigStore.get().fixCooldownMs;
        }
    }

    private static long parseRemainingMs(String norm) {
        try {
            var m = java.util.regex.Pattern.compile("(\\d+)\\s*(мин|min|хв)").matcher(norm);
            if (m.find()) {
                return Long.parseLong(m.group(1)) * 60000L;
            }
            m = java.util.regex.Pattern.compile("(\\d+)\\s*(с|сек|sec|s)\\b").matcher(norm);
            if (m.find()) {
                return Long.parseLong(m.group(1)) * 1000L;
            }
            m = java.util.regex.Pattern.compile("(\\d+)").matcher(norm);
            if (m.find()) {
                return Long.parseLong(m.group(1)) * 1000L;
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    public static void start(String reason) {
        Minecraft client = Minecraft.getInstance();
        int left = client != null && client.player != null ? MinerFlow.pickaxeLeft(client) : -1;
        start(reason, left);
    }

    public static void start(String reason, int left) {
        if (active()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        var cfg = ConfigStore.get();
        phase = Phase.OPENING;
        containerId = -1;
        clicks = 0;
        throwsDone = 0;
        closeAttempts = 0;
        deadlineMs = System.currentTimeMillis() + 6000;
        lastActionMs = 0;
        lastBatchLeft = left;
        fixStartLeft = left;
        dumpAll = false;
        int glass = countItem(client, Items.GLASS_BOTTLE);
        if (cfg.glassExcessDumpAll && glass >= cfg.glassBottleLimit) {
            dumpAll = true;
        }
        int target = repairTarget(client);
        throwsPlanned = left >= 0
            ? Math.min(cfg.throwMaxPerCycle,
                ua.atherium.agnelutils.client.ClientStats.estimateThrows(Math.max(1, target - left)))
            : cfg.throwMaxPerCycle;
        if (dumpAll) {
            throwsPlanned = cfg.throwMaxPerCycle;
        }
        AthAgnelUtils.LOGGER.info("[AthAgnelUtils][repair] start (reason={}, left={}, target={}, planThrows={}, glass={}, dumpAll={})",
            reason, left, target, throwsPlanned, glass, dumpAll);
        if (dumpAll) {
            Msg.title("Пустих пляшок " + glass + " — зливаю весь досвід (розбиваю баночки)…");
        }
        if (cfg.repairUseFix && fixCooldownLeftMs() <= 0) {
            try {
                lastFixMs = System.currentTimeMillis();
                phase = Phase.FIX_WAIT;
                deadlineMs = System.currentTimeMillis() + 8000;
                Msg.title("Ремонт кірки: пробую /" + cfg.fixCommand + "…");
                client.player.connection.sendCommand(cfg.fixCommand);
            } catch (Throwable t) {
                AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][repair] fix send failed: {}", t.toString());
                beginFallback("fix-send-failed");
            }
            return;
        }
        if (cfg.repairUseFix) {
            AthAgnelUtils.LOGGER.info("[AthAgnelUtils][repair] /fix on cd ({}s left), bottles fallback",
                fixCooldownLeftMs() / 1000);
        }
        beginFallback(cfg.repairUseFix ? "fix-cd" : "fix-off");
    }

    private static int repairTarget(Minecraft client) {
        var cfg = ConfigStore.get();
        if (dumpAll) {
            try {
                int max = MinerFlow.maxDamage(client);
                if (max > 0) {
                    return max;
                }
            } catch (Throwable ignored) {
            }
        }
        return cfg.repairThreshold + 200;
    }

    private static void beginFallback(String why) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            abort("нема гравця");
            return;
        }
        var cfg = ConfigStore.get();
        AthAgnelUtils.LOGGER.info("[AthAgnelUtils][repair] fallback ({})", why);
        int nuggets = countItem(client, Items.GOLD_NUGGET);
        int ingots = countItem(client, Items.GOLD_INGOT);
        if (cfg.repairUseNuggets && nuggets < 4 && ingots > 0) {
            phase = Phase.NUGGETS_OPEN;
            deadlineMs = System.currentTimeMillis() + 6000;
            Msg.title("Ремонт: роблю самородки зі злитків…");
            try {
                client.player.connection.sendCommand(cfg.craftCommand);
            } catch (Throwable t) {
                abort("не вийшло відкрити /" + cfg.craftCommand);
            }
            return;
        }
        if (cfg.repairUseBubbleCraft) {
            openBubble(client);
            return;
        }
        phase = Phase.THROWING;
        deadlineMs = System.currentTimeMillis() + 5000;
        Msg.title("Ремонт кірки: кидаю пляшки досвіду…");
    }

    private static void openBubble(Minecraft client) {
        phase = Phase.OPENING;
        containerId = -1;
        deadlineMs = System.currentTimeMillis() + 6000;
        Msg.title("Ремонт кірки: відкриваю крафти…");
        try {
            client.player.connection.sendCommand(ConfigStore.get().craftsCommand);
        } catch (Throwable t) {
            abort("не вийшло відкрити /" + ConfigStore.get().craftsCommand);
        }
    }

    public static void tick(Minecraft client) {
        if (!active() || client.player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        switch (phase) {
            case FIX_WAIT -> {
                int left = MinerFlow.pickaxeLeft(client);
                int target = repairTarget(client);
                if (left >= 0 && left >= target) {
                    finish(left);
                    return;
                }
                if (fixStartLeft >= 0 && left >= 0 && left > fixStartLeft
                    && left >= ConfigStore.get().repairThreshold) {
                    finish(left);
                    return;
                }
                if (now > deadlineMs) {
                    int cur = MinerFlow.pickaxeLeft(client);
                    AthAgnelUtils.LOGGER.info("[AthAgnelUtils][repair] /fix gave nothing (left={}), fallback", cur);
                    beginFallback("fix-timeout");
                }
            }
            case NUGGETS_OPEN -> {
                var screen = ScreenTracker.current();
                if (screen instanceof AbstractContainerScreen<?> cont && isWorkbench(cont)) {
                    containerId = cont.getMenu().containerId;
                    phase = Phase.NUGGETS_PLACE;
                    lastActionMs = 0;
                    AthAgnelUtils.LOGGER.info("[AthAgnelUtils][repair] workbench id={}", containerId);
                } else if (now > deadlineMs) {
                    openBubble(client);
                }
            }
            case NUGGETS_PLACE -> {
                var screen = ScreenTracker.current();
                if (!(screen instanceof AbstractContainerScreen<?> cont) || cont.getMenu().containerId != containerId) {
                    abort("верстак закрився");
                    return;
                }
                if (now - lastActionMs < 700) {
                    return;
                }
                lastActionMs = now;
                if (!placeIngot(client, cont)) {
                    openBubble(client);
                    return;
                }
                phase = Phase.NUGGETS_TAKE;
                nuggetTakes = 0;
            }
            case NUGGETS_TAKE -> {
                var screen = ScreenTracker.current();
                if (!(screen instanceof AbstractContainerScreen<?> cont) || cont.getMenu().containerId != containerId) {
                    abort("верстак закрився");
                    return;
                }
                if (now - lastActionMs < 700) {
                    return;
                }
                lastActionMs = now;
                int nuggets = countItem(client, Items.GOLD_NUGGET);
                var result = cont.getMenu().getSlot(0).getItem();
                if (nuggets < 20 && nuggetTakes < 6 && !result.isEmpty() && result.getItem() == Items.GOLD_NUGGET) {
                    clickSlot(client, containerId, 0, ContainerInput.QUICK_MOVE);
                    nuggetTakes++;
                    return;
                }
                takeBackGrid(client, cont);
                closeMenu(client);
                openBubble(client);
            }
            case OPENING -> {
                var screen = ScreenTracker.current();
                if (screen instanceof AbstractContainerScreen<?> cont && isBubbleMenu(cont)) {
                    containerId = cont.getMenu().containerId;
                    phase = Phase.IN_MENU;
                    lastActionMs = 0;
                    AthAgnelUtils.LOGGER.info("[AthAgnelUtils][repair] bubble menu id={}", containerId);
                } else if (now > deadlineMs) {
                    abort("крафт-меню не відкрилось, перевір команду /" + ConfigStore.get().craftsCommand);
                }
            }
            case IN_MENU -> {
                var screen = ScreenTracker.current();
                if (!(screen instanceof AbstractContainerScreen<?> cont) || cont.getMenu().containerId != containerId) {
                    abort("меню закрилось");
                    return;
                }
                var cfg = ConfigStore.get();
                if (now - lastActionMs < cfg.repairClickDelayMs) {
                    return;
                }
                lastActionMs = now;
                int nuggets = countItem(client, Items.GOLD_NUGGET);
                int diamonds = countItem(client, Items.DIAMOND);
                if (nuggets >= 4 && diamonds >= 1 && clicks < cfg.repairMaxClicks) {
                    clicks++;
                    try {
                        client.gameMode.handleContainerInput(containerId, cfg.bubbleCraftSlot, 0,
                            ContainerInput.PICKUP, client.player);
                    } catch (Throwable t) {
                        abort("клік по крафту не вийшов: " + t);
                        return;
                    }
                } else {
                    AthAgnelUtils.LOGGER.info("[AthAgnelUtils][repair] crafts done (clicks={}, nuggets={}, diamonds={})",
                        clicks, nuggets, diamonds);
                    closeMenu(client);
                    phase = Phase.THROWING;
                    deadlineMs = now + 5000;
                }
            }
            case THROWING -> {
                if (ScreenTracker.current() != null) {
                    if (now > deadlineMs) {
                        if (closeAttempts < 3) {
                            closeAttempts++;
                            AthAgnelUtils.LOGGER.info("[AthAgnelUtils][repair] close retry {}", closeAttempts);
                            closeMenu(client);
                            deadlineMs = now + 2500;
                        } else {
                            abort("меню не закрилось");
                        }
                    }
                    return;
                }
                var cfg = ConfigStore.get();
                if (!selectPickaxe(client)) {
                    abort("кірка не в хотбарі!");
                    return;
                }
                int left = MinerFlow.pickaxeLeft(client);
                if (left >= 0 && left >= repairTarget(client)) {
                    finish(left);
                    return;
                }
                int bottleSlot = findHotbar(client, Items.EXPERIENCE_BOTTLE);
                if (bottleSlot < 0) {
                    if (left >= 0 && left > cfg.minPickaxeDurability) {
                        abort("бутильків нема (злишилось міцності " + left + "), копаю далі — стеж за кіркою");
                    } else {
                        abort("бутильків нема і кірка на межі! СТОП, чиню вручну");
                    }
                    return;
                }
                if (now - lastActionMs < cfg.throwDelayMs) {
                    return;
                }
                lastActionMs = now;
                try {
                    client.player.getInventory().setSelectedSlot(bottleSlot);
                    client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
                    throwsDone++;
                } catch (Throwable t) {
                    abort("кидок не вийшов: " + t);
                    return;
                }
                if (throwsDone % cfg.throwBatchCheck == 0 || throwsDone >= cfg.throwMaxPerCycle
                    || throwsDone >= throwsPlanned) {
                    int cur = MinerFlow.pickaxeLeft(client);
                    if (lastBatchLeft >= 0 && cur >= 0) {
                        double perThrow = (double) (cur - lastBatchLeft) / cfg.throwBatchCheck;
                        ua.atherium.agnelutils.client.ClientStats.recordThrow(perThrow);
                    }
                    lastBatchLeft = cur;
                    if (cur >= 0 && cur >= repairTarget(client)) {
                        finish(cur);
                    } else if (throwsDone >= cfg.throwMaxPerCycle) {
                        abort("викинув " + throwsDone + " бутильків, міцність " + cur + " — копаю далі, стеж за кіркою");
                    } else if (throwsDone >= throwsPlanned) {
                        throwsPlanned = Math.min(cfg.throwMaxPerCycle, throwsPlanned + 16);
                    }
                }
            }
            default -> {
            }
        }
    }

    private static boolean isBubbleMenu(AbstractContainerScreen<?> cont) {
        try {
            String title = ChatText.norm(cont.getTitle() != null ? cont.getTitle().getString() : "");
            for (String kw : ConfigStore.get().bubbleTitleKeywords) {
                if (!title.contains(ChatText.norm(kw))) {
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static int countItem(Minecraft client, Item item) {
        try {
            int n = 0;
            var inv = client.player.getInventory();
            int size = Math.min(inv.getContainerSize(), 36);
            for (int i = 0; i < size; i++) {
                ItemStack st = inv.getItem(i);
                if (!st.isEmpty() && st.getItem() == item) {
                    n += st.getCount();
                }
            }
            return n;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static int findHotbar(Minecraft client, Item item) {
        try {
            for (int i = 0; i < 9; i++) {
                ItemStack st = client.player.getInventory().getItem(i);
                if (!st.isEmpty() && st.getItem() == item) {
                    return i;
                }
            }
            return -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    private static boolean selectPickaxe(Minecraft client) {
        try {
            for (int i = 0; i < 9; i++) {
                ItemStack st = client.player.getInventory().getItem(i);
                if (!st.isEmpty() && String.valueOf(st.getItem()).contains("pickaxe")) {
                    client.player.getInventory().setSelectedSlot(i);
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void closeMenu(Minecraft client) {
        try {
            client.player.connection.getConnection().send(
                new net.minecraft.network.protocol.game.ServerboundContainerClosePacket(containerId));
        } catch (Throwable t) {
            AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][repair] close failed: {}", t.toString());
        }
    }

    private static boolean isWorkbench(AbstractContainerScreen<?> cont) {
        try {
            if (cont.getMenu() instanceof net.minecraft.world.inventory.CraftingMenu) {
                return true;
            }
            String title = ChatText.norm(cont.getTitle() != null ? cont.getTitle().getString() : "");
            for (String kw : ConfigStore.get().workbenchTitleKeywords) {
                if (!title.contains(ChatText.norm(kw))) {
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void clickSlot(Minecraft client, int cid, int slot, ContainerInput input) {
        client.gameMode.handleContainerInput(cid, slot, 0, input, client.player);
    }

    private static boolean placeIngot(Minecraft client, AbstractContainerScreen<?> cont) {
        try {
            var menu = cont.getMenu();
            int ingotSlot = -1;
            for (int i = 10; i < menu.slots.size(); i++) {
                var st = menu.slots.get(i).getItem();
                if (!st.isEmpty() && st.getItem() == Items.GOLD_INGOT) {
                    ingotSlot = menu.slots.get(i).index;
                    break;
                }
            }
            if (ingotSlot < 0) {
                return false;
            }
            int gridSlot = -1;
            for (int i = 1; i <= 9 && i < menu.slots.size(); i++) {
                if (menu.getSlot(i).getItem().isEmpty()) {
                    gridSlot = menu.getSlot(i).index;
                    break;
                }
            }
            if (gridSlot < 0) {
                return false;
            }
            clickSlot(client, containerId, ingotSlot, ContainerInput.PICKUP);
            clickSlot(client, containerId, gridSlot, ContainerInput.PICKUP);
            var placed = menu.getSlot(gridSlot).getItem();
            return !placed.isEmpty() && placed.getItem() == Items.GOLD_INGOT;
        } catch (Throwable t) {
            AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][repair] place ingot failed: {}", t.toString());
            return false;
        }
    }

    private static void takeBackGrid(Minecraft client, AbstractContainerScreen<?> cont) {
        try {
            var menu = cont.getMenu();
            for (int i = 1; i <= 9 && i < menu.slots.size(); i++) {
                var st = menu.getSlot(i).getItem();
                if (!st.isEmpty() && st.getItem() == Items.GOLD_INGOT) {
                    int idx = menu.getSlot(i).index;
                    clickSlot(client, containerId, idx, ContainerInput.PICKUP);
                    for (int j = 10; j < menu.slots.size(); j++) {
                        if (menu.slots.get(j).getItem().isEmpty()) {
                            clickSlot(client, containerId, menu.slots.get(j).index, ContainerInput.PICKUP);
                            break;
                        }
                    }
                    break;
                }
            }
        } catch (Throwable t) {
            AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][repair] takeback failed: {}", t.toString());
        }
    }

    private static void finish(int left) {
        phase = Phase.IDLE;
        ua.atherium.agnelutils.client.ClientStats.repairs++;
        ua.atherium.agnelutils.client.KeyHolder.releaseAll();
        AthAgnelUtils.LOGGER.info("[AthAgnelUtils][repair] done, durability left={} throws={}", left, throwsDone);
        Msg.title("§aКірка відремонтована §f(міцність " + left + "), копаю далі…");
    }

    private static void abort(String reason) {
        phase = Phase.IDLE;
        lastAbortMs = System.currentTimeMillis();
        AthAgnelUtils.LOGGER.info("[AthAgnelUtils][repair] abort: {}", reason);
        Msg.title("§eРемонт перервано: §f" + reason);
        MinerFlow.stopAttack();
        ua.atherium.agnelutils.client.KeyHolder.releaseAll();
    }
}
