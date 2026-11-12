package com.ahmad.jooq_transaction_management_demo.common

import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType

object AccountTable {
    val TABLE = DSL.table("account")
    val ID = DSL.field("id", SQLDataType.INTEGER)
    val STATUS = DSL.field("status", SQLDataType.VARCHAR(64))
}
