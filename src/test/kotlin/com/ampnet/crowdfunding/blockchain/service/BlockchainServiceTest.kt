package com.ampnet.crowdfunding.blockchain.service

import com.ampnet.crowdfunding.BalanceRequest
import com.ampnet.crowdfunding.Empty
import com.ampnet.crowdfunding.GenerateActivateTxRequest
import com.ampnet.crowdfunding.GenerateAddOrganizationTxRequest
import com.ampnet.crowdfunding.GenerateAddWalletTxRequest
import com.ampnet.crowdfunding.GenerateApproveTxRequest
import com.ampnet.crowdfunding.GenerateBurnFromTxRequest
import com.ampnet.crowdfunding.GenerateMintTxRequest
import com.ampnet.crowdfunding.PostTransactionRequest
import com.ampnet.crowdfunding.RawTxResponse
import com.ampnet.crowdfunding.WalletActiveRequest
import com.ampnet.crowdfunding.blockchain.TestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.utils.Numeric

class BlockchainServiceTest : TestBase() {

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

//    @Test
//    fun mustBeAbleToDepositAndWithdrawTokens() {
//        val initialBalance = 0L // user starts with empty wallet
//        val depositAmount = 1550L // 15.50 EUR
//        val withdrawAmount = 1550L // 15.50 EUR
//        val finalBalance = initialBalance + depositAmount - withdrawAmount
//
//        suppose("User Bob is registered on AMPnet and has zero balance") {
//            addWallet(accounts.bob.address)
//            assertThat(getBalance(accounts.bob.address)).isEqualTo(initialBalance)
//        }
//        verify("Bob can deposit some amount of EUR") {
//            mint(accounts.bob.address, depositAmount)
//            assertThat(getBalance(accounts.bob.address)).isEqualTo(depositAmount)
//        }
//        verify("Bob can withdraw some amount of EUR") {
//            burn(accounts.bob, withdrawAmount)
//            assertThat(getBalance(accounts.bob.address)).isEqualTo(finalBalance)
//        }
//    }

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

    private fun addAndApproveOrganization(admin: Credentials, name: String) {
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

        val organizations = getAllOrganizations()
        val activateOrgTx = grpc.generateActivateTx(
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
    }

    private fun isWalletActive(address: String): Boolean {
        return grpc.isWalletActive(
                WalletActiveRequest.newBuilder()
                        .setWallet(address)
                        .build()
        ).active
    }

    private fun getBalance(address: String): Long {
        return grpc.getBalance(
                BalanceRequest.newBuilder()
                        .setAddress(address)
                        .build()
        ).balance
    }

    private fun getAllOrganizations(): List<String> {
        return grpc.getAllOrganizations(
                Empty.getDefaultInstance()
        ).organizationsList
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