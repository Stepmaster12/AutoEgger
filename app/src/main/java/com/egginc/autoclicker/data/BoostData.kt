package com.egginc.autoclicker.data

/**
 * Данные о бусте
 */
@Suppress("unused")
data class Boost(
    val id: String,
    val name: String,
    val shortName: String,
    val description: String,
    val x: Int,
    val y: Int,
    val page: Int,
    val iconName: String
)

/**
 * Пресет бустов
 */
@Suppress("unused")
data class Preset(
    val name: String,
    val description: String,
    val emoji: String = "🔧",
    val boosts: List<String> // Список ID бустов
)
