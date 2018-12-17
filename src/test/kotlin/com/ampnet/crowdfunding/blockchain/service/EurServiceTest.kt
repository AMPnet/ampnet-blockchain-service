package com.ampnet.crowdfunding.blockchain.service

import com.ampnet.crowdfunding.BalanceRequest
import com.ampnet.crowdfunding.blockchain.TestBase
import org.junit.jupiter.api.Test

class EurServiceTest: TestBase() {

    @Test
    fun balanceOfTest() {
        grpc.getBalance(
                BalanceRequest.newBuilder()
                        .setAddress(accounts.bob.address)
                        .build()
        )
    }
}