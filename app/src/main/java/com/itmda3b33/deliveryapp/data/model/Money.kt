package com.itmda3b33.deliveryapp.data.model

/** Converts between cents and the rand amounts people read and type. */
object Money {

    /** 1550 -> "R15.50" */
    fun format(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val abs = kotlin.math.abs(cents)
        return "%sR%d.%02d".format(sign, abs / 100, abs % 100)
    }

    /** "15.5", "R15.50" or "15" -> 1550. Returns null for anything that is not a positive amount with at most 2 decimals. */
    fun parse(text: String): Long? {
        val cleaned = text.trim().removePrefix("R").removePrefix("r").trim()
        if (!Regex("""\d+(\.\d{1,2})?""").matches(cleaned)) return null
        val parts = cleaned.split(".")
        val rands = parts[0].toLongOrNull() ?: return null
        val cents = parts.getOrNull(1)?.padEnd(2, '0')?.toLong() ?: 0
        return rands * 100 + cents
    }
}
