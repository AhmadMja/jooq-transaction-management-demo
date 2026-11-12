package com.ahmad.jooq_transaction_management_demo.v5_base_with_jooq_default_tx_provider

import com.ahmad.jooq_transaction_management_demo.common.OutboxTable.MESSAGE_ID
import com.ahmad.jooq_transaction_management_demo.common.OutboxTable.PAYLOAD
import com.ahmad.jooq_transaction_management_demo.common.OutboxTable.TABLE
import org.jooq.ConnectionProvider
import org.jooq.DSLContext
import org.jooq.impl.DefaultTransactionProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service("outboxService_base_with_jooq_default_tx_provider")
class OutboxService(
    dsl: DSLContext,
    connectionProvider: ConnectionProvider,
) {

    val log = LoggerFactory.getLogger(OutboxService::class.java)

    private val dslWithRequiredPropagation = dsl.configuration()
        .derive(DefaultTransactionProvider(connectionProvider))
        .dsl()

    fun publish(payload: String) {
        log.info("------publish: before transaction block.")
        dslWithRequiredPropagation.transactionResult { config ->
            log.info("------publish: inside transaction block before calling db.")
            dslWithRequiredPropagation.insertInto(TABLE)
                .set(PAYLOAD, payload)
                .returning(MESSAGE_ID)
                .fetchOne(MESSAGE_ID)!!
            // Postgres xact id (savepoints share the parent's id) + backend pid (identifies the physical connection)
            // subxact_count = number of subtransactions (savepoints) that have done writes in the current tx (PG16+).
            // Since we just inserted, count > 0 means this block runs inside a savepoint. Caveat: released
            // savepoints still count until the top-level tx ends, so it's "savepoints used", not exact depth.
            // pg_stat_clear_snapshot() avoids reading a stale backend-status snapshot cached earlier in the tx.
            dslWithRequiredPropagation.resultQuery("select pg_stat_clear_snapshot()").fetch()
            val txInfo = dslWithRequiredPropagation
                .resultQuery(
                    """
                    select pg_current_xact_id()::text as tx_id,
                           pg_backend_pid() as pid,
                           s.subxact_count
                    from pg_stat_get_backend_idset() as b(id)
                    cross join lateral pg_stat_get_backend_subxact(b.id) as s
                    where pg_stat_get_backend_pid(b.id) = pg_backend_pid()
                    """.trimIndent()
                )
                .fetchOne()!!
            log.info(
                "------publish: inside transaction block after calling db. txId={}, pid={}, subxactCount={}, inSavepoint={}",
                txInfo["tx_id"], txInfo["pid"], txInfo["subxact_count"], (txInfo["subxact_count"] as Int) > 0,
            )
        }
        log.info("------publish: after transaction block.")
    }
}
