package com.ampnet.crowdfunding.blockchain.persistence.model

import com.ampnet.crowdfunding.blockchain.enums.WalletType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "wallet")
data class Wallet(
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        val id: Int,

        @Column(nullable = false)
        var hash: String,

        @Column
        var address: String?,

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 7)
        var type: WalletType
)