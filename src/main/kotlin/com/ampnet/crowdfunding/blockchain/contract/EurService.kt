package com.ampnet.crowdfunding.blockchain.contract.impl

import org.web3j.crypto.RawTransaction
import java.math.BigInteger

interface EurService {

    fun generateMintTransaction(from: String, to: String, amount: BigInteger): RawTransaction

    fun generateBurnFromTransaction(from: String, burnFrom: String, amount: BigInteger): RawTransaction

    fun generateApproveTransaction(from: String, approve: String, amount: BigInteger): RawTransaction

    fun balanceOf(address: String): BigInteger
}