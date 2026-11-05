package com.ahmad.jooq_transaction_management_demo

import org.springframework.boot.fromApplication
import org.springframework.boot.with

/** Run this to start the app locally against a Testcontainers Postgres. */
fun main(args: Array<String>) {
    fromApplication<JooqTransactionManagementDemoApplication>()
        .with(TestcontainersConfiguration::class)
        .run(*args)
}
