package com.kompact.util

import java.text.DecimalFormat
import kotlin.math.ln
import kotlin.math.pow

private val sizeUnits = arrayOf("B", "KB", "MB", "GB", "TB")
private val percentageFormat = DecimalFormat("0.0")

fun Long.asReadableBytes(): String {
    if (this <= 0L) return "0 B"
    val digitGroups = (ln(this.toDouble()) / ln(1024.0)).toInt().coerceIn(0, sizeUnits.lastIndex)
    val value = this / 1024.0.pow(digitGroups.toDouble())
    return "${String.format("%.1f", value)} ${sizeUnits[digitGroups]}"
}

fun String.parseBytes(): Long? {
    val trimmed = this.trim()
    if (trimmed.isEmpty()) return null
    val regex = Regex("(\\d+(?:\\.\\d+)?)\\s*([A-Za-z]*)")
    val match = regex.matchEntire(trimmed) ?: return null
    val value = match.groupValues[1].toDoubleOrNull() ?: return null
    val unit = match.groupValues[2].uppercase()
    val fullUnit = when (unit) {
        "B" -> "B"
        "KB", "K" -> "KB"
        "MB", "M" -> "MB"
        "GB", "G" -> "GB"
        "TB", "T" -> "TB"
        "" -> "MB"
        else -> return null
    }
    val multiplier = when (fullUnit) {
        "B" -> 1.0
        "KB" -> 1024.0
        "MB" -> 1024.0 * 1024.0
        "GB" -> 1024.0 * 1024.0 * 1024.0
        "TB" -> 1024.0 * 1024.0 * 1024.0 * 1024.0
        else -> return null
    }
    return (value * multiplier).toLong()
}
