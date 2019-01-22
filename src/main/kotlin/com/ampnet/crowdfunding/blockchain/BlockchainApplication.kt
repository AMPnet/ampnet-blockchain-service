package com.ampnet.crowdfunding.blockchain

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties
class BlockchainApplication

fun main(args: Array<String>) {
    runApplication<BlockchainApplication>(*args)
}
