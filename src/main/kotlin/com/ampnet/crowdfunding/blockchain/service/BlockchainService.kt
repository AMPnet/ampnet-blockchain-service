package com.ampnet.crowdfunding.blockchain.service

import com.ampnet.crowdfunding.BalanceRequest
import com.ampnet.crowdfunding.BalanceResponse
import com.ampnet.crowdfunding.BlockchainServiceGrpc
import com.ampnet.crowdfunding.Empty
import com.ampnet.crowdfunding.GenerateActivateTxRequest
import com.ampnet.crowdfunding.GenerateAddOrganizationTxRequest
import com.ampnet.crowdfunding.GenerateAddWalletTxRequest
import com.ampnet.crowdfunding.GenerateApproveTxRequest
import com.ampnet.crowdfunding.GenerateBurnFromTxRequest
import com.ampnet.crowdfunding.GenerateMintTxRequest
import com.ampnet.crowdfunding.GetAllOrganizationsResponse
import com.ampnet.crowdfunding.PostTransactionRequest
import com.ampnet.crowdfunding.PostTransactionResponse
import com.ampnet.crowdfunding.RawTxResponse
import com.ampnet.crowdfunding.WalletActiveRequest
import com.ampnet.crowdfunding.WalletActiveResponse
import com.ampnet.crowdfunding.blockchain.contract.AmpnetService
import com.ampnet.crowdfunding.blockchain.contract.OrganizationService
import com.ampnet.crowdfunding.blockchain.contract.TransactionService
import com.ampnet.crowdfunding.blockchain.contract.impl.EurService
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import mu.KLogging
import org.lognet.springboot.grpc.GRpcService
import org.web3j.crypto.RawTransaction
import java.math.BigInteger

// TODO: - decide about amount type (uint256 in smart contracts, bigdecimal in blockchain service), where to convert?
@GRpcService
class BlockchainService(
    val transactionService: TransactionService,
    val ampnetService: AmpnetService,
    val eurService: EurService,
    val organizationService: OrganizationService
) : BlockchainServiceGrpc.BlockchainServiceImplBase() {

    companion object : KLogging()

    private val tokenFactor = BigInteger("10000000000000000") // 10e16

    override fun generateAddWalletTx(request: GenerateAddWalletTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        logger.info("generateAddWalletTx")
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
        try {
            val x = 5 / 0
            responseObserver.onNext(
                    GetAllOrganizationsResponse.newBuilder()
                            .addAllOrganizations(ampnetService.getAllOrganizations())
                            .build()
            )
            responseObserver.onCompleted()
        } catch (e: StatusRuntimeException) {
            responseObserver.onError(e)
        }
    }

    override fun isWalletActive(request: WalletActiveRequest, responseObserver: StreamObserver<WalletActiveResponse>) {
        responseObserver.onNext(
                WalletActiveResponse.newBuilder()
                        .setActive(ampnetService.isWalletActive(request.wallet).value)
                        .build()
        )
        responseObserver.onCompleted()
    }

    override fun generateMintTx(request: GenerateMintTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        val tx = eurService.generateMintTransaction(
                request.from,
                request.to,
                eurToToken(request.amount)
        )
        responseObserver.onNext(convert(tx))
        responseObserver.onCompleted()
    }

    override fun generateBurnFromTx(request: GenerateBurnFromTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        val tx = eurService.generateBurnFromTransaction(
                request.from,
                request.burnFrom,
                eurToToken(request.amount)
        )
        responseObserver.onNext(convert(tx))
        responseObserver.onCompleted()
    }

    override fun generateApproveTx(request: GenerateApproveTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        val tx = eurService.generateApproveTransaction(
                request.from,
                request.approve,
                eurToToken(request.amount)
        )
        responseObserver.onNext(convert(tx))
        responseObserver.onCompleted()
    }

    override fun getBalance(request: BalanceRequest, responseObserver: StreamObserver<BalanceResponse>) {
        val address = request.address
        val balance = eurService.balanceOf(address)
        val response = BalanceResponse.newBuilder()
                .setBalance(tokenToEur(balance))
                .build()
        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }

    override fun generateActivateTx(request: GenerateActivateTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        val tx = organizationService.generateActivateTx(
                request.organization,
                request.from
        )
        responseObserver.onNext(convert(tx))
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
                .setTo(tx.to)
                .build()
    }

    private fun eurToToken(eur: Long): BigInteger {
        return tokenFactor * eur.toBigInteger()
    }

    private fun tokenToEur(token: BigInteger): Long {
        return (token / tokenFactor).longValueExact()
    }
}