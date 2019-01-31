package com.ampnet.crowdfunding.blockchain.service

interface WalletService {

    fun getAddress(txHash: String): String

    fun getPublicKey(txHash: String): String?

    fun storePublicKey(publicKey: String, forTxHash: String)
}