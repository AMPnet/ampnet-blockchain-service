package com.ampnet.crowdfunding.blockchain.persistence.repository

import com.ampnet.crowdfunding.blockchain.persistence.model.Wallet
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface WalletRepository : JpaRepository<Wallet, Int> {

    fun findByHash(hash: String): Optional<Wallet>

    fun findByAddress(address: String): Optional<Wallet>

}