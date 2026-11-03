package com.jovensbuzz;

import com.jovensbuzz.common.exception.BadRequestException;
import com.jovensbuzz.common.util.TimeZones;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class TimeZonesTest {

    private static final ZoneId CHICAGO = ZoneId.of("America/Chicago");
    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");

    @ParameterizedTest
    @ValueSource(strings = {"America/Chicago", "Asia/Kolkata", "Europe/London", "UTC"})
    void acceptsIanaRegions(String zone) {
        assertEquals(zone, TimeZones.requireRegion(" " + zone + " "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"+05:30", "-06:00", "GMT+5", "UTC+05:30", "Etc/GMT+6", "CST", "Mars/Olympus", ""})
    void rejectsFixedOffsetsAndUnknownZones(String zone) {
        assertThrows(BadRequestException.class, () -> TimeZones.requireRegion(zone));
    }

    @Test
    void chicago_switchesBetweenCstAndCdt_automatically() {
        // 2026-03-08: 02:00 CST jumps to 03:00 CDT (08:00 UTC)
        assertEquals("Mar 8, 2026, 1:30 AM CST", TimeZones.formatWithZone(Instant.parse("2026-03-08T07:30:00Z"), CHICAGO));
        assertEquals("Mar 8, 2026, 4:00 AM CDT", TimeZones.formatWithZone(Instant.parse("2026-03-08T09:00:00Z"), CHICAGO));
        // 2026-11-01: 02:00 CDT falls back to 01:00 CST (07:00 UTC); 01:30 happens twice
        assertEquals("Nov 1, 2026, 1:30 AM CDT", TimeZones.formatWithZone(Instant.parse("2026-11-01T06:30:00Z"), CHICAGO));
        assertEquals("Nov 1, 2026, 1:30 AM CST", TimeZones.formatWithZone(Instant.parse("2026-11-01T07:30:00Z"), CHICAGO));
    }

    @Test
    void sameEvent_showsCorrespondingLocalTimes_inIndiaAndTexas() {
        Instant event = Instant.parse("2026-07-15T14:00:00Z");
        assertEquals("Jul 15, 2026, 9:00 AM CDT", TimeZones.formatWithZone(event, CHICAGO));
        assertEquals("Jul 15, 2026, 7:30 PM IST", TimeZones.formatWithZone(event, KOLKATA));
        Instant winter = Instant.parse("2026-01-15T14:00:00Z");
        assertEquals("Jan 15, 2026, 8:00 AM CST", TimeZones.formatWithZone(winter, CHICAGO));
        assertEquals("Jan 15, 2026, 7:30 PM IST", TimeZones.formatWithZone(winter, KOLKATA), "India has no DST");
    }

    @Test
    void invalidStoredZone_fallsBackToDefault() {
        assertEquals(ZoneId.of(TimeZones.DEFAULT_ZONE), TimeZones.zoneOrDefault("+05:30"));
        assertEquals(KOLKATA, TimeZones.zoneOrDefault("Asia/Kolkata"));
    }
}
