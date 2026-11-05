package com.ahmad.jooq_transaction_management_demo.account

import com.ahmad.jooq_transaction_management_demo.outbox.OutboxService
import org.jooq.DSLContext
import org.springframework.stereotype.Service

@Service
class AccountService(
    private val dsl: DSLContext,
    private val accountRepository: AccountRepository,
    private val outboxService: OutboxService,
) {

    /**
     * Transaction layout:
     *
     *   BEGIN                              -- AccountService tx (outermost)
     *     SAVEPOINT                        -- AccountRepository.updateStatus tx
     *     RELEASE SAVEPOINT
     *     SAVEPOINT                        -- OutboxService.publish tx
     *       SAVEPOINT                      -- OutboxRepository.insert tx
     *       RELEASE SAVEPOINT
     *     RELEASE SAVEPOINT
     *   COMMIT
     */
    fun changeStatus(id: Int, status: String) {
        dsl.transaction { _ ->
            val updated = accountRepository.updateStatus(id, status)
            require(updated == 1) { "Account $id not found" }
            outboxService.publish("""{"accountId":$id,"status":"$status"}""")
        }
    }
}
