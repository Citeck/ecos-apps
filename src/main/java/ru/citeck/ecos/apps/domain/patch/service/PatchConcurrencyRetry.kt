package ru.citeck.ecos.apps.domain.patch.service

import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Duration

/**
 * Retries an action that lost a row to a concurrent writer.
 *
 * ecos-data guards every update with an optimistic lock and rejects the loser by throwing a plain
 * `IllegalStateException("Concurrent modification of record with id: ...")` (`DbEntityRepoPg`,
 * `InMemEntityRepo`) — there is no typed exception to catch, so the message is all a caller has.
 */
object PatchConcurrencyRetry {

    private const val CONCURRENT_MODIFICATION_ERROR = "Concurrent modification"

    private val log = KotlinLogging.logger {}

    /**
     * Runs [action], repeating it while it fails on a lost update, up to [attempts] times. Any other
     * failure is rethrown at once, and so is the last attempt's — retrying is a mitigation, never a
     * way to swallow the error.
     *
     * The point of a repeat is not that the same write eventually wins: it is that the action reads
     * the row again and decides again, on state that has since moved on.
     */
    fun <T> retrying(what: String, attempts: Int, delay: Duration, action: () -> T): T {
        for (attempt in 1..attempts) {
            try {
                return action.invoke()
            } catch (e: Exception) {
                if (!isConcurrentModification(e) || attempt == attempts) {
                    throw e
                }
                log.debug(e) { "Attempt $attempt of $what lost the row to a concurrent writer. Retrying" }
                Thread.sleep(delay.toMillis())
            }
        }
        // Unreachable: the loop either returns or rethrows on its last attempt.
        error("Retrying '$what' ended without a result")
    }

    /**
     * The whole cause chain is inspected, not just the top exception: the records and transaction
     * layers between the caller and ecos-data may wrap the rejection, and a check that only looked at
     * the outermost message would silently stop retrying at all.
     */
    fun isConcurrentModification(error: Throwable): Boolean {
        return generateSequence(error) { it.cause }.any {
            it.message?.contains(CONCURRENT_MODIFICATION_ERROR) == true
        }
    }
}
