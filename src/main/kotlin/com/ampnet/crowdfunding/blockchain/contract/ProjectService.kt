package com.ampnet.crowdfunding.blockchain.contract

import org.web3j.crypto.RawTransaction
import java.math.BigDecimal

interface ProjectService {

    fun generateWithdrawFundsTransaction(
            project: String,
            from: String,
            tokenIssuer: String,
            amount: BigDecimal
    ): RawTransaction

}