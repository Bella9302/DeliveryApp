package com.itmda3b33.deliveryapp.ui.common

import com.itmda3b33.deliveryapp.data.model.ComplaintCategory
import com.itmda3b33.deliveryapp.data.model.ComplaintStatus
import com.itmda3b33.deliveryapp.data.model.OrderStatus
import com.itmda3b33.deliveryapp.data.model.RefundStatus
import java.text.DateFormat
import java.util.Date

/* Display text for stored values. Unknown values are shown as stored rather than hidden. */

fun orderStatusLabel(value: String) = OrderStatus.from(value)?.label ?: value
fun complaintStatusLabel(value: String) = ComplaintStatus.from(value)?.label ?: value
fun refundStatusLabel(value: String) = RefundStatus.from(value)?.label ?: value
fun categoryLabel(value: String) = ComplaintCategory.from(value)?.label ?: value

fun formatTime(millis: Long): String =
    if (millis <= 0) "" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
