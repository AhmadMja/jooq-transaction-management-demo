package com.ahmad.jooq_transaction_management_demo.account

import com.ahmad.jooq_transaction_management_demo.account.AccountTable.ID
import com.ahmad.jooq_transaction_management_demo.account.AccountTable.STATUS
import com.ahmad.jooq_transaction_management_demo.account.AccountTable.TABLE
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class AccountRepository(private val dsl: DSLContext) {

    /** Runs in its own jOOQ transaction; nested inside a caller's transaction it becomes a SAVEPOINT. */
    fun updateStatus(id: Int, status: String): Int =
        dsl.transactionResult { cfg ->
            DSL.using(cfg)
                .update(TABLE)
                .set(STATUS, status)
                .where(ID.eq(id))
                .execute()
        }

    fun findStatus(id: Int): String? =
        dsl.transactionResult { cfg ->
            DSL.using(cfg)
                .select(STATUS)
                .from(TABLE)
                .where(ID.eq(id))
                .fetchOne(STATUS)
        }
}
