package com.ampnet.crowdfunding.blockchain.persistence.repository

import com.ampnet.crowdfunding.blockchain.persistence.model.Wallet
import org.springframework.data.jpa.repository.JpaRepository

interface WalletRepository : JpaRepository<Wallet, Int> {


}