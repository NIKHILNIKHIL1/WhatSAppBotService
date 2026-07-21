package com.bot.whatsappbotservice.report.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Resolved date range for a report query: the caller's LocalDate boundaries plus the Instant
 * equivalents used in JPQL/SQL predicates and the ZoneId for date-bucketing in GROUP BY clauses.
 *
 * {@code toInstant} is an exclusive upper bound — midnight at the start of the day after
 * {@code to} — so queries use {@code created_at >= fromInstant AND created_at < toInstant}
 * and capture the entire last day without microsecond gaps.
 */
public record DateRange(
        LocalDate from,
        LocalDate to,
        Instant fromInstant,
        Instant toInstant,
        ZoneId zone
) {

    /** Builds a DateRange from LocalDate boundaries in the given timezone. */
    public static DateRange of(LocalDate from, LocalDate to, ZoneId zone) {
        Instant fromInstant = from.atStartOfDay(zone).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(zone).toInstant();
        return new DateRange(from, to, fromInstant, toInstant, zone);
    }

    /** Number of days covered by this range, inclusive on both ends. */
    public long days() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }

    /**
     * Returns a DateRange of equal length immediately before this one, used for
     * period-over-period growth comparisons on the Overview dashboard.
     * The previous period ends the day before {@code from} and has the same number of days.
     */
    public DateRange previousPeriod() {
        long d = days();
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(d - 1);
        return DateRange.of(prevFrom, prevTo, zone);
    }
}
