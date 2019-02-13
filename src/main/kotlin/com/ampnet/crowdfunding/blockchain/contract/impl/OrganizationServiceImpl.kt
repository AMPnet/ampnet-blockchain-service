package com.ampnet.crowdfunding.blockchain.contract.impl

import com.ampnet.crowdfunding.blockchain.config.ApplicationProperties
import com.ampnet.crowdfunding.blockchain.contract.OrganizationService
import org.springframework.stereotype.Service
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.RawTransaction
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import java.math.BigInteger

@Service
class OrganizationServiceImpl(
    val web3j: Web3j,
    val properties: ApplicationProperties
) : OrganizationService {

    override fun generateActivateTx(from: String, organization: String): RawTransaction {
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

    override fun generateWithdrawFundsTx(from: String, organization: String, amount: BigInteger): RawTransaction {
        val issuingAuthority = properties.accounts.issuingAuthorityAddress
        val function = Function(
                "withdrawFunds",
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
                organization,
                encodedFunction
        )
    }

    override fun generateAddMemberTx(from: String, organization: String, member: String): RawTransaction {
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
        from: String,
        organization: String,
        maxInvestmentPerUser: BigInteger,
        minInvestmentPerUser: BigInteger,
        investmentCap: BigInteger
    ): RawTransaction {
        val function = Function(
                "addProject",
                listOf(
                        Uint256(maxInvestmentPerUser),
                        Uint256(minInvestmentPerUser),
                        Uint256(investmentCap)
                ),
                emptyList()
        )
        val encodedFunction = FunctionEncoder.encode(function)
        val txCountResponse = web3j.ethGetTransactionCount(from, DefaultBlockParameterName.LATEST).send()
        val gasPriceResponse = web3j.ethGasPrice().send()

        return RawTransaction.createTransaction(
                txCountResponse.transactionCount,
                gasPriceResponse.gasPrice,
                BigInteger.valueOf(6000000),
                organization,
                encodedFunction
        )
    }

    override fun isVerified(organization: String): Boolean {
        val function = Function(
                "isVerified",
                emptyList(),
                listOf(TypeReference.create(Bool::class.java))
        )
        val encodedFunction = FunctionEncoder.encode(function)

        val response = web3j.ethCall(
                Transaction.createEthCallTransaction(
                        organization,
                        organization,
                        encodedFunction
                ),
                DefaultBlockParameterName.LATEST
        ).send()

        val returnValues = FunctionReturnDecoder.decode(response.value, function.outputParameters)

        return returnValues[0].value as Boolean
    }

    override fun getAllProjects(organization: String): List<String> {
        val function = Function(
                "getAllProjects",
                emptyList(),
                listOf(object : TypeReference<DynamicArray<Address>>() {})
        )
        val encodedFunction = FunctionEncoder.encode(function)

        val response = web3j.ethCall(
                Transaction.createEthCallTransaction(
                        organization,
                        organization,
                        encodedFunction
                ),
                DefaultBlockParameterName.LATEST
        ).send()

        val returnValues = FunctionReturnDecoder.decode(response.value, function.outputParameters)
        val projects = returnValues[0].value as List<Address>
        return projects.map { it.toString() }
    }

    override fun getMembers(organization: String): List<String> {
        val function = Function(
                "getMembers",
                emptyList(),
                listOf(object : TypeReference<DynamicArray<Address>>() {})
        )
        val encodedFunction = FunctionEncoder.encode(function)

        val response = web3j.ethCall(
                Transaction.createEthCallTransaction(
                        organization,
                        organization,
                        encodedFunction
                ),
                DefaultBlockParameterName.LATEST
        ).send()

        val returnValues = FunctionReturnDecoder.decode(response.value, function.outputParameters)
        val members = returnValues[0].value as List<Address>
        return members.map { it.toString() }
    }
}