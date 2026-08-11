package ru.citeck.ecos.apps.domain.patch

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.citeck.ecos.apps.domain.patch.service.PatchConcurrencyRetry
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class PatchConcurrencyRetryTest {

    companion object {
        private const val ATTEMPTS = 3

        /** The wording ecos-data throws — see DbEntityRepoPg / InMemEntityRepo. */
        private const val LOST_UPDATE = "Concurrent modification of record with id: 1"
    }

    private fun <T> retrying(action: () -> T): T {
        return PatchConcurrencyRetry.retrying("test action", ATTEMPTS, Duration.ZERO, action)
    }

    @Test
    fun lostUpdateIsRetried() {
        val calls = AtomicInteger(0)

        val result = retrying {
            if (calls.incrementAndGet() == 1) {
                error(LOST_UPDATE)
            }
            "done"
        }

        assertThat(result).isEqualTo("done")
        assertThat(calls.get())
            .describedAs("the action is invoked again, so it re-reads and decides on the new state")
            .isEqualTo(2)
    }

    @Test
    fun wrappedLostUpdateIsStillRecognised() {
        // The records and transaction layers between the caller and ecos-data may wrap the rejection,
        // and matching only the outermost message would silently disable the retry.
        val calls = AtomicInteger(0)

        retrying {
            if (calls.incrementAndGet() == 1) {
                throw IllegalStateException("Failed to mutate record", RuntimeException(LOST_UPDATE))
            }
            "done"
        }

        assertThat(calls.get()).isEqualTo(2)
    }

    @Test
    fun otherFailureIsRethrownAtOnce() {
        val calls = AtomicInteger(0)

        assertThatThrownBy {
            retrying<String> {
                calls.incrementAndGet()
                error("Permission denied")
            }
        }.hasMessage("Permission denied")

        assertThat(calls.get())
            .describedAs("rethrown at once, without burning the remaining attempts")
            .isEqualTo(1)
    }

    @Test
    fun lostUpdateIsRethrownAfterLastAttempt() {
        val calls = AtomicInteger(0)

        assertThatThrownBy {
            retrying<String> {
                calls.incrementAndGet()
                error(LOST_UPDATE)
            }
        }.hasMessage(LOST_UPDATE)

        assertThat(calls.get()).isEqualTo(ATTEMPTS)
    }

    @Test
    fun actionThatSucceedsIsRunExactlyOnce() {
        val calls = AtomicInteger(0)

        assertThat(retrying { calls.incrementAndGet() }).isEqualTo(1)
        assertThat(calls.get()).isEqualTo(1)
    }

    @Test
    fun wholeCauseChainIsInspected() {
        assertThat(PatchConcurrencyRetry.isConcurrentModification(RuntimeException(LOST_UPDATE))).isTrue()
        assertThat(
            PatchConcurrencyRetry.isConcurrentModification(
                RuntimeException("outer", IllegalStateException("middle", RuntimeException(LOST_UPDATE)))
            )
        ).isTrue()
        assertThat(PatchConcurrencyRetry.isConcurrentModification(RuntimeException("Permission denied"))).isFalse()
        assertThat(PatchConcurrencyRetry.isConcurrentModification(RuntimeException())).isFalse()
    }
}
