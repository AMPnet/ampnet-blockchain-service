package com.ampnet.crowdfunding.blockchain.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService

@Configuration
class Web3jConfig(val properties: ApplicationProperties) {

    @Bean
    fun getWeb3j(): Web3j {
        return Web3j.build(HttpService(properties.web3j.clientAddress))
    }

}