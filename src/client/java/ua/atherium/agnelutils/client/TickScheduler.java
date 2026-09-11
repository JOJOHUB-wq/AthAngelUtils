package ua.atherium.agnelutils.client;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;

public final class TickScheduler {
    private record Entry(long runAtMs, Runnable task) {
    }

    private static final Queue<Entry> QUEUE = new ConcurrentLinkedQueue<>();

    private TickScheduler() {
    }

    public static void runLater(long delayMs, Runnable task) {
        QUEUE.add(new Entry(System.currentTimeMillis() + Math.max(0, delayMs), task));
    }

    public static void tick(Minecraft client) {
        if (QUEUE.isEmpty() || client == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Entry e;
        while ((e = QUEUE.peek()) != null && e.runAtMs() <= now) {
            QUEUE.poll();
            try {
                e.task().run();
            } catch (Throwable t) {
                ua.atherium.agnelutils.AthAgnelUtils.LOGGER.warn("[AthAgnelUtils] scheduled task failed: {}", t.toString());
            }
        }
    }

    public static void clear() {
        QUEUE.clear();
    }
}
