package com.ampnet.crowdfunding.blockchain.contract

import org.web3j.crypto.RawTransaction
import java.math.BigDecimal

interface OrganizationService {

    fun generateActivateTx(organization: String, from: String): RawTransaction

    fun generateWithdrawFundsTx(organization: String, tokenIssuer: String, from: String, amount: BigDecimal): RawTransaction

    fun generateAddMemberTx(organization: String, from: String, member: String): RawTransaction

    fun generateAddProjectTx(
            organization: String,
            from: String,
            name: String,
            description: String,
            maxInvestmentPerUser: BigDecimal,
            minInvestmentPerUser: BigDecimal,
            investmentCap: BigDecimal
    ): RawTransaction

}