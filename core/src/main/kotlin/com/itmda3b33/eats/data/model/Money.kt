package com.itmda3b33.eats.data.model

import kotlin.math.abs

/** Formats cents as South African rand, for example 7900 -> "R79,00" and 125000 -> "R1 250,00". */
fun formatRand(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val absolute = abs(cents)
    val rands = (absolute / 100).toString().reversed().chunked(3).joinToString(" ").reversed()
    val remainder = (absolute % 100).toString().padStart(2, '0')
    return "${sign}R$rands,$remainder"
}
