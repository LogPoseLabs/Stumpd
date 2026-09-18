package com.oreki.stumpd.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The stats screens carry their date filter as a string the view models parse. Two screens had
 * their own copy of this formatting; these are the cases they both had to get right.
 */
class DateFilterLabelTest {

    @Test
    fun `all time is shown as it is stored`() {
        assertThat(formatDateFilterLabel("All Time")).isEqualTo("All Time")
    }

    @Test
    fun `a single date is shown in day-month-year`() {
        assertThat(formatDateFilterLabel("Date:2026-02-28")).isEqualTo("28 Feb 2026")
    }

    @Test
    fun `a range drops the year from the start date`() {
        assertThat(formatDateFilterLabel("CustomRange:2026-01-01|2026-01-31"))
            .isEqualTo("01 Jan - 31 Jan 2026")
    }

    @Test
    fun `an unparseable date falls back to the raw filter rather than crashing`() {
        assertThat(formatDateFilterLabel("Date:not-a-date")).isEqualTo("Date:not-a-date")
    }

    @Test
    fun `an unparseable range falls back to a generic label`() {
        assertThat(formatDateFilterLabel("CustomRange:2026-01-01")).isEqualTo("Custom Range")
        assertThat(formatDateFilterLabel("CustomRange:junk|junk")).isEqualTo("Custom Range")
    }

    @Test
    fun `an unrecognised filter is passed through`() {
        // The rolling-window screens store filters like this; showing the raw value is better
        // than showing nothing.
        assertThat(formatDateFilterLabel("Last 7 Days")).isEqualTo("Last 7 Days")
    }

    @Test
    fun `no pitch selection means both`() {
        assertThat(pitchTypeLabel(null)).isEqualTo("All Pitches")
        assertThat(pitchTypeLabel(true)).isEqualTo("Short Pitch")
        assertThat(pitchTypeLabel(false)).isEqualTo("Long Pitch")
    }

    @Test
    fun `the narrow filter buttons drop the word pitch`() {
        assertThat(pitchTypeLabel(true, abbreviated = true)).isEqualTo("Short")
        assertThat(pitchTypeLabel(false, abbreviated = true)).isEqualTo("Long")
        // "All" alone would read as "all groups" next to the group filter.
        assertThat(pitchTypeLabel(null, abbreviated = true)).isEqualTo("All Pitches")
    }
}
