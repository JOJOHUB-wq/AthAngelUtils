package ua.atherium.agnelutils.client.flow;

/** Юніт-тести чистої політики реконекта. Запускаються звичайним javac/java. Not shipped. */
public final class PolicyTest {
    private static int failed = 0;

    public static void main(String[] a) {
        // backoff: base*2^attempts capped
        check(ReconnectPolicy.delayMs(0, 5, 300) == 5000, "delay attempts=0 -> 5s");
        check(ReconnectPolicy.delayMs(1, 5, 300) == 10000, "delay attempts=1 -> 10s");
        check(ReconnectPolicy.delayMs(3, 5, 300) == 40000, "delay attempts=3 -> 40s");
        check(ReconnectPolicy.delayMs(10, 5, 300) == 300000, "delay capped at max 300s");
        check(ReconnectPolicy.delayMs(-1, 5, 300) == 5000, "negative attempts clamped to base");
        // pause
        check(!ReconnectPolicy.shouldPause(14, 15), "no pause below maxFails");
        check(ReconnectPolicy.shouldPause(15, 15), "pause at maxFails");
        check(!ReconnectPolicy.shouldPause(99, 0), "maxFails=0 -> never pause");
        // ban detection
        check(ReconnectPolicy.isBanReason("you are banned"), "ban en");
        check(ReconnectPolicy.isBanReason("ви забанені назавжди"), "ban ua");
        check(ReconnectPolicy.isBanReason("blacklist"), "blacklist");
        check(!ReconnectPolicy.isBanReason("connection timed out"), "timeout not ban");
        check(!ReconnectPolicy.isBanReason(null), "null not ban");
        if (failed == 0) {
            System.out.println("PolicyTest: ALL PASS");
        } else {
            System.out.println("PolicyTest: " + failed + " FAILED");
            System.exit(1);
        }
    }

    private static void check(boolean ok, String name) {
        if (ok) {
            System.out.println("  ok  " + name);
        } else {
            System.out.println("  FAIL " + name);
            failed++;
        }
    }
}
