package com.gatekeep.app.util

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SingleFlightGateTest {

    @Test
    fun runIfIdle_skipsSecondCallWhileFirstInProgress() = runBlocking {
        val gate = SingleFlightGate()
        var runs = 0
        val first = async {
            gate.runIfIdle {
                runs++
                delay(200)
            }
        }
        delay(20)
        gate.runIfIdle { runs++ }
        first.await()
        assertEquals(1, runs)
    }
}
