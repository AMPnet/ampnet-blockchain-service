package com.ampnet.crowdfunding.blockchain.service

import com.ampnet.crowdfunding.blockchain.TestBase
import com.ampnet.crowdfunding.blockchain.enums.ErrorCode
import com.ampnet.crowdfunding.blockchain.enums.TransactionState
import com.ampnet.crowdfunding.blockchain.enums.TransactionType
import com.ampnet.crowdfunding.blockchain.enums.WalletType
import com.ampnet.crowdfunding.blockchain.persistence.model.Transaction
import com.ampnet.crowdfunding.blockchain.persistence.model.Wallet
import com.ampnet.crowdfunding.blockchain.persistence.repository.TransactionRepository
import com.ampnet.crowdfunding.blockchain.persistence.repository.WalletRepository
import com.ampnet.crowdfunding.proto.ActivateOrganizationRequest
import com.ampnet.crowdfunding.proto.AddWalletRequest
import com.ampnet.crowdfunding.proto.BalanceRequest
import com.ampnet.crowdfunding.proto.Empty
import com.ampnet.crowdfunding.proto.GenerateAddMemberTxRequest
import com.ampnet.crowdfunding.proto.GenerateAddOrganizationTxRequest
import com.ampnet.crowdfunding.proto.GenerateAddProjectTxRequest
import com.ampnet.crowdfunding.proto.GenerateApproveTxRequest
import com.ampnet.crowdfunding.proto.GenerateBurnFromTxRequest
import com.ampnet.crowdfunding.proto.GenerateMintTxRequest
import com.ampnet.crowdfunding.proto.GenerateTransferTxRequest
import com.ampnet.crowdfunding.proto.GenerateWithdrawOrganizationFundsTxRequest
import com.ampnet.crowdfunding.proto.OrganizationExistsRequest
import com.ampnet.crowdfunding.proto.OrganizationMembersRequest
import com.ampnet.crowdfunding.proto.OrganizationProjectsRequest
import com.ampnet.crowdfunding.proto.OrganizationVerifiedRequest
import com.ampnet.crowdfunding.proto.PostTxRequest
import com.ampnet.crowdfunding.proto.PostTxResponse
import com.ampnet.crowdfunding.proto.PostVaultTxRequest
import com.ampnet.crowdfunding.proto.ProjectInvestmentCapRequest
import com.ampnet.crowdfunding.proto.ProjectMaxInvestmentPerUserRequest
import com.ampnet.crowdfunding.proto.ProjectMinInvestmentPerUserRequest
import com.ampnet.crowdfunding.proto.RawTxResponse
import com.ampnet.crowdfunding.proto.WalletActiveRequest
import io.github.novacrypto.base58.Base58
import io.grpc.StatusRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.rlp.RlpEncoder
import org.web3j.rlp.RlpList
import org.web3j.rlp.RlpString
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.time.ZonedDateTime

class BlockchainServiceTest : TestBase() {

    @Autowired
    private lateinit var transactionRepository: TransactionRepository

    @Autowired
    private lateinit var walletRepository: WalletRepository

    @Test
    fun mustBeAbleToRegisterUser() {
        lateinit var txResponse: PostTxResponse
        verify("User can be created") {
            txResponse = addWallet(accounts.bob)
            assertThat(isWalletActive(txResponse.txHash)).isTrue()
        }
        verify("User creation transaction is stored in database") {
            val transactionsResponse = transactionRepository.findAll()
            assertThat(transactionsResponse).hasSize(1)

            val tx = transactionsResponse.first()
            assertTransaction(
                    tx,
                    expectedFrom = accounts.ampnetOwner.address,
                    expectedTo = txResponse.txHash,
                    expectedType = TransactionType.WALLET_CREATE
            )
        }
        verify("User wallet is stored in database") {
            val walletsResponse = walletRepository.findAll()
            assertThat(walletsResponse).hasSize(1)

            val wallet = walletsResponse.first()
            assertThat(wallet.transaction.hash).isEqualTo(txResponse.txHash)
            assertThat(wallet.type).isEqualTo(WalletType.USER)
            assertThat(wallet.address).isEqualTo(accounts.bob.address)
            assertThat(wallet.publicKey).isEqualTo(getPublicKey(accounts.bob))
        }
    }

    @Test
    fun mustBeAbleToDepositAndWithdrawTokens() {
        val initialBalance = 0L // user starts with empty wallet
        val depositAmount = 1550L // 15.50 EUR
        val withdrawAmount = 1550L // 15.50 EUR
        val finalBalance = initialBalance + depositAmount - withdrawAmount

        lateinit var bobWalletTxHash: String

        suppose("User Bob is registered on AMPnet and has zero balance") {
            bobWalletTxHash = addWallet(accounts.bob).txHash
            assertThat(getBalance(bobWalletTxHash)).isEqualTo(initialBalance)
        }
        verify("Bob can deposit some amount of EUR") {
            mint(bobWalletTxHash, depositAmount)
            assertThat(getBalance(bobWalletTxHash)).isEqualTo(depositAmount)
        }
        verify("Bob can withdraw some amount of EUR") {
            approveTokenIssuer(bobWalletTxHash, accounts.bob, withdrawAmount)
            burn(bobWalletTxHash, withdrawAmount)
            assertThat(getBalance(bobWalletTxHash)).isEqualTo(finalBalance)
        }
        verify("All transactions are stored in database") {
            val transactions = transactionRepository.findAll()
            assertThat(transactions).hasSize(4)

            // skip transactions[0] - wallet create (already tested)

            val mintTx = transactions[1]
            assertTransaction(
                    mintTx,
                    expectedFrom = accounts.eurOwner.address,
                    expectedTo = bobWalletTxHash,
                    expectedType = TransactionType.DEPOSIT,
                    expectedAmount = depositAmount
            )

            val approveTx = transactions[2]
            assertTransaction(
                    approveTx,
                    expectedFrom = bobWalletTxHash,
                    expectedTo = accounts.eurOwner.address,
                    expectedType = TransactionType.PENDING_WITHDRAW,
                    expectedAmount = withdrawAmount
            )

            val burnTx = transactions[3]
            assertTransaction(
                    burnTx,
                    expectedFrom = bobWalletTxHash,
                    expectedTo = accounts.eurOwner.address,
                    expectedType = TransactionType.WITHDRAW,
                    expectedAmount = withdrawAmount
            )
        }
    }

    @Test
    fun mustBeAbleToTransferFunds() {
        val bobInitialBalance = 1000L // 10.00 EUR
        val aliceInitialBalance = 0L
        val bobToAliceAmount = 1000L
        val bobFinalBalance = bobInitialBalance - bobToAliceAmount
        val aliceFinalBalance = aliceInitialBalance + bobToAliceAmount

        lateinit var bobTxHash: String
        lateinit var aliceTxHash: String

        suppose("Users Bob and Alice are registered on AMPnet with their initial balances") {
            bobTxHash = addWallet(accounts.bob).txHash
            mint(bobTxHash, bobInitialBalance)
            aliceTxHash = addWallet(accounts.alice).txHash
            mint(aliceTxHash, aliceInitialBalance)
        }
        verify("Bob can transfer funds to Alice's wallet") {
            transfer(bobTxHash, accounts.bob, aliceTxHash, bobToAliceAmount)
            assertThat(getBalance(bobTxHash)).isEqualTo(bobFinalBalance)
            assertThat(getBalance(aliceTxHash)).isEqualTo(aliceFinalBalance)
        }
        verify("All transactions are stored in database") {
            val transactions = transactionRepository.findAll()
            assertThat(transactions).hasSize(5)

            // skip transactions[0 and 2] - wallet create (already tested)

            val bobMintTx = transactions[1]
            assertTransaction(
                    bobMintTx,
                    expectedFrom = accounts.eurOwner.address,
                    expectedTo = bobTxHash,
                    expectedType = TransactionType.DEPOSIT,
                    expectedAmount = bobInitialBalance
            )

            val aliceMintTx = transactions[3]
            assertTransaction(
                    aliceMintTx,
                    expectedFrom = accounts.eurOwner.address,
                    expectedTo = aliceTxHash,
                    expectedType = TransactionType.DEPOSIT,
                    expectedAmount = aliceInitialBalance
            )

            val transferTx = transactions[4]
            assertTransaction(
                    transferTx,
                    expectedFrom = bobTxHash,
                    expectedTo = aliceTxHash,
                    expectedType = TransactionType.TRANSFER,
                    expectedAmount = bobToAliceAmount
            )
        }
    }

    @Test
    fun mustBeAbleToCreateAndActivateOrganization() {
        lateinit var bobTxHash: String
        lateinit var orgTxHash: String
        suppose("User Bob is registered on AMPnet") {
            bobTxHash = addWallet(accounts.bob).txHash
        }
        verify("Bob can create organization") {
            orgTxHash = addAndApproveOrganization(bobTxHash, accounts.bob)
            assertThat(getAllOrganizations()).hasSize(1)
            assertThat(organizationExists(orgTxHash))
            assertThat(organizationActive(orgTxHash))
        }
        verify("All transactions are stored in database") {
            val transactions = transactionRepository.findAll()
            assertThat(transactions).hasSize(3)

            val orgCreateTx = transactions[1]
            assertTransaction(
                    orgCreateTx,
                    expectedFrom = bobTxHash,
                    expectedTo = orgTxHash,
                    expectedType = TransactionType.ORG_CREATE
            )

            val orgActivateTx = transactions[2]
            assertTransaction(
                    orgActivateTx,
                    expectedFrom = accounts.ampnetOwner.address,
                    expectedTo = orgTxHash,
                    expectedType = TransactionType.ORG_ACTIVATE
            )
        }
    }

    @Test
    fun mustBeAbleToAddUserToOrganization() {
        lateinit var orgTxHash: String
        lateinit var bobTxHash: String
        lateinit var aliceTxHash: String
        suppose("Users Bob,Alice exist and Bob created Greenpeace organization") {
            bobTxHash = addWallet(accounts.bob).txHash
            aliceTxHash = addWallet(accounts.alice).txHash
            orgTxHash = addAndApproveOrganization(bobTxHash, accounts.bob)
        }
        verify("Bob can add new user to organization") {
            addOrganizationMember(orgTxHash, bobTxHash, accounts.bob, aliceTxHash)

            val members = getAllMembers(orgTxHash)
            assertThat(members).hasSize(1)
            assertThat(members.first()).isEqualTo(accounts.alice.address)
        }
        verify("All transactions are stored in database") {
            val transactions = transactionRepository.findAll()
            assertThat(transactions).hasSize(5)

            val addMemberTx = transactions[4]
            assertTransaction(
                    addMemberTx,
                    expectedFrom = bobTxHash,
                    expectedTo = orgTxHash,
                    expectedType = TransactionType.ORG_ADD_MEMBER
            )
        }
    }

    @Test
    fun mustBeAbleToAddProjectToOrganization() {
        lateinit var bobTxHash: String
        lateinit var orgTxHash: String
        lateinit var projectTxHash: String

        suppose("User Bob exists and is admin of Greenpeace organization") {
            bobTxHash = addWallet(accounts.bob).txHash
            orgTxHash = addAndApproveOrganization(bobTxHash, accounts.bob)
        }
        verify("Bob can create new investment project") {
            projectTxHash = createTestProject(orgTxHash, bobTxHash, accounts.bob, testProject)

            val projects = getAllProjects(orgTxHash)
            assertThat(projects).hasSize(1)

            val fetchedProject = getProject(projectTxHash)
            assertThat(fetchedProject).isEqualTo(testProject)
        }
        verify("All transactions are stored in database") {
            val transactions = transactionRepository.findAll()
            assertThat(transactions).hasSize(4)

            val addProjectTx = transactions[3]
            assertTransaction(
                    addProjectTx,
                    expectedFrom = bobTxHash,
                    expectedTo = projectTxHash,
                    expectedType = TransactionType.ORG_ADD_PROJECT
            )
        }
    }

    @Test
    fun mustBeAbleToWithdrawMoneyFromOrganization() {
        lateinit var orgTxHash: String
        lateinit var bobTxHash: String

        val initialOrgBalance = 1000L
        val withdrawAmount = 1000L
        val finalOrgBalance = initialOrgBalance - withdrawAmount

        suppose("User Bob exists and is admin of Greenpeace organization which has some positive balance") {
            bobTxHash = addWallet(accounts.bob).txHash
            orgTxHash = addAndApproveOrganization(bobTxHash, accounts.bob)
            mint(orgTxHash, initialOrgBalance)
        }
        verify("Bob can withdraw money from organization") {
            withdrawFromOrganization(orgTxHash, bobTxHash, accounts.bob, withdrawAmount)
            burn(orgTxHash, withdrawAmount)
            assertThat(getBalance(orgTxHash)).isEqualTo(finalOrgBalance)
        }
        verify("All transactions are stored in database") {
            val transactions = transactionRepository.findAll()
            assertThat(transactions).hasSize(6)

            val pendingWithdrawTx = transactions[4]
            assertTransaction(
                    pendingWithdrawTx,
                    expectedFrom = orgTxHash,
                    expectedTo = accounts.eurOwner.address,
                    expectedType = TransactionType.PENDING_ORG_WITHDRAW,
                    expectedAmount = withdrawAmount
            )

            val withdrawTx = transactions[5]
            assertTransaction(
                    withdrawTx,
                    expectedFrom = orgTxHash,
                    expectedTo = accounts.eurOwner.address,
                    expectedType = TransactionType.WITHDRAW,
                    expectedAmount = withdrawAmount
            )
        }
    }

    @Test
    fun shouldCatchErrorWhenWalletWithTxHashTransactionFailed() {
        val failedTxHash = "failed-tx-hash"

        suppose("Failed txHash is stored in database") {
            persistTestWallet(accounts.bob.address, failedTxHash, TransactionState.FAILED)
        }

        verify("Should catch error when querying balance of failedTxHash organization") {
            val e = assertThrows<StatusRuntimeException> {
                getBalance(failedTxHash)
            }
            val errorCode = ErrorCode.fromMessage(e.status.description.orEmpty())
            assertThat(errorCode).isEqualTo(ErrorCode.WALLET_CREATION_FAILED.code)
        }
    }

    @Test
    fun shouldCatchErrorWhenWalletWithTxHashStillPendingAndUserExecutesSomeTransaction() {
        val pendingTxHash = "pending-tx-hash"

        suppose("Wallet creation tx is stored in database and still pending") {
            persistTestWallet(accounts.bob.address, pendingTxHash, TransactionState.PENDING)
        }

        verify("User tries to create organization and error is thrown") {
            val e = assertThrows<StatusRuntimeException> {
                addOrganization(pendingTxHash, accounts.bob)
            }
            val errorCode = ErrorCode.fromMessage(e.status.description.orEmpty())
            assertThat(errorCode).isEqualTo(ErrorCode.WALLET_CREATION_PENDING.code)
        }
    }

    @Test
    fun shouldCatchErrorWhenWalletTxPendingAndUserTriesToAddSameWallet() {
        val pendingTxHash = "pending-tx-hash"

        suppose("Wallet creation tx is stored in database and still pending") {
            persistTestWallet(accounts.bob.address, pendingTxHash, TransactionState.PENDING)
        }

        verify("User tries to create organization and error is thrown") {
            val e = assertThrows<StatusRuntimeException> {
                addWallet(accounts.bob)
            }
            val errorCode = ErrorCode.fromMessage(e.status.description.orEmpty())
            assertThat(errorCode).isEqualTo(ErrorCode.WALLET_ALREADY_EXISTS.code)
        }
    }

    @Test
    fun shouldReturnPublicKeyWithGeneratedTransaction() {
        lateinit var bobTxHash: String
        suppose("User bob exists") {
            bobTxHash = addWallet(accounts.bob).txHash
        }
        verify("Bob public key is available in generated transaction") {
            val tx = grpc.generateAddOrganizationTx(
                    GenerateAddOrganizationTxRequest.newBuilder()
                            .setFromTxHash(bobTxHash)
                            .build()
            )
            assertThat(tx.publicKey).isEqualTo(getPublicKey(accounts.bob))
        }
    }

//    @Test
//    fun mustBeAbleForUsersToInvestInProjectAndReachCap() {
//        lateinit var organization: String
//        lateinit var project: String
//        val initialBalance = 100000L
//
//        suppose("Users Bob, Alice and Jane exist with their initial balances") {
//            addWallet(accounts.bob.address)
//            addWallet(accounts.alice.address)
//            addWallet(accounts.jane.address)
//            mint(accounts.bob.address, initialBalance)
//            mint(accounts.alice.address, initialBalance)
//            mint(accounts.jane.address, initialBalance)
//        }
//
//        suppose("Bob created Greenpeace organization with Alice and Jane as members") {
//            organization = addAndApproveOrganization(accounts.bob, "Greenpeace")
//            addOrganizationMember(organization, accounts.bob, accounts.alice.address)
//            addOrganizationMember(organization, accounts.bob, accounts.jane.address)
//        }
//
//        suppose("Bob created new investment project") {
//            project = createTestProject(organization, accounts.bob, testProject)
//        }
//
//        verify("Alice and Jane can invest in project and reach cap") {
//            assertThat(getProjectTotalInvestment(project)).isZero()
//            assertThat(isProjectLockedForInvestments(project)).isFalse()
//
//            val investment = testProject.invesmentCap / 2
//
//            invest(project, accounts.alice, investment)
//            invest(project, accounts.jane, investment)
//
//            assertThat(getBalance(accounts.alice.address)).isEqualTo(initialBalance - investment)
//            assertThat(getBalance(accounts.jane.address)).isEqualTo(initialBalance - investment)
//
//            assertThat(getProjectInvestmentForUser(accounts.alice.address, project)).isEqualTo(investment)
//            assertThat(getProjectInvestmentForUser(accounts.alice.address, project)).isEqualTo(investment)
//
//            assertThat(getProjectTotalInvestment(project)).isEqualTo(testProject.invesmentCap)
//            assertThat(isProjectLockedForInvestments(project)).isTrue()
//        }
//
//        verify("Organization admin can withdraw funds from project") {
//            withdrawFundsFromProject(project, accounts.bob, testProject.invesmentCap)
//            burn(project, testProject.invesmentCap)
//            assertThat(getProjectTotalInvestment(project)).isZero()
//        }
//    }
//
//    @Test
//    fun userMustBeAbleToCancelInvestment() {
//        val initialBalance = 100000L
//        lateinit var organization: String
//        lateinit var project: String
//
//        suppose("Users Bob and Alice exist with initial balances") {
//            addWallet(accounts.bob.address)
//            addWallet(accounts.alice.address)
//            mint(accounts.bob.address, initialBalance)
//            mint(accounts.alice.address, initialBalance)
//        }
//
//        suppose("Bob creates organization and investment project") {
//            organization = addAndApproveOrganization(accounts.bob, "Greenpeace")
//            project = createTestProject(organization, accounts.bob, testProject)
//        }
//
//        verify("Alice can invest and cancel investment") {
//            val investment = 1000L
//            invest(project, accounts.alice, investment)
//            cancelInvestment(project, accounts.alice, investment)
//            assertThat(getBalance(accounts.alice.address)).isEqualTo(initialBalance)
//            assertThat(getProjectInvestmentForUser(accounts.alice.address, project)).isZero()
//        }
//    }
//
//    @Test
//    fun userMustBeAbleToTransferOwnership() {
//        val initialBalance = 10000L
//        val investment = 1000L
//        lateinit var organization: String
//        lateinit var project: String
//
//        suppose("Users Bob, Alice and Jane exist with initial balances") {
//            addWallet(accounts.bob.address)
//            addWallet(accounts.alice.address)
//            addWallet(accounts.jane.address)
//            mint(accounts.bob.address, initialBalance)
//            mint(accounts.alice.address, initialBalance)
//            mint(accounts.jane.address, initialBalance)
//        }
//
//        suppose("Bob creates organization and investment project") {
//            organization = addAndApproveOrganization(accounts.bob, "Greenpeace")
//            project = createTestProject(organization, accounts.bob, testProject)
//        }
//
//        suppose("Alice invested in project while Jane did not") {
//            invest(project, accounts.alice, investment)
//        }
//
//        verify("Alice can transfer ownership of investment to Jane") {
//            transferOwnership(project, accounts.alice, accounts.jane.address, investment)
//            assertThat(getProjectInvestmentForUser(accounts.alice.address, project)).isZero()
//            assertThat(getProjectInvestmentForUser(accounts.jane.address, project)).isEqualTo(investment)
//        }
//    }

    /** HELPER FUNCTIONS */

    private fun addWallet(wallet: Credentials): PostTxResponse {
        val address = wallet.address
        val publicKey = getPublicKey(wallet)
        return grpc.addWallet(
                AddWalletRequest.newBuilder()
                        .setAddress(address)
                        .setPublicKey(publicKey)
                        .build()
        )
    }

    private fun mint(toTxHash: String, amount: Long) {
        val tx = grpc.generateMintTx(
                GenerateMintTxRequest.newBuilder()
                        .setAmount(amount)
                        .setFrom(accounts.eurOwner.address)
                        .setToTxHash(toTxHash)
                        .build()
        )
        grpc.postTransaction(
                PostTxRequest.newBuilder()
                        .setData(sign(tx, accounts.eurOwner))
                        .setTxType(typeToProto(TransactionType.DEPOSIT))
                        .build()
        )
    }

    private fun approveTokenIssuer(fromTxHash: String, fromCredentials: Credentials, amount: Long) {
        val approveTx = grpc.generateApproveTx(
                GenerateApproveTxRequest.newBuilder()
                        .setFromTxHash(fromTxHash)
                        .setAmount(amount)
                        .setApprove(accounts.eurOwner.address)
                        .build()
        )
        grpc.postVaultTransaction(
                PostVaultTxRequest.newBuilder()
                        .setData(convertToVaultEncoding(sign(approveTx, fromCredentials)))
                        .setTxType(typeToProto(TransactionType.PENDING_WITHDRAW))
                        .build()
        )
    }

    private fun burn(fromTxHash: String, amount: Long) {
        val burnTx = grpc.generateBurnFromTx(
                GenerateBurnFromTxRequest.newBuilder()
                        .setAmount(amount)
                        .setBurnFromTxHash(fromTxHash)
                        .setFrom(accounts.eurOwner.address)
                        .build()
        )
        grpc.postTransaction(
                PostTxRequest.newBuilder()
                        .setData(sign(burnTx, accounts.eurOwner))
                        .setTxType(typeToProto(TransactionType.WITHDRAW))
                        .build()
        )
    }

    private fun transfer(fromTxHash: String, from: Credentials, toTxHash: String, amount: Long) {
        val transferTx = grpc.generateTransferTx(
                GenerateTransferTxRequest.newBuilder()
                        .setFromTxHash(fromTxHash)
                        .setToTxHash(toTxHash)
                        .setAmount(amount)
                        .build()
        )
        grpc.postVaultTransaction(
                PostVaultTxRequest.newBuilder()
                        .setData(convertToVaultEncoding(sign(transferTx, from)))
                        .setTxType(typeToProto(TransactionType.TRANSFER))
                        .build()
        )
    }

    private fun addAndApproveOrganization(adminTxHash: String, admin: Credentials): String {
        val orgTxHash = addOrganization(adminTxHash, admin)
        approveOrganization(orgTxHash)
        return orgTxHash
    }

    private fun addOrganization(adminTxHash: String, admin: Credentials): String {
        val addOrgTx = grpc.generateAddOrganizationTx(
                GenerateAddOrganizationTxRequest.newBuilder()
                        .setFromTxHash(adminTxHash)
                        .build()
        )
        return grpc.postVaultTransaction(
                PostVaultTxRequest.newBuilder()
                        .setData(convertToVaultEncoding(sign(addOrgTx, admin)))
                        .setTxType(typeToProto(TransactionType.ORG_CREATE))
                        .build()
        ).txHash
    }

    private fun approveOrganization(orgTxHash: String) {
        grpc.activateOrganization(
                ActivateOrganizationRequest.newBuilder()
                        .setOrganizationTxHash(orgTxHash)
                        .build()
        )
    }

    private fun isWalletActive(fromTxHash: String): Boolean {
        return grpc.isWalletActive(
                WalletActiveRequest.newBuilder()
                        .setWalletTxHash(fromTxHash)
                        .build()
        ).active
    }

    private fun organizationExists(orgTxHash: String): Boolean {
        return grpc.organizationExists(
                OrganizationExistsRequest.newBuilder()
                        .setOrganizationTxHash(orgTxHash)
                        .build()
        ).exists
    }

    private fun organizationActive(orgTxHash: String): Boolean {
        return grpc.isOrganizationVerified(
                OrganizationVerifiedRequest.newBuilder()
                        .setOrganizationTxHash(orgTxHash)
                        .build()
        ).verified
    }

    private fun getBalance(txHash: String): Long {
        return grpc.getBalance(
                BalanceRequest.newBuilder()
                        .setWalletTxHash(txHash)
                        .build()
        ).balance
    }

    private fun getAllOrganizations(): List<String> {
        return grpc.getAllOrganizations(
                Empty.getDefaultInstance()
        ).organizationsList
    }

    private fun addOrganizationMember(orgTxHash: String, adminTxHash: String, admin: Credentials, memberTxHash: String) {
        val addMemberTx = grpc.generateAddOrganizationMemberTx(
                GenerateAddMemberTxRequest.newBuilder()
                        .setFromTxHash(adminTxHash)
                        .setOrganizationTxHash(orgTxHash)
                        .setMemberTxHash(memberTxHash)
                        .build()
        )
        grpc.postVaultTransaction(
                PostVaultTxRequest.newBuilder()
                        .setData(convertToVaultEncoding(sign(addMemberTx, admin)))
                        .setTxType(typeToProto(TransactionType.ORG_ADD_MEMBER))
                        .build()
        )
    }

    private fun getAllMembers(orgTxHash: String): List<String> {
        return grpc.getAllOrganizationMembers(
                OrganizationMembersRequest.newBuilder()
                        .setOrganizationTxHash(orgTxHash)
                        .build()
        ).membersList
    }

    private fun getAllProjects(orgTxHash: String): List<String> {
        return grpc.getAllOrganizationProjects(
                OrganizationProjectsRequest.newBuilder()
                        .setOrganizationTxHash(orgTxHash)
                        .build()
        ).projectsList
    }

    private fun getProject(projectTxHash: String): TestProject {
        val minUserInvestment = grpc.getProjectMinInvestmentPerUser(
                ProjectMinInvestmentPerUserRequest.newBuilder()
                        .setProjectTxHash(projectTxHash)
                        .build()
        ).amount
        val maxUserInvestment = grpc.getProjectMaxInvestmentPerUser(
                ProjectMaxInvestmentPerUserRequest.newBuilder()
                        .setProjectTxHash(projectTxHash)
                        .build()
        ).amount
        val investmentCap = grpc.getProjectInvestmentCap(
                ProjectInvestmentCapRequest.newBuilder()
                        .setProjectTxHash(projectTxHash)
                        .build()
        ).amount
        return TestProject(
                minUserInvestment,
                maxUserInvestment,
                investmentCap
        )
    }

    private fun createTestProject(orgTxHash: String, adminTxHash: String, admin: Credentials, project: TestProject): String {
        val createProjectTx = grpc.generateAddOrganizationProjectTx(
                GenerateAddProjectTxRequest.newBuilder()
                        .setFromTxHash(adminTxHash)
                        .setOrganizationTxHash(orgTxHash)
                        .setMinInvestmentPerUser(project.minUserInvestment)
                        .setMaxInvestmentPerUser(project.maxUserInvesment)
                        .setInvestmentCap(project.invesmentCap)
                        .build()
        )
        return grpc.postVaultTransaction(
                PostVaultTxRequest.newBuilder()
                        .setData(convertToVaultEncoding(sign(createProjectTx, admin)))
                        .setTxType(typeToProto(TransactionType.ORG_ADD_PROJECT))
                        .build()
        ).txHash
    }

    private fun withdrawFromOrganization(orgTxHash: String, adminTxHash: String, admin: Credentials, amount: Long) {
        val withdrawTx = grpc.generateWithdrawOrganizationFundsTx(
                GenerateWithdrawOrganizationFundsTxRequest.newBuilder()
                        .setFromTxHash(adminTxHash)
                        .setOrganizationTxHash(orgTxHash)
                        .setAmount(amount)
                        .setTokenIssuer(accounts.eurOwner.address)
                        .build()
        )
        grpc.postVaultTransaction(
                PostVaultTxRequest.newBuilder()
                        .setData(convertToVaultEncoding(sign(withdrawTx, admin)))
                        .setTxType(typeToProto(TransactionType.PENDING_ORG_WITHDRAW))
                        .build()
        )
    }

//    private fun invest(project: String, user: Credentials, amount: Long) {
//        val investTx = grpc.generateInvestTx(
//                GenerateInvestTxRequest.newBuilder()
//                        .setFrom(user.address)
//                        .setProject(project)
//                        .setAmount(amount)
//                        .build()
//        )
//        grpc.postTransaction(
//                PostTxRequest.newBuilder()
//                        .setData(sign(investTx, user))
//                        .build()
//        )
//    }
//
//    private fun getProjectInvestmentForUser(user: String, project: String): Long {
//        return grpc.getProjectTotalInvestmentForUser(
//                ProjectTotalInvestmentForUserRequest.newBuilder()
//                        .setProject(project)
//                        .setUser(user)
//                        .build()
//        ).amount
//    }
//
//    private fun getProjectTotalInvestment(project: String): Long {
//        return grpc.getProjectCurrentTotalInvestment(
//                ProjectCurrentTotalInvestmentRequest.newBuilder()
//                        .setProject(project)
//                        .build()
//        ).amount
//    }
//
//    private fun isProjectLockedForInvestments(project: String): Boolean {
//        return grpc.isProjectLockedForInvestments(
//                ProjectLockedForInvestmentsRequest.newBuilder()
//                        .setProject(project)
//                        .build()
//        ).locked
//    }
//
//    private fun withdrawFundsFromProject(project: String, admin: Credentials, amount: Long) {
//        val withdrawTx = grpc.generateWithdrawProjectFundsTx(
//                GenerateWithdrawProjectFundsTx.newBuilder()
//                        .setFrom(admin.address)
//                        .setAmount(amount)
//                        .setProject(project)
//                        .setTokenIssuer(accounts.eurOwner.address)
//                        .build()
//        )
//        grpc.postTransaction(
//                PostTxRequest.newBuilder()
//                        .setData(sign(withdrawTx, admin))
//                        .build()
//        )
//    }
//
//    private fun transferOwnership(project: String, from: Credentials, to: String, amount: Long) {
//        val transferTx = grpc.generateTransferOwnershipTx(
//                GenerateTransferOwnershipTx.newBuilder()
//                        .setProject(project)
//                        .setFrom(from.address)
//                        .setTo(to)
//                        .setAmount(amount)
//                        .build()
//        )
//        grpc.postTransaction(
//                PostTxRequest.newBuilder()
//                        .setData(sign(transferTx, from))
//                        .build()
//        )
//    }
//
//    private fun cancelInvestment(project: String, from: Credentials, amount: Long) {
//        val cancelTx = grpc.generateCancelInvestmentTx(
//                GenerateCancelInvestmentTx.newBuilder()
//                        .setFrom(from.address)
//                        .setProject(project)
//                        .setAmount(amount)
//                        .build()
//        )
//        grpc.postTransaction(
//                PostTxRequest.newBuilder()
//                        .setData(sign(cancelTx, from))
//                        .build()
//        )
//    }

    // NOTE: - clients are going to handle this logic in production
    private fun sign(tx: RawTxResponse, credentials: Credentials): String {
        val rawTx = RawTransaction.createTransaction(
                tx.nonce.toBigInteger(),
                tx.gasPrice.toBigInteger(),
                tx.gasLimit.toBigInteger(),
                tx.to,
                tx.data
        )
        return Numeric.toHexString(
                TransactionEncoder.signMessage(rawTx, credentials)
        )
    }

    private fun convertToVaultEncoding(signedRawTx: String): String {
        val version = RlpString.create("1")
        val type = RlpString.create("1")
        val protocol = RlpString.create("eth")
        val payload = RlpList(
                RlpString.create(signedRawTx),
                RlpString.create("account-identifier")
        )
        val rlpEncodedTx = RlpEncoder.encode(RlpList(version, type, protocol, payload))
        val fakeChecksum = byteArrayOf(0, 0, 0, 0)
        return Base58.base58Encode(rlpEncodedTx + fakeChecksum)
    }

    // Helper functions

    private fun assertTransaction(
        tx: Transaction,
        expectedFrom: String,
        expectedTo: String,
        expectedType: TransactionType,
        expectedAmount: Long? = null
    ) {
        assertThat(tx.fromWallet).isEqualTo(expectedFrom)
        assertThat(tx.toWallet).isEqualTo(expectedTo)
        assertThat(tx.type).isEqualTo(expectedType)
        assertThat(tx.state).isEqualTo(TransactionState.MINED)
        assertThat(tx.amount).isEqualTo(eurToToken(expectedAmount))
    }

    private fun eurToToken(eur: Long?): BigInteger? {
        val centToTokenFactor = BigInteger("10000000000000000") // 10e16
        return eur?.let { eur.toBigInteger().times(centToTokenFactor) } ?: run { null }
    }

    private fun getPublicKey(account: Credentials): String {
        return Numeric.toHexString(
                account.ecKeyPair.publicKey.toByteArray()
        )
    }

    private fun typeToProto(type: TransactionType): com.ampnet.crowdfunding.proto.TransactionType {
        return com.ampnet.crowdfunding.proto.TransactionType.valueOf(type.name)
    }

    private fun persistTestWallet(address: String, txHash: String, state: TransactionState) {
        val tx = Transaction::class.java.newInstance()
        tx.createdAt = ZonedDateTime.now()
        tx.state = state
        tx.type = TransactionType.WALLET_CREATE
        tx.input = ""
        tx.fromWallet = "walletA"
        tx.toWallet = "walletB"
        tx.hash = txHash
        transactionRepository.save(tx)

        val wallet = Wallet::class.java.newInstance()
        wallet.transaction = tx
        wallet.publicKey = "pubkey"
        wallet.type = WalletType.USER
        wallet.address = address
        walletRepository.save(wallet)
    }
}