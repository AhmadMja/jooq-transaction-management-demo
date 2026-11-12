package com.ahmad.jooq_transaction_management_demo.v3_db_exception_caught

import com.ahmad.jooq_transaction_management_demo.common.OutboxTable.MESSAGE_ID
import com.ahmad.jooq_transaction_management_demo.common.OutboxTable.PAYLOAD
import com.ahmad.jooq_transaction_management_demo.common.OutboxTable.TABLE
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service("outboxService_db_exception_caught")
class OutboxService(private val dsl: DSLContext) {

    val log = LoggerFactory.getLogger(OutboxService::class.java)

    /**
     * Own transaction. Called standalone -> BEGIN/COMMIT.
     * Called from inside another transaction -> SAVEPOINT/RELEASE (or ROLLBACK TO SAVEPOINT on failure).
     */
    fun publish(payload: String) {
        try {
            dsl.transactionResult { _ ->
                dsl.insertInto(TABLE)
                    .set(PAYLOAD, payload)
                    .returning(MESSAGE_ID)
                    .fetchOne(MESSAGE_ID)!!
                log.info("------publish: fail in the inner transaction.")
                dsl.execute("SELECT 1/0")
            }
        } catch (e: Exception) {
            log.info("Exception occurred while executing insert into table")
        }
    }
}
