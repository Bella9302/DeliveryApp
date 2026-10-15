package com.itmda3b33.eats.validation

import com.itmda3b33.eats.data.model.ComplaintCategory
import com.itmda3b33.eats.data.model.ComplaintItem
import com.itmda3b33.eats.data.model.ComplaintStatus
import com.itmda3b33.eats.data.model.OrderStatus
import com.itmda3b33.eats.validation.Fixtures.CUSTOMER
import com.itmda3b33.eats.validation.Fixtures.OTHER_CUSTOMER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComplaintRulesTest {

    private fun create(
        customer: String = CUSTOMER,
        status: OrderStatus = OrderStatus.DELIVERED,
        category: ComplaintCategory? = ComplaintCategory.MISSING_ITEM,
        description: String? = "My chips are missing",
        items: List<ItemClaim> = listOf(ItemClaim("chips", 1)),
        existing: List<com.itmda3b33.eats.data.model.Complaint> = emptyList(),
    ) = ComplaintRules.validateCreate(customer, Fixtures.order(status), category, description, items, existing)

    private fun failureCode(result: Result<*>) = (result.exceptionOrNull() as RuleException).failure.code

    @Test
    fun `valid complaint returns items with names`() {
        assertEquals(listOf(ComplaintItem("chips", "Chips", 1)), create().getOrThrow())
    }

    @Test
    fun `ownership delivery duplicate and fields are checked`() {
        assertEquals("not_owner", failureCode(create(customer = OTHER_CUSTOMER)))
        assertEquals("not_delivered", failureCode(create(status = OrderStatus.PREPARING)))
        assertEquals("open_complaint_exists", failureCode(create(existing = listOf(Fixtures.complaint()))))
        assertEquals("missing_category", failureCode(create(category = null)))
        assertEquals("description_too_short", failureCode(create(description = "bad")))
        assertEquals("missing_items", failureCode(create(items = emptyList())))
        assertEquals("item_not_in_order", failureCode(create(items = listOf(ItemClaim("pizza", 1)))))
        assertEquals("bad_quantity", failureCode(create(items = listOf(ItemClaim("chips", 2)))))
        assertEquals("repeated_item", failureCode(create(items = listOf(ItemClaim("chips", 1), ItemClaim("chips", 1)))))
    }

    @Test
    fun `resolved complaint does not block a new one`() {
        assert(create(existing = listOf(Fixtures.complaint(status = ComplaintStatus.RESOLVED))).isSuccess)
    }

    @Test
    fun `late delivery can be reported before delivery and needs no items`() {
        assert(create(status = OrderStatus.OUT_FOR_DELIVERY, category = ComplaintCategory.LATE_DELIVERY, items = emptyList()).isSuccess)
    }

    @Test
    fun `safety complaints start escalated`() {
        assertEquals(ComplaintStatus.ESCALATED, ComplaintRules.initialStatus(ComplaintCategory.SAFETY_HEALTH))
        assertEquals(ComplaintStatus.LOGGED, ComplaintRules.initialStatus(ComplaintCategory.MISSING_ITEM))
    }

    @Test
    fun `customers can edit and delete only logged complaints they own`() {
        assertNull(ComplaintRules.validateCustomerEdit(CUSTOMER, Fixtures.complaint(), "Two dips were missing"))
        assertEquals("not_owner", ComplaintRules.validateCustomerEdit(OTHER_CUSTOMER, Fixtures.complaint(), "Two dips were missing")?.code)
        assertEquals("complaint_locked", ComplaintRules.validateCustomerEdit(CUSTOMER, Fixtures.complaint(status = ComplaintStatus.ESCALATED), "Two dips")?.code)
        assertEquals("description_too_short", ComplaintRules.validateCustomerEdit(CUSTOMER, Fixtures.complaint(), " ")?.code)

        assertNull(ComplaintRules.validateCustomerDelete(CUSTOMER, Fixtures.complaint(), emptyList()))
        assertEquals("has_refunds", ComplaintRules.validateCustomerDelete(CUSTOMER, Fixtures.complaint(), listOf(Fixtures.refund("dip")))?.code)
        assertEquals("complaint_locked", ComplaintRules.validateCustomerDelete(CUSTOMER, Fixtures.complaint(status = ComplaintStatus.RESOLVED), emptyList())?.code)
    }
}
