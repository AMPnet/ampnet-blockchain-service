package com.ampnet.crowdfunding.blockchain.service

interface WalletService {

    fun getWallet(txHash: String): String
}