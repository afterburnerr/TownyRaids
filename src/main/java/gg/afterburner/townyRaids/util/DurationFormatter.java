package gg.afterburner.townyRaids.util;

import java.time.Duration;
import java.util.Locale;

public final class DurationFormatter {

    private DurationFormatter() {}

    public static String format(Duration duration) {
        if (duration.isNegative() || duration.isZero()) {
            return "0s";
        }
        long totalSeconds = duration.getSeconds();
        long days = totalSeconds / 86_400;
        long hours = (totalSeconds % 86_400) / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder out = new StringBuilder();
        if (days > 0) {
            out.append(days).append("d ");
            if (hours > 0) out.append(hours).append("h");
            return out.toString().trim();
        }
        if (hours > 0) {
            out.append(hours).append("h ");
            if (minutes > 0) out.append(minutes).append("m");
            return out.toString().trim();
        }
        if (minutes > 0) {
            out.append(minutes).append("m ");
            if (seconds > 0) out.append(seconds).append("s");
            return out.toString().trim();
        }
        return String.format(Locale.ROOT, "%ds", seconds);
    }

    public static String formatSeconds(long seconds) {
        return format(Duration.ofSeconds(Math.max(0, seconds)));
    }
}
