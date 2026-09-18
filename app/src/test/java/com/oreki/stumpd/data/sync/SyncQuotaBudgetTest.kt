package com.oreki.stumpd.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class SyncQuotaBudgetTest {

    private lateinit var prefs: SharedPreferences
    private var now = 0L

    @Before
    fun setup() {
        prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("quota_budget_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        // Mid-morning US/Pacific so a +1 day jump cannot be mistaken for the same quota day.
        now = 1_700_000_000_000L
    }

    private fun budget(writeAllowance: Int = 100, readAllowance: Int = 200) =
        SyncQuotaBudget(
            prefs = { prefs },
            writeAllowance = writeAllowance,
            readAllowance = readAllowance,
            now = { now },
        )

    @Test
    fun `starts with the full allowance`() {
        val b = budget()
        assertThat(b.remainingWrites()).isEqualTo(100)
        assertThat(b.remainingReads()).isEqualTo(200)
    }

    @Test
    fun `spending reduces the remaining allowance`() {
        val b = budget()
        b.recordWrites(30)
        b.recordWrites(20)
        assertThat(b.writesSpentToday()).isEqualTo(50)
        assertThat(b.remainingWrites()).isEqualTo(50)
    }

    @Test
    fun `reads and writes are tracked separately`() {
        val b = budget()
        b.recordWrites(10)
        b.recordReads(40)
        assertThat(b.remainingWrites()).isEqualTo(90)
        assertThat(b.remainingReads()).isEqualTo(160)
    }

    @Test
    fun `an item that fits is affordable and one that does not is refused`() {
        val b = budget()
        b.recordWrites(95)
        assertThat(b.canAffordWrites(5)).isTrue()
        assertThat(b.canAffordWrites(6)).isFalse()
    }

    @Test
    fun `an oversized item is allowed when nothing has been spent yet`() {
        // Otherwise a single match larger than a whole day's allowance would block the queue
        // forever and the backlog could never drain.
        val b = budget(writeAllowance = 100)
        assertThat(b.canAffordWrites(5_000)).isTrue()

        b.recordWrites(1)
        assertThat(b.canAffordWrites(5_000)).isFalse()
    }

    @Test
    fun `allowance resets on the next quota day`() {
        val b = budget()
        b.recordWrites(100)
        assertThat(b.remainingWrites()).isEqualTo(0)

        now += TimeUnit.DAYS.toMillis(1)

        assertThat(b.writesSpentToday()).isEqualTo(0)
        assertThat(b.remainingWrites()).isEqualTo(100)
        assertThat(b.canAffordWrites(100)).isTrue()
    }

    @Test
    fun `spending after a day boundary does not inherit yesterday's total`() {
        val b = budget()
        b.recordWrites(80)
        now += TimeUnit.DAYS.toMillis(1)
        b.recordWrites(10)

        assertThat(b.writesSpentToday()).isEqualTo(10)
        assertThat(b.remainingWrites()).isEqualTo(90)
    }

    @Test
    fun `a new quota day clears both counters together`() {
        val b = budget()
        b.recordWrites(50)
        b.recordReads(150)
        now += TimeUnit.DAYS.toMillis(1)

        // Recording a write must not leave yesterday's read total in place.
        b.recordWrites(1)
        assertThat(b.readsSpentToday()).isEqualTo(0)
        assertThat(b.remainingReads()).isEqualTo(200)
    }

    @Test
    fun `remaining never goes negative when an oversized item is let through`() {
        val b = budget(writeAllowance = 100)
        b.recordWrites(500)
        assertThat(b.remainingWrites()).isEqualTo(0)
    }

    @Test
    fun `a large backlog drains over several days`() {
        // 20 matches of 900 writes each against a 5,000/day allowance: the point of the budget
        // is that this completes across days instead of failing at the cap.
        val b = budget(writeAllowance = 5_000)
        var remainingMatches = 20
        var days = 0

        while (remainingMatches > 0 && days < 30) {
            while (remainingMatches > 0 && b.canAffordWrites(900)) {
                b.recordWrites(900)
                remainingMatches--
            }
            now += TimeUnit.DAYS.toMillis(1)
            days++
        }

        assertThat(remainingMatches).isEqualTo(0)
        assertThat(days).isEqualTo(4)
    }

    @Test
    fun `zero and negative amounts are ignored`() {
        val b = budget()
        b.recordWrites(0)
        b.recordWrites(-5)
        assertThat(b.writesSpentToday()).isEqualTo(0)
    }

    @Test
    fun `reset clears today's usage`() {
        val b = budget()
        b.recordWrites(70)
        b.reset()
        assertThat(b.remainingWrites()).isEqualTo(100)
    }
}
