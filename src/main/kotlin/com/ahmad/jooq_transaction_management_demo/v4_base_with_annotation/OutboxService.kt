package com.ahmad.jooq_transaction_management_demo.v4_base_with_annotation

import com.ahmad.jooq_transaction_management_demo.common.DslUtils.logTransactionInfo
import com.ahmad.jooq_transaction_management_demo.common.OutboxTable.MESSAGE_ID
import com.ahmad.jooq_transaction_management_demo.common.OutboxTable.PAYLOAD
import com.ahmad.jooq_transaction_management_demo.common.OutboxTable.TABLE
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service("outboxService_base_with_annotation")
class OutboxService(private val dsl: DSLContext) {

    val log = LoggerFactory.getLogger(OutboxService::class.java)

    // This will always be 'propagation = Propagation.REQUIRED'. The annotation default value for propagation is REQUIRED, simple as that.
    @Transactional
    fun publish(payload: String) {
        log.info("------publish: inside transaction block before calling db.")
        log.info("------publish: transactionProvider={}", dsl.configuration().transactionProvider().javaClass.name)
        dsl.insertInto(TABLE)
            .set(PAYLOAD, payload)
            .returning(MESSAGE_ID)
            .fetchOne(MESSAGE_ID)!!
        logTransactionInfo(dsl)
        log.info("------publish: inside transaction block after calling db.")
    }
}
