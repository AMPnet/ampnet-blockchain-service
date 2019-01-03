package com.ampnet.crowdfunding.blockchain.contract.impl

import com.ampnet.crowdfunding.blockchain.contract.ProjectService
import org.springframework.stereotype.Service
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.RawTransaction
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import java.math.BigDecimal
import java.math.BigInteger

@Service
class ProjectServiceImpl(val web3j: Web3j) : ProjectService {

    override fun generateWithdrawFundsTransaction(
        project: String,
        from: String,
        tokenIssuer: String,
        amount: BigDecimal
    ): RawTransaction {
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
                project,
                encodedFunction
        )
    }
}