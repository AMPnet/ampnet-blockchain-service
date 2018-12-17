package com.ampnet.crowdfunding.blockchain.contract.impl

import com.ampnet.crowdfunding.blockchain.config.ApplicationProperties
import com.ampnet.crowdfunding.blockchain.contract.AmpnetService
import org.springframework.stereotype.Service
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Array
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Utf8String
import org.web3j.crypto.RawTransaction
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import java.math.BigInteger

@Service
class AmpnetServiceImpl(val applicationProperties: ApplicationProperties, val web3j: Web3j): AmpnetService {

    override fun generateAddWalletTx(wallet: String, from: String): RawTransaction {
        val function = Function(
                "addWallet",
                listOf(Address(wallet)),
                emptyList()
        )
        val encodedFunction = FunctionEncoder.encode(function)
        val txCountResponse = web3j.ethGetTransactionCount(from, DefaultBlockParameterName.LATEST).send()
        val gasPriceResponse = web3j.ethGasPrice().send()
        val ampnetAddress = applicationProperties.contracts.ampnetAddress

        return RawTransaction.createTransaction(
                txCountResponse.transactionCount,
                gasPriceResponse.gasPrice,
                BigInteger.valueOf(1000000),
                ampnetAddress,
                encodedFunction
        )
    }

    override fun generateAddOrganizationTx(name: String, from: String): RawTransaction {
        val function = Function(
                "addOrganization",
                listOf(Utf8String(name)),
                emptyList()
        )
        val encodedFunction = FunctionEncoder.encode(function)
        val txCountResponse = web3j.ethGetTransactionCount(from, DefaultBlockParameterName.LATEST).send()
        val gasPriceResponse = web3j.ethGasPrice().send()
        val ampnetAddress = applicationProperties.contracts.ampnetAddress

        return RawTransaction.createTransaction(
                txCountResponse.transactionCount,
                gasPriceResponse.gasPrice,
                BigInteger.valueOf(1000000),
                ampnetAddress,
                encodedFunction
        )
    }

    override fun getAllOrganizations(): List<String> {
        val function = Function(
                "getAllOrganizations",
                emptyList(),
                listOf(TypeReference.create(Array::class.java))
        )
        val encodedFunction = FunctionEncoder.encode(function)
        val ampnetAddress = applicationProperties.contracts.ampnetAddress

        val response = web3j.ethCall(
                Transaction.createEthCallTransaction(
                        "",
                        ampnetAddress,
                        encodedFunction
                ),
                DefaultBlockParameterName.LATEST
        ).send()

        val returnValues = FunctionReturnDecoder.decode(response.value, function.outputParameters)
        return emptyList()
    }
}