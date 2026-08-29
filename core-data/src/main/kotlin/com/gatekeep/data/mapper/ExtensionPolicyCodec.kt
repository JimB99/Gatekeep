package com.gatekeep.data.mapper

import com.gatekeep.domain.model.ExtensionPolicy
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class ExtensionPolicyDto(
    val optionMinutes: List<Int> = listOf(1, 5, 10),
    val maxExtensionsPerDay: Int? = null,
    val maxConsecutiveExtensions: Int? = null,
    val showNoLimitToday: Boolean = true,
    val customMinutes: Int? = null,
)

private val extensionPolicyJson = Json { ignoreUnknownKeys = true }

fun encodeExtensionPolicy(policy: ExtensionPolicy): String =
    extensionPolicyJson.encodeToString(
        ExtensionPolicyDto(
            optionMinutes = policy.optionMinutes,
            maxExtensionsPerDay = policy.maxExtensionsPerDay,
            maxConsecutiveExtensions = policy.maxConsecutiveExtensions,
            showNoLimitToday = policy.showNoLimitToday,
            customMinutes = policy.customMinutes,
        ),
    )

fun decodeExtensionPolicy(json: String?): ExtensionPolicy {
    if (json.isNullOrBlank()) return ExtensionPolicy()
    return runCatching {
        val dto = extensionPolicyJson.decodeFromString<ExtensionPolicyDto>(json)
        ExtensionPolicy(
            optionMinutes = dto.optionMinutes.ifEmpty { listOf(1, 5, 10) },
            maxExtensionsPerDay = dto.maxExtensionsPerDay,
            maxConsecutiveExtensions = dto.maxConsecutiveExtensions,
            showNoLimitToday = dto.showNoLimitToday,
            customMinutes = dto.customMinutes,
        )
    }.getOrDefault(ExtensionPolicy())
}
