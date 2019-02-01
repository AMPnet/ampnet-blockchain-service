package com.ampnet.crowdfunding.blockchain.service

interface AddressService {

    fun getAddress(txHash: String): String

}