package com.ampnet.crowdfunding.blockchain.contract.impl

import org.web3j.crypto.RawTransaction
import java.math.BigDecimal

interface EurService {

    fun generateMintTransaction(from: String, to: String, amount: BigDecimal): RawTransaction

    fun generateBurnFromTransaction(from: String, burnFrom: String, amount: BigDecimal): RawTransaction

    fun generateApproveTransaction(from: String, approve: String, amount: BigDecimal): RawTransaction

    fun balanceOf(address: String): BigDecimal
}