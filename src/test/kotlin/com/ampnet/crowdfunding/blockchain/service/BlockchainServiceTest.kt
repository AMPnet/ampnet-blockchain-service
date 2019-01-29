package com.ampnet.crowdfunding.blockchain.service

import com.ampnet.crowdfunding.blockchain.TestBase
import com.ampnet.crowdfunding.blockchain.enums.TransactionState
import com.ampnet.crowdfunding.blockchain.enums.TransactionType
import com.ampnet.crowdfunding.blockchain.enums.WalletType
import com.ampnet.crowdfunding.blockchain.persistence.model.Transaction
import com.ampnet.crowdfunding.blockchain.persistence.repository.TransactionRepository
import com.ampnet.crowdfunding.blockchain.persistence.repository.WalletRepository
import com.ampnet.crowdfunding.proto.AddWalletRequest
import com.ampnet.crowdfunding.proto.BalanceRequest
import com.ampnet.crowdfunding.proto.GenerateApproveTxRequest
import com.ampnet.crowdfunding.proto.GenerateBurnFromTxRequest
import com.ampnet.crowdfunding.proto.GenerateMintTxRequest
import com.ampnet.crowdfunding.proto.GenerateTransferTxRequest
import com.ampnet.crowdfunding.proto.PostTxRequest
import com.ampnet.crowdfunding.proto.PostTxResponse
import com.ampnet.crowdfunding.proto.RawTxResponse
import com.ampnet.crowdfunding.proto.WalletActiveRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.utils.Numeric
import java.math.BigInteger

class BlockchainServiceTest : TestBase() {

    @Autowired
    private lateinit var transactionRepository: TransactionRepository

    @Autowired
    private lateinit var walletRepository: WalletRepository

    @Test
    fun mustBeAbleToRegisterUser() {
        lateinit var txResponse: PostTxResponse
        verify("User can be created") {
            txResponse = addWallet(accounts.bob.address)
            assertThat(isWalletActive(txResponse.txHash)).isTrue()
        }
        verify("User creation transaction is stored in database") {
            val transactionsResponse = transactionRepository.findAll()
            assertThat(transactionsResponse).hasSize(1)

            val tx = transactionsResponse.first()
            assertTransaction(
                    tx,
                    expectedFrom = accounts.ampnetOwner.address,
                    expectedTo = applicationProperties.contracts.ampnetAddress,
                    expectedType = TransactionType.WALLET_CREATE
            )
        }
        verify("User wallet is stored in database") {
            val walletsResponse = walletRepository.findAll()
            assertThat(walletsResponse).hasSize(1)

            val wallet = walletsResponse.first()
            assertThat(wallet.hash).isEqualTo(txResponse.txHash)
            assertThat(wallet.type).isEqualTo(WalletType.USER)
            assertThat(wallet.address).isEqualTo(accounts.bob.address)
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
            bobWalletTxHash = addWallet(accounts.bob.address).txHash
            assertThat(getBalance(bobWalletTxHash)).isEqualTo(initialBalance)
        }
        verify("Bob can deposit some amount of EUR") {
            mint(accounts.bob.address, depositAmount)
            assertThat(getBalance(bobWalletTxHash)).isEqualTo(depositAmount)
        }
        verify("Bob can withdraw some amount of EUR") {
            approveTokenIssuer(bobWalletTxHash, accounts.bob, withdrawAmount)
            burn(accounts.bob.address, withdrawAmount)
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
                    expectedTo = accounts.bob.address,
                    expectedType = TransactionType.DEPOSIT,
                    expectedAmount = depositAmount
            )

            val approveTx = transactions[2]
            assertTransaction(
                    approveTx,
                    expectedFrom = accounts.bob.address,
                    expectedTo = accounts.eurOwner.address,
                    expectedType = TransactionType.PENDING_WITHDRAW,
                    expectedAmount = withdrawAmount
            )

            val burnTx = transactions[3]
            assertTransaction(
                    burnTx,
                    expectedFrom = accounts.bob.address,
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
            bobTxHash = addWallet(accounts.bob.address).txHash
            mint(accounts.bob.address, bobInitialBalance)
            aliceTxHash = addWallet(accounts.alice.address).txHash
            mint(accounts.alice.address, aliceInitialBalance)
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
                    expectedTo = accounts.bob.address,
                    expectedType = TransactionType.DEPOSIT,
                    expectedAmount = bobInitialBalance
            )

            val aliceMintTx = transactions[3]
            assertTransaction(
                    aliceMintTx,
                    expectedFrom = accounts.eurOwner.address,
                    expectedTo = accounts.alice.address,
                    expectedType = TransactionType.DEPOSIT,
                    expectedAmount = aliceInitialBalance
            )

            val transferTx = transactions[4]
            assertTransaction(
                    transferTx,
                    expectedFrom = accounts.bob.address,
                    expectedTo = accounts.alice.address,
                    expectedType = TransactionType.TRANSFER,
                    expectedAmount = bobToAliceAmount
            )
        }
    }
//
//    @Test
//    fun mustBeAbleToCreateAndActivateOrganization() {
//        suppose("User Bob is registered on AMPnet") {
//            addWallet(accounts.bob.address)
//        }
//        verify("Bob can create organization") {
//            val organization = addAndApproveOrganization(accounts.bob, "Greenpeace")
//            assertThat(getAllOrganizations(accounts.bob.address)).hasSize(1)
//            assertThat(organizationExists(organization))
//            assertThat(organizationActive(organization))
//        }
//    }
//
//    @Test
//    fun mustBeAbleToAddUserToOrganization() {
//        lateinit var organization: String
//        suppose("Users Bob,Alice exist and Bob created Greenpeace organization") {
//            addWallet(accounts.bob.address)
//            addWallet(accounts.alice.address)
//            organization = addAndApproveOrganization(accounts.bob, "Greenpeace")
//        }
//        verify("Bob can add new user to organization") {
//            addOrganizationMember(organization, accounts.bob, accounts.alice.address)
//
//            val members = getAllMembers(organization)
//            assertThat(members).hasSize(1)
//            assertThat(members.first()).isEqualTo(accounts.alice.address)
//        }
//    }
//
//    @Test
//    fun mustBeAbleToAddProjectToOrganization() {
//        lateinit var organization: String
//        suppose("User Bob exists and is admin of Greenpeace organization") {
//            addWallet(accounts.bob.address)
//            organization = addAndApproveOrganization(accounts.bob, "Greenpeace")
//        }
//        verify("Bob can create new investment project") {
//            createTestProject(organization, accounts.bob, testProject)
//
//            val projects = getAllProjects(organization)
//            assertThat(projects).hasSize(1)
//
//            val projectAddress = projects.first()
//            val fetchedProject = getProject(projectAddress)
//            assertThat(fetchedProject).isEqualTo(testProject)
//        }
//    }
//
//    @Test
//    fun mustBeAbleToWithdrawMoneyFromOrganization() {
//        lateinit var organization: String
//
//        val initialOrgBalance = 1000L
//        val withdrawAmount = 1000L
//        val finalOrgBalance = initialOrgBalance - withdrawAmount
//
//        suppose("User Bob exists and is admin of Greenpeace organization which has some positive balance") {
//            addWallet(accounts.bob.address)
//            organization = addAndApproveOrganization(accounts.bob, "Greenpeace")
//            mint(organization, initialOrgBalance)
//        }
//        verify("Bob can withdraw money from organization") {
//            withdrawFromOrganization(organization, accounts.bob, withdrawAmount)
//            burn(organization, withdrawAmount)
//            assertThat(getBalance(organization)).isEqualTo(finalOrgBalance)
//        }
//    }
//
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

    private fun addWallet(address: String): PostTxResponse {
        return grpc.addWallet(
                AddWalletRequest.newBuilder()
                        .setWallet(address)
                        .build()
        )
    }

    private fun mint(to: String, amount: Long) {
        val tx = grpc.generateMintTx(
                GenerateMintTxRequest.newBuilder()
                        .setAmount(amount)
                        .setFrom(accounts.eurOwner.address)
                        .setTo(to)
                        .build()
        )
        grpc.postTransaction(
                PostTxRequest.newBuilder()
                        .setData(sign(tx, accounts.eurOwner))
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
        grpc.postTransaction(
                PostTxRequest.newBuilder()
                        .setData(sign(approveTx, fromCredentials))
                        .build()
        )
    }

    private fun burn(from: String, amount: Long) {
        val burnTx = grpc.generateBurnFromTx(
                GenerateBurnFromTxRequest.newBuilder()
                        .setAmount(amount)
                        .setBurnFrom(from)
                        .setFrom(accounts.eurOwner.address)
                        .build()
        )
        grpc.postTransaction(
                PostTxRequest.newBuilder()
                        .setData(sign(burnTx, accounts.eurOwner))
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
        grpc.postTransaction(
                PostTxRequest.newBuilder()
                        .setData(sign(transferTx, from))
                        .build()
        )
    }

//    private fun addAndApproveOrganization(admin: Credentials, name: String): String {
//        val addOrgTx = grpc.generateAddOrganizationTx(
//                GenerateAddOrganizationTxRequest.newBuilder()
//                        .setFrom(admin.address)
//                        .setName(name)
//                        .build()
//        )
//        grpc.postTransaction(
//                PostTxRequest.newBuilder()
//                        .setData(sign(addOrgTx, admin))
//                        .build()
//        )
//
//        val organizations = getAllOrganizations(admin.address)
//        val activateOrgTx = grpc.generateActivateOrganizationTx(
//                GenerateActivateTxRequest.newBuilder()
//                        .setFrom(accounts.ampnetOwner.address)
//                        .setOrganization(organizations.first())
//                        .build()
//        )
//        grpc.postTransaction(
//                PostTxRequest.newBuilder()
//                        .setData(sign(activateOrgTx, accounts.ampnetOwner))
//                        .build()
//        )
//
//        return organizations.first()
//    }
//
    private fun isWalletActive(fromTxHash: String): Boolean {
        return grpc.isWalletActive(
                WalletActiveRequest.newBuilder()
                        .setWalletTxHash(fromTxHash)
                        .build()
        ).active
    }
//
//    private fun organizationExists(organization: String): Boolean {
//        return grpc.organizationExists(
//                OrganizationExistsRequest.newBuilder()
//                        .setFrom(organization)
//                        .setOrganization(organization)
//                        .build()
//        ).exists
//    }
//
//    private fun organizationActive(organization: String): Boolean {
//        return grpc.isOrganizationVerified(
//                OrganizationVerifiedRequest.newBuilder()
//                        .setOrganization(organization)
//                        .build()
//        ).verified
//    }
//
    private fun getBalance(txHash: String): Long {
        return grpc.getBalance(
                BalanceRequest.newBuilder()
                        .setWalletTxHash(txHash)
                        .build()
        ).balance
    }
//
//    private fun getAllOrganizations(from: String): List<String> {
//        return grpc.getAllOrganizations(
//                GetAllOrganizationsRequest.newBuilder()
//                        .setFrom(from)
//                        .build()
//        ).organizationsList
//    }
//
//    private fun addOrganizationMember(organization: String, admin: Credentials, member: String) {
//        val addMemberTx = grpc.generateAddOrganizationMemberTx(
//                GenerateAddMemberTxRequest.newBuilder()
//                        .setFrom(admin.address)
//                        .setOrganization(organization)
//                        .setMember(member)
//                        .build()
//        )
//        grpc.postTransaction(
//                PostTxRequest.newBuilder()
//                        .setData(sign(addMemberTx, admin))
//                        .build()
//        )
//    }
//
//    private fun getAllMembers(organization: String): List<String> {
//        return grpc.getAllOrganizationMembers(
//                OrganizationMembersRequest.newBuilder()
//                        .setOrganization(organization)
//                        .build()
//        ).membersList
//    }
//
//    private fun getAllProjects(organization: String): List<String> {
//        return grpc.getAllOrganizationProjects(
//                OrganizationProjectsRequest.newBuilder()
//                        .setOrganization(organization)
//                        .build()
//        ).projectsList
//    }
//
//    private fun getProject(address: String): TestProject {
//        val name = grpc.getProjectName(
//                ProjectNameRequest.newBuilder()
//                        .setProject(address)
//                        .build()
//        ).name
//        val description = grpc.getProjectDescription(
//                ProjectDescriptionRequest.newBuilder()
//                        .setProject(address)
//                        .build()
//        ).description
//        val minUserInvestment = grpc.getProjectMinInvestmentPerUser(
//                ProjectMinInvestmentPerUserRequest.newBuilder()
//                        .setProject(address)
//                        .build()
//        ).amount
//        val maxUserInvestment = grpc.getProjectMaxInvestmentPerUser(
//                ProjectMaxInvestmentPerUserRequest.newBuilder()
//                        .setProject(address)
//                        .build()
//        ).amount
//        val investmentCap = grpc.getProjectInvestmentCap(
//                ProjectInvestmentCapRequest.newBuilder()
//                        .setProject(address)
//                        .build()
//        ).amount
//        return TestProject(
//                name,
//                description,
//                minUserInvestment,
//                maxUserInvestment,
//                investmentCap
//        )
//    }
//
//    private fun createTestProject(organization: String, admin: Credentials, project: TestProject): String {
//        val createProjectTx = grpc.generateAddOrganizationProjectTx(
//                GenerateAddProjectTxRequest.newBuilder()
//                        .setFrom(admin.address)
//                        .setOrganization(organization)
//                        .setName(project.name)
//                        .setDescription(project.description)
//                        .setMinInvestmentPerUser(project.minUserInvestment)
//                        .setMaxInvestmentPerUser(project.maxUserInvesment)
//                        .setInvestmentCap(project.invesmentCap)
//                        .build()
//        )
//        grpc.postTransaction(
//                PostTxRequest.newBuilder()
//                        .setData(sign(createProjectTx, admin))
//                        .build()
//        )
//        return getAllProjects(organization).first()
//    }
//
//    private fun withdrawFromOrganization(organization: String, admin: Credentials, amount: Long) {
//        val withdrawTx = grpc.generateWithdrawOrganizationFundsTx(
//                GenerateWithdrawOrganizationFundsTxRequest.newBuilder()
//                        .setFrom(admin.address)
//                        .setOrganization(organization)
//                        .setAmount(amount)
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

    // Helper functions

    private fun assertTransaction(
        tx: Transaction,
        expectedFrom: String,
        expectedTo: String,
        expectedType: TransactionType,
        expectedAmount: Long? = null
    ) {
        assertThat(tx.fromAddress).isEqualTo(expectedFrom)
        assertThat(tx.toAddress).isEqualTo(expectedTo)
        assertThat(tx.type).isEqualTo(expectedType)
        assertThat(tx.state).isEqualTo(TransactionState.MINED)
        assertThat(tx.amount).isEqualTo(eurToToken(expectedAmount))
    }

    private fun eurToToken(eur: Long?): BigInteger? {
        val centToTokenFactor = BigInteger("10000000000000000") // 10e16
        return eur?.let { eur.toBigInteger().times(centToTokenFactor) } ?: run { null }
    }
}