package com.ampnet.crowdfunding.blockchain.contract

import org.web3j.crypto.RawTransaction
import java.math.BigInteger

interface ProjectService {

    fun generateInvestTx(from: String, project: String): RawTransaction

    fun generateWithdrawTx(
        from: String,
        project: String,
        amount: BigInteger
    ): RawTransaction

    fun getMaxInvestmentPerUser(project: String): BigInteger

    fun getMinInvestmentPerUser(project: String): BigInteger

    fun getInvestmentCap(project: String): BigInteger

    fun getCurrentTotalInvestment(project: String): BigInteger

    fun getTotalInvestmentForUser(project: String, user: String): BigInteger

    fun isCompletelyFunded(project: String): Boolean

}