package com.ampnet.crowdfunding.blockchain.service

import com.ampnet.crowdfunding.blockchain.persistence.model.Transaction

interface TransactionService {

    fun postAndCacheTransaction(txData: String, onComplete: (Transaction) -> Unit)

    fun getTransaction(txHash: String): Transaction

    fun getAllTransactions(wallet: String): List<Transaction>

    fun getAddressFromHash(hash: String): String

    fun updateTransactionStates()
}