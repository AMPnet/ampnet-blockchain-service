package com.ampnet.crowdfunding.blockchain.service

import com.ampnet.crowdfunding.blockchain.config.ApplicationProperties
import com.ampnet.crowdfunding.blockchain.contract.AmpnetService
import com.ampnet.crowdfunding.blockchain.contract.EurService
import com.ampnet.crowdfunding.proto.*
import io.grpc.Status
import io.grpc.stub.StreamObserver
import org.lognet.springboot.grpc.GRpcService
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.Sign
import org.web3j.crypto.TransactionDecoder
import org.web3j.crypto.TransactionEncoder
import org.web3j.crypto.TransactionUtils
import org.web3j.tx.RawTransactionManager
import org.web3j.utils.Numeric
import java.math.BigInteger

// TODO: - decide about amount type (uint256 in smart contracts, bigdecimal in blockchain service), where to convert?
@GRpcService
class BlockchainService(
        val transactionService: TransactionService,
        val walletService: WalletService,
        val ampnetService: AmpnetService,
        val eurService: EurService,
        val properties: ApplicationProperties
) : BlockchainServiceGrpc.BlockchainServiceImplBase() {

    private val tokenFactor = BigInteger("10000000000000000") // 10e16

    override fun addWallet(request: AddWalletRequest, responseObserver: StreamObserver<PostTxResponse>) {
        try {
            val credentials = Credentials.create(properties.accounts.ampnetPrivateKey)
            val tx = ampnetService.generateAddWalletTx(
                    credentials.address,
                    request.wallet
            )
            val signedTx = sign(tx, credentials)
            post(signedTx) { response ->
                responseObserver.onNext(response)
                responseObserver.onCompleted()
            }
        } catch (t: Throwable) {
            responseObserver.onError(Status.fromThrowable(t).asRuntimeException())
        }
    }

    override fun isWalletActive(request: WalletActiveRequest, responseObserver: StreamObserver<WalletActiveResponse>) {
        val wallet = walletService.getWallet(request.walletTxHash)
        responseObserver.onNext(
                WalletActiveResponse.newBuilder()
                        .setActive(ampnetService.isWalletActive(wallet))
                        .build()
        )
        responseObserver.onCompleted()
    }

//    override fun generateAddOrganizationTx(request: GenerateAddOrganizationTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
//        val address = getAddressFromTxHash()
//        val tx = ampnetService.generateAddOrganizationTx(
//                request.from,
//                request.name
//        )
//        responseObserver.onNext(convert(tx))
//        responseObserver.onCompleted()
//    }
//
//    override fun getAllOrganizations(request: GetAllOrganizationsRequest, responseObserver: StreamObserver<GetAllOrganizationsResponse>) {
//        responseObserver.onNext(
//                GetAllOrganizationsResponse.newBuilder()
//                        .addAllOrganizations(ampnetService.getAllOrganizations())
//                        .build()
//        )
//        responseObserver.onCompleted()
//    }
//
//
//    override fun organizationExists(request: OrganizationExistsRequest, responseObserver: StreamObserver<OrganizationExistsResponse>) {
//        responseObserver.onNext(
//                OrganizationExistsResponse.newBuilder()
//                        .setExists(ampnetService.organizationExists(request.organization))
//                        .build()
//        )
//        responseObserver.onCompleted()
//    }
//
    override fun generateMintTx(request: GenerateMintTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        val tx = eurService.generateMintTx(
                request.from,
                request.to,
                eurToToken(request.amount)
        )
        responseObserver.onNext(convert(tx))
        responseObserver.onCompleted()
    }

    override fun generateBurnFromTx(request: GenerateBurnFromTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        val tx = eurService.generateBurnFromTx(
                request.from,
                request.burnFrom,
                eurToToken(request.amount)
        )
        responseObserver.onNext(convert(tx))
        responseObserver.onCompleted()
    }

    override fun generateApproveTx(request: GenerateApproveTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        val wallet = walletService.getWallet(request.fromTxHash)
        val tx = eurService.generateApproveTx(
                wallet,
                request.approve,
                eurToToken(request.amount)
        )
        responseObserver.onNext(convert(tx))
        responseObserver.onCompleted()
    }

    override fun getBalance(request: BalanceRequest, responseObserver: StreamObserver<BalanceResponse>) {
        val wallet = walletService.getWallet(request.walletTxHash)
        val balance = eurService.balanceOf(wallet)
        val response = BalanceResponse.newBuilder()
                .setBalance(tokenToEur(balance))
                .build()
        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }
//
//    override fun generateInvestTx(request: GenerateInvestTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
//        val tx = eurService.generateInvestTx(
//                request.from,
//                request.project,
//                eurToToken(request.amount)
//        )
//        responseObserver.onNext(convert(tx))
//        responseObserver.onCompleted()
//    }
//
//    override fun generateTransferTx(request: GenerateTransferTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
//        val tx = eurService.generateTransferTx(
//                request.from,
//                request.to,
//                eurToToken(request.amount)
//        )
//        responseObserver.onNext(convert(tx))
//        responseObserver.onCompleted()
//    }
//
//    override fun generateActivateOrganizationTx(request: GenerateActivateTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
//        val tx = organizationService.generateActivateTx(
//                request.from,
//                request.organization
//        )
//        responseObserver.onNext(convert(tx))
//        responseObserver.onCompleted()
//    }
//
//    override fun generateWithdrawOrganizationFundsTx(request: GenerateWithdrawOrganizationFundsTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
//        val tx = organizationService.generateWithdrawFundsTx(
//                request.from,
//                request.organization,
//                request.tokenIssuer,
//                eurToToken(request.amount)
//        )
//        responseObserver.onNext(convert(tx))
//        responseObserver.onCompleted()
//    }
//
//    override fun generateAddOrganizationMemberTx(request: GenerateAddMemberTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
//        val tx = organizationService.generateAddMemberTx(
//                request.from,
//                request.organization,
//                request.member
//        )
//        responseObserver.onNext(convert(tx))
//        responseObserver.onCompleted()
//    }
//
//    override fun generateAddOrganizationProjectTx(request: GenerateAddProjectTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
//        val tx = organizationService.generateAddProjectTx(
//                request.from,
//                request.organization,
//                request.name,
//                request.description,
//                eurToToken(request.maxInvestmentPerUser),
//                eurToToken(request.minInvestmentPerUser),
//                eurToToken(request.investmentCap)
//        )
//        responseObserver.onNext(convert(tx))
//        responseObserver.onCompleted()
//    }
//
//    override fun isOrganizationVerified(request: OrganizationVerifiedRequest, responseObserver: StreamObserver<OrganizationVerifiedResponse>) {
//        val verified = organizationService.isVerified(
//                request.organization
//        )
//        responseObserver.onNext(
//                OrganizationVerifiedResponse.newBuilder()
//                        .setVerified(verified)
//                        .build()
//        )
//        responseObserver.onCompleted()
//    }
//
//    override fun getAllOrganizationProjects(request: OrganizationProjectsRequest, responseObserver: StreamObserver<OrganizationProjectsResponse>) {
//        val projects = organizationService.getAllProjects(
//                request.organization
//        )
//        responseObserver.onNext(
//                OrganizationProjectsResponse.newBuilder()
//                        .addAllProjects(projects)
//                        .build()
//        )
//        responseObserver.onCompleted()
//    }
//
//    override fun getAllOrganizationMembers(request: OrganizationMembersRequest, responseObserver: StreamObserver<OrganizationMembersResponse>) {
//        val members = organizationService.getMembers(
//                request.organization
//        )
//        responseObserver.onNext(
//                OrganizationMembersResponse.newBuilder()
//                        .addAllMembers(members)
//                        .build()
//        )
//        responseObserver.onCompleted()
//    }
//
//    override fun generateWithdrawProjectFundsTx(request: GenerateWithdrawProjectFundsTx, responseObserver: StreamObserver<RawTxResponse>) {
//        val tx = projectService.generateWithdrawFundsTx(
//                request.from,
//                request.project,
//                request.tokenIssuer,
//                eurToToken(request.amount)
//        )
//        responseObserver.onNext(convert(tx))
//        responseObserver.onCompleted()
//    }
//
//    override fun generateTransferOwnershipTx(request: GenerateTransferOwnershipTx, responseObserver: StreamObserver<RawTxResponse>) {
//        val tx = projectService.generateTransferOwnershipTx(
//                request.from,
//                request.project,
//                request.to,
//                eurToToken(request.amount)
//        )
//        responseObserver.onNext(convert(tx))
//        responseObserver.onCompleted()
//    }
//
//    override fun generateCancelInvestmentTx(request: GenerateCancelInvestmentTx, responseObserver: StreamObserver<RawTxResponse>) {
//        val tx = projectService.generateCancelInvestmentTx(
//                request.from,
//                request.project,
//                eurToToken(request.amount)
//        )
//        responseObserver.onNext(convert(tx))
//        responseObserver.onCompleted()
//    }
//
//    override fun getProjectName(request: ProjectNameRequest, responseObserver: StreamObserver<ProjectNameResponse>) {
//        responseObserver.onNext(
//                ProjectNameResponse.newBuilder()
//                        .setName(projectService.getName(request.project))
//                        .build()
//        )
//        responseObserver.onCompleted()
//    }
//
//    override fun getProjectDescription(request: ProjectDescriptionRequest, responseObserver: StreamObserver<ProjectDescriptionResponse>) {
//        responseObserver.onNext(
//                ProjectDescriptionResponse.newBuilder()
//                        .setDescription(projectService.getDescription(request.project))
//                        .build()
//        )
//        responseObserver.onCompleted()
//    }
//
//    override fun getProjectMaxInvestmentPerUser(request: ProjectMaxInvestmentPerUserRequest, responseObserver: StreamObserver<ProjectMaxInvestmentPerUserResponse>) {
//        responseObserver.onNext(
//                ProjectMaxInvestmentPerUserResponse.newBuilder()
//                        .setAmount(tokenToEur(projectService.getMaxInvestmentPerUser(request.project)))
//                        .build()
//        )
//        responseObserver.onCompleted()
//    }
//
//    override fun getProjectMinInvestmentPerUser(request: ProjectMinInvestmentPerUserRequest, responseObserver: StreamObserver<ProjectMinInvestmentPerUserResponse>) {
//        responseObserver.onNext(
//                ProjectMinInvestmentPerUserResponse.newBuilder()
//                        .setAmount(tokenToEur(projectService.getMinInvestmentPerUser(request.project)))
//                        .build()
//        )
//        responseObserver.onCompleted()
//    }
//
//    override fun getProjectInvestmentCap(request: ProjectInvestmentCapRequest, responseObserver: StreamObserver<ProjectInvestmentCapResponse>) {
//        responseObserver.onNext(
//                ProjectInvestmentCapResponse.newBuilder()
//                        .setAmount(tokenToEur(projectService.getInvestmentCap(request.project)))
//                        .build()
//        )
//        responseObserver.onCompleted()
//    }
//
//    override fun getProjectCurrentTotalInvestment(request: ProjectCurrentTotalInvestmentRequest, responseObserver: StreamObserver<ProjectCurrentTotalInvestmentResponse>) {
//        responseObserver.onNext(
//                ProjectCurrentTotalInvestmentResponse.newBuilder()
//                        .setAmount(tokenToEur(projectService.getCurrentTotalInvestment(request.project)))
//                        .build()
//        )
//        responseObserver.onCompleted()
//    }
//
//    override fun getProjectTotalInvestmentForUser(request: ProjectTotalInvestmentForUserRequest, responseObserver: StreamObserver<ProjectTotalInvestmentForUserResponse>) {
//        responseObserver.onNext(
//                ProjectTotalInvestmentForUserResponse.newBuilder()
//                        .setAmount(tokenToEur(projectService.getTotalInvestmentForUser(request.project, request.user)))
//                        .build()
//        )
//        responseObserver.onCompleted()
//    }
//
//    override fun isProjectLockedForInvestments(request: ProjectLockedForInvestmentsRequest, responseObserver: StreamObserver<ProjectLockedForInvestmentsResponse>) {
//        responseObserver.onNext(
//                ProjectLockedForInvestmentsResponse.newBuilder()
//                        .setLocked(projectService.isLockedForInvestments(request.project))
//                        .build()
//        )
//        responseObserver.onCompleted()
//    }

    override fun postTransaction(request: PostTxRequest, responseObserver: StreamObserver<PostTxResponse>) {
        try {
            post(request.data) { response ->
                responseObserver.onNext(response)
                responseObserver.onCompleted()
            }
        } catch (e: Exception) {
            responseObserver.onError(Status.fromThrowable(e).asRuntimeException())
        }
    }

    private fun sign(rawTx: RawTransaction, credentials: Credentials): String {
        val signedTx = TransactionEncoder.signMessage(rawTx, credentials)
        return Numeric.toHexString(signedTx)
    }

    private fun post(txData: String, onComplete: (PostTxResponse) -> Unit) {
        transactionService.postAndCacheTransaction(txData) { tx ->
            onComplete(
                    PostTxResponse.newBuilder()
                    .setTxHash(tx.hash)
                    .setTxType(com.ampnet.crowdfunding.proto.TransactionType.valueOf(tx.type.name))
                    .build()
            )
        }
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