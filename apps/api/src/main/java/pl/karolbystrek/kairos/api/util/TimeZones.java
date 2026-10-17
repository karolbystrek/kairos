package pl.karolbystrek.kairos.api.util;

import java.time.ZoneId;
import java.util.Set;

public final class TimeZones {

    private static final Set<String> IANA_IDS = Set.copyOf(ZoneId.getAvailableZoneIds());

    private TimeZones() {
    }

    public static boolean isSupported(String candidate) {
        return candidate != null && IANA_IDS.contains(candidate);
    }
}
