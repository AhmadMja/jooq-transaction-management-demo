package com.ahmad.jooq_transaction_management_demo.common

import org.jooq.DSLContext
import org.slf4j.LoggerFactory

object DslUtils {

    val log = LoggerFactory.getLogger(DslUtils::class.java)

    fun logTransactionInfo(dsl: DSLContext) {
        // Postgres xact id (savepoints share the parent's id) + backend pid (identifies the physical connection)
        // subxact_count = number of subtransactions (savepoints) that have done writes in the current tx (PG16+).
        // Since we just inserted, count > 0 means this block runs inside a savepoint. Caveat: released
        // savepoints still count until the top-level tx ends, so it's "savepoints used", not exact depth.
        // pg_stat_clear_snapshot() avoids reading a stale backend-status snapshot cached earlier in the tx.
        dsl.resultQuery("select pg_stat_clear_snapshot()").fetch()
        val txInfo = dsl
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
}