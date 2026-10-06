package com.itmda3b33.deliveryapp.validation

/**
 * Stops customers getting stuck in a loop. After [maxFailures] blocked or failed actions in a row,
 * the chat is handed to a human instead of asking the customer to try again.
 */
class LoopGuard(private val maxFailures: Int = 2) {

    private var failuresInARow = 0

    /** Records a blocked or failed action. Returns true when it is time to hand over to a human. */
    fun recordFailure(): Boolean {
        failuresInARow++
        return failuresInARow >= maxFailures
    }

    /** Call after any action that worked or escalated. */
    fun reset() {
        failuresInARow = 0
    }
}
