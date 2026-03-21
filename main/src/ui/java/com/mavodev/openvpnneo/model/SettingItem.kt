package com.mavodev.openvpnneo.model

enum class SettingType {
    TOGGLE_SLIDER,
    ACTION,
    CATEGORY
}

data class SettingItem(
    val key: String,
    val title: String,
    var description: String,
    val type: SettingType,
    var value: Boolean = false,
    val action: (() -> Unit)? = null
)
