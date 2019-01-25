package com.ampnet.crowdfunding.blockchain.persistence.model

import com.ampnet.crowdfunding.blockchain.enums.TransactionState
import com.ampnet.crowdfunding.blockchain.enums.TransactionType
import java.math.BigInteger
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "transaction")
data class Transaction(
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        val id: Int,

        @Column(nullable = false)
        var hash: String,

        @Column(nullable = false)
        var fromAddress: String,

        @Column(nullable = false)
        var toAddress: String,

        @Column(nullable = false)
        var input: String,

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 7)
        var state: TransactionState,

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 18)
        var type: TransactionType,

        @Column(nullable = true)
        var amount: BigInteger?
)