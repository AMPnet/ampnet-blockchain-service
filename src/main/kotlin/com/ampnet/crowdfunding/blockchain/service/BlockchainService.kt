package com.ampnet.crowdfunding.blockchain.service

import com.ampnet.crowdfunding.BalanceRequest
import com.ampnet.crowdfunding.BalanceResponse
import com.ampnet.crowdfunding.BlockchainServiceGrpc
import com.ampnet.crowdfunding.Empty
import com.ampnet.crowdfunding.GenerateAddOrganizationTxRequest
import com.ampnet.crowdfunding.GenerateAddWalletTxRequest
import com.ampnet.crowdfunding.GenerateApproveTxRequest
import com.ampnet.crowdfunding.GenerateBurnFromTxRequest
import com.ampnet.crowdfunding.GenerateMintTxRequest
import com.ampnet.crowdfunding.GetAllOrganizationsResponse
import com.ampnet.crowdfunding.PostTransactionRequest
import com.ampnet.crowdfunding.PostTransactionResponse
import com.ampnet.crowdfunding.RawTxResponse
import com.ampnet.crowdfunding.blockchain.contract.AmpnetService
import com.ampnet.crowdfunding.blockchain.contract.TransactionService
import com.ampnet.crowdfunding.blockchain.contract.impl.EurService
import io.grpc.stub.StreamObserver
import org.lognet.springboot.grpc.GRpcService
import org.web3j.crypto.RawTransaction
import java.math.BigDecimal

// TODO: - decide about amount type (uint256 in smart contracts, bigdecimal in blockchain service), where to convert?
@GRpcService
class BlockchainService(
        val transactionService: TransactionService,
        val ampnetService: AmpnetService,
        val eurService: EurService
): BlockchainServiceGrpc.BlockchainServiceImplBase() {

    override fun generateAddWalletTx(request: GenerateAddWalletTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        val tx = ampnetService.generateAddWalletTx(
                request.wallet,
                request.from
        )
        responseObserver.onNext(convert(tx))
        responseObserver.onCompleted()
    }

    override fun generateAddOrganizationTx(request: GenerateAddOrganizationTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        val tx = ampnetService.generateAddOrganizationTx(
                request.name,
                request.from
        )
        responseObserver.onNext(convert(tx))
        responseObserver.onCompleted()
    }

    override fun getAllOrganizations(request: Empty, responseObserver: StreamObserver<GetAllOrganizationsResponse>) {
        val response = ampnetService.getAllOrganizations()
        // TODO: - how to generate array response?
    }

    override fun generateMintTx(request: GenerateMintTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        val tx = eurService.generateMintTransaction(
                request.from,
                request.to,
                BigDecimal.valueOf(request.amount)
        )
        responseObserver.onNext(convert(tx))
        responseObserver.onCompleted()
    }

    override fun generateBurnFromTx(request: GenerateBurnFromTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        val tx = eurService.generateBurnFromTransaction(
                request.from,
                request.burnFrom,
                BigDecimal.valueOf(request.amount)
        )
        responseObserver.onNext(convert(tx))
        responseObserver.onCompleted()
    }

    override fun generateApproveTx(request: GenerateApproveTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        val tx = eurService.generateApproveTransaction(
                request.from,
                request.approve,
                BigDecimal.valueOf(request.amount)
        )
        responseObserver.onNext(convert(tx))
        responseObserver.onCompleted()
    }

    override fun getBalance(request: BalanceRequest, responseObserver: StreamObserver<BalanceResponse>) {
        val address = request.address
        val balance = eurService.balanceOf(address)
        val response = BalanceResponse.newBuilder()
                .setBalance(balance.toPlainString())
                .build()
        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }

    override fun postTransaction(request: PostTransactionRequest, responseObserver: StreamObserver<PostTransactionResponse>) {
        val txHash = transactionService.postTransaction(request.data)
        val response = PostTransactionResponse.newBuilder()
                .setTxHash(txHash)
                .build()
        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }

    private fun convert(tx: RawTransaction): RawTxResponse {
        return RawTxResponse.newBuilder()
                .setData(tx.data)
                .setGasLimit(tx.gasLimit.longValueExact())
                .setGasPrice(tx.gasPrice.longValueExact())
                .setNonce(tx.nonce.longValueExact())
                .setValue(tx.value.longValueExact())
                .build()
    }


}