package com.ampnet.crowdfunding.blockchain.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "com.ampnet.crowdfunding.blockchain")
class ApplicationProperties {
    val web3j: Web3jProperties = Web3jProperties()
    val contracts: ContractsProperties = ContractsProperties()
    val accounts: AccountsProperties = AccountsProperties()
}

class Web3jProperties {
    lateinit var clientAddress: String
}
class ContractsProperties {
    lateinit var coopAddress: String
    lateinit var eurAddress: String
}
class AccountsProperties {
    lateinit var coopPrivateKey: String
    lateinit var issuingAuthorityAddress: String
}
