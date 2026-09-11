package com.example

import java.util.UUID

enum class VoiceLanguage(val code: String, val displayName: String, val speechPrompt: String, val localeTag: String) {
    AUTO("auto", "Auto Detect", "শুনছি...", "bn-IN"),
    BENGALI("bn_IN", "বাংলা", "শুনছি...", "bn-IN"),
    HINDI("hi_IN", "हिन्दी", "सुन रहा हूँ...", "hi-IN"),
    ENGLISH("en_US", "English", "Listening...", "en-US")
}

enum class AssistantState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

enum class ActionType {
    OPEN_APP,
    SEARCH_GOOGLE,
    CALL,
    SMS,
    VOLUME_CONTROL,
    CONVERSATION,
    SYSTEM_SETTING
}

data class PendingAction(
    val id: String = UUID.randomUUID().toString(),
    val type: ActionType,
    val title: String,
    val description: String,
    val targetData: String? = null,
    val language: String = "bn",
    val execute: () -> Unit
)

data class RkHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val command: String,
    val response: String,
    val isSuccess: Boolean = true,
    val actionType: ActionType = ActionType.CONVERSATION,
    val timestamp: Long = System.currentTimeMillis()
)

data class QuickCommand(
    val title: String,
    val subtitle: String,
    val commandPhrase: String,
    val category: String,
    val languageCode: String = "bn"
)
