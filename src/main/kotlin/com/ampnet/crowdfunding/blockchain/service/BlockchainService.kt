package com.ampnet.crowdfunding.blockchain.service

import com.ampnet.crowdfunding.blockchain.contract.AmpnetService
import com.ampnet.crowdfunding.blockchain.contract.EurService
import com.ampnet.crowdfunding.blockchain.contract.OrganizationService
import com.ampnet.crowdfunding.blockchain.contract.ProjectService
import com.ampnet.crowdfunding.blockchain.contract.TransactionService
import com.ampnet.crowdfunding.proto.BalanceRequest
import com.ampnet.crowdfunding.proto.BalanceResponse
import com.ampnet.crowdfunding.proto.BlockchainServiceGrpc
import com.ampnet.crowdfunding.proto.GenerateActivateTxRequest
import com.ampnet.crowdfunding.proto.GenerateAddMemberTxRequest
import com.ampnet.crowdfunding.proto.GenerateAddOrganizationTxRequest
import com.ampnet.crowdfunding.proto.GenerateAddProjectTxRequest
import com.ampnet.crowdfunding.proto.GenerateAddWalletTxRequest
import com.ampnet.crowdfunding.proto.GenerateApproveTxRequest
import com.ampnet.crowdfunding.proto.GenerateBurnFromTxRequest
import com.ampnet.crowdfunding.proto.GenerateCancelInvestmentTx
import com.ampnet.crowdfunding.proto.GenerateInvestTxRequest
import com.ampnet.crowdfunding.proto.GenerateMintTxRequest
import com.ampnet.crowdfunding.proto.GenerateTransferOwnershipTx
import com.ampnet.crowdfunding.proto.GenerateTransferTxRequest
import com.ampnet.crowdfunding.proto.GenerateWithdrawOrganizationFundsTxRequest
import com.ampnet.crowdfunding.proto.GenerateWithdrawProjectFundsTx
import com.ampnet.crowdfunding.proto.GetAllOrganizationsRequest
import com.ampnet.crowdfunding.proto.GetAllOrganizationsResponse
import com.ampnet.crowdfunding.proto.OrganizationExistsRequest
import com.ampnet.crowdfunding.proto.OrganizationExistsResponse
import com.ampnet.crowdfunding.proto.OrganizationMembersRequest
import com.ampnet.crowdfunding.proto.OrganizationMembersResponse
import com.ampnet.crowdfunding.proto.OrganizationProjectsRequest
import com.ampnet.crowdfunding.proto.OrganizationProjectsResponse
import com.ampnet.crowdfunding.proto.OrganizationVerifiedRequest
import com.ampnet.crowdfunding.proto.OrganizationVerifiedResponse
import com.ampnet.crowdfunding.proto.PostTransactionRequest
import com.ampnet.crowdfunding.proto.PostTransactionResponse
import com.ampnet.crowdfunding.proto.ProjectCurrentTotalInvestmentRequest
import com.ampnet.crowdfunding.proto.ProjectCurrentTotalInvestmentResponse
import com.ampnet.crowdfunding.proto.ProjectDescriptionRequest
import com.ampnet.crowdfunding.proto.ProjectDescriptionResponse
import com.ampnet.crowdfunding.proto.ProjectInvestmentCapRequest
import com.ampnet.crowdfunding.proto.ProjectInvestmentCapResponse
import com.ampnet.crowdfunding.proto.ProjectLockedForInvestmentsRequest
import com.ampnet.crowdfunding.proto.ProjectLockedForInvestmentsResponse
import com.ampnet.crowdfunding.proto.ProjectMaxInvestmentPerUserRequest
import com.ampnet.crowdfunding.proto.ProjectMaxInvestmentPerUserResponse
import com.ampnet.crowdfunding.proto.ProjectMinInvestmentPerUserRequest
import com.ampnet.crowdfunding.proto.ProjectMinInvestmentPerUserResponse
import com.ampnet.crowdfunding.proto.ProjectNameRequest
import com.ampnet.crowdfunding.proto.ProjectNameResponse
import com.ampnet.crowdfunding.proto.ProjectTotalInvestmentForUserRequest
import com.ampnet.crowdfunding.proto.ProjectTotalInvestmentForUserResponse
import com.ampnet.crowdfunding.proto.RawTxResponse
import com.ampnet.crowdfunding.proto.WalletActiveRequest
import com.ampnet.crowdfunding.proto.WalletActiveResponse
import io.grpc.stub.StreamObserver
import org.lognet.springboot.grpc.GRpcService
import org.web3j.crypto.RawTransaction
import java.math.BigInteger

// TODO: - decide about amount type (uint256 in smart contracts, bigdecimal in blockchain service), where to convert?
@GRpcService
class BlockchainService(
    val transactionService: TransactionService,
    val ampnetService: AmpnetService,
    val eurService: EurService,
    val organizationService: OrganizationService,
    val projectService: ProjectService
) : BlockchainServiceGrpc.BlockchainServiceImplBase() {

    private val tokenFactor = BigInteger("10000000000000000") // 10e16

    override fun generateAddWalletTx(request: GenerateAddWalletTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        val tx = ampnetService.generateAddWalletTx(
                request.from,
                request.wallet
        )
        responseObserver.onNext(convert(tx))
        responseObserver.onCompleted()
    }

    override fun generateAddOrganizationTx(request: GenerateAddOrganizationTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        val tx = ampnetService.generateAddOrganizationTx(
                request.from,
                request.name
        )
        responseObserver.onNext(convert(tx))
        responseObserver.onCompleted()
    }

    override fun getAllOrganizations(request: GetAllOrganizationsRequest, responseObserver: StreamObserver<GetAllOrganizationsResponse>) {
        responseObserver.onNext(
                GetAllOrganizationsResponse.newBuilder()
                        .addAllOrganizations(ampnetService.getAllOrganizations())
                        .build()
        )
        responseObserver.onCompleted()
    }

    override fun isWalletActive(request: WalletActiveRequest, responseObserver: StreamObserver<WalletActiveResponse>) {
        responseObserver.onNext(
                WalletActiveResponse.newBuilder()
                        .setActive(ampnetService.isWalletActive(request.wallet))
                        .build()
        )
        responseObserver.onCompleted()
    }

    override fun organizationExists(request: OrganizationExistsRequest, responseObserver: StreamObserver<OrganizationExistsResponse>) {
        responseObserver.onNext(
                OrganizationExistsResponse.newBuilder()
                        .setExists(ampnetService.organizationExists(request.organization))
                        .build()
        )
        responseObserver.onCompleted()
    }

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
        val tx = eurService.generateApproveTx(
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

    override fun generateInvestTx(request: GenerateInvestTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        val tx = eurService.generateInvestTx(
                request.from,
                request.project,
                eurToToken(request.amount)
        )
        responseObserver.onNext(convert(tx))
        responseObserver.onCompleted()
    }

    override fun generateTransferTx(request: GenerateTransferTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        val tx = eurService.generateTransferTx(
                request.from,
                request.to,
                eurToToken(request.amount)
        )
        responseObserver.onNext(convert(tx))
        responseObserver.onCompleted()
    }

    override fun generateActivateOrganizationTx(request: GenerateActivateTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        val tx = organizationService.generateActivateTx(
                request.from,
                request.organization
        )
        responseObserver.onNext(convert(tx))
        responseObserver.onCompleted()
    }

    override fun generateWithdrawOrganizationFundsTx(request: GenerateWithdrawOrganizationFundsTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        val tx = organizationService.generateWithdrawFundsTx(
                request.from,
                request.organization,
                request.tokenIssuer,
                eurToToken(request.amount)
        )
        responseObserver.onNext(convert(tx))
        responseObserver.onCompleted()
    }

    override fun generateAddOrganizationMemberTx(request: GenerateAddMemberTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        val tx = organizationService.generateAddMemberTx(
                request.from,
                request.organization,
                request.member
        )
        responseObserver.onNext(convert(tx))
        responseObserver.onCompleted()
    }

    override fun generateAddOrganizationProjectTx(request: GenerateAddProjectTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        val tx = organizationService.generateAddProjectTx(
                request.from,
                request.organization,
                request.name,
                request.description,
                eurToToken(request.maxInvestmentPerUser),
                eurToToken(request.minInvestmentPerUser),
                eurToToken(request.investmentCap)
        )
        responseObserver.onNext(convert(tx))
        responseObserver.onCompleted()
    }

    override fun isOrganizationVerified(request: OrganizationVerifiedRequest, responseObserver: StreamObserver<OrganizationVerifiedResponse>) {
        val verified = organizationService.isVerified(
                request.organization
        )
        responseObserver.onNext(
                OrganizationVerifiedResponse.newBuilder()
                        .setVerified(verified)
                        .build()
        )
        responseObserver.onCompleted()
    }

    override fun getAllOrganizationProjects(request: OrganizationProjectsRequest, responseObserver: StreamObserver<OrganizationProjectsResponse>) {
        val projects = organizationService.getAllProjects(
                request.organization
        )
        responseObserver.onNext(
                OrganizationProjectsResponse.newBuilder()
                        .addAllProjects(projects)
                        .build()
        )
        responseObserver.onCompleted()
    }

    override fun getAllOrganizationMembers(request: OrganizationMembersRequest, responseObserver: StreamObserver<OrganizationMembersResponse>) {
        val members = organizationService.getMembers(
                request.organization
        )
        responseObserver.onNext(
                OrganizationMembersResponse.newBuilder()
                        .addAllMembers(members)
                        .build()
        )
        responseObserver.onCompleted()
    }

    override fun generateWithdrawProjectFundsTx(request: GenerateWithdrawProjectFundsTx, responseObserver: StreamObserver<RawTxResponse>) {
        val tx = projectService.generateWithdrawFundsTx(
                request.from,
                request.project,
                request.tokenIssuer,
                eurToToken(request.amount)
        )
        responseObserver.onNext(convert(tx))
        responseObserver.onCompleted()
    }

    override fun generateTransferOwnershipTx(request: GenerateTransferOwnershipTx, responseObserver: StreamObserver<RawTxResponse>) {
        val tx = projectService.generateTransferOwnershipTx(
                request.from,
                request.project,
                request.to,
                eurToToken(request.amount)
        )
        responseObserver.onNext(convert(tx))
        responseObserver.onCompleted()
    }

    override fun generateCancelInvestmentTx(request: GenerateCancelInvestmentTx, responseObserver: StreamObserver<RawTxResponse>) {
        val tx = projectService.generateCancelInvestmentTx(
                request.from,
                request.project,
                eurToToken(request.amount)
        )
        responseObserver.onNext(convert(tx))
        responseObserver.onCompleted()
    }

    override fun getProjectName(request: ProjectNameRequest, responseObserver: StreamObserver<ProjectNameResponse>) {
        responseObserver.onNext(
                ProjectNameResponse.newBuilder()
                        .setName(projectService.getName(request.project))
                        .build()
        )
        responseObserver.onCompleted()
    }

    override fun getProjectDescription(request: ProjectDescriptionRequest, responseObserver: StreamObserver<ProjectDescriptionResponse>) {
        responseObserver.onNext(
                ProjectDescriptionResponse.newBuilder()
                        .setDescription(projectService.getDescription(request.project))
                        .build()
        )
        responseObserver.onCompleted()
    }

    override fun getProjectMaxInvestmentPerUser(request: ProjectMaxInvestmentPerUserRequest, responseObserver: StreamObserver<ProjectMaxInvestmentPerUserResponse>) {
        responseObserver.onNext(
                ProjectMaxInvestmentPerUserResponse.newBuilder()
                        .setAmount(tokenToEur(projectService.getMaxInvestmentPerUser(request.project)))
                        .build()
        )
        responseObserver.onCompleted()
    }

    override fun getProjectMinInvestmentPerUser(request: ProjectMinInvestmentPerUserRequest, responseObserver: StreamObserver<ProjectMinInvestmentPerUserResponse>) {
        responseObserver.onNext(
                ProjectMinInvestmentPerUserResponse.newBuilder()
                        .setAmount(tokenToEur(projectService.getMinInvestmentPerUser(request.project)))
                        .build()
        )
        responseObserver.onCompleted()
    }

    override fun getProjectInvestmentCap(request: ProjectInvestmentCapRequest, responseObserver: StreamObserver<ProjectInvestmentCapResponse>) {
        responseObserver.onNext(
                ProjectInvestmentCapResponse.newBuilder()
                        .setAmount(tokenToEur(projectService.getInvestmentCap(request.project)))
                        .build()
        )
        responseObserver.onCompleted()
    }

    override fun getProjectCurrentTotalInvestment(request: ProjectCurrentTotalInvestmentRequest, responseObserver: StreamObserver<ProjectCurrentTotalInvestmentResponse>) {
        responseObserver.onNext(
                ProjectCurrentTotalInvestmentResponse.newBuilder()
                        .setAmount(tokenToEur(projectService.getCurrentTotalInvestment(request.project)))
                        .build()
        )
        responseObserver.onCompleted()
    }

    override fun getProjectTotalInvestmentForUser(request: ProjectTotalInvestmentForUserRequest, responseObserver: StreamObserver<ProjectTotalInvestmentForUserResponse>) {
        responseObserver.onNext(
                ProjectTotalInvestmentForUserResponse.newBuilder()
                        .setAmount(tokenToEur(projectService.getTotalInvestmentForUser(request.project, request.user)))
                        .build()
        )
        responseObserver.onCompleted()
    }

    override fun isProjectLockedForInvestments(request: ProjectLockedForInvestmentsRequest, responseObserver: StreamObserver<ProjectLockedForInvestmentsResponse>) {
        responseObserver.onNext(
                ProjectLockedForInvestmentsResponse.newBuilder()
                        .setLocked(projectService.isLockedForInvestments(request.project))
                        .build()
        )
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