package com.ampnet.crowdfunding.blockchain

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties
class BlockchainApplication

fun main(args: Array<String>) {
    runApplication<BlockchainApplication>(*args)
}
