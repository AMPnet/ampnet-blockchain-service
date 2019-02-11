package com.ampnet.crowdfunding.blockchain.service

import com.ampnet.crowdfunding.blockchain.persistence.model.Wallet

interface WalletService {

    fun getByAddress(address: String): Wallet

    fun getByTxHash(txHash: String): Wallet

    fun getTxHash(address: String): String

    fun storePublicKey(publicKey: String, forTxHash: String)
}