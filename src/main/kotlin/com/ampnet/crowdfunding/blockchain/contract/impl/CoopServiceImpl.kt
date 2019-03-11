package com.ampnet.crowdfunding.blockchain.contract.impl

import com.ampnet.crowdfunding.blockchain.config.ApplicationProperties
import com.ampnet.crowdfunding.blockchain.contract.CoopService
import org.springframework.stereotype.Service
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.Function
import org.web3j.crypto.RawTransaction
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import java.math.BigInteger

@Service
class CoopServiceImpl(
    val applicationProperties: ApplicationProperties,
    val web3j: Web3j
) : CoopService {

    override fun generateAddWalletTx(from: String, wallet: String): RawTransaction {
        val function = Function(
                "addWallet",
                listOf(Address(wallet)),
                emptyList()
        )
        val encodedFunction = FunctionEncoder.encode(function)
        val txCountResponse = web3j.ethGetTransactionCount(from, DefaultBlockParameterName.LATEST).send()
        val gasPriceResponse = web3j.ethGasPrice().send()
        val coopAddress = applicationProperties.contracts.coopAddress

        return RawTransaction.createTransaction(
                txCountResponse.transactionCount,
                gasPriceResponse.gasPrice,
                BigInteger.valueOf(1000000),
                coopAddress,
                encodedFunction
        )
    }

    override fun generateAddOrganizationTx(from: String): RawTransaction {
        val function = Function(
                "addOrganization",
                emptyList(),
                emptyList()
        )
        val encodedFunction = FunctionEncoder.encode(function)
        val txCountResponse = web3j.ethGetTransactionCount(from, DefaultBlockParameterName.LATEST).send()
        val gasPriceResponse = web3j.ethGasPrice().send()
        val coopAddress = applicationProperties.contracts.coopAddress

        return RawTransaction.createTransaction(
                txCountResponse.transactionCount,
                gasPriceResponse.gasPrice,
                BigInteger.valueOf(4000000),
                coopAddress,
                encodedFunction
        )
    }

    override fun getOrganizations(): List<String> {
        val function = Function(
                "getOrganizations",
                emptyList(),
                listOf(object : TypeReference<DynamicArray<Address>>() {})
        )
        val encodedFunction = FunctionEncoder.encode(function)
        val coopAddress = applicationProperties.contracts.coopAddress

        val response = web3j.ethCall(
                Transaction.createEthCallTransaction(
                        coopAddress,
                        coopAddress,
                        encodedFunction
                ),
                DefaultBlockParameterName.LATEST
        ).send()
        val returnValues = FunctionReturnDecoder.decode(response.value, function.outputParameters)
        val organizations = returnValues[0].value as List<Address>
        return organizations.map { it.toString() }
    }

    override fun isWalletActive(wallet: String): Boolean {
        val function = Function(
                "isWalletActive",
                listOf(Address(wallet)),
                listOf(TypeReference.create(Bool::class.java))
        )
        val encodedFunction = FunctionEncoder.encode(function)
        val coopAddress = applicationProperties.contracts.coopAddress

        val response = web3j.ethCall(
                Transaction.createEthCallTransaction(
                        wallet,
                        coopAddress,
                        encodedFunction
                ),
                DefaultBlockParameterName.LATEST
        ).send()

        val returnValues = FunctionReturnDecoder.decode(response.value, function.outputParameters)

        return returnValues[0].value as Boolean
    }

    override fun isOrganizationActive(organization: String): Boolean {
        val function = Function(
                "isOrganizationActive",
                listOf(Address(organization)),
                listOf(TypeReference.create(Bool::class.java))
        )
        val encodedFunction = FunctionEncoder.encode(function)
        val coopAddress = applicationProperties.contracts.coopAddress

        val response = web3j.ethCall(
                Transaction.createEthCallTransaction(
                        organization,
                        coopAddress,
                        encodedFunction
                ),
                DefaultBlockParameterName.LATEST
        ).send()

        val returnValues = FunctionReturnDecoder.decode(response.value, function.outputParameters)

        return returnValues[0].value as Boolean
    }
}