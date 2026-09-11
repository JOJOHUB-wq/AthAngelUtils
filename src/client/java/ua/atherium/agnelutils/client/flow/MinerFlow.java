package ua.atherium.agnelutils.client.flow;

import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import ua.atherium.agnelutils.AthAgnelUtils;
import ua.atherium.agnelutils.client.ScreenTracker;
import ua.atherium.agnelutils.client.TickScheduler;
import ua.atherium.agnelutils.client.config.ConfigStore;
import ua.atherium.agnelutils.client.util.Msg;

public final class MinerFlow {
    public static KeyMapping toggleKey;
    private static boolean toggleOn = false;
    private static long lastWarpMs = 0;
    private static long lastMineMs = 0;
    private static String lastTargetKey = "";
    private static long sameTargetSinceMs = 0;
    private static long noTargetSinceMs = 0;
    private static int stuckEpisodes = 0;
    private static long lastDisposableWarnMs = 0;
    private static boolean walkingBox = false;
    private static long walkStartMs = 0;
    private static long lastJumpMs = 0;
    private static long lastDenyRewarpMs = 0;
    private static int denyStreak = 0;

    private MinerFlow() {
    }

    public static boolean capsLockOn() {
        try {
            return Toolkit.getDefaultToolkit().getLockingKeyState(KeyEvent.VK_CAPS_LOCK);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean gateOpen() {
        var cfg = ConfigStore.get();
        if (!cfg.minerEnabled) {
            return false;
        }
        if (ua.atherium.agnelutils.client.flow.FarmMode.active()) {
            return false;
        }
        if (cfg.capslockGate && capsLockOn()) {
            return true;
        }
        return toggleOn;
    }

    public static void flipToggle() {
        var cfg = ConfigStore.get();
        if (!cfg.minerEnabled) {
            cfg.minerEnabled = true;
            ConfigStore.save();
            Msg.title("Майнер §aввімкнено в конфігу §7(збережено)");
        }
        toggleOn = !toggleOn;
        Msg.title("Майнер: " + (toggleOn ? "§aУВІМК" : "§cВИМК") + " §7(CapsLock=" + (capsLockOn() ? "ON" : "OFF") + ")");
        if (toggleOn) {
            warpMine();
        } else {
            stopAttack();
        }
    }

    public static void warpMine() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastWarpMs < 5000) {
            return;
        }
        lastWarpMs = now;
        lastTargetKey = "";
        sameTargetSinceMs = 0;
        noTargetSinceMs = 0;
        stuckEpisodes = 0;
        String cmd = ConfigStore.get().warpMineCommand;
        AthAgnelUtils.LOGGER.info("[AthAgnelUtils][miner] /{}", cmd);
        Msg.title("Варпаюсь: /" + cmd);
        TickScheduler.runLater(300, () -> {
            try {
                var p = Minecraft.getInstance().player;
                if (p != null) {
                    p.connection.sendCommand(cmd);
                }
            } catch (Throwable t) {
                AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][miner] warp failed: {}", t.toString());
            }
        });
    }

    public static void tick(Minecraft client) {
        pollToggleKey();
        if (client.player == null || client.level == null || client.gameMode == null) {
            return;
        }
        if (!gateOpen()) {
            stopAttack();
            stopWalk(Minecraft.getInstance());
            ua.atherium.agnelutils.client.KeyHolder.releaseAll();
            return;
        }
        var cfg = ConfigStore.get();
        if (RepairFlow.active()) {
            stopAttack();
            return;
        }
        if (ScreenTracker.current() != null) {
            stopAttack();
            stopWalk(client);
            ua.atherium.agnelutils.client.KeyHolder.releaseAll();
            return;
        }
        if (cfg.mineBoxEnforce && !insideBox(client.player.blockPosition(), cfg)) {
            stopAttack();
            double dist = distToBoxCenter(client, cfg);
            if (dist > 40 && System.currentTimeMillis() - lastWarpMs > 15000) {
                Msg.actionbar("[AthAgnel] Далеко від шахти — варп…");
                warpMine();
            } else {
                walkTick(client, cfg);
            }
            return;
        }
        try {
            if (cfg.noFly && client.player.getAbilities().flying) {
                stopAttack();
                Msg.actionbar("[AthAgnel] Вимкни флай — реси не підбираються!");
                return;
            }
        } catch (Throwable ignored) {
        }
        if (!pickaxeOk(client)) {
            stopAttack();
            return;
        }
        int left = pickaxeLeft(client);
        if (left >= 0 && !RepairFlow.active()
            && System.currentTimeMillis() - RepairFlow.lastAbort() > cfg.repairAbortCooldownMs) {
            int max = maxDamage(client);
            int effThreshold = max > 0 ? Math.min(cfg.repairThreshold, max * 3 / 10) : cfg.repairThreshold;
            if (left < effThreshold) {
                if (hasMending(client)) {
                    stopAttack();
                    RepairFlow.start("durability-" + left, left);
                    return;
                } else if (System.currentTimeMillis() - lastDisposableWarnMs > 60000) {
                    lastDisposableWarnMs = System.currentTimeMillis();
                    Msg.title("§eКірка без лагодження (міцність " + left + ") — заміни вручну!");
                }
            }
        }
        long now = System.currentTimeMillis();
        if (now - lastMineMs < cfg.mineTickDelayMs) {
            return;
        }
        lastMineMs = now;
        BlockPos target = findTarget(client, cfg.mineRadius);
        if (target == null) {
            stopAttack();
            if (noTargetSinceMs == 0) {
                noTargetSinceMs = now;
            }
            if (now - noTargetSinceMs > cfg.noTargetStepMs) {
                noTargetSinceMs = now;
                step(client, (int) cfg.stuckStepMs);
            }
            return;
        }
        noTargetSinceMs = 0;
        String key = target.getX() + "," + target.getY() + "," + target.getZ();
        if (key.equals(lastTargetKey)) {
            if (now - sameTargetSinceMs > cfg.stuckTimeoutMs) {
                sameTargetSinceMs = now;
                stuckEpisodes++;
                ua.atherium.agnelutils.client.ClientStats.stucks++;
                AthAgnelUtils.LOGGER.info("[AthAgnelUtils][miner] stuck #{} at {}", stuckEpisodes, key);
                if (stuckEpisodes >= cfg.maxStucksBeforeRewarp) {
                    stuckEpisodes = 0;
                    ua.atherium.agnelutils.client.ClientStats.rewarps++;
                    Msg.title("Застряг — реварпаюсь на шахту…");
                    stopAttack();
                    warpMine();
                    return;
                }
                step(client, (int) cfg.stuckStepMs);
            }
        } else {
            lastTargetKey = key;
            sameTargetSinceMs = now;
        }
        if (cfg.lookAtTargets) {
            lookAt(client, target);
        }
        try {
            ua.atherium.agnelutils.client.KeyHolder.set(client.options.keyAttack, true);
            ua.atherium.agnelutils.client.ClientStats.attackTicks++;
        } catch (Throwable t) {
            AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][miner] attack failed: {}", t.toString());
        }
    }

    private static void lookAt(Minecraft client, BlockPos target) {
        try {
            var eye = client.player.getEyePosition();
            double dx = (target.getX() + 0.5) - eye.x;
            double dy = (target.getY() + 0.5) - eye.y;
            double dz = (target.getZ() + 0.5) - eye.z;
            float yaw = (float) (Math.atan2(-dx, dz) * 57.29577951308232);
            float pitch = (float) (-(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 57.29577951308232));
            client.player.setYRot(yaw);
            client.player.setXRot(pitch);
        } catch (Throwable ignored) {
        }
    }

    private static void step(Minecraft client, int ms) {
        try {
            ua.atherium.agnelutils.client.KeyHolder.set(client.options.keyUp, true);
            ua.atherium.agnelutils.client.KeyHolder.set(client.options.keyJump, true);
            TickScheduler.runLater(ms, () -> {
                try {
                    Minecraft c = Minecraft.getInstance();
                    if (c != null && c.options != null) {
                        ua.atherium.agnelutils.client.KeyHolder.set(c.options.keyUp, false);
                        ua.atherium.agnelutils.client.KeyHolder.set(c.options.keyJump, false);
                    }
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static void pollToggleKey() {
        try {
            if (toggleKey != null) {
                while (toggleKey.consumeClick()) {
                    Minecraft client = Minecraft.getInstance();
                    if (client != null) {
                        client.setScreenAndShow(
                            new ua.atherium.agnelutils.client.gui.AgnelMenuScreen());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }
    public static int pickaxeLeft(Minecraft client) {
        try {
            var stack = client.player.getMainHandItem();
            if (stack.isEmpty() || !String.valueOf(stack.getItem()).contains("pickaxe")) {
                return -1;
            }
            int max = stack.getMaxDamage();
            if (max <= 0) {
                return Integer.MAX_VALUE;
            }
            return max - stack.getDamageValue();
        } catch (Throwable t) {
            return -1;
        }
    }

    static int maxDamage(Minecraft client) {
        try {
            return client.player.getMainHandItem().getMaxDamage();
        } catch (Throwable t) {
            return 0;
        }
    }

    static boolean hasMending(Minecraft client) {
        try {
            var stack = client.player.getMainHandItem();
            var ench = stack.getEnchantments();
            if (ench == null || ench.isEmpty()) {
                return stack.getMaxDamage() >= 300;
            }
            var holder = client.level.registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.MENDING);
            return ench.getLevel(holder) > 0;
        } catch (Throwable t) {
            try {
                return client.player.getMainHandItem().getMaxDamage() >= 300;
            } catch (Throwable ignored) {
                return true;
            }
        }
    }

    static boolean pickaxeOk(Minecraft client) {        try {
            var cfg = ConfigStore.get();
            var stack = client.player.getMainHandItem();
            if (stack.isEmpty()) {
                Msg.actionbar("[AthAgnel] Візьми кірку в руку!");
                return false;
            }
            String id = String.valueOf(stack.getItem());
            if (!id.contains("pickaxe")) {
                Msg.actionbar("[AthAgnel] Візьми кірку в руку!");
                return false;
            }
            int max = stack.getMaxDamage();
            int dmg = stack.getDamageValue();
            int left = max - dmg;
            if (max > 0 && left < cfg.minPickaxeDurability) {
                stopAttack();
                Msg.title("§cСТОП! §fКірка майже зламана (залишилось " + left + "). Чиню/міняю вручну!");
                AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][miner] durability guard: left={} min={}", left,
                    cfg.minPickaxeDurability);
                return false;
            }
            return true;
        } catch (Throwable t) {
            return true;
        }
    }

    private static BlockPos findTarget(Minecraft client, int radius) {
        try {
            var player = client.player;
            var level = client.level;
            var cfg = ConfigStore.get();
            HitResult hr = client.hitResult;
            if (hr instanceof BlockHitResult bhr) {
                BlockPos p = bhr.getBlockPos();
                if ((!cfg.mineBoxEnforce || insideBox(p, cfg)) && isMineable(level.getBlockState(p), level, p)) {
                    return p;
                }
            }
            BlockPos base = player.blockPosition();
            double best = Double.MAX_VALUE;
            BlockPos bestPos = null;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -3; dy <= 2; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        BlockPos p = base.offset(dx, dy, dz);
                        if (cfg.mineBoxEnforce && !insideBox(p, cfg)) {
                            continue;
                        }
                        if (!isMineable(level.getBlockState(p), level, p)) {
                            continue;
                        }
                        double d = p.distSqr(base);
                        if (d < best) {
                            best = d;
                            bestPos = p;
                        }
                    }
                }
            }
            return bestPos;
        } catch (Throwable t) {
            return null;
        }
    }

    static double distToBoxCenter(Minecraft client, ua.atherium.agnelutils.client.config.ModConfig cfg) {
        try {
            int[] b = cfg.mineBox;
            if (b == null || b.length < 6) {
                return 0;
            }
            double cx = (b[0] + b[3]) / 2.0;
            double cy = (b[1] + b[4]) / 2.0;
            double cz = (b[2] + b[5]) / 2.0;
            var e = client.player.getEyePosition();
            double dx = cx - e.x;
            double dy = cy - e.y;
            double dz = cz - e.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        } catch (Throwable t) {
            return 0;
        }
    }

    static boolean insideBox(BlockPos p, ua.atherium.agnelutils.client.config.ModConfig cfg) {        try {
            int[] b = cfg.mineBox;
            if (b == null || b.length < 6) {
                return true;
            }
            return p.getX() >= b[0] && p.getX() <= b[3]
                && p.getY() >= b[1] && p.getY() <= b[4]
                && p.getZ() >= b[2] && p.getZ() <= b[5];
        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean isMineable(BlockState st, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        try {
            if (st.isAir() || st.is(Blocks.BEDROCK) || st.is(Blocks.BARRIER) || st.is(Blocks.OBSIDIAN)) {
                return false;
            }
            float hardness = st.getDestroySpeed(level, pos);
            return hardness >= 0 && hardness < 50;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void stopAttack() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.options != null) {
                ua.atherium.agnelutils.client.KeyHolder.set(client.options.keyAttack, false);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void resetDenyStreak() {
        denyStreak = 0;
    }

    public static void denyRewarp() {
        long now = System.currentTimeMillis();
        stopAttack();
        if (now - lastDenyRewarpMs < ConfigStore.get().denyRewarpCooldownMs) {
            return;
        }
        lastDenyRewarpMs = now;
        denyStreak++;
        if (denyStreak >= 4) {
            denyStreak = 0;
            Msg.title("Багато відмов — йду в бокс ногами…");
            startWalk();
            return;
        }
        Msg.title("§cНе та зона (рг спавна?) — реварп на шахту…");
        warpMine();
    }

    public static void startWalk() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        if (!walkingBox) {
            walkingBox = true;
            walkStartMs = System.currentTimeMillis();
            var p = client.player.blockPosition();
            AthAgnelUtils.LOGGER.info("[AthAgnelUtils][miner] walk to box from {},{},{}", p.getX(), p.getY(), p.getZ());
            Msg.title("Йду в бокс шахти…");
        }
    }

    private static void walkTick(Minecraft client, ua.atherium.agnelutils.client.config.ModConfig cfg) {
        if (!walkingBox) {
            startWalk();
        }
        long now = System.currentTimeMillis();
        var p = client.player.blockPosition();
        if (insideBox(p, cfg)) {
            stopWalk(client);
            Msg.title("Дійшов до бокса, копаю…");
            return;
        }
        if (now - walkStartMs > cfg.walkTimeoutMs) {
            stopWalk(client);
            Msg.title("Не дійшов — реварп…");
            warpMine();
            return;
        }
        try {
            int[] b = cfg.mineBox;
            double cx = (b[0] + b[3]) / 2.0;
            double cz = (b[2] + b[5]) / 2.0;
            var eye = client.player.getEyePosition();
            double dx = cx - eye.x;
            double dz = cz - eye.z;
            float yaw = (float) (Math.atan2(-dx, dz) * 57.29577951308232);
            client.player.setYRot(yaw);
            client.player.setXRot(10.0f);
            ua.atherium.agnelutils.client.KeyHolder.set(client.options.keyUp, true);
            if (now - lastJumpMs > 700) {
                lastJumpMs = now;
                ua.atherium.agnelutils.client.KeyHolder.set(client.options.keyJump, true);
                TickScheduler.runLater(250, () -> {
                    try {
                        Minecraft c = Minecraft.getInstance();
                        if (c != null && c.options != null) {
                            ua.atherium.agnelutils.client.KeyHolder.set(c.options.keyJump, false);
                        }
                    } catch (Throwable ignored) {
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    private static void stopWalk(Minecraft client) {
        walkingBox = false;
        try {
            ua.atherium.agnelutils.client.KeyHolder.set(client.options.keyUp, false);
            ua.atherium.agnelutils.client.KeyHolder.set(client.options.keyJump, false);
        } catch (Throwable ignored) {
        }
    }
}
