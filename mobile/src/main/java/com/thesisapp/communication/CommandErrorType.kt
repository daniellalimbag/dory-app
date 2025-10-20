package com.thesisapp.communication

enum class CommandErrorType {
    ALREADY_RECORDING,
    PENDING_UPLOAD,
    NOT_RECORDING,
    UNKNOWN_COMMAND,
    UNKNOWN;

    companion object {
        fun fromPayload(raw: String?): CommandErrorType {
            return when (raw?.trim()?.uppercase()) {
                "ALREADY_RECORDING" -> ALREADY_RECORDING
                "PENDING_UPLOAD" -> PENDING_UPLOAD
                "NOT_RECORDING" -> NOT_RECORDING
                "UNKNOWN_COMMAND" -> UNKNOWN_COMMAND
                else -> UNKNOWN
            }
        }
    }
}