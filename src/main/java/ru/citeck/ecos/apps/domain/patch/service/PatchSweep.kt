package ru.citeck.ecos.apps.domain.patch.service

/**
 * Drives the apps of one patch-job tick until none of them has work left.
 *
 * A single pass is not enough, because applying a patch of one app can hand the job a patch of an
 * app the pass has already been through. Repeating the pass is what keeps the job from paying its
 * fixed delay while work is waiting — but repeating it must not let a tick do unbounded work, and
 * must not let one busy app crowd the others out. Hence the budget: every app gets its own share of
 * steps for the WHOLE tick, spent across all passes and never refilled by a new one.
 */
object PatchSweep {

    /**
     * @param maxStepsPerApp steps one app may spend across all passes of this tick
     * @param isActive checked before each pass; false abandons the tick (the job's lock is gone)
     * @param apps the apps to sweep, re-read each pass — the set changes while the tick runs
     * @param step performs up to the given number of steps for one app and returns how many it did
     * @return total steps performed
     */
    fun run(
        maxStepsPerApp: Int,
        isActive: () -> Boolean,
        apps: () -> Set<String>,
        step: (appName: String, stepsLimit: Int) -> Int
    ): Int {
        val stepsLeftByApp = HashMap<String, Int>()
        var total = 0
        while (isActive()) {
            var stepsDone = 0
            apps().forEach { appName ->
                val stepsLeft = stepsLeftByApp.getOrDefault(appName, maxStepsPerApp)
                if (stepsLeft > 0) {
                    val steps = step(appName, stepsLeft)
                    stepsLeftByApp[appName] = stepsLeft - steps
                    stepsDone += steps
                }
            }
            if (stepsDone == 0) {
                break
            }
            total += stepsDone
        }
        return total
    }
}
