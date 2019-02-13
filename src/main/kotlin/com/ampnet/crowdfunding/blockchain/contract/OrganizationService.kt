package com.ampnet.crowdfunding.blockchain.contract

import org.web3j.crypto.RawTransaction
import java.math.BigInteger

interface OrganizationService {

    fun generateActivateTx(from: String, organization: String): RawTransaction

    fun generateWithdrawFundsTx(from: String, organization: String, amount: BigInteger): RawTransaction

    fun generateAddMemberTx(from: String, organization: String, member: String): RawTransaction

    fun generateAddProjectTx(
        from: String,
        organization: String,
        maxInvestmentPerUser: BigInteger,
        minInvestmentPerUser: BigInteger,
        investmentCap: BigInteger
    ): RawTransaction

    fun isVerified(organization: String): Boolean

    fun getAllProjects(organization: String): List<String>

    fun getMembers(organization: String): List<String>
}