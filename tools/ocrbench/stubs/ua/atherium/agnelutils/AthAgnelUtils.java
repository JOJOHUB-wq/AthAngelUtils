package ua.atherium.agnelutils;

/** Offline stub logger for the OCR benchmark harness. Not shipped. */
public final class AthAgnelUtils {
    public static final String MOD_ID = "athagnelutils";
    public static final Logger LOGGER = new Logger();

    private AthAgnelUtils() {
    }

    public static final class Logger {
        public void info(String fmt, Object... args) {
            log("INFO", fmt, args);
        }

        public void warn(String fmt, Object... args) {
            log("WARN", fmt, args);
        }

        private void log(String lvl, String fmt, Object[] args) {
            String out = fmt;
            int i = 0;
            while (out.contains("{}") && i < args.length) {
                out = out.replaceFirst("\\{\\}", java.util.regex.Matcher.quoteReplacement(String.valueOf(args[i++])));
            }
            if (System.getProperty("bench.verbose") != null) {
                System.out.println("[" + lvl + "] " + out);
            }
        }
    }
}
