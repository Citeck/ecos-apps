package ru.citeck.ecos.apps.domain.patch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.citeck.ecos.apps.domain.patch.service.PatchSweep

class PatchSweepTest {

    /**
     * Steps performed per app, in the order the sweep asked for them.
     */
    private class StepLog {
        val calls = mutableListOf<Pair<String, Int>>()
        fun stepsOf(appName: String) = calls.filter { it.first == appName }.sumOf { it.second }
        fun passCountOf(appName: String) = calls.count { it.first == appName }
    }

    private fun run(
        apps: Set<String>,
        maxStepsPerApp: Int = 10,
        isActive: () -> Boolean = { true },
        log: StepLog = StepLog(),
        work: (appName: String, stepsLimit: Int) -> Int
    ): StepLog {
        return run({ apps }, maxStepsPerApp, isActive, log, work)
    }

    private fun run(
        apps: () -> Set<String>,
        maxStepsPerApp: Int = 10,
        isActive: () -> Boolean = { true },
        log: StepLog = StepLog(),
        work: (appName: String, stepsLimit: Int) -> Int
    ): StepLog {
        PatchSweep.run(
            maxStepsPerApp = maxStepsPerApp,
            isActive = isActive,
            apps = apps,
            step = { appName, stepsLimit ->
                val steps = work(appName, stepsLimit)
                log.calls.add(appName to steps)
                steps
            }
        )
        return log
    }

    @Test
    fun sweepThatDoesNothingEndsTick() {
        val log = run(setOf("a", "b")) { _, _ -> 0 }

        assertThat(log.calls).containsExactly("a" to 0, "b" to 0)
    }

    @Test
    fun appThatGainsWorkAfterSweepPassedItIsSweptAgain() {
        // The reason the loop exists: applying a patch of "b" hands the job a patch of "a", which
        // this pass has already been through. Ending the tick here would make that patch wait out
        // the job's fixed delay for nothing.
        var pass = 0
        val log = run(setOf("a", "b")) { appName, _ ->
            when {
                appName == "b" && pass == 0 -> 1
                appName == "a" && pass == 1 -> 1
                else -> 0
            }.also { if (appName == "b") pass++ }
        }

        assertThat(log.stepsOf("a")).isEqualTo(1)
        assertThat(log.passCountOf("a")).isGreaterThan(1)
    }

    @Test
    fun appBudgetIsNotRefilledBySweeps() {
        // Refilling the budget on every sweep would make the tick unbounded.
        val log = run(setOf("a"), maxStepsPerApp = 10) { _, stepsLimit -> stepsLimit.coerceAtMost(4) }

        assertThat(log.stepsOf("a")).isEqualTo(10)
    }

    @Test
    fun endlesslyBusyAppDoesNotConsumeShareOfOthers() {
        // The bug this pins: with one budget for the whole tick, the first app drained it and every
        // later app was handed a limit of 0, tick after tick.
        val log = run(setOf("busy", "quiet"), maxStepsPerApp = 5) { appName, stepsLimit ->
            if (appName == "busy") stepsLimit else 1
        }

        assertThat(log.stepsOf("busy")).isEqualTo(5)
        assertThat(log.stepsOf("quiet"))
            .describedAs("the second app got its own full budget")
            .isEqualTo(5)
    }

    @Test
    fun losingLockAbandonsTickBeforeNextSweep() {
        var active = true
        val log = run(setOf("a"), isActive = { active }) { _, _ ->
            active = false
            1
        }

        assertThat(log.calls).containsExactly("a" to 1)
    }

    @Test
    fun appThatGoesAwayMidTickIsNotSweptAgain() {
        // The app set is re-read on every sweep on purpose: apps come and go while a tick runs, and
        // a set captured once would keep handing work to an app that is no longer there.
        var sweeps = 0
        val log = run(
            apps = { if (sweeps++ == 0) setOf("a", "b") else setOf("a") },
            maxStepsPerApp = 3
        ) { _, _ -> 1 }

        assertThat(log.passCountOf("b"))
            .describedAs("the app present only for the first sweep")
            .isEqualTo(1)
        assertThat(log.passCountOf("a")).isEqualTo(3)
    }
}
