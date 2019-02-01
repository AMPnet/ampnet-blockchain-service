package com.ampnet.crowdfunding.blockchain.service

import com.ampnet.crowdfunding.blockchain.enums.TransactionType
import com.ampnet.crowdfunding.blockchain.persistence.model.Transaction

interface TransactionService {

    fun postAndCacheTransaction(txData: String, txType: TransactionType): Transaction

    fun getTransaction(txHash: String): Transaction

    fun getAllTransactions(wallet: String): List<Transaction>

    fun getAddressFromHash(hash: String): String

    fun updateTransactionStates()
}