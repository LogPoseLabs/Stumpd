package com.oreki.stumpd.viewmodel

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Joker and captain rotation only excludes players used "today", so this boundary decides who
 * is still eligible. Getting it wrong either drops matches (rotation repeats a player) or picks
 * up yesterday's (rotation pool exhausts early and falls back to pure random).
 */
class StartOfLocalDayTest {

    private fun at(zoneId: String, y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone(zoneId)).apply {
            clear()
            set(y, m - 1, d, h, min, 0)
        }.timeInMillis

    private fun localFields(zoneId: String, millis: Long): Triple<Int, Int, Int> =
        Calendar.getInstance(TimeZone.getTimeZone(zoneId)).apply { timeInMillis = millis }
            .let { Triple(it.get(Calendar.DAY_OF_MONTH), it.get(Calendar.HOUR_OF_DAY), it.get(Calendar.MINUTE)) }

    @Test
    fun `boundary is local midnight in IST`() {
        val zone = "Asia/Kolkata"
        val morningMatch = at(zone, 2026, 3, 15, 8, 30)

        val start = startOfLocalDayMillis(morningMatch, TimeZone.getTimeZone(zone))

        assertThat(localFields(zone, start)).isEqualTo(Triple(15, 0, 0))
    }

    @Test
    fun `an early morning match counts as today in IST`() {
        // The old UTC-based boundary landed at 05:30 IST, so a 02:00 match was treated as
        // yesterday and its joker stayed eligible for the very next match.
        val zone = "Asia/Kolkata"
        val now = at(zone, 2026, 3, 15, 9, 0)
        val twoAmMatch = at(zone, 2026, 3, 15, 2, 0)

        val start = startOfLocalDayMillis(now, TimeZone.getTimeZone(zone))

        assertThat(twoAmMatch).isAtLeast(start)
    }

    @Test
    fun `yesterday's evening match does not count as today in a western timezone`() {
        // The old boundary in US/Pacific landed on the previous afternoon, so yesterday's
        // matches were counted as today and the rotation pool emptied early.
        val zone = "America/Los_Angeles"
        val now = at(zone, 2026, 3, 15, 14, 0)
        val yesterdayEvening = at(zone, 2026, 3, 14, 19, 0)

        val start = startOfLocalDayMillis(now, TimeZone.getTimeZone(zone))

        assertThat(yesterdayEvening).isLessThan(start)
        assertThat(localFields(zone, start)).isEqualTo(Triple(15, 0, 0))
    }

    @Test
    fun `midnight itself is the start of the day`() {
        val zone = "Asia/Kolkata"
        val midnight = at(zone, 2026, 3, 15, 0, 0)

        assertThat(startOfLocalDayMillis(midnight, TimeZone.getTimeZone(zone))).isEqualTo(midnight)
    }

    @Test
    fun `a minute before midnight belongs to the previous day`() {
        val zone = "Asia/Kolkata"
        val lateNight = at(zone, 2026, 3, 15, 23, 59)

        val start = startOfLocalDayMillis(lateNight, TimeZone.getTimeZone(zone))

        assertThat(localFields(zone, start)).isEqualTo(Triple(15, 0, 0))
        assertThat(start).isLessThan(lateNight)
    }
}
