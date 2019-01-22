package com.ampnet.crowdfunding.blockchain.service

import com.ampnet.crowdfunding.blockchain.persistence.model.Wallet

interface WalletService {

    fun getWallet(txHash: String): String

}