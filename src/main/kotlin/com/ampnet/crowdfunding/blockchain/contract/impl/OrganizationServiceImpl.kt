package com.ampnet.crowdfunding.blockchain.contract.impl

import com.ampnet.crowdfunding.blockchain.contract.OrganizationService
import org.springframework.stereotype.Service
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.RawTransaction
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import java.math.BigDecimal
import java.math.BigInteger

@Service
class OrganizationServiceImpl(val web3j: Web3j) : OrganizationService {

    override fun generateActivateTx(organization: String, from: String): RawTransaction {
        val function = Function(
                "activate",
                emptyList(),
                emptyList()
        )
        val encodedFunction = FunctionEncoder.encode(function)
        val txCountResponse = web3j.ethGetTransactionCount(from, DefaultBlockParameterName.LATEST).send()
        val gasPriceResponse = web3j.ethGasPrice().send()

        return RawTransaction.createTransaction(
                txCountResponse.transactionCount,
                gasPriceResponse.gasPrice,
                BigInteger.valueOf(1000000),
                organization,
                encodedFunction
        )
    }

    override fun generateWithdrawFundsTx(organization: String, tokenIssuer: String, from: String, amount: BigDecimal): RawTransaction {
        val function = Function(
                "withdrawFunds",
                listOf(Address(tokenIssuer), Uint256(amount.longValueExact())),
                emptyList()
        )
        val encodedFunction = FunctionEncoder.encode(function)
        val txCountResponse = web3j.ethGetTransactionCount(from, DefaultBlockParameterName.LATEST).send()
        val gasPriceResponse = web3j.ethGasPrice().send()

        return RawTransaction.createTransaction(
                txCountResponse.transactionCount,
                gasPriceResponse.gasPrice,
                BigInteger.valueOf(1000000),
                organization,
                encodedFunction
        )
    }

    override fun generateAddMemberTx(organization: String, from: String, member: String): RawTransaction {
        val function = Function(
                "addMember",
                listOf(Address(member)),
                emptyList()
        )
        val encodedFunction = FunctionEncoder.encode(function)
        val txCountResponse = web3j.ethGetTransactionCount(from, DefaultBlockParameterName.LATEST).send()
        val gasPriceResponse = web3j.ethGasPrice().send()

        return RawTransaction.createTransaction(
                txCountResponse.transactionCount,
                gasPriceResponse.gasPrice,
                BigInteger.valueOf(1000000),
                organization,
                encodedFunction
        )
    }

    override fun generateAddProjectTx(
        organization: String,
        from: String,
        name: String,
        description: String,
        maxInvestmentPerUser: BigDecimal,
        minInvestmentPerUser: BigDecimal,
        investmentCap: BigDecimal
    ): RawTransaction {
        val function = Function(
                "addProject",
                listOf(
                        Utf8String(name),
                        Utf8String(description),
                        Uint256(maxInvestmentPerUser.longValueExact()),
                        Uint256(minInvestmentPerUser.longValueExact()),
                        Uint256(investmentCap.longValueExact())
                ),
                emptyList()
        )
        val encodedFunction = FunctionEncoder.encode(function)
        val txCountResponse = web3j.ethGetTransactionCount(from, DefaultBlockParameterName.LATEST).send()
        val gasPriceResponse = web3j.ethGasPrice().send()

        return RawTransaction.createTransaction(
                txCountResponse.transactionCount,
                gasPriceResponse.gasPrice,
                BigInteger.valueOf(1000000),
                organization,
                encodedFunction
        )
    }
}