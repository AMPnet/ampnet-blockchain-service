package com.ampnet.crowdfunding.blockchain.controller.pojo

import com.ampnet.crowdfunding.blockchain.enums.TransactionState
import com.ampnet.crowdfunding.blockchain.enums.TransactionType
import com.ampnet.crowdfunding.blockchain.persistence.model.Transaction
import java.time.ZonedDateTime

data class TransactionResponse(
        val hash: String,
        val state: TransactionState,
        val type: TransactionType,
        val createdAt: ZonedDateTime,
        val processedAt: ZonedDateTime
) {
    constructor(transaction: Transaction): this (
        transaction.hash,
        transaction.state,
        transaction.type,
        transaction.createdAt,
        transaction.processedAt
    )
}

data class TransactionListResponse(val transactions: List<TransactionResponse>)
