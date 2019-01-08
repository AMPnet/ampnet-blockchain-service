package com.ampnet.crowdfunding.blockchain.contract

import org.web3j.crypto.RawTransaction
import java.math.BigInteger

interface ProjectService {

    fun generateWithdrawFundsTx(
        from: String,
        project: String,
        tokenIssuer: String,
        amount: BigInteger
    ): RawTransaction

    fun generateTransferOwnershipTx(
        from: String,
        project: String,
        to: String,
        amount: BigInteger
    ): RawTransaction

    fun generateCancelInvestmentTx(from: String, project: String, amount: BigInteger): RawTransaction

    fun getName(project: String): String

    fun getDescription(project: String): String

    fun getMaxInvestmentPerUser(project: String): BigInteger

    fun getMinInvestmentPerUser(project: String): BigInteger

    fun getInvestmentCap(project: String): BigInteger

    fun getCurrentTotalInvestment(project: String): BigInteger

    fun getTotalInvestmentForUser(project: String, user: String): BigInteger

    fun isLockedForInvestments(project: String): Boolean
}