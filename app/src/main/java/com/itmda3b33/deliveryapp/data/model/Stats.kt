package com.itmda3b33.deliveryapp.data.model

/** Figures for the stats screen, worked out from all complaints and refunds. */
data class Stats(
    val totalComplaints: Int,
    val byStatus: Map<ComplaintStatus, Int>,
    val byCategory: Map<ComplaintCategory, Int>,
    val autoRefunds: Int,
    val agentRefunds: Int,
    val refundsWaiting: Int,
    val refundedCents: Long,
) {
    companion object {
        fun from(complaints: List<Complaint>, refunds: List<Refund>): Stats {
            val paid = refunds.filter {
                RefundStatus.from(it.status) in setOf(RefundStatus.APPROVED, RefundStatus.COMPLETED)
            }
            return Stats(
                totalComplaints = complaints.size,
                byStatus = ComplaintStatus.entries.associateWith { s -> complaints.count { it.status == s.value } },
                byCategory = ComplaintCategory.entries.associateWith { c -> complaints.count { it.category == c.value } },
                autoRefunds = paid.count { it.approvedBy == Refund.APPROVED_BY_SYSTEM },
                agentRefunds = paid.count { it.approvedBy != Refund.APPROVED_BY_SYSTEM },
                refundsWaiting = refunds.count { RefundStatus.from(it.status) == RefundStatus.REQUESTED },
                refundedCents = paid.sumOf { it.amountCents },
            )
        }
    }
}
