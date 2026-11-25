package com.termux.victor.assistant

/**
 * One entry of the intent table. The `destructive` hint from JSON is advisory only:
 * [DestructiveClassifier] is the authority and runs on the resolved command.
 */
data class Intent(
    val id: String,
    val title: String,
    val keywords: List<String>,
    val aliases: List<String> = emptyList(),
    val commandUser: String? = null,
    val commandRoot: String? = null,
    val explain: String = "",
    val androidNote: String = "",
)
