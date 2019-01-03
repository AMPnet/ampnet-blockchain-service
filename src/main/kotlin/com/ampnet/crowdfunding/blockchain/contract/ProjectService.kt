package com.ampnet.crowdfunding.blockchain.contract

import org.web3j.crypto.RawTransaction
import java.math.BigInteger

interface ProjectService {

    fun generateWithdrawFundsTransaction(
        project: String,
        from: String,
        tokenIssuer: String,
        amount: BigInteger
    ): RawTransaction
}