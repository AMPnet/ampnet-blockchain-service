package com.ampnet.crowdfunding.blockchain.service

import com.ampnet.crowdfunding.blockchain.persistence.model.Transaction

interface TransactionService {

    fun postTransaction(txData: String): String

    fun persistTransaction(txHash: String): Transaction

    fun getTransaction(txHash: String): Transaction

    fun getAllTransactions(wallet: String): List<Transaction>

    fun getAddressFromHash(hash: String): String

    fun updateTransactionStates()

}