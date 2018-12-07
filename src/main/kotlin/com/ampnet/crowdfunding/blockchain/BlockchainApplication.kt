package com.ampnet.crowdfunding.blockchain

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BlockchainApplication

fun main(args: Array<String>) {
    runApplication<BlockchainApplication>(*args)
}
