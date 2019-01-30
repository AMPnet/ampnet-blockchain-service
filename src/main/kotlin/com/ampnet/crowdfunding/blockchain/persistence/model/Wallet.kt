package com.ampnet.crowdfunding.blockchain.persistence.model

import com.ampnet.crowdfunding.blockchain.enums.WalletType
import javax.persistence.Entity
import javax.persistence.Table
import javax.persistence.Id
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Column
import javax.persistence.Enumerated
import javax.persistence.EnumType

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

    @Column
    var publicKey: String?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 7)
    var type: WalletType
)