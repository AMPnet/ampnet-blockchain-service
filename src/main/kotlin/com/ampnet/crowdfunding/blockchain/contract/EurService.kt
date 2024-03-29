package com.ampnet.crowdfunding.blockchain.contract

import org.web3j.crypto.RawTransaction
import java.math.BigInteger

interface EurService {

    fun generateMintTx(from: String, to: String, amount: BigInteger): RawTransaction

    fun generateBurnFromTx(from: String, burnFrom: String, amount: BigInteger): RawTransaction

    fun generateApproveTx(from: String, spender: String, amount: BigInteger): RawTransaction

    fun balanceOf(address: String): BigInteger

    fun generateTransferTx(from: String, to: String, amount: BigInteger): RawTransaction
}