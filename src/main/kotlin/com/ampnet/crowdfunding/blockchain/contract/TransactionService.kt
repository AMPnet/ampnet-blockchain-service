package com.ampnet.crowdfunding.blockchain.contract

import com.ampnet.crowdfunding.blockchain.persistence.model.Transaction

interface TransactionService {

    fun postTransaction(txData: String): String

    fun persistTransaction(txHash: String): Transaction

}