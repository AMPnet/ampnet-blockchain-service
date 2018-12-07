package com.ampnet.crowdfunding.blockchain.service.impl

import com.ampnet.crowdfunding.BalanceRequest
import com.ampnet.crowdfunding.BalanceResponse
import com.ampnet.crowdfunding.TestServiceGrpc
import io.grpc.stub.StreamObserver

class TestServiceImpl : TestServiceGrpc.TestServiceImplBase() {
    override fun getBalance(request: BalanceRequest?, responseObserver: StreamObserver<BalanceResponse>?) {
        super.getBalance(request, responseObserver)
    }
}
