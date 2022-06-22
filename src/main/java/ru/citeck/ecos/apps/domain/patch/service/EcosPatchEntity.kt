package ru.citeck.ecos.apps.domain.patch.service

import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import java.time.Instant

class EcosPatchEntity(
    var id: String = "",
    var name: MLText,
    var patchId: String,
    var state: ObjectData = ObjectData.create(),
    var targetApp: String,
    var date: Instant,
    var manual: Boolean = false,
    var type: String,
    var config: ObjectData = ObjectData.create(),
    var status: EcosPatchStatus = EcosPatchStatus.PENDING,
    var patchResult: DataValue = DataValue.NULL,
    var errorsCount: Int = 0,
    var nextExecDate: Instant? = null,
    var lastError: String? = null,
    var dependsOn: List<String>
)
