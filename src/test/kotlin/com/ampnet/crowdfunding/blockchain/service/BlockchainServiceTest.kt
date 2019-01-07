package com.ampnet.crowdfunding.blockchain.service

import com.ampnet.crowdfunding.blockchain.TestBase
import com.ampnet.crowdfunding.proto.BalanceRequest
import com.ampnet.crowdfunding.proto.GenerateActivateTxRequest
import com.ampnet.crowdfunding.proto.GenerateAddMemberTxRequest
import com.ampnet.crowdfunding.proto.GenerateAddOrganizationTxRequest
import com.ampnet.crowdfunding.proto.GenerateAddProjectTxRequest
import com.ampnet.crowdfunding.proto.GenerateAddWalletTxRequest
import com.ampnet.crowdfunding.proto.GenerateApproveTxRequest
import com.ampnet.crowdfunding.proto.GenerateBurnFromTxRequest
import com.ampnet.crowdfunding.proto.GenerateMintTxRequest
import com.ampnet.crowdfunding.proto.GenerateTransferTxRequest
import com.ampnet.crowdfunding.proto.GetAllOrganizationsRequest
import com.ampnet.crowdfunding.proto.OrganizationExistsRequest
import com.ampnet.crowdfunding.proto.OrganizationMembersRequest
import com.ampnet.crowdfunding.proto.OrganizationProjectsRequest
import com.ampnet.crowdfunding.proto.OrganizationVerifiedRequest
import com.ampnet.crowdfunding.proto.PostTransactionRequest
import com.ampnet.crowdfunding.proto.ProjectDescriptionRequest
import com.ampnet.crowdfunding.proto.ProjectInvestmentCapRequest
import com.ampnet.crowdfunding.proto.ProjectMaxInvestmentPerUserRequest
import com.ampnet.crowdfunding.proto.ProjectMinInvestmentPerUserRequest
import com.ampnet.crowdfunding.proto.ProjectNameRequest
import com.ampnet.crowdfunding.proto.RawTxResponse
import com.ampnet.crowdfunding.proto.WalletActiveRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.utils.Numeric

class BlockchainServiceTest : TestBase() {

    data class TestProject(
            val name: String,
            val description: String,
            val minUserInvestment: Long,
            val maxUserInvesment: Long,
            val invesmentCap: Long
    )

    val testProject = TestProject(
            "Greenpeace",
            "Test desc",
            1000L,  // 10 EUR min investment per user
            10000L, // 100 EUR max investment per user
            10000L     // 100 EUR investment cap
    )

    @Test
    fun mustBeAbleToRegisterUser() {
        suppose("User Bob does not exist") {
            assertThat(isWalletActive(accounts.bob.address)).isFalse()
        }
        verify("User can be created") {
            addWallet(accounts.bob.address)
            assertThat(isWalletActive(accounts.bob.address)).isTrue()
        }
    }

    @Test
    fun mustBeAbleToDepositAndWithdrawTokens() {
        val initialBalance = 0L // user starts with empty wallet
        val depositAmount = 1550L // 15.50 EUR
        val withdrawAmount = 1550L // 15.50 EUR
        val finalBalance = initialBalance + depositAmount - withdrawAmount

        suppose("User Bob is registered on AMPnet and has zero balance") {
            addWallet(accounts.bob.address)
            assertThat(getBalance(accounts.bob.address)).isEqualTo(initialBalance)
        }
        verify("Bob can deposit some amount of EUR") {
            mint(accounts.bob.address, depositAmount)
            assertThat(getBalance(accounts.bob.address)).isEqualTo(depositAmount)
        }
        verify("Bob can withdraw some amount of EUR") {
            burn(accounts.bob, withdrawAmount)
            assertThat(getBalance(accounts.bob.address)).isEqualTo(finalBalance)
        }
    }

    @Test
    fun mustBeAbleToTransferFunds() {
        val bobInitialBalance = 1000L // 10.00 EUR
        val aliceInitialBalance = 0L
        val bobToAliceAmount = 1000L
        val bobFinalBalance = bobInitialBalance - bobToAliceAmount
        val aliceFinalBalance = aliceInitialBalance + bobToAliceAmount

        suppose("Users Bob and Alice are registered on AMPnet with their initial balances") {
            addWallet(accounts.bob.address)
            mint(accounts.bob.address, bobInitialBalance)
            addWallet(accounts.alice.address)
            mint(accounts.alice.address, aliceInitialBalance)
        }
        verify("Bob can transfer funds to Alice's wallet") {
            transfer(accounts.bob, accounts.alice.address, bobToAliceAmount)
            assertThat(getBalance(accounts.bob.address)).isEqualTo(bobFinalBalance)
            assertThat(getBalance(accounts.alice.address)).isEqualTo(aliceFinalBalance)
        }
    }

    @Test
    fun mustBeAbleToCreateAndActivateOrganization() {
        suppose("User Bob is registered on AMPnet") {
            addWallet(accounts.bob.address)
        }
        verify("Bob can create organization") {
            val organization = addAndApproveOrganization(accounts.bob, "Greenpeace")
            assertThat(getAllOrganizations(accounts.bob.address)).hasSize(1)
            assertThat(organizationExists(organization))
            assertThat(organizationActive(organization))
        }
    }

    @Test
    fun mustBeAbleToAddUserToOrganization() {
        lateinit var organization: String
        suppose("Users Bob,Alice exist and Bob created Greenpeace organization") {
            addWallet(accounts.bob.address)
            addWallet(accounts.alice.address)
            organization = addAndApproveOrganization(accounts.bob, "Greenpeace")
        }
        verify("Bob can add new user to organization") {
            addOrganizationMember(organization, accounts.bob, accounts.alice.address)

            val members = getAllMembers(organization)
            assertThat(members).hasSize(1)
            assertThat(members.first()).isEqualTo(accounts.alice.address)
        }
    }

    @Test
    fun mustBeAbleToAddProjectToOrganization() {
        lateinit var organization: String
        suppose("User Bob exists and is admin of Greenpeace organization") {
            addWallet(accounts.bob.address)
            organization = addAndApproveOrganization(accounts.bob, "Greenpeace")
        }
        verify("Bob can create new investment project") {
            createTestProject(organization, accounts.bob, testProject)

            val projects = getAllProjects(organization)
            assertThat(projects).hasSize(1)

            val projectAddress = projects.first()
            val fetchedProject = getProject(projectAddress)
            assertThat(fetchedProject).isEqualTo(testProject)
        }
    }

    /** HELPER FUNCTIONS */

    private fun addWallet(address: String) {
        val tx = grpc.generateAddWalletTx(
                GenerateAddWalletTxRequest.newBuilder()
                        .setFrom(accounts.ampnetOwner.address)
                        .setWallet(address)
                        .build()
        )
        grpc.postTransaction(
                PostTransactionRequest.newBuilder()
                        .setData(sign(tx, accounts.ampnetOwner))
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
                PostTransactionRequest.newBuilder()
                        .setData(sign(tx, accounts.eurOwner))
                        .build()
        )
    }

    private fun burn(from: Credentials, amount: Long) {
        val approveTx = grpc.generateApproveTx(
                GenerateApproveTxRequest.newBuilder()
                        .setFrom(from.address)
                        .setAmount(amount)
                        .setApprove(accounts.eurOwner.address)
                        .build()
        )
        grpc.postTransaction(
                PostTransactionRequest.newBuilder()
                        .setData(sign(approveTx, from))
                        .build()
        )
        val burnTx = grpc.generateBurnFromTx(
                GenerateBurnFromTxRequest.newBuilder()
                        .setAmount(amount)
                        .setBurnFrom(from.address)
                        .setFrom(accounts.eurOwner.address)
                        .build()
        )
        grpc.postTransaction(
                PostTransactionRequest.newBuilder()
                        .setData(sign(burnTx, accounts.eurOwner))
                        .build()
        )
    }

    private fun transfer(from: Credentials, to: String, amount: Long) {
        val transferTx = grpc.generateTransferTx(
                GenerateTransferTxRequest.newBuilder()
                        .setFrom(from.address)
                        .setTo(to)
                        .setAmount(amount)
                        .build()
        )
        grpc.postTransaction(
                PostTransactionRequest.newBuilder()
                        .setData(sign(transferTx, from))
                        .build()
        )
    }

    private fun addAndApproveOrganization(admin: Credentials, name: String): String {
        val addOrgTx = grpc.generateAddOrganizationTx(
                GenerateAddOrganizationTxRequest.newBuilder()
                        .setFrom(admin.address)
                        .setName(name)
                        .build()
        )
        grpc.postTransaction(
                PostTransactionRequest.newBuilder()
                        .setData(sign(addOrgTx, admin))
                        .build()
        )

        val organizations = getAllOrganizations(admin.address)
        val activateOrgTx = grpc.generateActivateOrganizationTx(
                GenerateActivateTxRequest.newBuilder()
                        .setFrom(accounts.ampnetOwner.address)
                        .setOrganization(organizations.first())
                        .build()
        )
        grpc.postTransaction(
                PostTransactionRequest.newBuilder()
                        .setData(sign(activateOrgTx, accounts.ampnetOwner))
                        .build()
        )

        return organizations.first()
    }

    private fun isWalletActive(address: String): Boolean {
        return grpc.isWalletActive(
                WalletActiveRequest.newBuilder()
                        .setFrom(address)
                        .setWallet(address)
                        .build()
        ).active
    }

    private fun organizationExists(organization: String): Boolean {
        return grpc.organizationExists(
                OrganizationExistsRequest.newBuilder()
                        .setFrom(organization)
                        .setOrganization(organization)
                        .build()
        ).exists
    }

    private fun organizationActive(organization: String): Boolean {
        return grpc.isOrganizationVerified(
                OrganizationVerifiedRequest.newBuilder()
                        .setOrganization(organization)
                        .build()
        ).verified
    }

    private fun getBalance(address: String): Long {
        return grpc.getBalance(
                BalanceRequest.newBuilder()
                        .setAddress(address)
                        .build()
        ).balance
    }

    private fun getAllOrganizations(from: String): List<String> {
        return grpc.getAllOrganizations(
                GetAllOrganizationsRequest.newBuilder()
                        .setFrom(from)
                        .build()
        ).organizationsList
    }

    private fun addOrganizationMember(organization: String, admin: Credentials, member: String) {
        val addMemberTx = grpc.generateAddOrganizationMemberTx(
                GenerateAddMemberTxRequest.newBuilder()
                        .setFrom(admin.address)
                        .setOrganization(organization)
                        .setMember(member)
                        .build()
        )
        grpc.postTransaction(
                PostTransactionRequest.newBuilder()
                        .setData(sign(addMemberTx, admin))
                        .build()
        )
    }

    private fun getAllMembers(organization: String): List<String> {
        return grpc.getAllOrganizationMembers(
                OrganizationMembersRequest.newBuilder()
                        .setOrganization(organization)
                        .build()
        ).membersList
    }

    private fun getAllProjects(organization: String): List<String> {
        return grpc.getAllOrganizationProjects(
                OrganizationProjectsRequest.newBuilder()
                        .setOrganization(organization)
                        .build()
        ).projectsList
    }

    private fun getProject(address: String): TestProject {
        val name = grpc.getProjectName(
                ProjectNameRequest.newBuilder()
                        .setProject(address)
                        .build()
        ).name
        val description = grpc.getProjectDescription(
                ProjectDescriptionRequest.newBuilder()
                        .setProject(address)
                        .build()
        ).description
        val minUserInvestment = grpc.getProjectMinInvestmentPerUser(
                ProjectMinInvestmentPerUserRequest.newBuilder()
                        .setProject(address)
                        .build()
        ).amount
        val maxUserInvestment = grpc.getProjectMaxInvestmentPerUser(
                ProjectMaxInvestmentPerUserRequest.newBuilder()
                        .setProject(address)
                        .build()
        ).amount
        val investmentCap = grpc.getProjectInvestmentCap(
                ProjectInvestmentCapRequest.newBuilder()
                        .setProject(address)
                        .build()
        ).amount
        return TestProject(
                name,
                description,
                minUserInvestment,
                maxUserInvestment,
                investmentCap
        )
    }

    private fun createTestProject(organization: String, admin: Credentials, project: TestProject) {
        val createProjectTx = grpc.generateAddOrganizationProjectTx(
                GenerateAddProjectTxRequest.newBuilder()
                        .setFrom(admin.address)
                        .setOrganization(organization)
                        .setName(project.name)
                        .setDescription(project.description)
                        .setMinInvestmentPerUser(project.minUserInvestment)
                        .setMaxInvestmentPerUser(project.maxUserInvesment)
                        .setInvestmentCap(project.invesmentCap)
                        .build()
        )
        grpc.postTransaction(
                PostTransactionRequest.newBuilder()
                        .setData(sign(createProjectTx, admin))
                        .build()
        )
    }

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
}