package com.ampnet.crowdfunding.blockchain.contract

import org.web3j.crypto.RawTransaction

interface CoopService {

    fun generateAddWalletTx(from: String, wallet: String): RawTransaction

    fun generateAddOrganizationTx(from: String): RawTransaction

    fun getOrganizations(): List<String>

    fun isOrganizationActive(organization: String): Boolean

    fun isWalletActive(wallet: String): Boolean
}