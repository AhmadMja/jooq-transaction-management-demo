package com.ahmad.jooq_transaction_management_demo.common

import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType

object OutboxTable {
    val TABLE = DSL.table("outbox")
    val MESSAGE_ID = DSL.field("message_id", SQLDataType.INTEGER)
    val PAYLOAD = DSL.field("payload", SQLDataType.CLOB)
}
