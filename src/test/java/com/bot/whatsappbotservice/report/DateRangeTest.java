package com.bot.whatsappbotservice.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.bot.whatsappbotservice.report.dto.DateRange;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class DateRangeTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Test
    void of_setsFromAndToAndZone() {
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to   = LocalDate.of(2026, 7, 10);

        DateRange range = DateRange.of(from, to, UTC);

        assertThat(range.from()).isEqualTo(from);
        assertThat(range.to()).isEqualTo(to);
        assertThat(range.zone()).isEqualTo(UTC);
    }

    @Test
    void of_fromInstantIsStartOfFromDayInZone() {
        LocalDate from = LocalDate.of(2026, 7, 1);
        DateRange range = DateRange.of(from, from, IST);

        Instant expected = from.atStartOfDay(IST).toInstant();
        assertThat(range.fromInstant()).isEqualTo(expected);
    }

    @Test
    void of_toInstantIsExclusiveUpperBound() {
        // toInstant must be the start of the day AFTER 'to', not the end of 'to'
        LocalDate to = LocalDate.of(2026, 7, 10);
        DateRange range = DateRange.of(to, to, UTC);

        Instant expectedExclusive = LocalDate.of(2026, 7, 11).atStartOfDay(UTC).toInstant();
        assertThat(range.toInstant()).isEqualTo(expectedExclusive);
    }

    @Test
    void of_toInstantRespectsTimezone() {
        // IST is UTC+5:30; start of July 11 IST is July 10 18:30 UTC
        LocalDate to = LocalDate.of(2026, 7, 10);
        DateRange range = DateRange.of(to, to, IST);

        Instant expectedExclusive = LocalDate.of(2026, 7, 11).atStartOfDay(IST).toInstant();
        assertThat(range.toInstant()).isEqualTo(expectedExclusive);
        // Sanity-check: different from the UTC equivalent
        assertThat(range.toInstant())
                .isNotEqualTo(LocalDate.of(2026, 7, 11).atStartOfDay(UTC).toInstant());
    }

    @Test
    void days_countsBothEndpointsInclusive() {
        DateRange range = DateRange.of(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10), UTC);

        assertThat(range.days()).isEqualTo(10);
    }

    @Test
    void days_singleDayRangeIsOne() {
        LocalDate day = LocalDate.of(2026, 7, 5);
        DateRange range = DateRange.of(day, day, UTC);

        assertThat(range.days()).isEqualTo(1);
    }

    @Test
    void previousPeriod_hasEqualLength() {
        DateRange range = DateRange.of(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10), UTC); // 10 days

        DateRange prev = range.previousPeriod();

        assertThat(prev.days()).isEqualTo(range.days());
    }

    @Test
    void previousPeriod_endsOneDayBeforeCurrentFrom() {
        LocalDate from = LocalDate.of(2026, 7, 1);
        DateRange range = DateRange.of(from, LocalDate.of(2026, 7, 10), UTC);

        DateRange prev = range.previousPeriod();

        assertThat(prev.to()).isEqualTo(from.minusDays(1)); // June 30
    }

    @Test
    void previousPeriod_startsCorrectlyForTenDayRange() {
        DateRange range = DateRange.of(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10), UTC); // 10 days

        DateRange prev = range.previousPeriod();

        // prev ends June 30, spans 10 days → starts June 21
        assertThat(prev.from()).isEqualTo(LocalDate.of(2026, 6, 21));
        assertThat(prev.to()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    void previousPeriod_inheritsZone() {
        DateRange range = DateRange.of(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 7), IST);

        assertThat(range.previousPeriod().zone()).isEqualTo(IST);
    }

    @Test
    void previousPeriod_toInstantEqualsCurrentFromInstant() {
        // The previous period ends exactly where this one starts — no gap, no overlap
        DateRange range = DateRange.of(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 7), UTC);

        DateRange prev = range.previousPeriod();

        assertThat(prev.toInstant()).isEqualTo(range.fromInstant());
    }
}
