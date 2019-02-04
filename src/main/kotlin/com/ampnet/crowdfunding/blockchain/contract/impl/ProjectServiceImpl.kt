package com.ampnet.crowdfunding.blockchain.contract.impl

import com.ampnet.crowdfunding.blockchain.contract.ProjectService
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
class ProjectServiceImpl(val web3j: Web3j) : ProjectService {

    override fun generateWithdrawFundsTx(
        from: String,
        project: String,
        tokenIssuer: String,
        amount: BigInteger
    ): RawTransaction {
        val function = Function(
                "withdrawFunds",
                listOf(Address(tokenIssuer), Uint256(amount)),
                emptyList()
        )
        val encodedFunction = FunctionEncoder.encode(function)
        val txCountResponse = web3j.ethGetTransactionCount(from, DefaultBlockParameterName.LATEST).send()
        val gasPriceResponse = web3j.ethGasPrice().send()

        return RawTransaction.createTransaction(
                txCountResponse.transactionCount,
                gasPriceResponse.gasPrice,
                BigInteger.valueOf(1000000),
                project,
                encodedFunction
        )
    }

    override fun generateTransferOwnershipTx(
        from: String,
        project: String,
        to: String,
        amount: BigInteger
    ): RawTransaction {
        val function = Function(
                "transferOwnership",
                listOf(Address(to), Uint256(amount)),
                emptyList()
        )
        val encodedFunction = FunctionEncoder.encode(function)
        val txCountResponse = web3j.ethGetTransactionCount(from, DefaultBlockParameterName.LATEST).send()
        val gasPriceResponse = web3j.ethGasPrice().send()

        return RawTransaction.createTransaction(
                txCountResponse.transactionCount,
                gasPriceResponse.gasPrice,
                BigInteger.valueOf(1000000),
                project,
                encodedFunction
        )
    }

    override fun generateCancelInvestmentTx(
        from: String,
        project: String,
        amount: BigInteger
    ): RawTransaction {
        val function = Function(
                "cancelInvestment",
                listOf(Uint256(amount)),
                emptyList()
        )
        val encodedFunction = FunctionEncoder.encode(function)
        val txCountResponse = web3j.ethGetTransactionCount(from, DefaultBlockParameterName.LATEST).send()
        val gasPriceResponse = web3j.ethGasPrice().send()

        return RawTransaction.createTransaction(
                txCountResponse.transactionCount,
                gasPriceResponse.gasPrice,
                BigInteger.valueOf(1000000),
                project,
                encodedFunction
        )
    }

    override fun getMaxInvestmentPerUser(project: String): BigInteger {
        val function = Function(
                "getMaxInvestmentPerUser",
                emptyList(),
                listOf(TypeReference.create(Uint256::class.java))
        )
        val encodedFunction = FunctionEncoder.encode(function)

        val response = web3j.ethCall(
                Transaction.createEthCallTransaction(
                        project,
                        project,
                        encodedFunction
                ),
                DefaultBlockParameterName.LATEST
        ).send()

        val returnValues = FunctionReturnDecoder.decode(response.value, function.outputParameters)

        return returnValues[0].value as BigInteger
    }

    override fun getMinInvestmentPerUser(project: String): BigInteger {
        val function = Function(
                "getMinInvestmentPerUser",
                emptyList(),
                listOf(TypeReference.create(Uint256::class.java))
        )
        val encodedFunction = FunctionEncoder.encode(function)

        val response = web3j.ethCall(
                Transaction.createEthCallTransaction(
                        project,
                        project,
                        encodedFunction
                ),
                DefaultBlockParameterName.LATEST
        ).send()

        val returnValues = FunctionReturnDecoder.decode(response.value, function.outputParameters)

        return returnValues[0].value as BigInteger
    }

    override fun getInvestmentCap(project: String): BigInteger {
        val function = Function(
                "getInvestmentCap",
                emptyList(),
                listOf(TypeReference.create(Uint256::class.java))
        )
        val encodedFunction = FunctionEncoder.encode(function)

        val response = web3j.ethCall(
                Transaction.createEthCallTransaction(
                        project,
                        project,
                        encodedFunction
                ),
                DefaultBlockParameterName.LATEST
        ).send()

        val returnValues = FunctionReturnDecoder.decode(response.value, function.outputParameters)

        return returnValues[0].value as BigInteger
    }

    override fun getCurrentTotalInvestment(project: String): BigInteger {
        val function = Function(
                "getCurrentTotalInvestment",
                emptyList(),
                listOf(TypeReference.create(Uint256::class.java))
        )
        val encodedFunction = FunctionEncoder.encode(function)

        val response = web3j.ethCall(
                Transaction.createEthCallTransaction(
                        project,
                        project,
                        encodedFunction
                ),
                DefaultBlockParameterName.LATEST
        ).send()

        val returnValues = FunctionReturnDecoder.decode(response.value, function.outputParameters)

        return returnValues[0].value as BigInteger
    }

    override fun getTotalInvestmentForUser(project: String, user: String): BigInteger {
        val function = Function(
                "getTotalInvestmentForUser",
                listOf(Address(user)),
                listOf(TypeReference.create(Uint256::class.java))
        )
        val encodedFunction = FunctionEncoder.encode(function)

        val response = web3j.ethCall(
                Transaction.createEthCallTransaction(
                        project,
                        project,
                        encodedFunction
                ),
                DefaultBlockParameterName.LATEST
        ).send()

        val returnValues = FunctionReturnDecoder.decode(response.value, function.outputParameters)

        return returnValues[0].value as BigInteger
    }

    override fun isLockedForInvestments(project: String): Boolean {
        val function = Function(
                "isLockedForInvestments",
                emptyList(),
                listOf(TypeReference.create(Bool::class.java))
        )
        val encodedFunction = FunctionEncoder.encode(function)

        val response = web3j.ethCall(
                Transaction.createEthCallTransaction(
                        project,
                        project,
                        encodedFunction
                ),
                DefaultBlockParameterName.LATEST
        ).send()

        val returnValues = FunctionReturnDecoder.decode(response.value, function.outputParameters)

        return returnValues[0].value as Boolean
    }
}