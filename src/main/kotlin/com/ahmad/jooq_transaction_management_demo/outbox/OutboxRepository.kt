package com.ahmad.jooq_transaction_management_demo.outbox

import com.ahmad.jooq_transaction_management_demo.outbox.OutboxTable.MESSAGE_ID
import com.ahmad.jooq_transaction_management_demo.outbox.OutboxTable.PAYLOAD
import com.ahmad.jooq_transaction_management_demo.outbox.OutboxTable.TABLE
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class OutboxRepository(private val dsl: DSLContext) {

    /** Runs in its own jOOQ transaction; nested inside a caller's transaction it becomes a SAVEPOINT. */
    fun insert(payload: String): Int =
        dsl.transactionResult { cfg ->
            DSL.using(cfg)
                .insertInto(TABLE)
                .set(PAYLOAD, payload)
                .returning(MESSAGE_ID)
                .fetchOne(MESSAGE_ID)!!
        }

    fun findAllPayloads(): List<String> =
        dsl.transactionResult { cfg ->
            DSL.using(cfg)
                .select(PAYLOAD)
                .from(TABLE)
                .orderBy(MESSAGE_ID)
                .fetch(PAYLOAD)
        }
}
