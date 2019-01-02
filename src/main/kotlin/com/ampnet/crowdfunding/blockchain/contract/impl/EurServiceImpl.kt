package com.ampnet.crowdfunding.blockchain.contract.impl

import com.ampnet.crowdfunding.blockchain.config.ApplicationProperties
import com.ampnet.crowdfunding.blockchain.contract.TransactionService
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
import org.web3j.utils.Convert
import java.math.BigDecimal
import java.math.BigInteger

@Service
class EurServiceImpl(
        val properties: ApplicationProperties,
        val web3j: Web3j
): EurService  {

    override fun balanceOf(address: String): BigDecimal {
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
        val balanceBigDecimal = balance.value.toBigDecimal()
        return Convert.fromWei(balanceBigDecimal, Convert.Unit.ETHER)
    }

    override fun generateMintTransaction(from: String, to: String, amount: BigDecimal): RawTransaction {
        val function = Function(
                "mint",
                listOf(Address(to), Uint256(amount.longValueExact())),
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

    override fun generateBurnFromTransaction(from: String, burnFrom: String, amount: BigDecimal): RawTransaction {
        val function = Function(
                "burnFrom",
                listOf(Address(burnFrom), Uint256(amount.longValueExact())),
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

    override fun generateApproveTransaction(from: String, approve: String, amount: BigDecimal): RawTransaction {
        val function = Function(
                "approve",
                listOf(Address(approve), Uint256(amount.longValueExact())),
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

}