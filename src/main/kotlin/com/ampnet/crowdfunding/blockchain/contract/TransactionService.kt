package com.ampnet.crowdfunding.blockchain.contract

interface TransactionService {

    fun postTransaction(txData: String): String

}