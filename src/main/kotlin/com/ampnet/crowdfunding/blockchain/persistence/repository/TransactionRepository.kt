package com.ampnet.crowdfunding.blockchain.persistence.repository

import com.ampnet.crowdfunding.blockchain.persistence.model.Transaction
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.*

interface TransactionRepository : JpaRepository<Transaction, Int> {

    fun findByHash(hash: String): Optional<Transaction>

    @Query("SELECT t FROM Transaction t WHERE t.state = 'PENDING'")
    fun findAllPending(): List<Transaction>

}