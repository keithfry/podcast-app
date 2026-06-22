package com.frybynite.podlore.ui.player

import org.junit.Test
import kotlin.test.assertEquals

class SleepTimerFormatTest {

    @Test fun `seconds only when under 60`() {
        assertEquals("45s", formatSleepTimer(45))
    }

    @Test fun `shows 0s at zero`() {
        assertEquals("0s", formatSleepTimer(0))
    }

    @Test fun `shows 1s for one second`() {
        assertEquals("1s", formatSleepTimer(1))
    }

    @Test fun `shows mm colon ss for exactly one minute`() {
        assertEquals("1:00", formatSleepTimer(60))
    }

    @Test fun `pads seconds to two digits`() {
        assertEquals("1:05", formatSleepTimer(65))
    }

    @Test fun `shows minutes and seconds for mid-range value`() {
        assertEquals("4:30", formatSleepTimer(270))
    }

    @Test fun `handles 30 minutes`() {
        assertEquals("30:00", formatSleepTimer(1800))
    }

    @Test fun `59 seconds is seconds-only`() {
        assertEquals("59s", formatSleepTimer(59))
    }
}
