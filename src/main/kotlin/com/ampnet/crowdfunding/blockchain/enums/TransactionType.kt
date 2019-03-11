package com.ampnet.crowdfunding.blockchain.enums

import org.web3j.crypto.Hash

enum class TransactionType(val functionHash: String) {

    WALLET_CREATE(EthUtilities.getFunctionHash("addWallet(address)")),
    ORG_CREATE(EthUtilities.getFunctionHash("addOrganization()")),
    DEPOSIT(EthUtilities.getFunctionHash("mint(address,uint256)")),
    APPROVE(EthUtilities.getFunctionHash("approve(address,uint256)")),
    PENDING_ORG_WITHDRAW(EthUtilities.getFunctionHash("withdrawFunds(address,uint256)")),
    PENDING_PROJ_WITHDRAW(EthUtilities.getFunctionHash("withdraw(address,uint256)")),
    WITHDRAW(EthUtilities.getFunctionHash("burnFrom(address,uint256)")),
    INVEST(EthUtilities.getFunctionHash("invest()")),
    TRANSFER(EthUtilities.getFunctionHash("transfer(address,uint256)")),
    ORG_ADD_MEMBER(EthUtilities.getFunctionHash("addMember(address)")),
    ORG_ADD_PROJECT(EthUtilities.getFunctionHash("addProject(uint256,uint256,uint256,uint256)")),
    ORG_ACTIVATE(EthUtilities.getFunctionHash("activate()")),
    START_REVENUE_PAYOUT(EthUtilities.getFunctionHash("startRevenueSharesPayout(uint256)")),
    REVENUE_PAYOUT(EthUtilities.getFunctionHash("payoutRevenueShares()")),
    SHARE_PAYOUT("SHARE_PAYOUT"),
    WITHDRAW_INVESTMENT(EthUtilities.getFunctionHash("withdrawInvestment()"));
}

object EthUtilities {
    fun getFunctionHash(signature: String) = Hash.sha3String(signature).substring(2, 10).toLowerCase()
}