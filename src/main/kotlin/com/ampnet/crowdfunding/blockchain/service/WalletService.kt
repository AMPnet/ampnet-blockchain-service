package com.ampnet.crowdfunding.blockchain.service

import com.ampnet.crowdfunding.blockchain.persistence.model.Wallet
import java.util.Optional

interface WalletService {

    fun get(address: String): Optional<Wallet>

    fun getTxHash(address: String): String

    fun getPublicKey(txHash: String): String?

    fun storePublicKey(publicKey: String, forTxHash: String)
}