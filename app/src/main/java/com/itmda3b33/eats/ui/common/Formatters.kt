package com.itmda3b33.eats.ui.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Display formatting only. Money formatting lives in the core module (formatRand). */
private val dateTime = SimpleDateFormat("d MMM, HH:mm", Locale.UK)

fun formatDateTime(millis: Long): String = if (millis <= 0) "Just now" else dateTime.format(Date(millis))

/** Short form of a Firestore ID so people can read it out, for example "#A1B2C3". */
fun shortId(id: String): String = "#" + id.take(6).uppercase()
