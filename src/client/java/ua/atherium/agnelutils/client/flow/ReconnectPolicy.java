package ua.atherium.agnelutils.client.flow;

/**
 * Чиста (без Minecraft-залежностей) політика реконекта: бек-оф, пауза після серії провалів,
 * детект БАНу. Виділена окремо, щоб її можна було покрити юніт-тестами без клієнта.
 */
public final class ReconnectPolicy {
    private ReconnectPolicy() {
    }

    /** Експоненційний бек-оф: base * 2^attempts, обмежений max. */
    public static long delayMs(int attempts, long baseSec, long maxSec) {
        long d = baseSec * 1000L * (1L << Math.max(0, Math.min(attempts, 6)));
        return Math.min(d, maxSec * 1000L);
    }

    /** Чи треба ставити тривалу паузу після серії провалів. */
    public static boolean shouldPause(int fails, int maxFails) {
        return maxFails > 0 && fails >= maxFails;
    }

    /** Чи схоже причина кіку на БАН (тоді реконект безсенсовний). */
    public static boolean isBanReason(String normReason) {
        if (normReason == null) {
            return false;
        }
        String n = normReason;
        return n.contains("ban") || n.contains("бан") || n.contains("заблок")
            || n.contains("blacklist") || n.contains("чорн") || n.contains("перманент");
    }
}
