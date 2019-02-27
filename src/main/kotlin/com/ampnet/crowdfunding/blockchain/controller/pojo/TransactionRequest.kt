package com.ampnet.crowdfunding.blockchain.controller.pojo

data class TransactionRequest(val hash: String)

data class TransactionListRequest(val transactions: List<TransactionRequest>)
