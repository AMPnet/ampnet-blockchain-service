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
import com.ampnet.crowdfunding.proto.GenerateInvestmentTxRequest
import com.ampnet.crowdfunding.proto.GenerateApproveWithdrawTxRequest
import com.ampnet.crowdfunding.proto.GenerateBurnFromTxRequest
import com.ampnet.crowdfunding.proto.GenerateConfirmInvestmentTxRequest
import com.ampnet.crowdfunding.proto.GenerateMintTxRequest
import com.ampnet.crowdfunding.proto.GeneratePayoutRevenueSharesTxRequest
import com.ampnet.crowdfunding.proto.GenerateStartRevenuePayoutTxRequest
import com.ampnet.crowdfunding.proto.GenerateTransferTxRequest
import com.ampnet.crowdfunding.proto.GenerateWithdrawOrganizationFundsTxRequest
import com.ampnet.crowdfunding.proto.GenerateWithdrawProjectFundsTx
import com.ampnet.crowdfunding.proto.OrganizationExistsRequest
import com.ampnet.crowdfunding.proto.OrganizationMembersRequest
import com.ampnet.crowdfunding.proto.OrganizationProjectsRequest
import com.ampnet.crowdfunding.proto.OrganizationVerifiedRequest
import com.ampnet.crowdfunding.proto.PostTxRequest
import com.ampnet.crowdfunding.proto.PostVaultTxRequest
import com.ampnet.crowdfunding.proto.ProjectCompletelyFundedRequest
import com.ampnet.crowdfunding.proto.ProjectCurrentTotalInvestmentRequest
import com.ampnet.crowdfunding.proto.ProjectInvestmentCapRequest
import com.ampnet.crowdfunding.proto.ProjectMaxInvestmentPerUserRequest
import com.ampnet.crowdfunding.proto.ProjectMinInvestmentPerUserRequest
import com.ampnet.crowdfunding.proto.ProjectTotalInvestmentForUserRequest
import com.ampnet.crowdfunding.proto.RawTxResponse
import com.ampnet.crowdfunding.proto.WalletActiveRequest
import com.ampnet.crowdfunding.proto.GenerateWithdrawInvestmentTxRequest
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
import java.time.Instant
import java.time.ZonedDateTime

class BlockchainServiceTest : TestBase() {

    @Autowired
    private lateinit var transactionRepository: TransactionRepository

    @Autowired
    private lateinit var walletRepository: WalletRepository

    @Test
    fun mustBeAbleToRegisterUser() {
        lateinit var txHash: String
        verify("User can be created") {
            txHash = addWallet(accounts.bob)
            assertThat(isWalletActive(txHash)).isTrue()
        }
        verify("User creation transaction is stored in database") {
            val transactionsResponse = transactionRepository.findAll()
            assertThat(transactionsResponse).hasSize(1)

            val tx = transactionRepository.findByHash(txHash).get()
            assertTransaction(
                    tx,
                    expectedFrom = accounts.coopOwner.address,
                    expectedTo = txHash,
                    expectedType = TransactionType.WALLET_CREATE
            )
        }
        verify("User wallet is stored in database") {
            val walletsResponse = walletRepository.findAll()
            assertThat(walletsResponse).hasSize(1)

            val wallet = walletRepository.findByTransaction_Hash(txHash).get()
            assertThat(wallet.transaction.hash).isEqualTo(txHash)
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

        lateinit var createWalletHash: String
        lateinit var mintHash: String
        lateinit var approveHash: String
        lateinit var burnHash: String

        suppose("User Bob is registered on AMPnet and has zero balance") {
            createWalletHash = addWallet(accounts.bob)
            assertThat(getBalance(createWalletHash)).isEqualTo(initialBalance)
        }
        verify("Bob can deposit some amount of EUR") {
            mintHash = mint(createWalletHash, depositAmount)
            assertThat(getBalance(createWalletHash)).isEqualTo(depositAmount)
        }
        verify("Bob can withdraw some amount of EUR") {
            approveHash = approveTokenIssuer(createWalletHash, accounts.bob, withdrawAmount)
            burnHash = burn(createWalletHash, withdrawAmount)
            assertThat(getBalance(createWalletHash)).isEqualTo(finalBalance)
        }
        verify("All transactions are stored in database") {
            val transactions = transactionRepository.findAll()
            assertThat(transactions).hasSize(4)

            val mintTx = transactionRepository.findByHash(mintHash).get()
            assertTransaction(
                    mintTx,
                    expectedFrom = accounts.eurOwner.address,
                    expectedTo = createWalletHash,
                    expectedType = TransactionType.DEPOSIT,
                    expectedAmount = depositAmount
            )

            val approveTx = transactionRepository.findByHash(approveHash).get()
            assertTransaction(
                    approveTx,
                    expectedFrom = createWalletHash,
                    expectedTo = accounts.eurOwner.address,
                    expectedType = TransactionType.APPROVE,
                    expectedAmount = withdrawAmount
            )

            val burnTx = transactionRepository.findByHash(burnHash).get()
            assertTransaction(
                    burnTx,
                    expectedFrom = createWalletHash,
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
        lateinit var transferTxHash: String

        suppose("Users Bob and Alice are registered on AMPnet with their initial balances") {
            bobTxHash = addWallet(accounts.bob)
            mint(bobTxHash, bobInitialBalance)
            aliceTxHash = addWallet(accounts.alice)
            mint(aliceTxHash, aliceInitialBalance)
        }
        verify("Bob can transfer funds to Alice's wallet") {
            transferTxHash = transfer(bobTxHash, accounts.bob, aliceTxHash, bobToAliceAmount)
            assertThat(getBalance(bobTxHash)).isEqualTo(bobFinalBalance)
            assertThat(getBalance(aliceTxHash)).isEqualTo(aliceFinalBalance)
        }
        verify("All transactions are stored in database") {
            val transactions = transactionRepository.findAll()
            assertThat(transactions).hasSize(5)

            // skip transactions[0 and 2] - wallet create (already tested)

            val transferTx = transactionRepository.findByHash(transferTxHash).get()
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
        lateinit var orgCreateHash: String
        lateinit var orgActivateHash: String
        suppose("User Bob is registered on AMPnet") {
            bobTxHash = addWallet(accounts.bob)
        }
        verify("Bob can create organization") {
            orgCreateHash = addOrganization(bobTxHash, accounts.bob)
            orgActivateHash = approveOrganization(orgCreateHash)
            assertThat(getOrganizations()).hasSize(1)
            assertThat(organizationExists(orgCreateHash))
            assertThat(organizationActive(orgCreateHash))
        }
        verify("All transactions are stored in database") {
            val transactions = transactionRepository.findAll()
            assertThat(transactions).hasSize(3)

            val orgCreateTx = transactionRepository.findByHash(orgCreateHash).get()
            assertTransaction(
                    orgCreateTx,
                    expectedFrom = bobTxHash,
                    expectedTo = orgCreateHash,
                    expectedType = TransactionType.ORG_CREATE
            )

            val orgActivateTx = transactionRepository.findByHash(orgActivateHash).get()
            assertTransaction(
                    orgActivateTx,
                    expectedFrom = accounts.coopOwner.address,
                    expectedTo = orgCreateHash,
                    expectedType = TransactionType.ORG_ACTIVATE
            )
        }
    }

    @Test
    fun mustBeAbleToAddUserToOrganization() {
        lateinit var orgTxHash: String
        lateinit var bobTxHash: String
        lateinit var aliceTxHash: String
        lateinit var addMemberHash: String
        suppose("Users Bob,Alice exist and Bob created Greenpeace organization") {
            bobTxHash = addWallet(accounts.bob)
            aliceTxHash = addWallet(accounts.alice)
            orgTxHash = addOrganization(bobTxHash, accounts.bob)
            approveOrganization(orgTxHash)
        }
        verify("Bob can add new user to organization") {
            addMemberHash = addOrganizationMember(orgTxHash, bobTxHash, accounts.bob, aliceTxHash)

            val members = getAllMembers(orgTxHash)
            assertThat(members).hasSize(1)
            assertThat(members.first()).isEqualTo(accounts.alice.address)
        }
        verify("All transactions are stored in database") {
            val transactions = transactionRepository.findAll()
            assertThat(transactions).hasSize(5)

            val addMemberTx = transactionRepository.findByHash(addMemberHash).get()
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
            bobTxHash = addWallet(accounts.bob)
            orgTxHash = addOrganization(bobTxHash, accounts.bob)
            approveOrganization(orgTxHash)
        }
        verify("Bob can create new investment project") {
            projectTxHash = createTestProject(
                    orgTxHash,
                    bobTxHash,
                    accounts.bob,
                    testProject,
                    Instant.now().plusSeconds(1000000).epochSecond
            )

            val projects = getAllProjects(orgTxHash)
            assertThat(projects).hasSize(1)

            val fetchedProject = getProject(projectTxHash)
            assertThat(fetchedProject).isEqualTo(testProject)
        }
        verify("All transactions are stored in database") {
            val transactions = transactionRepository.findAll()
            assertThat(transactions).hasSize(4)

            val addProjectTx = transactionRepository.findByHash(projectTxHash).get()
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
        lateinit var pendingWithdrawHash: String
        lateinit var withdrawHash: String

        val initialOrgBalance = 1000L
        val withdrawAmount = 1000L
        val finalOrgBalance = initialOrgBalance - withdrawAmount

        suppose("User Bob exists and is admin of Greenpeace organization which has some positive balance") {
            bobTxHash = addWallet(accounts.bob)
            orgTxHash = addOrganization(bobTxHash, accounts.bob)
            approveOrganization(orgTxHash)
            mint(orgTxHash, initialOrgBalance)
        }
        verify("Bob can withdraw money from organization") {
            pendingWithdrawHash = withdrawFromOrganization(orgTxHash, bobTxHash, accounts.bob, withdrawAmount)
            withdrawHash = burn(orgTxHash, withdrawAmount)
            assertThat(getBalance(orgTxHash)).isEqualTo(finalOrgBalance)
        }
        verify("All transactions are stored in database") {
            val transactions = transactionRepository.findAll()
            assertThat(transactions).hasSize(6)

            val pendingWithdrawTx = transactionRepository.findByHash(pendingWithdrawHash).get()
            assertTransaction(
                    pendingWithdrawTx,
                    expectedFrom = orgTxHash,
                    expectedTo = accounts.eurOwner.address,
                    expectedType = TransactionType.PENDING_ORG_WITHDRAW,
                    expectedAmount = withdrawAmount
            )

            val withdrawTx = transactionRepository.findByHash(withdrawHash).get()
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
            bobTxHash = addWallet(accounts.bob)
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

    @Test
    fun mustBeAbleForUsersToInvestInProjectAndReachCap() {
        lateinit var bobTxHash: String
        lateinit var aliceTxHash: String
        lateinit var janeTxHash: String
        lateinit var orgTxHash: String
        lateinit var projTxHash: String

        lateinit var aliceInvestmentApproveHash: String
        lateinit var aliceInvestmentHash: String
        lateinit var janeInvestmentApproveHash: String
        lateinit var janeInvestmentHash: String

        lateinit var approveWithdrawHash: String
        lateinit var withdrawHash: String

        val initialBalance = 100000L

        suppose("Users Bob, Alice and Jane exist with their initial balances") {
            bobTxHash = addWallet(accounts.bob)
            aliceTxHash = addWallet(accounts.alice)
            janeTxHash = addWallet(accounts.jane)
            mint(bobTxHash, initialBalance)
            mint(aliceTxHash, initialBalance)
            mint(janeTxHash, initialBalance)
        }

        suppose("Bob created Greenpeace organization with Alice and Jane as members") {
            orgTxHash = addOrganization(bobTxHash, accounts.bob)
            approveOrganization(orgTxHash)
            addOrganizationMember(orgTxHash, bobTxHash, accounts.bob, aliceTxHash)
            addOrganizationMember(orgTxHash, bobTxHash, accounts.bob, janeTxHash)
        }

        suppose("Bob created new investment project") {
            projTxHash = createTestProject(
                    orgTxHash,
                    bobTxHash,
                    accounts.bob,
                    testProject,
                    Instant.now().plusSeconds(1000000).epochSecond
            )
        }

        verify("Alice and Jane can invest in project and reach cap") {
            assertThat(getProjectTotalInvestment(projTxHash)).isZero()
            assertThat(isProjectCompletelyFunded(projTxHash)).isFalse()

            val investment = testProject.invesmentCap / 2

            aliceInvestmentApproveHash = approveInvestment(aliceTxHash, accounts.alice, investment, projTxHash)
            aliceInvestmentHash = invest(projTxHash, aliceTxHash, accounts.alice)

            janeInvestmentApproveHash = approveInvestment(janeTxHash, accounts.jane, investment, projTxHash)
            janeInvestmentHash = invest(projTxHash, janeTxHash, accounts.jane)

            assertThat(getBalance(aliceTxHash)).isEqualTo(initialBalance - investment)
            assertThat(getBalance(janeTxHash)).isEqualTo(initialBalance - investment)

            assertThat(getProjectInvestmentForUser(aliceTxHash, projTxHash)).isEqualTo(investment)
            assertThat(getProjectInvestmentForUser(janeTxHash, projTxHash)).isEqualTo(investment)

            assertThat(getProjectTotalInvestment(projTxHash)).isEqualTo(testProject.invesmentCap)
            assertThat(isProjectCompletelyFunded(projTxHash)).isTrue()
        }

        verify("Organization admin can withdraw funds from project") {
            approveWithdrawHash = withdrawFundsFromProject(projTxHash, bobTxHash, accounts.bob, testProject.invesmentCap)
            withdrawHash = burn(projTxHash, testProject.invesmentCap)
            assertThat(getBalance(projTxHash)).isZero()
        }

        verify("All transactions are stored in database") {
            val transactions = transactionRepository.findAll()
            assertThat(transactions).hasSize(17)

            val aliceApproveInvestmentTx = transactionRepository.findByHash(aliceInvestmentApproveHash).get()
            assertTransaction(
                    aliceApproveInvestmentTx,
                    expectedFrom = aliceTxHash,
                    expectedTo = projTxHash,
                    expectedType = TransactionType.APPROVE,
                    expectedAmount = testProject.invesmentCap / 2
            )

            val aliceInvestTx = transactionRepository.findByHash(aliceInvestmentHash).get()
            assertTransaction(
                    aliceInvestTx,
                    expectedFrom = aliceTxHash,
                    expectedTo = projTxHash,
                    expectedType = TransactionType.INVEST,
                    expectedAmount = testProject.invesmentCap / 2
            )

            val janeApproveInvesmentTx = transactionRepository.findByHash(janeInvestmentApproveHash).get()
            assertTransaction(
                    janeApproveInvesmentTx,
                    expectedFrom = janeTxHash,
                    expectedTo = projTxHash,
                    expectedType = TransactionType.APPROVE,
                    expectedAmount = testProject.invesmentCap / 2
            )

            val janeInvestTx = transactionRepository.findByHash(janeInvestmentHash).get()
            assertTransaction(
                    janeInvestTx,
                    expectedFrom = janeTxHash,
                    expectedTo = projTxHash,
                    expectedType = TransactionType.INVEST,
                    expectedAmount = testProject.invesmentCap / 2
            )

            val pendingWithdrawProjectFundsTx = transactionRepository.findByHash(approveWithdrawHash).get()
            assertTransaction(
                    pendingWithdrawProjectFundsTx,
                    expectedFrom = projTxHash,
                    expectedTo = accounts.eurOwner.address,
                    expectedType = TransactionType.PENDING_PROJ_WITHDRAW,
                    expectedAmount = testProject.invesmentCap
            )

            val withdrawProjectFundsTx = transactionRepository.findByHash(withdrawHash).get()
            assertTransaction(
                    withdrawProjectFundsTx,
                    expectedFrom = projTxHash,
                    expectedTo = accounts.eurOwner.address,
                    expectedType = TransactionType.WITHDRAW,
                    expectedAmount = testProject.invesmentCap
            )
        }
    }

    @Test
    fun shouldThrowErrorIfTxTypeDoesNotMatchActualTx() {
        lateinit var bobTxHash: String
        suppose("User Bob is registered") {
            bobTxHash = addWallet(accounts.bob)
        }
        verify("User tries to create organization but signed tx does not match provided tx type, error is thrown") {
            val tx = grpc.generateAddOrganizationTx(
                    GenerateAddOrganizationTxRequest.newBuilder()
                            .setFromTxHash(bobTxHash)
                            .build()
            )
            val e = assertThrows<StatusRuntimeException> {
                grpc.postVaultTransaction(
                        PostVaultTxRequest.newBuilder()
                                .setData(convertToVaultEncoding(sign(tx, accounts.bob)))
                                .setTxType(typeToProto(TransactionType.INVEST)) // wrong! should be ORG_CREATE
                                .build()
                )
            }
            val errorCode = ErrorCode.fromMessage(e.status.description.orEmpty())
            assertThat(errorCode).isEqualTo(ErrorCode.INVALID_TX_TYPE.code)
        }
    }

    @Test
    fun mustBePossibleForProjectAdminToPayoutRevenueShares() {
        lateinit var bobTxHash: String
        lateinit var aliceTxHash: String
        lateinit var janeTxHash: String
        lateinit var orgTxHash: String
        lateinit var projTxHash: String

        lateinit var startPayoutTxHash: String
        lateinit var payoutTxHash: String

        val initialBalance = testProject.invesmentCap / 2
        val revenue = 100000L

        suppose("Investors and project are created, investment cap reached.") {
            bobTxHash = addWallet(accounts.bob)
            aliceTxHash = addWallet(accounts.alice)
            janeTxHash = addWallet(accounts.jane)

            mint(bobTxHash, initialBalance)
            mint(aliceTxHash, initialBalance)
            mint(janeTxHash, initialBalance)

            orgTxHash = addOrganization(bobTxHash, accounts.bob)
            approveOrganization(orgTxHash)
            projTxHash = createTestProject(
                    orgTxHash,
                    bobTxHash,
                    accounts.bob,
                    testProject,
                    Instant.now().plusSeconds(1000000).epochSecond
            )

            approveInvestment(aliceTxHash, accounts.alice, initialBalance, projTxHash)
            invest(projTxHash, aliceTxHash, accounts.alice)

            approveInvestment(janeTxHash, accounts.jane, initialBalance, projTxHash)
            invest(projTxHash, janeTxHash, accounts.jane)
        }

        verify("Admin can payout revenue shares to investors.") {
            mint(projTxHash, revenue)

            startPayoutTxHash = startRevenuePayout(bobTxHash, accounts.bob, projTxHash, revenue)
            payoutTxHash = payoutRevenueShares(projTxHash, bobTxHash, accounts.bob)

            // Alice and Jane both have 50% stake at power plant so they should get half of revenue each
            assertThat(getBalance(aliceTxHash)).isEqualTo(revenue / 2)
            assertThat(getBalance(janeTxHash)).isEqualTo(revenue / 2)
        }

        verify("All transactions are stored in database") {
            val transactions = transactionRepository.findAll()
            assertThat(transactions).hasSize(18)

            val payoutStartedTx = transactionRepository.findByHash(startPayoutTxHash).get()
            assertTransaction(
                    payoutStartedTx,
                    expectedFrom = bobTxHash,
                    expectedTo = projTxHash,
                    expectedType = TransactionType.START_REVENUE_PAYOUT,
                    expectedAmount = revenue
            )

            val payoutTx = transactionRepository.findByHash(payoutTxHash).get()
            assertTransaction(
                    payoutTx,
                    expectedFrom = bobTxHash,
                    expectedTo = projTxHash,
                    expectedType = TransactionType.REVENUE_PAYOUT
            )

            val aliceSharePayoutTx = transactionRepository.findByHash(
                    "$payoutTxHash+${accounts.alice.address}"
            ).get()
            assertTransaction(
                    aliceSharePayoutTx,
                    expectedFrom = projTxHash,
                    expectedTo = aliceTxHash,
                    expectedType = TransactionType.SHARE_PAYOUT,
                    expectedAmount = revenue / 2
            )

            val janeSharePayoutTx = transactionRepository.findByHash(
                    "$payoutTxHash+${accounts.jane.address}"
            ).get()
            assertTransaction(
                    janeSharePayoutTx,
                    expectedFrom = projTxHash,
                    expectedTo = janeTxHash,
                    expectedType = TransactionType.SHARE_PAYOUT,
                    expectedAmount = revenue / 2
            )
        }
    }

    @Test
    fun mustBePossibleForUserToWithdrawInvestmentIfProjectExpired() {
        lateinit var bobTxHash: String
        lateinit var aliceTxHash: String
        lateinit var orgTxHash: String
        lateinit var projTxHash: String
        lateinit var withdrawInvestmentTxHash: String

        val initialAliceBalance = testProject.minUserInvestment

        suppose("Project is created, Alice invests, Project expires and cap not reached.") {
            // create and fund wallets
            bobTxHash = addWallet(accounts.bob)
            aliceTxHash = addWallet(accounts.alice)
            mint(aliceTxHash, initialAliceBalance)

            // create organization
            orgTxHash = addOrganization(bobTxHash, accounts.bob)
            approveOrganization(orgTxHash)

            // create project
            projTxHash = createTestProject(
                    orgTxHash,
                    bobTxHash,
                    accounts.bob,
                    testProject,
                    Instant.now().plusSeconds(4).epochSecond // Expires in 2 seconds, Alice has to invest before!
            )

            // Alice invests
            approveInvestment(aliceTxHash, accounts.alice, initialAliceBalance, projTxHash)
            invest(projTxHash, aliceTxHash, accounts.alice)
        }

        verify("Alice can withdraw her investment.") {
            Thread.sleep(5000) // Wait for project to expire
            withdrawInvestmentTxHash = withdrawInvestment(projTxHash, aliceTxHash, accounts.alice)
            assertThat(getBalance(aliceTxHash)).isEqualTo(initialAliceBalance)
        }

        verify("Withdraw transaction is stored in database.") {
            val transactions = transactionRepository.findAll()
            assertThat(transactions).hasSize(9)

            val tx = transactionRepository.findByHash(withdrawInvestmentTxHash).get()
            assertTransaction(
                    tx,
                    expectedFrom = projTxHash,
                    expectedTo = aliceTxHash,
                    expectedType = TransactionType.WITHDRAW_INVESTMENT,
                    expectedAmount = initialAliceBalance
            )
        }
    }

    /** HELPER FUNCTIONS */

    private fun addWallet(wallet: Credentials): String {
        val address = wallet.address
        val publicKey = getPublicKey(wallet)
        return grpc.addWallet(
                AddWalletRequest.newBuilder()
                        .setAddress(address)
                        .setPublicKey(publicKey)
                        .build()
        ).txHash
    }

    private fun mint(toTxHash: String, amount: Long): String {
        val tx = grpc.generateMintTx(
                GenerateMintTxRequest.newBuilder()
                        .setAmount(amount)
                        .setFrom(accounts.eurOwner.address)
                        .setToTxHash(toTxHash)
                        .build()
        )
        return grpc.postTransaction(
                PostTxRequest.newBuilder()
                        .setData(sign(tx, accounts.eurOwner))
                        .setTxType(typeToProto(TransactionType.DEPOSIT))
                        .build()
        ).txHash
    }

    private fun approveTokenIssuer(fromTxHash: String, fromCredentials: Credentials, amount: Long): String {
        val approveTx = grpc.generateApproveWithdrawTx(
                GenerateApproveWithdrawTxRequest.newBuilder()
                        .setFromTxHash(fromTxHash)
                        .setAmount(amount)
                        .build()
        )
        return grpc.postVaultTransaction(
                PostVaultTxRequest.newBuilder()
                        .setData(convertToVaultEncoding(sign(approveTx, fromCredentials)))
                        .setTxType(typeToProto(TransactionType.APPROVE))
                        .build()
        ).txHash
    }

    private fun burn(fromTxHash: String, amount: Long): String {
        val burnTx = grpc.generateBurnFromTx(
                GenerateBurnFromTxRequest.newBuilder()
                        .setAmount(amount)
                        .setBurnFromTxHash(fromTxHash)
                        .setFrom(accounts.eurOwner.address)
                        .build()
        )
        return grpc.postTransaction(
                PostTxRequest.newBuilder()
                        .setData(sign(burnTx, accounts.eurOwner))
                        .setTxType(typeToProto(TransactionType.WITHDRAW))
                        .build()
        ).txHash
    }

    private fun transfer(fromTxHash: String, from: Credentials, toTxHash: String, amount: Long): String {
        val transferTx = grpc.generateTransferTx(
                GenerateTransferTxRequest.newBuilder()
                        .setFromTxHash(fromTxHash)
                        .setToTxHash(toTxHash)
                        .setAmount(amount)
                        .build()
        )
        return grpc.postVaultTransaction(
                PostVaultTxRequest.newBuilder()
                        .setData(convertToVaultEncoding(sign(transferTx, from)))
                        .setTxType(typeToProto(TransactionType.TRANSFER))
                        .build()
        ).txHash
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

    private fun approveOrganization(orgTxHash: String): String {
        return grpc.activateOrganization(
                ActivateOrganizationRequest.newBuilder()
                        .setOrganizationTxHash(orgTxHash)
                        .build()
        ).txHash
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

    private fun getOrganizations(): List<String> {
        return grpc.getOrganizations(
                Empty.getDefaultInstance()
        ).organizationsList
    }

    private fun addOrganizationMember(orgTxHash: String, adminTxHash: String, admin: Credentials, memberTxHash: String): String {
        val addMemberTx = grpc.generateAddOrganizationMemberTx(
                GenerateAddMemberTxRequest.newBuilder()
                        .setFromTxHash(adminTxHash)
                        .setOrganizationTxHash(orgTxHash)
                        .setMemberTxHash(memberTxHash)
                        .build()
        )
        return grpc.postVaultTransaction(
                PostVaultTxRequest.newBuilder()
                        .setData(convertToVaultEncoding(sign(addMemberTx, admin)))
                        .setTxType(typeToProto(TransactionType.ORG_ADD_MEMBER))
                        .build()
        ).txHash
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

    private fun createTestProject(
        orgTxHash: String,
        adminTxHash: String,
        admin: Credentials,
        project: TestProject,
        endInvestmentTime: Long
    ): String {
        val createProjectTx = grpc.generateAddOrganizationProjectTx(
                GenerateAddProjectTxRequest.newBuilder()
                        .setFromTxHash(adminTxHash)
                        .setOrganizationTxHash(orgTxHash)
                        .setMinInvestmentPerUser(project.minUserInvestment)
                        .setMaxInvestmentPerUser(project.maxUserInvesment)
                        .setInvestmentCap(project.invesmentCap)
                        .setEndInvestmentTime(endInvestmentTime)
                        .build()
        )
        return grpc.postVaultTransaction(
                PostVaultTxRequest.newBuilder()
                        .setData(convertToVaultEncoding(sign(createProjectTx, admin)))
                        .setTxType(typeToProto(TransactionType.ORG_ADD_PROJECT))
                        .build()
        ).txHash
    }

    private fun withdrawFromOrganization(orgTxHash: String, adminTxHash: String, admin: Credentials, amount: Long): String {
        val withdrawTx = grpc.generateWithdrawOrganizationFundsTx(
                GenerateWithdrawOrganizationFundsTxRequest.newBuilder()
                        .setFromTxHash(adminTxHash)
                        .setOrganizationTxHash(orgTxHash)
                        .setAmount(amount)
                        .build()
        )
        return grpc.postVaultTransaction(
                PostVaultTxRequest.newBuilder()
                        .setData(convertToVaultEncoding(sign(withdrawTx, admin)))
                        .setTxType(typeToProto(TransactionType.PENDING_ORG_WITHDRAW))
                        .build()
        ).txHash
    }

    private fun approveInvestment(fromTxHash: String, from: Credentials, amount: Long, projectTxHash: String): String {
        val approveTx = grpc.generateInvestmentTx(
                GenerateInvestmentTxRequest.newBuilder()
                        .setFromTxHash(fromTxHash)
                        .setProjectTxHash(projectTxHash)
                        .setAmount(amount)
                        .build()
        )
        return grpc.postVaultTransaction(
                PostVaultTxRequest.newBuilder()
                        .setData(convertToVaultEncoding(sign(approveTx, from)))
                        .setTxType(typeToProto(TransactionType.APPROVE))
                        .build()
        ).txHash
    }

    private fun invest(projectTxHash: String, investorTxHash: String, investor: Credentials): String {
        val investTx = grpc.generateConfirmInvestmentTx(
                GenerateConfirmInvestmentTxRequest.newBuilder()
                        .setFromTxHash(investorTxHash)
                        .setProjectTxHash(projectTxHash)
                        .build()
        )
        return grpc.postVaultTransaction(
                PostVaultTxRequest.newBuilder()
                        .setData(convertToVaultEncoding(sign(investTx, investor)))
                        .setTxType(typeToProto(TransactionType.INVEST))
                        .build()
        ).txHash
    }

    private fun startRevenuePayout(adminTxHash: String, admin: Credentials, projectTxHash: String, revenue: Long): String {
        val startPayoutTx = grpc.generateStartRevenuePayoutTx(
                GenerateStartRevenuePayoutTxRequest.newBuilder()
                        .setFromTxHash(adminTxHash)
                        .setProjectTxHash(projectTxHash)
                        .setRevenue(revenue)
                        .build()
        )
        return grpc.postVaultTransaction(
                PostVaultTxRequest.newBuilder()
                        .setData(convertToVaultEncoding(sign(startPayoutTx, admin)))
                        .setTxType(typeToProto(TransactionType.START_REVENUE_PAYOUT))
                        .build()
        ).txHash
    }

    private fun payoutRevenueShares(projectTxHash: String, adminTxHash: String, admin: Credentials): String {
        val payoutTx = grpc.generatePayoutRevenueSharesTx(
                GeneratePayoutRevenueSharesTxRequest.newBuilder()
                        .setFromTxHash(adminTxHash)
                        .setProjectTxHash(projectTxHash)
                        .build()
        )
        return grpc.postVaultTransaction(
                PostVaultTxRequest.newBuilder()
                        .setData(convertToVaultEncoding(sign(payoutTx, admin)))
                        .setTxType(typeToProto(TransactionType.REVENUE_PAYOUT))
                        .build()
        ).txHash
    }

    private fun withdrawInvestment(projectTxHash: String, investorTxHash: String, investor: Credentials): String {
        val withdrawTx = grpc.generateWithdrawInvestmentTx(
                GenerateWithdrawInvestmentTxRequest.newBuilder()
                        .setFromTxHash(investorTxHash)
                        .setProjectTxHash(projectTxHash)
                        .build()
        )
        return grpc.postVaultTransaction(
                PostVaultTxRequest.newBuilder()
                        .setData(convertToVaultEncoding(sign(withdrawTx, investor)))
                        .setTxType(typeToProto(TransactionType.WITHDRAW_INVESTMENT))
                        .build()
        ).txHash
    }

    private fun getProjectInvestmentForUser(userTxHash: String, projectTxHash: String): Long {
        return grpc.getProjectTotalInvestmentForUser(
                ProjectTotalInvestmentForUserRequest.newBuilder()
                        .setProjectTxHash(projectTxHash)
                        .setUserTxHash(userTxHash)
                        .build()
        ).amount
    }

    private fun getProjectTotalInvestment(projectTxHash: String): Long {
        return grpc.getProjectCurrentTotalInvestment(
                ProjectCurrentTotalInvestmentRequest.newBuilder()
                        .setProjectTxHash(projectTxHash)
                        .build()
        ).amount
    }

    private fun isProjectCompletelyFunded(projectTxHash: String): Boolean {
        return grpc.isProjectCompletelyFunded(
                ProjectCompletelyFundedRequest.newBuilder()
                        .setProjectTxHash(projectTxHash)
                        .build()
        ).funded
    }

    private fun withdrawFundsFromProject(projectTxHash: String, adminTxHash: String, admin: Credentials, amount: Long): String {
        val withdrawTx = grpc.generateWithdrawProjectFundsTx(
                GenerateWithdrawProjectFundsTx.newBuilder()
                        .setFromTxHash(adminTxHash)
                        .setAmount(amount)
                        .setProjectTxHash(projectTxHash)
                        .build()
        )
        return grpc.postVaultTransaction(
                PostVaultTxRequest.newBuilder()
                        .setData(convertToVaultEncoding(sign(withdrawTx, admin)))
                        .setTxType(typeToProto(TransactionType.PENDING_PROJ_WITHDRAW))
                        .build()
        ).txHash
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