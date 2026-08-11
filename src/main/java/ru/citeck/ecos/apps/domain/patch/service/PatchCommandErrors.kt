package ru.citeck.ecos.apps.domain.patch.service

import ru.citeck.ecos.commands.dto.CommandResult
import ru.citeck.ecos.webapp.lib.patch.EcosPatchCommandExecutor

object PatchCommandErrors {

    /**
     * Message to store in the patch's `lastError`, or null when the command succeeded.
     *
     * [CommandResult.primaryError] is `@JsonIgnore`, so it is filled in only for a command executed
     * inside this very application. A patch normally runs on a remote target app, and there the
     * cause of the failure arrives in [CommandResult.errors] only. Reading primaryError alone
     * reported every remote failure as the meaningless "Command result is null. Json: null" and
     * dropped the actual error, which made such patches undiagnosable from the journal.
     *
     * @param parsedResult what the command answer was parsed into, or null when it could not be. A
     *        null result without any reported error is a failure too, but nothing else describes it.
     */
    fun getErrorMessage(result: CommandResult, parsedResult: EcosPatchCommandExecutor.CommandRes?): String? {
        val primaryErrorMsg = result.primaryError?.message
        if (!primaryErrorMsg.isNullOrBlank()) {
            return primaryErrorMsg
        }
        val errorsMsg = result.errors.mapNotNull { error ->
            when {
                error.message.isNotBlank() && error.type.isNotBlank() -> "${error.type}: ${error.message}"
                error.message.isNotBlank() -> error.message
                error.type.isNotBlank() -> error.type
                else -> null
            }
        }.joinToString("; ")
        if (errorsMsg.isNotBlank()) {
            return errorsMsg
        }
        if (parsedResult == null) {
            return "Command result is null. Json: " + result.result
        }
        return null
    }
}
