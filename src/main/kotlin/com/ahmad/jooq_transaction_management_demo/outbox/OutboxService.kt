package com.ahmad.jooq_transaction_management_demo.outbox

import org.jooq.DSLContext
import org.springframework.stereotype.Service

@Service
class OutboxService(
    private val dsl: DSLContext,
    private val outboxRepository: OutboxRepository,
) {

    /**
     * Own transaction. Called standalone -> BEGIN/COMMIT.
     * Called from inside another transaction -> SAVEPOINT/RELEASE (or ROLLBACK TO SAVEPOINT on failure).
     */
    fun publish(payload: String): Int =
        dsl.transactionResult { _ ->
            outboxRepository.insert(payload)
        }
}
