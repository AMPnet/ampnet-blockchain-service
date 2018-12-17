package com.ampnet.crowdfunding.blockchain.contract.impl

import com.ampnet.crowdfunding.blockchain.contract.TransactionService
import org.springframework.stereotype.Service
import org.web3j.protocol.Web3j

@Service
class TransactionServiceImpl(val web3j: Web3j): TransactionService {

    override fun postTransaction(txData: String): String {
        val result = web3j.ethSendRawTransaction(txData).send()
        return result.transactionHash
    }

}