package com.ampnet.crowdfunding.blockchain.enums

import io.grpc.Status
import org.web3j.crypto.Hash

enum class TransactionType(val functionHash: String) {

    WALLET_CREATE(EthUtilities.getFunctionHash("addWallet(address)")),
    ORG_CREATE(EthUtilities.getFunctionHash("addOrganization()")),
    DEPOSIT(EthUtilities.getFunctionHash("mint(address,uint256)")),
    PENDING_WITHDRAW(EthUtilities.getFunctionHash("approve(address,uint256)")),
    PENDING_ORG_WITHDRAW(EthUtilities.getFunctionHash("withdrawFunds(address,uint256)")),
    WITHDRAW(EthUtilities.getFunctionHash("burnFrom(address,uint256)")),
    INVEST(EthUtilities.getFunctionHash("addWallet(address,uint256)")),
    TRANSFER(EthUtilities.getFunctionHash("transfer(address,uint256)")),
    ORG_ADD_MEMBER(EthUtilities.getFunctionHash("addMember(address)")),
    ORG_ADD_PROJECT(EthUtilities.getFunctionHash("addProject(uint256,uint256,uint256)")),
    ORG_ACTIVATE(EthUtilities.getFunctionHash("activate()")),
    TRANSFER_OWNERSHIP(EthUtilities.getFunctionHash("transferOwnership(address,uint256)")),
    CANCEL_INVESTMENT(EthUtilities.getFunctionHash("cancelInvestment(uint256)"));

    companion object {
        private val map = TransactionType.values().associateBy(TransactionType::functionHash)

        fun fromFunctionHash(hash: String) = map.get(hash)
                ?: throw Status.INVALID_ARGUMENT
                        .withDescription("Invalid transaction function call! Function not recognized.")
                        .asRuntimeException()
    }
}

object EthUtilities {
    fun getFunctionHash(signature: String) = Hash.sha3String(signature).substring(2, 10).toLowerCase()
}