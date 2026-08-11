package ru.citeck.ecos.apps.domain.patch

import com.fasterxml.jackson.databind.node.NullNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.citeck.ecos.apps.domain.patch.service.PatchCommandErrors
import ru.citeck.ecos.commands.TransactionType
import ru.citeck.ecos.commands.dto.Command
import ru.citeck.ecos.commands.dto.CommandError
import ru.citeck.ecos.commands.dto.CommandResult
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.webapp.lib.patch.EcosPatchCommandExecutor
import ru.citeck.ecos.webapp.lib.patch.EcosPatchExecutionResult
import java.time.Instant

class PatchCommandErrorsTest {

    companion object {
        // A parsed answer of a patch that completed — only its presence is read.
        private val PARSED_RESULT = EcosPatchCommandExecutor.CommandRes(
            EcosPatchExecutionResult(
                durationMs = 1,
                result = "ok",
                state = ObjectData.create(),
                completed = true,
                nextExecutionTime = Instant.EPOCH
            )
        )
    }

    private fun result(
        errors: List<CommandError> = emptyList(),
        primaryError: Throwable? = null,
        resultJson: Any? = null
    ): CommandResult {
        return CommandResult(
            id = "res-id",
            started = 0,
            completed = 0,
            command = Command(
                id = "cmd-id",
                tenant = "",
                time = 0,
                targetApp = "emodel",
                user = "system",
                sourceApp = "eapps",
                sourceAppId = "eapps-0",
                type = "ecos-apps.patch.execute",
                transaction = TransactionType.REQUIRED,
                ttl = null
            ),
            appName = "emodel",
            appInstanceId = "emodel-0",
            result = resultJson?.let { Json.mapper.toJson(it) } ?: NullNode.instance,
            errors = errors,
            primaryError = primaryError
        )
    }

    @Test
    fun remoteCommandErrorIsTakenFromErrorsList() {
        // CommandResult.primaryError is @JsonIgnore, so a command executed on another app comes back
        // with the cause in 'errors' only. Reporting "Command result is null" here hid every remote
        // patch failure behind the same meaningless message.
        val errorMsg = PatchCommandErrors.getErrorMessage(
            result(errors = listOf(CommandError("IllegalStateException", "Invalid patch config"))),
            parsedResult = null
        )
        assertThat(errorMsg).isEqualTo("IllegalStateException: Invalid patch config")
    }

    @Test
    fun allReportedErrorsAreKept() {
        val errorMsg = PatchCommandErrors.getErrorMessage(
            result(
                errors = listOf(
                    CommandError("IllegalStateException", "first"),
                    CommandError("", "second"),
                    CommandError("ThirdException", "")
                )
            ),
            parsedResult = null
        )
        assertThat(errorMsg).isEqualTo("IllegalStateException: first; second; ThirdException")
    }

    @Test
    fun primaryErrorWinsOverErrorsList() {
        val errorMsg = PatchCommandErrors.getErrorMessage(
            result(
                errors = listOf(CommandError("IllegalStateException", "converted")),
                primaryError = IllegalStateException("original")
            ),
            parsedResult = null
        )
        assertThat(errorMsg)
            .describedAs("primaryError is set only for a locally executed command, and it wins")
            .isEqualTo("original")
    }

    @Test
    fun nullResultWithoutErrorsIsStillAnError() {
        val errorMsg = PatchCommandErrors.getErrorMessage(result(), parsedResult = null)
        assertThat(errorMsg).isEqualTo("Command result is null. Json: null")
    }

    @Test
    fun parsedResultWithoutErrorsIsSuccess() {
        val errorMsg = PatchCommandErrors.getErrorMessage(
            result(resultJson = mapOf("result" to "ok")),
            parsedResult = PARSED_RESULT
        )
        assertThat(errorMsg).isNull()
    }

    @Test
    fun errorsFailPatchEvenWhenResultWasParsed() {
        val errorMsg = PatchCommandErrors.getErrorMessage(
            result(
                errors = listOf(CommandError("IllegalStateException", "boom")),
                resultJson = mapOf("result" to "ok")
            ),
            parsedResult = PARSED_RESULT
        )
        assertThat(errorMsg).isEqualTo("IllegalStateException: boom")
    }
}
