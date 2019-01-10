package com.ampnet.crowdfunding.blockchain.persistence.repository

import com.ampnet.crowdfunding.blockchain.persistence.model.Transaction
import org.springframework.data.jpa.repository.JpaRepository

interface TransactionRepository : JpaRepository<Transaction, Int>