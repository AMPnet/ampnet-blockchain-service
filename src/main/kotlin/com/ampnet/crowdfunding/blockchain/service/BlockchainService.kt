package com.ampnet.crowdfunding.blockchain.service

import com.ampnet.crowdfunding.blockchain.config.ApplicationProperties
import com.ampnet.crowdfunding.blockchain.contract.AmpnetService
import com.ampnet.crowdfunding.blockchain.contract.EurService
import com.ampnet.crowdfunding.blockchain.contract.OrganizationService
import com.ampnet.crowdfunding.blockchain.enums.TransactionType
import com.ampnet.crowdfunding.proto.ActivateOrganizationRequest
import com.ampnet.crowdfunding.proto.BlockchainServiceGrpc
import com.ampnet.crowdfunding.proto.AddWalletRequest
import com.ampnet.crowdfunding.proto.PostTxRequest
import com.ampnet.crowdfunding.proto.PostTxResponse
import com.ampnet.crowdfunding.proto.WalletActiveRequest
import com.ampnet.crowdfunding.proto.WalletActiveResponse
import com.ampnet.crowdfunding.proto.GenerateMintTxRequest
import com.ampnet.crowdfunding.proto.GenerateBurnFromTxRequest
import com.ampnet.crowdfunding.proto.GenerateTransferTxRequest
import com.ampnet.crowdfunding.proto.GenerateApproveTxRequest
import com.ampnet.crowdfunding.proto.BalanceRequest
import com.ampnet.crowdfunding.proto.BalanceResponse
import com.ampnet.crowdfunding.proto.Empty
import com.ampnet.crowdfunding.proto.GenerateAddOrganizationTxRequest
import com.ampnet.crowdfunding.proto.GetAllOrganizationsResponse
import com.ampnet.crowdfunding.proto.OrganizationExistsRequest
import com.ampnet.crowdfunding.proto.OrganizationExistsResponse
import com.ampnet.crowdfunding.proto.OrganizationVerifiedRequest
import com.ampnet.crowdfunding.proto.OrganizationVerifiedResponse
import com.ampnet.crowdfunding.proto.RawTxResponse
import io.grpc.stub.StreamObserver
import org.lognet.springboot.grpc.GRpcService
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.utils.Numeric
import java.math.BigInteger

@GRpcService
class BlockchainService(
    val transactionService: TransactionService,
    val walletService: WalletService,
    val addressService: AddressService,
    val ampnetService: AmpnetService,
    val eurService: EurService,
    val organizationService: OrganizationService,
    val properties: ApplicationProperties
) : BlockchainServiceGrpc.BlockchainServiceImplBase() {

    private val tokenFactor = BigInteger("10000000000000000") // 10e16

    override fun addWallet(request: AddWalletRequest, responseObserver: StreamObserver<PostTxResponse>) {
        try {
            val credentials = Credentials.create(properties.accounts.ampnetPrivateKey)
            val tx = ampnetService.generateAddWalletTx(
                    credentials.address,
                    request.address
            )
            val signedTx = sign(tx, credentials)
            val response = post(signedTx, TransactionType.WALLET_CREATE)

            walletService.storePublicKey(request.publicKey, response.txHash)

            responseObserver.onNext(response)
            responseObserver.onCompleted()
        } catch (e: Exception) {
            responseObserver.onError(e)
        }
    }

    override fun isWalletActive(request: WalletActiveRequest, responseObserver: StreamObserver<WalletActiveResponse>) {
        try {
            val address = addressService.getAddress(request.walletTxHash)
            responseObserver.onNext(
                    WalletActiveResponse.newBuilder()
                            .setActive(ampnetService.isWalletActive(address))
                            .build()
            )
            responseObserver.onCompleted()
        } catch (e: Exception) {
            responseObserver.onError(e)
        }
    }

    override fun generateAddOrganizationTx(request: GenerateAddOrganizationTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        try {
            val wallet = addressService.getAddress(request.fromTxHash)
            val tx = ampnetService.generateAddOrganizationTx(wallet)
            responseObserver.onNext(convert(tx))
            responseObserver.onCompleted()
        } catch (e: Exception) {
            responseObserver.onError(e)
        }
    }

    override fun getAllOrganizations(request: Empty, responseObserver: StreamObserver<GetAllOrganizationsResponse>) {
        try {
            responseObserver.onNext(
                    GetAllOrganizationsResponse.newBuilder()
                            .addAllOrganizations(ampnetService.getAllOrganizations())
                            .build()
            )
            responseObserver.onCompleted()
        } catch (e: Exception) {
            responseObserver.onError(e)
        }
    }


    override fun organizationExists(request: OrganizationExistsRequest, responseObserver: StreamObserver<OrganizationExistsResponse>) {
        try {
            val wallet = addressService.getAddress(request.organizationTxHash)
            responseObserver.onNext(
                    OrganizationExistsResponse.newBuilder()
                            .setExists(ampnetService.organizationExists(wallet))
                            .build()
            )
            responseObserver.onCompleted()
        } catch (e: Exception) {
            responseObserver.onError(e)
        }
    }

    override fun generateMintTx(request: GenerateMintTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        try {
            val wallet = addressService.getAddress(request.toTxHash)
            val tx = eurService.generateMintTx(
                    request.from,
                    wallet,
                    eurToToken(request.amount)
            )
            responseObserver.onNext(convert(tx))
            responseObserver.onCompleted()
        } catch (e: Exception) {
            responseObserver.onError(e)
        }
    }

    override fun generateBurnFromTx(request: GenerateBurnFromTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        try {
            val wallet = addressService.getAddress(request.burnFromTxHash)
            val tx = eurService.generateBurnFromTx(
                    request.from,
                    wallet,
                    eurToToken(request.amount)
            )
            responseObserver.onNext(convert(tx))
            responseObserver.onCompleted()
        } catch (e: Exception) {
            responseObserver.onError(e)
        }
    }

    override fun generateApproveTx(request: GenerateApproveTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        try {
            val publicKey = walletService.getPublicKey(request.fromTxHash)
            val address = addressService.getAddress(request.fromTxHash)
            val tx = eurService.generateApproveTx(
                    address,
                    request.approve,
                    eurToToken(request.amount)
            )
            responseObserver.onNext(convert(tx, publicKey))
            responseObserver.onCompleted()
        } catch (e: Exception) {
            responseObserver.onError(e)
        }
    }

    override fun getBalance(request: BalanceRequest, responseObserver: StreamObserver<BalanceResponse>) {
        try {
            val wallet = addressService.getAddress(request.walletTxHash)
            val balance = eurService.balanceOf(wallet)
            val response = BalanceResponse.newBuilder()
                    .setBalance(tokenToEur(balance))
                    .build()
            responseObserver.onNext(response)
            responseObserver.onCompleted()
        } catch (e: Exception) {
            responseObserver.onError(e)
        }
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
    override fun generateTransferTx(request: GenerateTransferTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        try {
            val fromWallet = addressService.getAddress(request.fromTxHash)
            val toWallet = addressService.getAddress(request.toTxHash)
            val publicKey = walletService.getPublicKey(request.fromTxHash)
            val tx = eurService.generateTransferTx(
                    fromWallet,
                    toWallet,
                    eurToToken(request.amount)
            )
            responseObserver.onNext(convert(tx, publicKey))
            responseObserver.onCompleted()
        } catch (e: Exception) {
            responseObserver.onError(e)
        }
    }

    override fun activateOrganization(request: ActivateOrganizationRequest, responseObserver: StreamObserver<PostTxResponse>) {
        try {
            val credentials = Credentials.create(properties.accounts.ampnetPrivateKey)
            val wallet = addressService.getAddress(request.organizationTxHash)
            val activateOrgTx = organizationService.generateActivateTx(credentials.address, wallet)
            val signedTx = sign(activateOrgTx, credentials)
            responseObserver.onNext(post(signedTx, TransactionType.ORG_ACTIVATE))
            responseObserver.onCompleted()
        } catch (e: Exception) {
            responseObserver.onError(e)
        }
    }

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
    override fun isOrganizationVerified(request: OrganizationVerifiedRequest, responseObserver: StreamObserver<OrganizationVerifiedResponse>) {
        val wallet = addressService.getAddress(request.organizationTxHash)
        val verified = organizationService.isVerified(wallet)
        responseObserver.onNext(
                OrganizationVerifiedResponse.newBuilder()
                        .setVerified(verified)
                        .build()
        )
        responseObserver.onCompleted()
    }

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
            responseObserver.onNext(
                    post(request.data, TransactionType.valueOf(request.txType.name))
            )
            responseObserver.onCompleted()
        } catch (e: Exception) {
            responseObserver.onError(e)
        }
    }

    private fun sign(rawTx: RawTransaction, credentials: Credentials): String {
        val signedTx = TransactionEncoder.signMessage(rawTx, credentials)
        return Numeric.toHexString(signedTx)
    }

    private fun post(txData: String, txType: TransactionType): PostTxResponse {
        val tx = transactionService.postAndCacheTransaction(txData, txType)
        return PostTxResponse.newBuilder()
                .setTxHash(tx.hash)
                .build()
    }

    private fun convert(tx: RawTransaction, publicKey: String? = null): RawTxResponse {
        return RawTxResponse.newBuilder()
                .setData(tx.data)
                .setGasLimit(tx.gasLimit.longValueExact())
                .setGasPrice(tx.gasPrice.longValueExact())
                .setNonce(tx.nonce.longValueExact())
                .setTo(tx.to)
                .setPublicKey(publicKey ?: "")
                .build()
    }

    private fun eurToToken(eur: Long): BigInteger {
        return tokenFactor * eur.toBigInteger()
    }

    private fun tokenToEur(token: BigInteger): Long {
        return (token / tokenFactor).longValueExact()
    }
}