package com.ampnet.crowdfunding.blockchain.config

import com.ampnet.crowdfunding.blockchain.contract.OrganizationService
import com.ampnet.crowdfunding.blockchain.contract.ProjectService
import com.ampnet.crowdfunding.blockchain.contract.TransactionService
import com.ampnet.crowdfunding.blockchain.contract.impl.EurService
import org.springframework.stereotype.Service
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger

@Service
class BlockchainCleanerService(
        val applicationProperties: ApplicationProperties,
        val web3j: Web3j,
        val eurService: EurService,
        val organizationService: OrganizationService,
        val projectService: ProjectService,
        val transactionService: TransactionService
) {

    private val ampnetAddress = applicationProperties.contracts.ampnetAddress
    private val eurAddress = applicationProperties.contracts.eurAddress

    // Burn all user tokens and disable wallet
    fun disableUser(user: Credentials, ampnet: Credentials, tokenIssuer: Credentials) {

        val userBalance = eurService.balanceOf(user.address)
        if (userBalance > BigDecimal.ZERO) {
            val tx = eurService.generateApproveTransaction(
                    user.address,
                    tokenIssuer.address,
                    userBalance
            )
            val signedTx = TransactionEncoder.signMessage(tx, user)
            transactionService.postTransaction(Numeric.toHexString(signedTx))
        }

        val function = Function(
                "removeUser",
                listOf(Address(user.address)),
                emptyList()
        )
        val encodedFunction = FunctionEncoder.encode(function)
        val txCountResponse = web3j.ethGetTransactionCount(ampnetAddress, DefaultBlockParameterName.LATEST).send()
        val gasPriceResponse = web3j.ethGasPrice().send()

        val rawTx = RawTransaction.createTransaction(
                txCountResponse.transactionCount,
                gasPriceResponse.gasPrice,
                BigInteger.valueOf(1000000),
                ampnetAddress,
                encodedFunction
        )

        val signedTx = TransactionEncoder.signMessage(rawTx, ampnet)
        val signedTxHex = Numeric.toHexString(signedTx)

        web3j.ethSendRawTransaction(signedTxHex).send()
    }

    fun disableProject(address: String, admin: Credentials, tokenIssuer: Credentials) {

        val projectBalance = eurService.balanceOf(address)
        val withdrawTx = projectService.generateWithdrawFundsTransaction(
                address,
                admin.address,
                tokenIssuer.address,
                projectBalance
        )
        transactionService.postTransaction(
                Numeric.toHexString(
                        TransactionEncoder.signMessage(withdrawTx, admin)
                )
        )

        val burnTx = eurService.generateBurnFromTransaction(tokenIssuer.address, address, projectBalance)
        transactionService.postTransaction(
                Numeric.toHexString(
                        TransactionEncoder.signMessage(burnTx, tokenIssuer)
                )
        )

    }

    fun disableOrganization(address: String, admin: Credentials, tokenIssuer: Credentials) {

        val organizationBalance = eurService.balanceOf(address)
        val withdrawTx = organizationService.generateWithdrawFundsTx(
                address,
                tokenIssuer.address,
                admin.address,
                organizationBalance
        )
        transactionService.postTransaction(
                Numeric.toHexString(
                        TransactionEncoder.signMessage(withdrawTx, admin)
                )
        )

        val burnTx = eurService.generateBurnFromTransaction(tokenIssuer.address, address, organizationBalance)
        transactionService.postTransaction(
                Numeric.toHexString(
                        TransactionEncoder.signMessage(burnTx, tokenIssuer)
                )
        )

    }

    fun clearOrganizations(ampnet: Credentials) {
        val function = Function(
                "removeOrganizations",
                emptyList(),
                emptyList()
        )
        val encodedFunction = FunctionEncoder.encode(function)
        val txCountResponse = web3j.ethGetTransactionCount(ampnet.address, DefaultBlockParameterName.LATEST).send()
        val gasPriceResponse = web3j.ethGasPrice().send()

        val rawTx = RawTransaction.createTransaction(
                txCountResponse.transactionCount,
                gasPriceResponse.gasPrice,
                BigInteger.valueOf(1000000),
                ampnetAddress,
                encodedFunction
        )

        val signedTx = TransactionEncoder.signMessage(rawTx, ampnet)
        val signedTxHex = Numeric.toHexString(signedTx)

        web3j.ethSendRawTransaction(signedTxHex).send()
    }

}