package com.ampnet.crowdfunding.blockchain.contract.impl

import com.ampnet.crowdfunding.blockchain.config.ApplicationProperties
import com.ampnet.crowdfunding.blockchain.contract.EurService
import org.springframework.stereotype.Service
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.RawTransaction
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import java.math.BigInteger

@Service
class EurServiceImpl(
    val properties: ApplicationProperties,
    val web3j: Web3j
) : EurService {

    override fun balanceOf(address: String): BigInteger {
        val function = Function(
                "balanceOf",
                listOf(Address(address)),
                listOf(TypeReference.create(Uint256::class.java))
        )
        val encodedFunction = FunctionEncoder.encode(function)
        val response = web3j.ethCall(
                Transaction.createEthCallTransaction(
                        address,
                        properties.contracts.eurAddress,
                        encodedFunction
                ),
                DefaultBlockParameterName.LATEST
        ).send()

        val returnValues = FunctionReturnDecoder.decode(response.value, function.outputParameters)
        val balance = returnValues[0] as Uint256
        return balance.value
    }

    override fun generateMintTx(from: String, to: String, amount: BigInteger): RawTransaction {
        val function = Function(
                "mint",
                listOf(Address(to), Uint256(amount)),
                listOf(TypeReference.create(Bool::class.java))
        )
        val encodedFunction = FunctionEncoder.encode(function)
        val txCountResponse = web3j.ethGetTransactionCount(from, DefaultBlockParameterName.LATEST).send()
        val gasPriceResponse = web3j.ethGasPrice().send()

        return RawTransaction.createTransaction(
                txCountResponse.transactionCount,
                gasPriceResponse.gasPrice,
                BigInteger.valueOf(1000000),
                properties.contracts.eurAddress,
                encodedFunction
        )
    }

    override fun generateBurnFromTx(from: String, burnFrom: String, amount: BigInteger): RawTransaction {
        val function = Function(
                "burnFrom",
                listOf(Address(burnFrom), Uint256(amount)),
                listOf(TypeReference.create(Bool::class.java))
        )
        val encodedFunction = FunctionEncoder.encode(function)
        val txCountResponse = web3j.ethGetTransactionCount(from, DefaultBlockParameterName.LATEST).send()
        val gasPriceResponse = web3j.ethGasPrice().send()

        return RawTransaction.createTransaction(
                txCountResponse.transactionCount,
                gasPriceResponse.gasPrice,
                BigInteger.valueOf(1000000),
                properties.contracts.eurAddress,
                encodedFunction
        )
    }

    override fun generateApproveTx(from: String, amount: BigInteger): RawTransaction {
        val issuingAuthority = properties.accounts.issuingAuthorityAddress
        val function = Function(
                "approve",
                listOf(Address(issuingAuthority), Uint256(amount)),
                emptyList()
        )
        val encodedFunction = FunctionEncoder.encode(function)
        val txCountResponse = web3j.ethGetTransactionCount(from, DefaultBlockParameterName.LATEST).send()
        val gasPriceResponse = web3j.ethGasPrice().send()

        return RawTransaction.createTransaction(
                txCountResponse.transactionCount,
                gasPriceResponse.gasPrice,
                BigInteger.valueOf(1000000),
                properties.contracts.eurAddress,
                encodedFunction
        )
    }

    override fun generateInvestTx(from: String, project: String, amount: BigInteger): RawTransaction {
        val function = Function(
                "invest",
                listOf(Address(project), Uint256(amount)),
                emptyList()
        )
        val encodedFunction = FunctionEncoder.encode(function)
        val txCountResponse = web3j.ethGetTransactionCount(from, DefaultBlockParameterName.LATEST).send()
        val gasPriceResponse = web3j.ethGasPrice().send()

        return RawTransaction.createTransaction(
                txCountResponse.transactionCount,
                gasPriceResponse.gasPrice,
                BigInteger.valueOf(1000000),
                properties.contracts.eurAddress,
                encodedFunction
        )
    }

    override fun generateTransferTx(from: String, to: String, amount: BigInteger): RawTransaction {
        val function = Function(
                "transfer",
                listOf(Address(to), Uint256(amount)),
                listOf(TypeReference.create(Bool::class.java))
        )
        val encodedFunction = FunctionEncoder.encode(function)
        val txCountResponse = web3j.ethGetTransactionCount(from, DefaultBlockParameterName.LATEST).send()
        val gasPriceResponse = web3j.ethGasPrice().send()

        return RawTransaction.createTransaction(
                txCountResponse.transactionCount,
                gasPriceResponse.gasPrice,
                BigInteger.valueOf(1000000),
                properties.contracts.eurAddress,
                encodedFunction
        )
    }
}