package com.itmda3b33.eats.data.model

/** Figures shown on the stats screen. */
data class ComplaintStats(
    val totalComplaints: Int,
    val byStatus: Map<ComplaintStatus, Int>,
    val byCategory: Map<ComplaintCategory, Int>,
    /** Complaints resolved by an automatic (system-approved) refund. */
    val autoResolved: Int,
    /** Complaints that reached a human agent. Includes resolved ones that were escalated first. */
    val escalated: Int,
    val refundsByStatus: Map<RefundStatus, Int>,
    /** Sum of approved and completed refunds. */
    val refundedCents: Long,
    val systemRefundedCents: Long,
    val agentRefundedCents: Long,
) {
    /** Share of complaints the bot resolved without a person, from 0.0 to 1.0. */
    val autoResolveRate: Double
        get() = if (totalComplaints == 0) 0.0 else autoResolved.toDouble() / totalComplaints
}

/**
 * Works out the stats from raw complaints and refunds.
 */
fun computeStats(complaints: List<Complaint>, refunds: List<Refund>): ComplaintStats {
    val paid = refunds.filter { it.status == RefundStatus.APPROVED || it.status == RefundStatus.COMPLETED }
    val systemPaid = paid.filter { it.approvedBy == Refund.APPROVED_BY_SYSTEM }
    val autoResolvedIds = systemPaid.map { it.complaintId }.toSet()
    val escalated = complaints.count { it.status == ComplaintStatus.ESCALATED || it.assignedAgentId != null }

    return ComplaintStats(
        totalComplaints = complaints.size,
        byStatus = ComplaintStatus.entries.associateWith { status -> complaints.count { it.status == status } },
        byCategory = ComplaintCategory.entries.associateWith { cat -> complaints.count { it.category == cat } },
        autoResolved = complaints.count { it.status == ComplaintStatus.RESOLVED && it.id in autoResolvedIds },
        escalated = escalated,
        refundsByStatus = RefundStatus.entries.associateWith { status -> refunds.count { it.status == status } },
        refundedCents = paid.sumOf { it.amountCents },
        systemRefundedCents = systemPaid.sumOf { it.amountCents },
        agentRefundedCents = paid.sumOf { it.amountCents } - systemPaid.sumOf { it.amountCents },
    )
}
