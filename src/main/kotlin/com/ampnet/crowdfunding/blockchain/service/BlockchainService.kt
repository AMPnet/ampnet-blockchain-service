package com.ampnet.crowdfunding.blockchain.service

import com.ampnet.crowdfunding.blockchain.config.ApplicationProperties
import com.ampnet.crowdfunding.blockchain.contract.CoopService
import com.ampnet.crowdfunding.blockchain.contract.EurService
import com.ampnet.crowdfunding.blockchain.contract.OrganizationService
import com.ampnet.crowdfunding.blockchain.contract.ProjectService
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
import com.ampnet.crowdfunding.proto.BalanceRequest
import com.ampnet.crowdfunding.proto.BalanceResponse
import com.ampnet.crowdfunding.proto.Empty
import com.ampnet.crowdfunding.proto.GenerateAddMemberTxRequest
import com.ampnet.crowdfunding.proto.GenerateAddOrganizationTxRequest
import com.ampnet.crowdfunding.proto.GenerateAddProjectTxRequest
import com.ampnet.crowdfunding.proto.GenerateInvestmentTxRequest
import com.ampnet.crowdfunding.proto.GenerateApproveWithdrawTxRequest
import com.ampnet.crowdfunding.proto.GenerateCancelPendingInvestmentTxRequest
import com.ampnet.crowdfunding.proto.GenerateConfirmInvestmentTxRequest
import com.ampnet.crowdfunding.proto.GeneratePayoutRevenueSharesTxRequest
import com.ampnet.crowdfunding.proto.GenerateStartRevenuePayoutTxRequest
import com.ampnet.crowdfunding.proto.GenerateWithdrawOrganizationFundsTxRequest
import com.ampnet.crowdfunding.proto.GenerateWithdrawProjectFundsTx
import com.ampnet.crowdfunding.proto.GetOrganizationsResponse
import com.ampnet.crowdfunding.proto.OrganizationExistsRequest
import com.ampnet.crowdfunding.proto.OrganizationExistsResponse
import com.ampnet.crowdfunding.proto.OrganizationMembersRequest
import com.ampnet.crowdfunding.proto.OrganizationMembersResponse
import com.ampnet.crowdfunding.proto.OrganizationProjectsRequest
import com.ampnet.crowdfunding.proto.OrganizationProjectsResponse
import com.ampnet.crowdfunding.proto.OrganizationVerifiedRequest
import com.ampnet.crowdfunding.proto.OrganizationVerifiedResponse
import com.ampnet.crowdfunding.proto.PostVaultTxRequest
import com.ampnet.crowdfunding.proto.ProjectCompletelyFundedRequest
import com.ampnet.crowdfunding.proto.ProjectCompletelyFundedResponse
import com.ampnet.crowdfunding.proto.ProjectCurrentTotalInvestmentRequest
import com.ampnet.crowdfunding.proto.ProjectCurrentTotalInvestmentResponse
import com.ampnet.crowdfunding.proto.ProjectInvestmentCapRequest
import com.ampnet.crowdfunding.proto.ProjectInvestmentCapResponse
import com.ampnet.crowdfunding.proto.ProjectMaxInvestmentPerUserRequest
import com.ampnet.crowdfunding.proto.ProjectMaxInvestmentPerUserResponse
import com.ampnet.crowdfunding.proto.ProjectMinInvestmentPerUserRequest
import com.ampnet.crowdfunding.proto.ProjectMinInvestmentPerUserResponse
import com.ampnet.crowdfunding.proto.ProjectTotalInvestmentForUserRequest
import com.ampnet.crowdfunding.proto.ProjectTotalInvestmentForUserResponse
import com.ampnet.crowdfunding.proto.GenerateWithdrawInvestmentTxRequest
import com.ampnet.crowdfunding.proto.RawTxResponse
import io.github.novacrypto.base58.Base58
import io.grpc.stub.StreamObserver
import mu.KLogging
import org.lognet.springboot.grpc.GRpcService
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.rlp.RlpDecoder
import org.web3j.rlp.RlpList
import org.web3j.rlp.RlpString
import org.web3j.utils.Numeric
import java.math.BigInteger

@GRpcService
class BlockchainService(
    val transactionService: TransactionService,
    val walletService: WalletService,
    val coopService: CoopService,
    val eurService: EurService,
    val organizationService: OrganizationService,
    val projectService: ProjectService,
    val properties: ApplicationProperties
) : BlockchainServiceGrpc.BlockchainServiceImplBase() {

    companion object : KLogging()

    private val tokenFactor = BigInteger("10000000000000000") // 10e16

    override fun addWallet(request: AddWalletRequest, responseObserver: StreamObserver<PostTxResponse>) {
        logger.info { "Received request to addWallet: $request" }
        try {
            val credentials = Credentials.create(properties.accounts.coopPrivateKey)
            val tx = coopService.generateAddWalletTx(
                    credentials.address,
                    request.address
            )
            val signedTx = sign(tx, credentials)
            val response = post(signedTx, TransactionType.WALLET_CREATE)
            logger.info { "Successfully added wallet: ${response.txHash}" }

            walletService.storePublicKey(request.publicKey, response.txHash)

            responseObserver.onNext(response)
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to addWallet: $request" }
            responseObserver.onError(e)
        }
    }

    override fun isWalletActive(request: WalletActiveRequest, responseObserver: StreamObserver<WalletActiveResponse>) {
        logger.info { "Received request for isWalletActive: $request" }
        try {
            val (address, _) = getPublicIdentity(request.walletTxHash)
            logger.info { "Address $address for hash: ${request.walletTxHash}" }
            val isWalletActive = coopService.isWalletActive(address)
            logger.info { "Wallet $address is active: $isWalletActive" }
            responseObserver.onNext(
                    WalletActiveResponse.newBuilder()
                            .setActive(isWalletActive)
                            .build()
            )
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed isWalletActive: $request" }
            responseObserver.onError(e)
        }
    }

    override fun generateAddOrganizationTx(request: GenerateAddOrganizationTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        logger.info { "Received request to generateAddOrganizationTx: $request" }
        try {
            val (address, pubKey) = getPublicIdentity(request.fromTxHash)
            logger.debug { "Address $address for hash: ${request.fromTxHash}" }
            val tx = coopService.generateAddOrganizationTx(address)
            logger.info { "Successfully generateAddOrganization" }
            responseObserver.onNext(convert(tx, pubKey))
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to generateAddOrganizationTx" }
            responseObserver.onError(e)
        }
    }

    override fun getOrganizations(request: Empty, responseObserver: StreamObserver<GetOrganizationsResponse>) {
        logger.info { "Received request to getOrganizations" }
        try {
            val organizations = coopService.getOrganizations()
            logger.info { "Successfully getOrganizations: $organizations" }
            responseObserver.onNext(
                    GetOrganizationsResponse.newBuilder()
                            .addAllOrganizations(organizations)
                            .build()
            )
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to getOrganizations" }
            responseObserver.onError(e)
        }
    }

    override fun organizationExists(request: OrganizationExistsRequest, responseObserver: StreamObserver<OrganizationExistsResponse>) {
        logger.info { "Received request for organizationExists: $request" }
        try {
            val (address, _) = getPublicIdentity(request.organizationTxHash)
            logger.debug { "Address $address for hash: ${request.organizationTxHash}" }
            val organizationExists = coopService.isOrganizationActive(address)
            logger.info { "Organization with wallet: $address - exists: $organizationExists" }
            responseObserver.onNext(
                    OrganizationExistsResponse.newBuilder()
                            .setExists(organizationExists)
                            .build()
            )
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to check if organizationExists" }
            responseObserver.onError(e)
        }
    }

    override fun generateMintTx(request: GenerateMintTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        logger.warn { "Received request generateMintTx: $request" }
        try {
            val (address, _) = getPublicIdentity(request.toTxHash)
            logger.info { "Mint for wallet: $address" }
            val tx = eurService.generateMintTx(
                    request.from,
                    address,
                    eurToToken(request.amount)
            )
            logger.info { "Successfully generatedMintTx: $tx" }
            responseObserver.onNext(convert(tx))
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to generateMintTx" }
            responseObserver.onError(e)
        }
    }

    override fun generateBurnFromTx(request: GenerateBurnFromTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        logger.warn { "Received request generateBurnFromTx: $request" }
        try {
            val (address, _) = getPublicIdentity(request.burnFromTxHash)
            logger.info { "Burn from wallet: $address" }
            val tx = eurService.generateBurnFromTx(
                    request.from,
                    address,
                    eurToToken(request.amount)
            )
            logger.info { "Successfully generatedBurnFromTx: $tx" }
            responseObserver.onNext(convert(tx))
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to generateBurnFromTx" }
            responseObserver.onError(e)
        }
    }

    override fun generateApproveWithdrawTx(request: GenerateApproveWithdrawTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        logger.info { "Received request generateApproveWithdrawTx: $request" }
        try {
            val (address, pubKey) = getPublicIdentity(request.fromTxHash)
            logger.info { "Approve withdraw from wallet $address with public key $pubKey" }
            val tokenIssuer = properties.accounts.issuingAuthorityAddress
            val tx = eurService.generateApproveTx(
                    address,
                    tokenIssuer,
                    eurToToken(request.amount)
            )
            logger.info { "Successfully generateApproveWithdrawTx: $tx" }
            responseObserver.onNext(convert(tx, pubKey))
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to generateApproveWithdrawTx" }
            responseObserver.onError(e)
        }
    }

    override fun generateInvestmentTx(request: GenerateInvestmentTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        logger.info { "Received request generateInvestmentTx: $request" }
        try {
            val (address, pubKey) = getPublicIdentity(request.fromTxHash)
            logger.info { "Approve investment from wallet $address with public key $pubKey" }
            val (projectAddress, _) = getPublicIdentity(request.projectTxHash)
            logger.info { "Approve investment into project with address $projectAddress" }
            val tx = eurService.generateApproveTx(
                    address,
                    projectAddress,
                    eurToToken(request.amount)
            )
            logger.info { "Successfully generateInvestmentTx: $tx" }
            responseObserver.onNext(convert(tx, pubKey))
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to generateInvestmentTx" }
            responseObserver.onError(e)
        }
    }

    override fun generateCancelPendingInvestmentTx(request: GenerateCancelPendingInvestmentTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        logger.info { "Received request to generateCancelPendingInvestmentTx: $request" }
        try {
            val (address, pubKey) = getPublicIdentity(request.fromTxHash)
            logger.info { "Investor wallet $address with public key $pubKey" }
            val (projectAddress, _) = getPublicIdentity(request.projectTxHash)
            logger.info { "Cancel investment approval for project with address $projectAddress" }
            val tx = eurService.generateApproveTx(
                    address,
                    projectAddress,
                    BigInteger.ZERO
            )
            logger.info { "Successfully generateCancelPendingInvestmentTx: $tx" }
            responseObserver.onNext(convert(tx, pubKey))
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to generateCancelPendingInvestmentTx" }
            responseObserver.onError(e)
        }
    }

    override fun getBalance(request: BalanceRequest, responseObserver: StreamObserver<BalanceResponse>) {
        logger.info { "Received request to getBalance: $request" }
        try {
            val (address, _) = getPublicIdentity(request.walletTxHash)
            logger.debug { "Address $address for hash: ${request.walletTxHash}" }
            val balance = eurService.balanceOf(address)
            logger.info { "Balance = $balance for hash: ${request.walletTxHash}" }
            val response = BalanceResponse.newBuilder()
                    .setBalance(tokenToEur(balance))
                    .build()
            responseObserver.onNext(response)
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to getBalance" }
            responseObserver.onError(e)
        }
    }

    override fun generateConfirmInvestmentTx(request: GenerateConfirmInvestmentTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        logger.info { "Received request to generateConfirmInvestmentTx: $request" }
        try {
            val (wallet, pubKey) = getPublicIdentity(request.fromTxHash)
            logger.debug { "Address $wallet for hash ${request.fromTxHash}" }
            logger.debug { "Public key $pubKey for hash ${request.fromTxHash}" }
            val (project, _) = getPublicIdentity(request.projectTxHash)
            logger.debug { "Project address $project for hash ${request.projectTxHash}" }
            val tx = projectService.generateInvestTx(wallet, project)
            logger.info { "Successfully generateConfirmInvestmentTx: $tx" }
            responseObserver.onNext(convert(tx, pubKey))
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to generateConfirmInvestmentTx" }
            responseObserver.onError(e)
        }
    }

    override fun generateTransferTx(request: GenerateTransferTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
    logger.info { "Received request to generateTransferTx: $request" }
        try {
            val (fromAddress, fromPubKey) = getPublicIdentity(request.fromTxHash)
            val (toAddress, _) = getPublicIdentity(request.toTxHash)
            val tx = eurService.generateTransferTx(
                    fromAddress,
                    toAddress,
                    eurToToken(request.amount)
            )
            logger.info { "Successfully generateTransferTx" }
            responseObserver.onNext(convert(tx, fromPubKey))
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to generateTransferTx" }
            responseObserver.onError(e)
        }
    }

    override fun activateOrganization(request: ActivateOrganizationRequest, responseObserver: StreamObserver<PostTxResponse>) {
        logger.info { "Received request to activateOrganization: $request" }
        try {
            val credentials = Credentials.create(properties.accounts.coopPrivateKey)
            val (address, _) = getPublicIdentity(request.organizationTxHash)
            logger.debug { "Wallet $address for hash: ${request.organizationTxHash}" }
            val activateOrgTx = organizationService.generateActivateTx(credentials.address, address)
            val signedTx = sign(activateOrgTx, credentials)
            logger.info { "Successfully signed transaction: activateOrganization" }
            responseObserver.onNext(post(signedTx, TransactionType.ORG_ACTIVATE))
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to activateOrganization" }
            responseObserver.onError(e)
        }
    }

    override fun generateWithdrawOrganizationFundsTx(request: GenerateWithdrawOrganizationFundsTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        logger.info { "Received request to generateWithdrawOrganizationFundsTx: $request" }
        try {
            val (fromAddress, fromPubKey) = getPublicIdentity(request.fromTxHash)
            logger.debug { "Wallet from $fromAddress for hash: ${request.fromTxHash}" }
            val (orgAddress, _) = getPublicIdentity(request.organizationTxHash)
            logger.debug { "Wallet org $orgAddress for hash: ${request.organizationTxHash}" }

            val tx = organizationService.generateWithdrawFundsTx(
                    fromAddress,
                    orgAddress,
                    eurToToken(request.amount)
            )
            logger.info { "Successfully generateWithdrawOrganizationFundsTx" }
            responseObserver.onNext(convert(tx, fromPubKey))
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to generateWithdrawOrganizationFundsTx" }
            responseObserver.onError(e)
        }
    }

    override fun generateAddOrganizationMemberTx(request: GenerateAddMemberTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        logger.info { "Received request to generateAddOrganizationMemberTx: $request" }
        try {
            val (fromAddress, fromPubKey) = getPublicIdentity(request.fromTxHash)
            logger.debug { "Wallet from $fromAddress for hash: ${request.fromTxHash}" }
            val (orgAddress, _) = getPublicIdentity(request.organizationTxHash)
            logger.debug { "Wallet org $orgAddress for hash: ${request.organizationTxHash}" }
            val (memberAddress, _) = getPublicIdentity(request.memberTxHash)
            logger.debug { "Wallet member $memberAddress for hash: ${request.memberTxHash}" }

            val tx = organizationService.generateAddMemberTx(fromAddress, orgAddress, memberAddress)
            logger.info { "Successfully generateAddOrganizationMemberTx" }
            responseObserver.onNext(convert(tx, fromPubKey))
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to generateAddOrganizationMemberTx" }
            responseObserver.onError(e)
        }
    }

    override fun generateAddOrganizationProjectTx(request: GenerateAddProjectTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        logger.info { "Received request to generateAddOrganizationProjectTx: $request" }
        try {
            val (fromAddress, fromPubKey) = getPublicIdentity(request.fromTxHash)
            logger.debug { "Wallet from $fromAddress for hash: ${request.fromTxHash}" }
            val (orgAddress, _) = getPublicIdentity(request.organizationTxHash)
            logger.debug { "Wallet org $orgAddress for hash: ${request.organizationTxHash}" }
            val tx = organizationService.generateAddProjectTx(
                    fromAddress,
                    orgAddress,
                    eurToToken(request.maxInvestmentPerUser),
                    eurToToken(request.minInvestmentPerUser),
                    eurToToken(request.investmentCap),
                    request.endInvestmentTime.toBigInteger()
            )
            logger.info { "Successfully generateAddOrganizationProjectTx" }
            responseObserver.onNext(convert(tx, fromPubKey))
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to generateAddOrganizationProjectTx" }
            responseObserver.onError(e)
        }
    }

    override fun isOrganizationVerified(request: OrganizationVerifiedRequest, responseObserver: StreamObserver<OrganizationVerifiedResponse>) {
        logger.info { "Received request isOrganizationVerified: $request" }
        try {
            val (orgAddress, _) = getPublicIdentity(request.organizationTxHash)
            logger.debug { "Wallet $orgAddress for hash: ${request.organizationTxHash}" }
            val verified = organizationService.isVerified(orgAddress)
            logger.info { "Wallet for hash: ${request.organizationTxHash} is verified: $verified" }

            responseObserver.onNext(
                    OrganizationVerifiedResponse.newBuilder()
                            .setVerified(verified)
                            .build()
            )
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to isOrganizationVerified" }
            responseObserver.onError(e)
        }
    }

    override fun getAllOrganizationProjects(request: OrganizationProjectsRequest, responseObserver: StreamObserver<OrganizationProjectsResponse>) {
        logger.info { "Received request to getAllOrganizationProjects: $request" }
        try {
            val (orgAddress, _) = getPublicIdentity(request.organizationTxHash)
            logger.debug { "Wallet $orgAddress for hash: ${request.organizationTxHash}" }
            val projects = organizationService.getProjects(orgAddress)
            logger.info { "All projects: $projects" }
            responseObserver.onNext(
                    OrganizationProjectsResponse.newBuilder()
                            .addAllProjects(projects)
                            .build()
            )
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to getAllOrganizationProjects" }
            responseObserver.onError(e)
        }
    }

    override fun getAllOrganizationMembers(request: OrganizationMembersRequest, responseObserver: StreamObserver<OrganizationMembersResponse>) {
        logger.info { "Received request to getAllOrganizationMembers: $request" }
        try {
            val (orgAddress, _) = getPublicIdentity(request.organizationTxHash)
            logger.debug { "Wallet $orgAddress for hash: ${request.organizationTxHash}" }
            val members = organizationService.getMembers(orgAddress)
            logger.info { "All members: $members" }
            responseObserver.onNext(
                    OrganizationMembersResponse.newBuilder()
                            .addAllMembers(members)
                            .build()
            )
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to getAllOrganizationMembers" }
            responseObserver.onError(e)
        }
    }

    override fun generateStartRevenuePayoutTx(request: GenerateStartRevenuePayoutTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        logger.info { "Received request to generateStartRevenuePayoutTx: $request" }
        try {
            val (address, publicKey) = getPublicIdentity(request.fromTxHash)
            logger.info { "Wallet $address with public key $publicKey for txHash ${request.fromTxHash}" }
            val (projAddress, _) = getPublicIdentity(request.projectTxHash)
            logger.info { "Project $projAddress for txHash ${request.projectTxHash}" }
            val tx = projectService.generateStartRevenuePayoutTx(
                    address,
                    projAddress,
                    eurToToken(request.revenue)
            )
            logger.info { "Successfully generateStartRevenuePayoutTx: $tx" }
            responseObserver.onNext(convert(tx, publicKey))
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to generateStartRevenuePayoutTx" }
            responseObserver.onError(e)
        }
    }

    override fun generatePayoutRevenueSharesTx(request: GeneratePayoutRevenueSharesTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        logger.info { "Received request to generatePayoutRevenueSharesTx: $request" }
        try {
            val (address, publicKey) = getPublicIdentity(request.fromTxHash)
            logger.info { "Wallet $address with public key $publicKey for txHash ${request.fromTxHash}" }
            val (projAddress, _) = getPublicIdentity(request.projectTxHash)
            logger.info { "Project $projAddress for txHash ${request.projectTxHash}" }
            val tx = projectService.generatePayoutRevenueSharesTx(address, projAddress)
            logger.info { "Successfully generatePayoutRevenueSharesTx: $tx" }
            responseObserver.onNext(convert(tx, publicKey))
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to generatePayoutRevenueSharesTx" }
            responseObserver.onError(e)
        }
    }

    override fun generateWithdrawInvestmentTx(request: GenerateWithdrawInvestmentTxRequest, responseObserver: StreamObserver<RawTxResponse>) {
        logger.info { "Received request to generateWithdrawInvestmentTx: $request" }
        try {
            val (address, publicKey) = getPublicIdentity(request.fromTxHash)
            logger.info { "Wallet $address with public key $publicKey for txHash ${request.fromTxHash}" }
            val (projAddress, _) = getPublicIdentity(request.projectTxHash)
            logger.info { "Project $projAddress for txHash ${request.projectTxHash}" }
            val tx = projectService.generateWithdrawInvestmentTx(address, projAddress)
            logger.info { "Successfully generateWithdrawInvestmentTx: $tx" }
            responseObserver.onNext(convert(tx, publicKey))
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to generateWithdrawInvestmentTx" }
            responseObserver.onError(e)
        }
    }

    override fun generateWithdrawProjectFundsTx(request: GenerateWithdrawProjectFundsTx, responseObserver: StreamObserver<RawTxResponse>) {
        logger.info { "Received request to generateWithdrawProjectFundsTx: $request" }
        try {
            val (from, publicKey) = getPublicIdentity(request.fromTxHash)
            logger.debug { "Wallet $from and public key $publicKey for hash: ${request.fromTxHash}" }
            val (project, _) = getPublicIdentity(request.projectTxHash)
            logger.debug { "Project $project for hash ${request.projectTxHash}" }
            val tx = projectService.generateWithdrawTx(
                    from,
                    project,
                    eurToToken(request.amount)
            )
            logger.info { "Successfully generateWithdrawProjectFundsTx: $tx" }
            responseObserver.onNext(convert(tx, publicKey))
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to generateWithdrawProjectFundsTx" }
            responseObserver.onError(e)
        }
    }

    override fun getProjectMaxInvestmentPerUser(request: ProjectMaxInvestmentPerUserRequest, responseObserver: StreamObserver<ProjectMaxInvestmentPerUserResponse>) {
        logger.info { "Received request to getProjectMaxInvestmentPerUser: $request" }
        try {
            val (projectAddress, _) = getPublicIdentity(request.projectTxHash)
            logger.debug { "Wallet $projectAddress for hash: ${request.projectTxHash}" }
            val maxInvestmentPerUser = projectService.getMaxInvestmentPerUser(projectAddress)
            logger.info { "Max investment per user = $maxInvestmentPerUser" }
            responseObserver.onNext(
                    ProjectMaxInvestmentPerUserResponse.newBuilder()
                            .setAmount(tokenToEur(maxInvestmentPerUser))
                            .build()
            )
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to getProjectMaxInvestmentPerUser" }
            responseObserver.onError(e)
        }
    }

    override fun getProjectMinInvestmentPerUser(request: ProjectMinInvestmentPerUserRequest, responseObserver: StreamObserver<ProjectMinInvestmentPerUserResponse>) {
        logger.info { "Received request to getProjectMinInvestmentPerUser: $request" }
        try {
            val (projectAddress, _) = getPublicIdentity(request.projectTxHash)
            logger.debug { "Wallet $projectAddress for hash: ${request.projectTxHash}" }
            val minInvestmentPerUser = projectService.getMinInvestmentPerUser(projectAddress)
            logger.info { "Min investment per user = $minInvestmentPerUser" }
            responseObserver.onNext(
                    ProjectMinInvestmentPerUserResponse.newBuilder()
                            .setAmount(tokenToEur(minInvestmentPerUser))
                            .build()
            )
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to getProjectMinInvestmentPerUser" }
            responseObserver.onError(e)
        }
    }

    override fun getProjectInvestmentCap(request: ProjectInvestmentCapRequest, responseObserver: StreamObserver<ProjectInvestmentCapResponse>) {
        logger.info { "Received request to getProjectInvestmentCap: $request" }
        try {
            val (projectAddress, _) = getPublicIdentity(request.projectTxHash)
            logger.debug { "Wallet $projectAddress for hash: ${request.projectTxHash}" }
            val investmentCap = projectService.getInvestmentCap(projectAddress)
            logger.info { "Investment cap = $investmentCap" }
            responseObserver.onNext(
                    ProjectInvestmentCapResponse.newBuilder()
                            .setAmount(tokenToEur(investmentCap))
                            .build()
            )
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to getProjectInvestmentCap" }
            responseObserver.onError(e)
        }
    }

    override fun getProjectCurrentTotalInvestment(request: ProjectCurrentTotalInvestmentRequest, responseObserver: StreamObserver<ProjectCurrentTotalInvestmentResponse>) {
        logger.info { "Received request to getProjectCurrentTotalInvestment: $request" }
        try {
            val (projectAddress, _) = getPublicIdentity(request.projectTxHash)
            logger.debug { "Wallet $projectAddress for project hash: ${request.projectTxHash}" }
            val currentTotalInvestment = tokenToEur(projectService.getCurrentTotalInvestment(projectAddress))
            logger.info { "Current total investment = $currentTotalInvestment" }
            responseObserver.onNext(
                    ProjectCurrentTotalInvestmentResponse.newBuilder()
                            .setAmount(currentTotalInvestment)
                            .build()
            )
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to getProjectCurrentTotalInvestment" }
            responseObserver.onError(e)
        }
    }

    override fun getProjectTotalInvestmentForUser(request: ProjectTotalInvestmentForUserRequest, responseObserver: StreamObserver<ProjectTotalInvestmentForUserResponse>) {
        logger.info { "Received request to getProjectTotalInvestmentForUser: $request" }
        try {
            val (projectAddress, _) = getPublicIdentity(request.projectTxHash)
            logger.debug { "Wallet $projectAddress for project hash: ${request.projectTxHash}" }
            val (userAddress, _) = getPublicIdentity(request.userTxHash)
            logger.debug { "Wallet $userAddress for user hash: ${request.userTxHash}" }
            val investmentForUser = tokenToEur(
                    projectService.getTotalInvestmentForUser(projectAddress, userAddress)
            )
            logger.info { "Investment for user = $investmentForUser" }
            responseObserver.onNext(
                    ProjectTotalInvestmentForUserResponse.newBuilder()
                            .setAmount(investmentForUser)
                            .build()
            )
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to getProjectTotalInvestmentForUser" }
            responseObserver.onError(e)
        }
    }

    override fun isProjectCompletelyFunded(request: ProjectCompletelyFundedRequest, responseObserver: StreamObserver<ProjectCompletelyFundedResponse>) {
        logger.info { "Received request to check isProjectCompletelyFunded: $request" }
        try {
            val (projectAddress, _) = getPublicIdentity(request.projectTxHash)
            logger.debug { "Wallet $projectAddress for project hash: ${request.projectTxHash}" }
            val completelyFunded = projectService.isCompletelyFunded(projectAddress)
            logger.info { "Project completely funded: $completelyFunded" }
            responseObserver.onNext(
                    ProjectCompletelyFundedResponse.newBuilder()
                            .setFunded(completelyFunded)
                            .build()
            )
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to check isProjectCompletelyFunded" }
            responseObserver.onError(e)
        }
    }

    override fun postTransaction(request: PostTxRequest, responseObserver: StreamObserver<PostTxResponse>) {
        logger.info { "Received request to postTransaction: $request" }
        try {
            val postResponse = post(request.data, TransactionType.valueOf(request.txType.name))
            logger.info { "Successfully postTransaction" }
            responseObserver.onNext(postResponse)
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to postTransaction" }
            responseObserver.onError(e)
        }
    }

    override fun postVaultTransaction(request: PostVaultTxRequest, responseObserver: StreamObserver<PostTxResponse>) {
        logger.info { "Received request to postVaultTransaction: $request" }
        try {
            val txData = decodeVaultTransaction(request.data)
            val postResponse = post(txData, TransactionType.valueOf(request.txType.name))
            logger.info { "Successfully postVaultTransaction" }
            responseObserver.onNext(postResponse)
            responseObserver.onCompleted()
        } catch (e: Exception) {
            logger.error(e) { "Failed to postVaultTransaction" }
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

    private fun decodeVaultTransaction(encodedTx: String): String {
        // Base58 decode
        val decodedBytesWithChecksum = Base58.base58Decode(encodedTx)
        val size = decodedBytesWithChecksum.size

        // Remove last 4 bytes (checksum)
        val decodedBytes = decodedBytesWithChecksum.take(size - 4).toByteArray()

        // Extract tx payload from decoded input
        val rlp = RlpDecoder.decode(decodedBytes).values[0] as RlpList
        val payload = rlp.values[3] as RlpList
        val tx = payload.values[0] as RlpString

        return tx.bytes.toString(Charsets.UTF_8)
    }

    private fun getPublicIdentity(txHash: String): Pair<String, String?> {
        val wallet = walletService.getByTxHash(txHash)
        val address = wallet.address.orEmpty()
        val pubKey = wallet.publicKey
        return Pair(address, pubKey)
    }
}