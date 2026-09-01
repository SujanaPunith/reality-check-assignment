package com.comeon.assignment.realitycheck.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class RealityCheckTimeFormatter {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("d MMMM yy HH:mm", Locale.ENGLISH);

    public String format(Long epochSeconds, String timezone) {
        if (epochSeconds == null) {
            return null;
        }

        ZoneId zoneId = ZoneId.of(timezone);

        return ZonedDateTime.ofInstant(
                Instant.ofEpochSecond(epochSeconds),
                zoneId
        ).format(FORMATTER);
    }
}
