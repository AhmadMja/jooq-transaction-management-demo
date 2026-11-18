package com.ahmad.jooq_transaction_management_demo.v6_jooq_default_tx_provider_outer_fails

import com.ahmad.jooq_transaction_management_demo.common.DslUtils.logTransactionInfo
import com.ahmad.jooq_transaction_management_demo.common.OutboxTable.MESSAGE_ID
import com.ahmad.jooq_transaction_management_demo.common.OutboxTable.PAYLOAD
import com.ahmad.jooq_transaction_management_demo.common.OutboxTable.TABLE
import org.jooq.ConnectionProvider
import org.jooq.DSLContext
import org.jooq.impl.DefaultTransactionProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service("outboxService_jooq_default_tx_provider_outer_fails")
class OutboxService(
    dsl: DSLContext,
    connectionProvider: ConnectionProvider,
) {

    val log = LoggerFactory.getLogger(OutboxService::class.java)

    private val dslWithDefaultTransactionProvider = dsl.configuration()
        // Nested is true if not specified.
        .derive(DefaultTransactionProvider(connectionProvider))
        .dsl()

    fun publish(payload: String) {
        log.info("------publish: before transaction block.")
        log.info("------publish: transactionProvider={}", dslWithDefaultTransactionProvider.configuration().transactionProvider().javaClass.name)
        dslWithDefaultTransactionProvider.transactionResult { _ ->
            log.info("------publish: inside transaction block before calling db.")
            logTransactionInfo(dslWithDefaultTransactionProvider)
            dslWithDefaultTransactionProvider.insertInto(TABLE)
                .set(PAYLOAD, payload)
                .returning(MESSAGE_ID)
                .fetchOne(MESSAGE_ID)!!
        }
        log.info("------publish: after transaction block.")
    }
}