package com.ampnet.crowdfunding.blockchain.persistence.repository

import com.ampnet.crowdfunding.blockchain.persistence.model.Wallet
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface WalletRepository : JpaRepository<Wallet, Int> {

    fun findByTransaction_Hash(hash: String): Optional<Wallet>

    fun findByAddress(address: String): Optional<Wallet>
}