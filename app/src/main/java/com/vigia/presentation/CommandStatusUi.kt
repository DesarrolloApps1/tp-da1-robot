package com.vigia.presentation

import com.vigia.domain.model.CommandRecord
import com.vigia.domain.model.CommandStatus

data class CommandStatusUi(val label: String, val tone: StatusTone)

fun CommandRecord.toStatusUi(): CommandStatusUi = when (status) {
    CommandStatus.PENDING -> CommandStatusUi(status.label, StatusTone.NEUTRAL)
    CommandStatus.CONFIRMED -> CommandStatusUi(status.label, StatusTone.OK)
    CommandStatus.REJECTED -> CommandStatusUi(
        reason?.let { "${status.label}: ${it.label}" } ?: status.label,
        StatusTone.ERROR,
    )
    CommandStatus.NO_RESPONSE -> CommandStatusUi(status.label, StatusTone.WARNING)
}
