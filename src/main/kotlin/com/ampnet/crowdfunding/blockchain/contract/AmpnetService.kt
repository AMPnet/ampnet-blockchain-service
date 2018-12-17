package com.ampnet.crowdfunding.blockchain.contract

import org.web3j.crypto.RawTransaction

interface AmpnetService {

    fun generateAddWalletTx(wallet: String, from: String): RawTransaction

    fun generateAddOrganizationTx(name: String, from: String): RawTransaction

    fun getAllOrganizations(): List<String>

}