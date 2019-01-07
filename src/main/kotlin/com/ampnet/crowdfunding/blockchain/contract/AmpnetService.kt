package com.ampnet.crowdfunding.blockchain.contract

import org.web3j.crypto.RawTransaction

interface AmpnetService {

    fun generateAddWalletTx(from: String, wallet: String): RawTransaction

    fun generateAddOrganizationTx(from: String, name: String): RawTransaction

    fun getAllOrganizations(): List<String>

    fun organizationExists(organization: String): Boolean

    fun isWalletActive(wallet: String): Boolean
}