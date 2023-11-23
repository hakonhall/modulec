package no.ion.modulec.util;

import java.time.Duration;

public interface Formatter {
    static String toString(Duration duration) {
        long minutes = duration.toMinutes();
        if (minutes > 0) {
            return "%d:%02d".formatted((int) minutes, duration.toSecondsPart());
        } else {
            return "%.3fs".formatted(duration.toNanos() / 1_000_000_000d);
        }
    }
}
